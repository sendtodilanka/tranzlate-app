package com.codeboxlk.subscription

import android.app.Activity
import android.util.Log
import com.qonversion.android.sdk.Qonversion
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "Subscription"

/**
 * The SDK seam: everything the gateway needs from Qonversion, expressed in types
 * this module owns.
 *
 * WHY it exists: a review round proved that every billing decision made against
 * a raw SDK type is a decision no unit test can reach — inverting the trial
 * eligibility check, or dropping the week→days conversion, left the whole suite
 * green. Confining the SDK to [RealQonversionApi] means the gateway's rules run
 * against [ApiProduct]/[ApiEligibility]/[PurchaseOutcome], all constructible in
 * a plain JVM test with no Play connection, no Activity, no mocking framework.
 *
 * Sequencing contract: [purchase] resolves its store product from the answer of
 * this instance's **most recent [products] call** — callers must fetch products
 * first (the gateway does, because it needs the catalogue to validate the id
 * anyway). A purchase without a preceding fetch answers [PurchaseOutcome.Error].
 */
internal interface QonversionApi {
    /** The store catalogue for this app, already stripped to facts. */
    suspend fun products(): Result<List<ApiProduct>>

    /** What the CURRENT identity owns. NOT the recovery path — see [restore]. */
    suspend fun checkEntitlements(): Result<Entitlement>

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
    suspend fun restore(): Result<Entitlement>

    /** Trial eligibility per offering. Empty on any error — an unknown must read as "no trial", never "yes". */
    suspend fun eligibility(offeringIds: List<String>): Map<String, ApiEligibility>

    /** Launch the store purchase flow for [offeringId]. See the sequencing contract above. */
    suspend fun purchase(
        activity: Activity,
        offeringId: String,
    ): PurchaseOutcome
}

/**
 * A billing period as the store expresses it — count plus unit, nothing decided.
 */
internal data class ApiPeriod(
    val unitCount: Int,
    val unit: Unit,
) {
    enum class Unit { DAY, WEEK, MONTH, YEAR, UNKNOWN }
}

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
internal fun ApiPeriod.exactDays(): Int? =
    when (unit) {
        ApiPeriod.Unit.DAY -> unitCount
        ApiPeriod.Unit.WEEK -> unitCount * DAYS_PER_WEEK
        else -> null
    }

private const val DAYS_PER_WEEK = 7

/**
 * One store product, SDK types already stripped off. [prettyPrice] is the
 * store-localized display string or null when the store priced nothing;
 * [trialPeriod] is the product's intro period regardless of whether THIS
 * account may still claim it — that judgement is [productFacts]'s, not ours.
 */
internal data class ApiProduct(
    val offeringId: String,
    val prettyPrice: String?,
    val trialPeriod: ApiPeriod?,
)

/** Trial eligibility for one offering, 1:1 with what the provider can report. */
internal enum class ApiEligibility { ELIGIBLE, INELIGIBLE, UNKNOWN, NON_INTRO }

internal fun QIntroEligibilityStatus.toApiEligibility(): ApiEligibility =
    when (this) {
        QIntroEligibilityStatus.Eligible -> ApiEligibility.ELIGIBLE
        QIntroEligibilityStatus.Ineligible -> ApiEligibility.INELIGIBLE
        QIntroEligibilityStatus.Unknown -> ApiEligibility.UNKNOWN
        QIntroEligibilityStatus.NonIntroOrTrialProduct -> ApiEligibility.NON_INTRO
    }

/**
 * Every way a store purchase flow can end, kept distinguishable on purpose:
 * the gateway must publish on [Granted], stay silent on [Cancelled], and be
 * honest about [Pending] — collapsing these was exactly the "Nothing was
 * charged" lie an earlier round flagged.
 */
internal sealed interface PurchaseOutcome {
    data class Granted(
        val entitlement: Entitlement,
    ) : PurchaseOutcome

    data object Cancelled : PurchaseOutcome

    data object Pending : PurchaseOutcome

    data class Error(
        val detail: String,
    ) : PurchaseOutcome
}

/** The one wording for "the store has no such product" — shared so the gateway can recognise its own backstop. */
internal fun noStoreProductDetail(offeringId: String): String = "No store product for id '$offeringId'"

/**
 * The only class that touches Qonversion's own types. Thin by design: every
 * bridge here is a callback→coroutine adaptation plus a 1:1 mapping, with no
 * branching a unit test would need to defend — the rules all live above the
 * seam, in the gateway and its pure functions.
 */
