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

/**
 * The user closed the store sheet. It arrives as an [AppResult.Failure] because
 * no entitlement was granted, but it is the ONE failure a screen must stay quiet
 * about — telling someone "purchase failed" right after they chose to back out
 * is what makes a paywall feel broken.
 *
 * Provider-neutral on purpose: the billing library has its own cancellation type,
 * and translating it here keeps that SDK out of the feature modules.
 */
class PurchaseCancelledException : Exception("Purchase cancelled by the user")
