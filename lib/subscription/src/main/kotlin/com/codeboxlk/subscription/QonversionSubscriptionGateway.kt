package com.codeboxlk.subscription

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.eligibility.QEligibility
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.dto.products.QSubscriptionPeriod
import com.qonversion.android.sdk.listeners.QonversionEligibilityCallback
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionProductsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "Subscription"

/**
 * Hard ceiling on the FIRST entitlement resolution.
 *
 * This is a correctness bound, not a nicety: consumers are told [entitlement]
 * leaves [Entitlement.Loading], and some of them wait on exactly that before
 * metering a paid feature. Qonversion's callbacks carry no timeout of their own,
 * so a callback that never fires would strand those callers forever. Unresolved
 * therefore has to decay to a decision, and the safe decision is "not paid".
 */
private const val FIRST_RESOLUTION_TIMEOUT_MS = 8_000L

/**
 * Ceiling on the network calls a user is watching a spinner for — restore, and
 * the product lookup that precedes a purchase. Deliberately NOT applied to the
 * purchase itself: that one is slow because the Play sheet is open and the user
 * is typing, and cancelling it from under them would be worse than waiting.
 */
private const val STORE_CALL_TIMEOUT_MS = 15_000L

private fun timedOut(what: String) = Result.failure<Nothing>(SubscriptionFailure.StoreError("$what timed out"))

/**
 * Qonversion-backed [SubscriptionGateway]. The SDK never escapes this file — the
 * host still only sees [SubscriptionGateway] / [Entitlement], so this library
 * stays droppable into another app (Ring 1 rule: zero project dependencies).
 *
 * ## Why the config arrives as a suspending provider
 * The project key is not necessarily known when the DI graph is built: hosts that
 * serve it from a remote config have nothing on a cold first launch. Taking a
 * plain [SubscriptionConfig] would therefore hard-wire "no billing" for the whole
 * of a user's first session. Instead the SDK is initialised **lazily, once**, at
 * the first moment anything actually needs it, by which time the host's provider
 * can answer. A host that hardcodes its key just returns immediately.
 *
 * ## Why a blank key is not an error
 * A brand without subscriptions, and a first launch that never reached the
 * network, both legitimately produce no key. Both resolve [entitlement] to
 * [Entitlement.Free] — a RESOLVED state, so entitlement gates unblock — while
 * purchase/restore fail with [SubscriptionFailure.NotConfigured]. Behaviourally
 * identical to [NoOpSubscriptionGateway], which stays the compiled-in fallback
 * for hosts that want no SDK at all.
 *
 * ## Threading
 * Qonversion is callback-only (no suspend API), and its purchase flow launches an
 * Activity, so `initialize` and `purchase` are marshalled onto the main thread
 * here rather than trusting the caller's dispatcher.
 */