internal class RealQonversionApi(
    private val sdk: Qonversion,
) : QonversionApi {
    /**
     * The raw catalogue behind the last [products] answer. [purchase] needs the
     * actual [QProduct] object (the SDK will not launch a flow from a bare id),
     * and refetching it inside purchase would double the store round-trips the
     * gateway already times. See the sequencing contract on [QonversionApi].
     */
    @Volatile
    private var lastCatalogue: Map<String, QProduct> = emptyMap()

    override suspend fun products(): Result<List<ApiProduct>> =
        suspendCancellableCoroutine<Result<Map<String, QProduct>>> { continuation ->
            sdk.products(
                object : QonversionProductsCallback {
                    override fun onSuccess(products: Map<String, QProduct>) =
                        continuation.resume(Result.success(products))

                    override fun onError(error: QonversionError) =
                        continuation.resume(Result.failure(SubscriptionFailure.StoreError(error.toString())))
                },
            )
        }.onSuccess { lastCatalogue = it }
            .map { catalogue ->
                catalogue.map { (offeringId, product) ->
                    ApiProduct(
                        offeringId = offeringId,
                        prettyPrice = product.prettyPrice,
                        trialPeriod = product.trialPeriod?.toApiPeriod(),
                    )
                }
            }

    override suspend fun checkEntitlements(): Result<Entitlement> =
        suspendCancellableCoroutine { continuation ->
            sdk.checkEntitlements(entitlementsCallback(continuation))
        }

    override suspend fun restore(): Result<Entitlement> =
        suspendCancellableCoroutine { continuation ->
            sdk.restore(entitlementsCallback(continuation))
        }

    /** Shared bridge: both entitlement calls answer on the same callback type. */
    private fun entitlementsCallback(continuation: CancellableContinuation<Result<Entitlement>>) =
        object : QonversionEntitlementsCallback {
            override fun onSuccess(entitlements: Map<String, QEntitlement>) =
                continuation.resume(Result.success(entitlements.toEntitlement()))

            override fun onError(error: QonversionError) =
                continuation.resume(Result.failure(SubscriptionFailure.StoreError(error.toString())))
        }

    override suspend fun eligibility(offeringIds: List<String>): Map<String, ApiEligibility> =
        suspendCancellableCoroutine { continuation ->
            sdk.checkTrialIntroEligibility(
                offeringIds,
                object : QonversionEligibilityCallback {
                    override fun onSuccess(eligibilities: Map<String, QEligibility>) =
                        continuation.resume(eligibilities.mapValues { it.value.status.toApiEligibility() })

                    override fun onError(error: QonversionError) {
                        Log.w(TAG, "Trial eligibility unresolved — treating as not eligible: $error")
                        continuation.resume(emptyMap())
                    }
                },
            )
        }

    /**
     * Marshalled onto the main thread here rather than trusting the caller's
     * dispatcher: Qonversion's purchase flow launches an Activity.
     */
    override suspend fun purchase(
        activity: Activity,
        offeringId: String,
    ): PurchaseOutcome {
        val product =
            lastCatalogue[offeringId]
                ?: return PurchaseOutcome.Error(noStoreProductDetail(offeringId))
        val result =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    sdk.purchase(
                        activity,
                        product,
                        object : QonversionPurchaseCallback {
                            override fun onResult(result: QPurchaseResult) = continuation.resume(result)
                        },
                    )
                }
            }
        return when {
            result.isSuccessful -> PurchaseOutcome.Granted(result.entitlements.toEntitlement())
            result.isCanceledByUser -> PurchaseOutcome.Cancelled
            result.isPending -> PurchaseOutcome.Pending
            else -> PurchaseOutcome.Error(result.error?.toString() ?: "Purchase failed")
        }
    }
}

/**
 * Single-paid-tier reading: ANY active entitlement means paid, and its id names
 * the purchase. Matching on specific ids here would make a dashboard rename a
 * silent revenue outage.
 */
private fun Map<String, QEntitlement>.toEntitlement(): Entitlement =
    values.firstOrNull { it.isActive }?.let { Entitlement.Paid(it.id) } ?: Entitlement.Free

private fun QSubscriptionPeriod.toApiPeriod(): ApiPeriod =
    ApiPeriod(
        unitCount = unitCount,
        unit =
            when (unit) {
                QSubscriptionPeriod.Unit.Day -> ApiPeriod.Unit.DAY
                QSubscriptionPeriod.Unit.Week -> ApiPeriod.Unit.WEEK
                QSubscriptionPeriod.Unit.Month -> ApiPeriod.Unit.MONTH
                QSubscriptionPeriod.Unit.Year -> ApiPeriod.Unit.YEAR
                QSubscriptionPeriod.Unit.Unknown -> ApiPeriod.Unit.UNKNOWN
            },
    )
