package com.codeboxlk.subscription

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host-app-agnostic subscription configuration — the host `@Provides` this
 * (plan §2 Ring 1). This library knows NOTHING about Tranzlate.
 *
 * @property projectKey billing-provider project key (e.g. Qonversion).
 * @property offeringIds purchasable offering identifiers, in display order.
 */
data class SubscriptionConfig(
    val projectKey: String,
    val offeringIds: List<String>,
)

/**
 * Entitlement as this library reports it: `Loading | Free | Paid(tier)`.
 * Consumers must treat [Loading] as "not yet resolved" and never gate on it
 * (the old app decided on stale data — structural fix).
 */
sealed interface Entitlement {
    data object Loading : Entitlement

    data object Free : Entitlement

    /** @property tier provider-side entitlement/tier identifier (e.g. "plus", "premium"). */
    data class Paid(
        val tier: String,
    ) : Entitlement
}

/**
 * Every way a purchase or restore can fail, named.
 *
 * A billing surface that reports one undifferentiated "failed" is what makes
 * paywalls feel broken: a user who tapped Back gets an error toast, and a brand
 * with no store products looks identical to a network outage. Hosts are expected
 * to branch on these — [Cancelled] in particular should show NOTHING.
 */
sealed class SubscriptionFailure(
    message: String,
) : Exception(message) {
    /** No project key resolved — this build simply has no billing wired. */
    class NotConfigured : SubscriptionFailure("Subscriptions are not configured for this build")

    /** Nothing in the foreground to attach the store dialog to. */
    class NoForegroundActivity : SubscriptionFailure("No foreground activity to launch the store flow")

    /** The store has no product under this id (typo, or not published yet). */
    class ProductUnavailable(
        val productId: String,
    ) : SubscriptionFailure("No store product for id '$productId'")

    /** The USER dismissed the store sheet. Expected, not an error. */
    class Cancelled : SubscriptionFailure("Purchase cancelled by the user")

    /** Deferred payment (e.g. cash) — the entitlement arrives later, not now. */
    class Pending : SubscriptionFailure("Purchase is pending approval")

    /** Anything the provider itself reported. */
    class StoreError(
        detail: String,
    ) : SubscriptionFailure(detail)
}

/**
 * What the STORE says a plan costs — never what we think it costs.
 *
 * A paywall that prints its prices from a string resource is telling every
 * buyer outside that currency a false number, and it does so at the exact
 * moment money changes hands. The store already knows the localized price and
 * whether *this* account still has a trial coming; both come from there.
 *
 * @property offeringId the id the host asked for.
 * @property price store-formatted and already localized ("Rs 1,200.00", "€4,99").
 *   Display it verbatim — reformatting it would reintroduce the same class of bug.
 * @property trialDays exact free-trial length, when the store expresses it in a
 *   unit that converts without rounding. Null when there is no trial, when this
 *   account is not eligible for one, or when the period is a month or a year —
 *   see [hasTrial], which stays true in that last case.
 * @property hasTrial whether an eligible trial exists at all, whatever its unit.
 */
data class SubscriptionProduct(
    val offeringId: String,
    val price: String,
    val trialDays: Int? = null,
    val hasTrial: Boolean = false,
)

/**
 * Public subscription API. The billing SDK stays `internal` behind this surface;
 * swapping/adding the real SDK must not change this interface.
 */
interface SubscriptionGateway {
    /** Hot entitlement state; starts at [Entitlement.Loading] until resolved. */
    val entitlement: Flow<Entitlement>

    /**
     * Store-reported plan details, keyed by offering id.
     *
     * EMPTY until the store answers, and a host must render that state rather
     * than substituting anything: an absent price is a price we do not know
     * yet, and the only honest thing to show is that we do not know it.
     */
    val products: Flow<Map<String, SubscriptionProduct>>

    /**
     * Ask the store for prices again. Idempotent, and safe to call on every
     * paywall open — which is the point: a single failed attempt must not leave
     * the screen permanently unable to sell anything.
     */
    suspend fun refreshPrices()

    /** Launch a purchase for [offeringId]; returns the resolved entitlement. */
    suspend fun purchase(offeringId: String): Result<Entitlement>

    /** Restore purchases; returns the resolved entitlement. */
    suspend fun restore(): Result<Entitlement>
}

/**
 * SDK-free stand-in until the billing integration phase: resolves to [Entitlement.Free],
 * purchase/restore fail cleanly. Hosts bind this today and swap the internal SDK
 * implementation later without an API change.
 */
class NoOpSubscriptionGateway(
    @Suppress("unused") private val config: SubscriptionConfig,
) : SubscriptionGateway {
    private val state = MutableStateFlow<Entitlement>(Entitlement.Free)

    override val entitlement: Flow<Entitlement> = state.asStateFlow()

    /** No store, so no prices — and a host that renders that honestly stays honest here too. */
    override val products: Flow<Map<String, SubscriptionProduct>> =
        MutableStateFlow(emptyMap<String, SubscriptionProduct>()).asStateFlow()

    override suspend fun refreshPrices() = Unit

    override suspend fun purchase(offeringId: String): Result<Entitlement> =
        Result.failure(SubscriptionFailure.NotConfigured())

    override suspend fun restore(): Result<Entitlement> = Result.failure(SubscriptionFailure.NotConfigured())
}
