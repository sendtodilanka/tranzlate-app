package com.codeboxlk.subscription

import android.app.Application
import android.util.Log
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.QonversionConfig
import com.qonversion.android.sdk.dto.QLaunchMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

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
 * Qonversion-backed [SubscriptionGateway]. The SDK never escapes this module —
 * its types stop at [QonversionApi], the host still only sees
 * [SubscriptionGateway] / [Entitlement], so this library stays droppable into
 * another app (Ring 1 rule: zero project dependencies). Everything above the
 * seam — including this class — is testable on a plain JVM against a fake
 * [QonversionApi]; only [RealQonversionApi] needs the store.
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
 * Activity, so `initialize` (here, via the default [sessionFactory]) and
 * `purchase` (in [RealQonversionApi]) are marshalled onto the main thread rather
 * than trusting the caller's dispatcher.
 */
class QonversionSubscriptionGateway internal constructor(
    private val application: Application,
    private val activityProvider: ActivityProvider,
    scope: CoroutineScope,
    private val configProvider: suspend () -> SubscriptionConfig,
    // Injectable seam for tests; production uses ::initialiseQonversion below.
    // Returns null when the SDK could not come up — the gateway then behaves
    // exactly like the blank-key case. The blank-key check itself stays HERE
    // (createSession), before the factory, so no factory can accidentally
    // initialise a store SDK against an empty key.
    private val sessionFactory: suspend (Application, SubscriptionConfig) -> QonversionApi?,
) : SubscriptionGateway {
    /**
     * Production entry point — the signature the host has always called. Kept as
     * a secondary constructor because the primary one names [QonversionApi],
     * which is internal and may not appear in a public signature.
     */
    constructor(
        application: Application,
        activityProvider: ActivityProvider,
        scope: CoroutineScope,
        configProvider: suspend () -> SubscriptionConfig,
    ) : this(application, activityProvider, scope, configProvider, ::initialiseQonversion)

    private val state = MutableStateFlow<Entitlement>(Entitlement.Loading)

    override val entitlement: Flow<Entitlement> = state.asStateFlow()

    private val productState = MutableStateFlow<StorePrices>(StorePrices.Loading)

    override val products: Flow<StorePrices> = productState.asStateFlow()

    private val initLock = Mutex()

    @Volatile
    private var session: QonversionApi? = null

    /** See [refreshPrices] for why this exists. */
    private val priceRefreshInFlight = AtomicBoolean(false)

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
     *
     * At most ONE refresh runs at a time; a call that arrives while one is in
     * flight returns immediately without touching [productState]. WHY: two
     * concurrent refreshes race their publications, and last-writer-wins could
     * wipe a rendered Known with a stale Unavailable — and a retry tapped while
     * the first attempt is still out must not double-fetch either.
     */
    override suspend fun refreshPrices() {
        if (!priceRefreshInFlight.compareAndSet(false, true)) return
        try {
            productState.value = StorePrices.Loading
            val session =
                ensureReady() ?: run {
                    // No key: settled, not pending. Saying "loading" here would spin
                    // forever on a brand that simply has no billing.
                    productState.value = StorePrices.Unavailable
                    return
                }
            val catalogue =
                withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { session.products() }
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
                withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { session.eligibility(catalogue.map { it.offeringId }) }
                    .orEmpty()
            // Everything that DECIDES anything below this line is pure and
            // tested — see `productFacts` (trial rules) and `storePricesFrom`
            // (blank-price rule). A store that priced nothing still lands here:
            // that is an ANSWER, so it publishes Known(empty), never Unavailable.
            productState.value =
                storePricesFrom(catalogue.map { productFacts(it, eligibility[it.offeringId]) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Deliberately generic: the whole point is that we cannot know what
            // a misbehaving provider SDK throws, and anything non-cancellation
            // escaping here rides the host's bare launch to a process crash.
            @Suppress("TooGenericExceptionCaught") thrown: Exception,
        ) {
            // The seam's contract is answer-by-value, but a provider SDK that
            // throws synchronously would otherwise ride the caller's bare
            // viewModelScope.launch straight to an app crash — with the state
            // stranded at Loading. Every failure lands at Unavailable, where
            // the retry affordance lives.
            Log.w(TAG, "Store price refresh threw — the paywall will offer a retry", thrown)
            productState.value = StorePrices.Unavailable
        } finally {
            priceRefreshInFlight.set(false)
        }
    }

    override suspend fun purchase(offeringId: String): Result<Entitlement> {
        val session = ensureReady() ?: return Result.failure(SubscriptionFailure.NotConfigured())
        val activity =
            activityProvider.current()
                ?: return Result.failure(SubscriptionFailure.NoForegroundActivity())
        // Fetch-then-purchase, in that order: QonversionApi's sequencing contract
        // says purchase resolves its store product from the latest products()
        // answer, and the catalogue is what lets us fail an unknown id honestly.
        val catalogue =
            (withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { session.products() } ?: timedOut("Product lookup"))
                .getOrElse { return Result.failure(it) }
        if (catalogue.none { it.offeringId == offeringId }) {
            return Result.failure(SubscriptionFailure.ProductUnavailable(offeringId))
        }
        return when (val outcome = session.purchase(activity, offeringId)) {
            // Publish on success so a purchase and the entitlement flow can never
            // disagree — and keep the store's own outcomes distinguishable.
            is PurchaseOutcome.Granted -> {
                Result.success(outcome.entitlement.also { state.value = it })
            }

            PurchaseOutcome.Cancelled -> {
                Result.failure(SubscriptionFailure.Cancelled())
            }

            PurchaseOutcome.Pending -> {
                Result.failure(SubscriptionFailure.Pending())
            }

            is PurchaseOutcome.Error -> {
                Result.failure(
                    // The api's own absent-id backstop (mis-sequenced call, or the
                    // store's answer changed under us) states the same fact as the
                    // catalogue check above and must fail the same way.
                    if (outcome.detail == noStoreProductDetail(offeringId)) {
                        SubscriptionFailure.ProductUnavailable(offeringId)
                    } else {
                        SubscriptionFailure.StoreError(outcome.detail)
                    },
                )
            }
        }
    }

    override suspend fun restore(): Result<Entitlement> {
        val session = ensureReady() ?: return Result.failure(SubscriptionFailure.NotConfigured())
        // Bounded: the paywall shows a spinner for the whole of this, and a
        // spinner with no exit is the dead end EDGE_CASES forbids.
        return (withTimeoutOrNull(STORE_CALL_TIMEOUT_MS) { session.restore() } ?: timedOut("Restore"))
            .onSuccess { state.value = it }
    }

    /**
     * Re-reads the provider's entitlement state and publishes it. Every exit —
     * error, silence, timeout — ends at a resolved value; see
     * [FIRST_RESOLUTION_TIMEOUT_MS].
     */
    private suspend fun refresh() {
        val session = ensureReady() ?: return
        val resolved =
            withTimeoutOrNull(FIRST_RESOLUTION_TIMEOUT_MS) { session.checkEntitlements() }
                ?: timedOut("Entitlement check")
        resolved
            .onSuccess { state.value = it }
            .onFailure {
                Log.w(TAG, "Entitlement unresolved — treating as Free", it)
                state.value = Entitlement.Free
            }
    }

    /**
     * Opens the provider session at most once. Returns null when this build has
     * no key or the SDK failed to come up, having first resolved [entitlement]
     * so nothing waits on it.
     */
    private suspend fun ensureReady(): QonversionApi? {
        session?.let { return it }
        return initLock.withLock {
            session ?: createSession()
        }
    }

    private suspend fun createSession(): QonversionApi? {
        val config = configProvider()
        if (config.projectKey.isBlank()) {
            state.value = Entitlement.Free
            return null
        }
        return sessionFactory(application, config)
            ?.also { session = it }
            ?: run {
                state.value = Entitlement.Free
                null
            }
    }
}

/**
 * The production [QonversionApi] factory: initialise the SDK — on the main
 * thread, which Qonversion requires — and wrap it in [RealQonversionApi].
 * Null on failure, after logging; the gateway then resolves entitlement to
 * Free, exactly as it does for a blank key. Never caches a failure: the next
 * ensureReady() genuinely retries, so a recovered network can still bring
 * billing up.
 */
private suspend fun initialiseQonversion(
    application: Application,
    config: SubscriptionConfig,
): QonversionApi? =
    runCatching {
        withContext(Dispatchers.Main) {
            Qonversion.initialize(
                QonversionConfig
                    .Builder(application, config.projectKey, QLaunchMode.SubscriptionManagement)
                    .build(),
            )
        }
    }.onFailure { Log.e(TAG, "Qonversion initialisation failed", it) }
        .map(::RealQonversionApi)
        .getOrNull()

/**
 * What the provider told us about one product, with its SDK types already
 * stripped off. Exists so the rules below can be tested without a Play
 * connection, an Activity, or a mocking framework.
 */
internal data class StoreProductFacts(
    val offeringId: String,
    val price: String?,
    val trialDays: Int?,
    val hasTrial: Boolean,
)

/**
 * The ONLY place the trial rules live. A trial is granted when — and only
 * when — the store says THIS account is [ApiEligibility.ELIGIBLE] **and** the
 * product actually carries a trial period; every other eligibility answer
 * (ineligible, unknown, non-intro product, or no answer at all) grants
 * nothing. `trialDays` is then the period's exact day count where one exists
 * ([exactDays]), and `hasTrial` is that same grant — never independently true.
 *
 * A named pure function for the same reason as [storePricesFrom]: while these
 * decisions sat inline in a coroutine, inverting the eligibility check or
 * dropping the week→days conversion left the whole suite green.
 */
internal fun productFacts(
    product: ApiProduct,
    eligibility: ApiEligibility?,
): StoreProductFacts {
    val trial = product.trialPeriod.takeIf { eligibility == ApiEligibility.ELIGIBLE }
    return StoreProductFacts(
        offeringId = product.offeringId,
        price = product.prettyPrice,
        trialDays = trial?.exactDays(),
        hasTrial = trial != null,
    )
}

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
