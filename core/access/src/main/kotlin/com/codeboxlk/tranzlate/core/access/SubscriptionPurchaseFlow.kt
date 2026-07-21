package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts `:lib:subscription`'s gateway to the domain [PurchaseFlow] ask-surface —
 * the library's string tier ids map to typed [Tier] HERE (the library stays
 * Tranzlate-agnostic).
 */
@Singleton
class SubscriptionPurchaseFlow
    @Inject
    constructor(
        private val gateway: SubscriptionGateway,
    ) : PurchaseFlow {
        // TODO(#4-brains): real implementation — placeholder returns Error(ENGINE) / safe defaults.
        // (Gateway today = NoOpSubscriptionGateway; mapping shape is final, data isn't.)
        override suspend fun purchase(offeringId: String): AppResult<Entitlement> =
            gateway.purchase(offeringId).toAppResult()

        override suspend fun restore(): AppResult<Entitlement> = gateway.restore().toAppResult()
    }

private fun Result<com.codeboxlk.subscription.Entitlement>.toAppResult(): AppResult<Entitlement> =
    fold(
        onSuccess = { AppResult.Success(it.toDomain()) },
        onFailure = { AppResult.Failure(it) },
    )

private fun com.codeboxlk.subscription.Entitlement.toDomain(): Entitlement =
    when (this) {
        com.codeboxlk.subscription.Entitlement.Loading -> {
            Entitlement.Loading
        }

        com.codeboxlk.subscription.Entitlement.Free -> {
            Entitlement.Free
        }

        is com.codeboxlk.subscription.Entitlement.Paid -> {
            when (tier.lowercase()) {
                "premium" -> Entitlement.Paid(Tier.PREMIUM)
                else -> Entitlement.Paid(Tier.PLUS)
            }
        }
    }
