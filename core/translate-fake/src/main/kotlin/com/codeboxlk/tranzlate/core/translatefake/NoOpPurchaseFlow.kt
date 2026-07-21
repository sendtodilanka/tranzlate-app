package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow

/** NoOp purchase-flow (plan §6.4): fake variants carry zero billing behaviour. */
class NoOpPurchaseFlow : PurchaseFlow {
    override suspend fun purchase(offeringId: String): AppResult<Entitlement> =
        AppResult.Failure(UnsupportedOperationException("NoOp purchase flow (fake variant)"))

    override suspend fun restore(): AppResult<Entitlement> =
        AppResult.Failure(UnsupportedOperationException("NoOp purchase flow (fake variant)"))
}