class QonversionSubscriptionGateway(
    private val application: Application,
    private val activityProvider: ActivityProvider,
    scope: CoroutineScope,
    private val configProvider: suspend () -> SubscriptionConfig,
) : SubscriptionGateway {
    private val state = MutableStateFlow<Entitlement>(Entitlement.Loading)

    override val entitlement: Flow<Entitlement> = state.asStateFlow()

    private val productState = MutableStateFlow<StorePrices>(StorePrices.Loading)

    override val products: Flow<StorePrices> = productState.asStateFlow()

    private val initLock = Mutex()

    @Volatile
    private var sdk: Qonversion? = null

    init {
        // Resolve eagerly: FeatureAccess.awaitResolved() blocks on the first
        // non-Loading value, so nothing may wait for a UI event to start this.
        scope.launch { refresh() }
        // Independent of the entitlement resolve, deliberately: a paywall opened
        // before entitlement settles still needs its prices, and a store lookup
        // that stalls must not hold the entitlement gate behind it.
        scope.launch { refreshPrices() }
    }

    /**
     * Fetches what the store charges THIS user, and whether they still have a
     * trial coming, then publishes a [StorePrices] the host can render without
     * guessing: Loading while the call is out, Known on an answer, Unavailable
     * when there is nothing to reach.
     *
     * Public and re-runnable on purpose. As a one-shot it was a dead end: an
     * offline first launch, or one store error, left the paywall unable to sell
     * anything for the whole life of the process, with a permanently disabled
     * button and no way back but killing the app. Hosts call this on every open.
     */
    override suspend fun refreshPrices() {
        productState.value = StorePrices.Loading
        val instance =
            ensureReady() ?: run {
                // No key: settled, not pending. Saying "loading" here would spin
                // forever on a brand that simply has no billing.
                productState.value = StorePrices.Unavailable
                return
            }
        val catalogue =
            withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { loadProducts(instance) }
                ?.getOrNull()
                ?: run {
                    Log.w(TAG, "Store prices unresolved — the paywall will offer a retry")
                    productState.value = StorePrices.Unavailable
                    return
                }
        // Eligibility is per ACCOUNT, not per product: a user who already spent
        // the intro offer is not getting another one, and printing "7-day free
        // trial" at them would be the same lie as printing the wrong currency.
        // An unresolved answer is treated as NOT eligible — under-promising is
        // recoverable, over-promising is a policy problem.
        val eligibility =
            withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { checkTrialEligibility(instance, catalogue.keys.toList()) }
                .orEmpty()
        // The SDK's own types stop here. Everything that DECIDES anything below
        // this line is pure and tested — see `storePricesFrom`.
        productState.value =
            storePricesFrom(
                catalogue.map { (offeringId, product) ->
                    val eligible = eligibility[offeringId] == QIntroEligibilityStatus.Eligible
                    val trial = product.trialPeriod.takeIf { eligible }
                    StoreProductFacts(
                        offeringId = offeringId,
                        price = product.prettyPrice,
                        trialDays = trial?.exactDays(),
                        hasTrial = trial != null,
                    )
                },
            )
    }

    override suspend fun purchase(offeringId: String): Result<Entitlement> {
        val instance = ensureReady() ?: return Result.failure(SubscriptionFailure.NotConfigured())
        val activity =
            activityProvider.current()
                ?: return Result.failure(SubscriptionFailure.NoForegroundActivity())
        val products =
            withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { loadProducts(instance) }
                ?: timedOut("Product lookup")
        val product =
            products.getOrElse { return Result.failure(it) }[offeringId]
                ?: return Result.failure(SubscriptionFailure.ProductUnavailable(offeringId))

        val outcome =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    instance.purchase(
                        activity,
                        product,
                        object : QonversionPurchaseCallback {
                            override fun onResult(result: QPurchaseResult) = continuation.resume(result)
                        },
                    )
                }
            }
        return outcome.toResult()
    }

    override suspend fun restore(): Result<Entitlement> {
        val instance = ensureReady() ?: return Result.failure(SubscriptionFailure.NotConfigured())
        // Bounded: the paywall shows a spinner for the whole of this, and a
        // spinner with no exit is the dead end EDGE_CASES forbids.
        return (withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { restorePurchases(instance) } ?: timedOut("Restore"))
            .onSuccess { state.value = it }
    }

    /**
     * Re-reads the provider's entitlement state and publishes it. Every exit —
     * error, silence, timeout — ends at a resolved value; see
     * [FIRST_RESOLUTION_TIMEOUT_MS].
     */
    private suspend fun refresh() {
        val instance = ensureReady() ?: return
        val resolved =
            withTimeoutOrNull(FIRST_RESOLUTION_TIMEOUT_MS) { checkEntitlements(instance) }
                ?: timedOut("Entitlement check")
        resolved
            .onSuccess { state.value = it }
            .onFailure {
                Log.w(TAG, "Entitlement unresolved — treating as Free", it)
                state.value = Entitlement.Free
            }
    }

    /**
     * Initialises the SDK at most once. Returns null when this build has no key,
     * having first resolved [entitlement] so nothing waits on it.
     */
    private suspend fun ensureReady(): Qonversion? {
        sdk?.let { return it }
        return initLock.withLock {
            sdk ?: createSdk()
        }
    }

    private suspend fun createSdk(): Qonversion? {
        val config = configProvider()
        if (config.projectKey.isBlank()) {
            state.value = Entitlement.Free
            return null
        }
        return runCatching {
            withContext(Dispatchers.Main) {
                Qonversion.initialize(
                    QonversionConfig
                        .Builder(application, config.projectKey, QLaunchMode.SubscriptionManagement)
                        .build(),
                )
            }
        }.onFailure {
            Log.e(TAG, "Qonversion initialisation failed", it)
            state.value = Entitlement.Free
        }.onSuccess { sdk = it }
            .getOrNull()
    }

    private suspend fun checkEntitlements(instance: Qonversion): Result<Entitlement> =
        suspendCancellableCoroutine { continuation ->
            instance.checkEntitlements(entitlementsCallback(continuation))
        }

    /**
     * The recovery path, and NOT [checkEntitlements].
     *
     * `checkEntitlements` answers "what does the CURRENT identity own" — on a
     * reinstall or a new phone that identity is a fresh anonymous one, so a
     * paying subscriber would be told they have nothing to restore. `restore`
     * is the call that reads the store's own purchase history and re-attaches
     * it to this identity; verified present on the SDK we ship
     * (`javap` on sdk-9.7.0.aar: `restore(QonversionEntitlementsCallback)`).
     *
     * Play requires a working restore path for a subscription app, so this
     * distinction is a policy obligation, not a refinement.
     */
    private suspend fun restorePurchases(instance: Qonversion): Result<Entitlement> =
        suspendCancellableCoroutine { continuation ->
            instance.restore(entitlementsCallback(continuation))
        }

    /** Shared bridge: both entitlement calls answer on the same callback type. */
    private fun entitlementsCallback(continuation: CancellableContinuation<Result<Entitlement>>) =
        object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                continuation.resume(Result.success(entitlements.toEntitlement()))

            override fun onError(error: QonversionError) =
                continuation.resume(Result.failure(SubscriptionFailure.StoreError(error.toString())))
        }

    private suspend fun loadProducts(instance: Qonversion): Result<Map<String, QProduct>> =
        suspendCancellableCoroutine { continuation ->
            instance.products(
                object : QonversionProductsCallback {
                    override fun onSuccess(products: Map<String, QProduct>) =
                        continuation.resume(Result.success(products))

                    override fun onError(error: QonversionError) =
                        continuation.resume(Result.failure(SubscriptionFailure.StoreError(error.toString())))
                },
            )
        }

    /** Empty on any error — an unknown eligibility must read as "no trial", never as "yes". */
    private suspend fun checkTrialEligibility(
        instance: Qonversion,
        offeringIds: List<String>,
    ): Map<String, QIntroEligibilityStatus> =
        suspendCancellableCoroutine { continuation ->
            instance.checkTrialIntroEligibility(
                offeringIds,
                object : QonversionEligibilityCallback {
                    override fun onSuccess(eligibilities: Map<String, QEligibility>) =
                        continuation.resume(eligibilities.mapValues { it.value.status })

                    override fun onError(error: QonversionError) {
                        Log.w(TAG, "Trial eligibility unresolved — treating as not eligible: $error")
                        continuation.resume(emptyMap())
                    }
                },
            )
        }

    /**
     * Publishes on success so a purchase and the entitlement flow can never
     * disagree, and keeps the store's own outcomes distinguishable.
     */
    private fun QPurchaseResult.toResult(): Result<Entitlement> =
        when {
            isSuccessful -> Result.success(entitlements.toEntitlement().also { state.value = it })
            isCanceledByUser -> Result.failure(SubscriptionFailure.Cancelled())
            isPending -> Result.failure(SubscriptionFailure.Pending())
            else -> Result.failure(SubscriptionFailure.StoreError(error?.toString() ?: "Purchase failed"))
        }
}

