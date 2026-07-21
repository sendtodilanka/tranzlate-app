package com.codeboxlk.tranzlate.domain.access

import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement

/**
 * Purchase/restore ask-surface (Access brain; paywall screens ASK — they never talk
 * to a billing SDK). Prod adapts `:lib:subscription`; the fake variant binds a NoOp
 * (plan §6.4).
 */
interface PurchaseFlow {
    suspend fun purchase(offeringId: String): AppResult<Entitlement>

    suspend fun restore(): AppResult<Entitlement>
}
