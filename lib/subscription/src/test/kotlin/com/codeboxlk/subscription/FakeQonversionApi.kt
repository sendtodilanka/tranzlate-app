package com.codeboxlk.subscription

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * Recording fake for [QonversionApi] — the whole point of the seam. Every
 * answer is a plain field, every call is counted, and the two suspension
 * switches ([productsGate], [productsHangs]/[eligibilityHangs]) let tests hold
 * a call open to observe in-flight behaviour (dedupe, timeouts) under virtual
 * time. No mocking framework on purpose.
 */
internal class FakeQonversionApi : QonversionApi {
    var productsCalls = 0
        private set
    var checkEntitlementsCalls = 0
        private set
    var restoreCalls = 0
        private set
    var purchaseCalls = 0
        private set

    /** Every id list [eligibility] was asked about, in order. */
    val eligibilityRequests = mutableListOf<List<String>>()

    var productsResult: Result<List<ApiProduct>> = Result.success(emptyList())

    /** When set, [products] suspends until this completes — for overlap tests. */
    var productsGate: CompletableDeferred<Unit>? = null

    /** When true, [products] never answers — for timeout tests. */
    var productsHangs = false

    /** When set, [products] THROWS instead of answering — the seam contract broken. */
    var productsThrows: Exception? = null

    var entitlementsResult: Result<Entitlement> = Result.success(Entitlement.Free)
    var restoreResult: Result<Entitlement> = Result.success(Entitlement.Free)
    var eligibilityResult: Map<String, ApiEligibility> = emptyMap()

    /** When true, [eligibility] never answers — for timeout tests. */
    var eligibilityHangs = false

    var purchaseOutcome: PurchaseOutcome = PurchaseOutcome.Error("unstubbed purchase")

    override suspend fun products(): Result<List<ApiProduct>> {
        productsCalls++
        if (productsHangs) awaitCancellation()
        productsThrows?.let { throw it }
        productsGate?.await()
        return productsResult
    }

    override suspend fun checkEntitlements(): Result<Entitlement> {
        checkEntitlementsCalls++
        return entitlementsResult
    }

    override suspend fun restore(): Result<Entitlement> {
        restoreCalls++
        return restoreResult
    }

    override suspend fun eligibility(offeringIds: List<String>): Map<String, ApiEligibility> {
        eligibilityRequests += offeringIds
        if (eligibilityHangs) awaitCancellation()
        return eligibilityResult
    }

    override suspend fun purchase(
        activity: Activity,
        offeringId: String,
    ): PurchaseOutcome {
        purchaseCalls++
        return purchaseOutcome
    }
}