/**
 * Single-paid-tier reading: ANY active entitlement means paid, and its id names
 * the purchase. Matching on specific ids here would make a dashboard rename a
 * silent revenue outage.
 */
private fun Map<String, QEntitlement>.toEntitlement(): Entitlement =
    values.firstOrNull { it.isActive }?.let { Entitlement.Paid(it.id) } ?: Entitlement.Free

/**
 * The trial length in days, but only when the store's own unit converts without
 * rounding.
 *
 * A day is a day and a billing week is exactly seven of them, so both are
 * exact. A month is not: printing "30-day free trial" for a P1M offer would be
 * wrong in seven months of the year, and this whole change exists to stop the
 * paywall stating figures it cannot source. Those periods return null and the
 * host falls back to naming the trial without a number — `hasTrial` still
 * carries the fact that one exists.
 */
private fun QSubscriptionPeriod.exactDays(): Int? =
    when (unit) {
        QSubscriptionPeriod.Unit.Day -> unitCount
        QSubscriptionPeriod.Unit.Week -> unitCount * DAYS_PER_WEEK
        else -> null
    }

private const val DAYS_PER_WEEK = 7

/**
 * What the provider told us about one product, with its SDK types already
 * stripped off. Exists so the rule below can be tested without a Play
 * connection, an Activity, or a mocking framework.
 */
internal data class StoreProductFacts(
    val offeringId: String,
    val price: String?,
    val trialDays: Int?,
    val hasTrial: Boolean,
)

/**
 * The rule: **a product without a price is not published.**
 *
 * This is the invariant the purchase gate rests on — the host asks the map
 * whether it knows what the selected plan costs, and arms its button on the
 * answer. A product the store answered for but priced at nothing would put an
 * entry in that map and arm the button over a blank card. Reachable whenever a
 * Qonversion product exists whose Play base plan is unpublished or unavailable
 * in the buyer's country, which the SDK reports as success with no store
 * details.
 *
 * It is a named function rather than a `filterValues` in the middle of a
 * coroutine because a review round proved what the inline version was worth:
 * deleting it left the entire test suite green. A rule nothing can fail is not
 * enforced, it is merely written down.
 */
internal fun storePricesFrom(facts: List<StoreProductFacts>): StorePrices.Known =
    StorePrices.Known(
        facts
            .filter { !it.price.isNullOrBlank() }
            .associate { fact ->
                fact.offeringId to
                    SubscriptionProduct(
                        offeringId = fact.offeringId,
                        price = fact.price.orEmpty(),
                        trialDays = fact.trialDays,
                        hasTrial = fact.hasTrial,
                    )
            },
    )
