package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** NoOp purchase-flow (plan §6.4): fake variants carry zero billing behaviour. */
class NoOpPurchaseFlow : PurchaseFlow {
    /**
     * No store, so no prices — and that is not a gap to paper over. The screen
     * already renders "we have not been told yet" as its own state, which is
     * exactly what a fake variant should exercise.
     */
    override val prices: Flow<Map<String, PlanPrice>> = flowOf(emptyMap())

    override suspend fun refreshPrices() = Unit

    override suspend fun purchase(offeringId: String): AppResult<Entitlement> =
        AppResult.Failure(UnsupportedOperationException("NoOp purchase flow (fake variant)"))

    override suspend fun restore(): AppResult<Entitlement> =
        AppResult.Failure(UnsupportedOperationException("NoOp purchase flow (fake variant)"))
}
