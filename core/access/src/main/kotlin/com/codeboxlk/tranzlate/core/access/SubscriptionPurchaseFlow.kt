package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.subscription.SubscriptionFailure
import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.domain.access.PurchaseCancelledException
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts `:lib:subscription`'s gateway to the domain [PurchaseFlow] ask-surface —
 * provider entitlements become typed domain values through the shared
 * [toDomain] mapping (the library stays Tranzlate-agnostic; A7: purchase and
 * gating share one tier source).
 */
@Singleton
class SubscriptionPurchaseFlow
    @Inject
    constructor(
        private val gateway: SubscriptionGateway,
    ) : PurchaseFlow {
        override suspend fun purchase(offeringId: String): AppResult<Entitlement> =
            gateway.purchase(offeringId).toAppResult()

        override suspend fun restore(): AppResult<Entitlement> = gateway.restore().toAppResult()
    }

/**
 * Provider failures cross into the domain unchanged EXCEPT cancellation, which
 * becomes the neutral [PurchaseCancelledException]. That translation happens
 * here, at the one boundary that knows both vocabularies — a screen must be able
 * to stay silent on a user's own back-tap without importing the billing SDK.
 */
private fun Result<com.codeboxlk.subscription.Entitlement>.toAppResult(): AppResult<Entitlement> =
    fold(
        onSuccess = { AppResult.Success(it.toDomain()) },
        onFailure = { failure ->
            AppResult.Failure(
                if (failure is SubscriptionFailure.Cancelled) PurchaseCancelledException() else failure,
            )
        },
    )
