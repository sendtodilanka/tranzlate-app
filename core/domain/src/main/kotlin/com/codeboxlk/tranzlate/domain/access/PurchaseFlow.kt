package com.codeboxlk.tranzlate.domain.access

import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrice
import kotlinx.coroutines.flow.Flow

/**
 * Purchase/restore ask-surface (Access brain; paywall screens ASK — they never talk
 * to a billing SDK). Prod adapts `:lib:subscription`; the fake variant binds a NoOp
 * (plan §6.4).
 */
interface PurchaseFlow {
    /**
     * What the store charges THIS user per offering id, and whether a trial is
     * still coming to them. Empty until the store answers — a screen must render
     * that as "not known yet" and never substitute a figure of its own.
     */
    val prices: Flow<Map<String, PlanPrice>>

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
