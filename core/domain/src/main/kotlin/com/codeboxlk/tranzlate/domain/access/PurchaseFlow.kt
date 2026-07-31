package com.codeboxlk.tranzlate.domain.access

import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrices
import kotlinx.coroutines.flow.Flow

/**
 * Purchase/restore ask-surface (Access brain; paywall screens ASK — they never talk
 * to a billing SDK). Prod adapts `:lib:subscription`; the fake variant binds a NoOp
 * (plan §6.4).
 */
interface PurchaseFlow {
    /**
     * What the store charges THIS user, as one of [PlanPrices]' three states.
     * A screen renders the distinction; it never substitutes a figure of its own.
     */
    val prices: Flow<PlanPrices>

    /** Re-ask the store for prices; called whenever the paywall opens. */
    suspend fun refreshPrices()

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

/**
 * The store accepted the purchase but the payment has not cleared yet.
 *
 * Deferred payment methods (cash at a store, slow bank transfers — Google's
 * PENDING purchase state) settle minutes to days later, and when they do the
 * buyer IS charged and the entitlement arrives. So this must never surface as
 * "Couldn't complete the purchase. Nothing was charged." — both halves of that
 * sentence can turn out false, on a screen that ships in pt-rBR to Brazil,
 * where cash-based pending payments are routine.
 *
 * Provider-neutral for the same reason as [PurchaseCancelledException]: the
 * billing library's own pending type stays out of the feature modules.
 */
class PurchasePendingException : Exception("Purchase is pending — payment may still complete and charge later")
