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
 * Public subscription API. The billing SDK stays `internal` behind this surface;
 * swapping/adding the real SDK must not change this interface.
 */
interface SubscriptionGateway {
    /** Hot entitlement state; starts at [Entitlement.Loading] until resolved. */
    val entitlement: Flow<Entitlement>

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

    override suspend fun purchase(offeringId: String): Result<Entitlement> =
        Result.failure(UnsupportedOperationException("Billing SDK not integrated yet"))

    override suspend fun restore(): Result<Entitlement> =
        Result.failure(UnsupportedOperationException("Billing SDK not integrated yet"))
}
