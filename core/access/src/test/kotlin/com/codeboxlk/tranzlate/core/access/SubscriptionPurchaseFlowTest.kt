package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.subscription.StorePrices
import com.codeboxlk.subscription.SubscriptionFailure
import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.subscription.SubscriptionProduct
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.PlanPrices
import com.codeboxlk.tranzlate.domain.access.PurchaseCancelledException
import com.codeboxlk.tranzlate.domain.access.PurchasePendingException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.codeboxlk.subscription.Entitlement as ProviderEntitlement

/**
 * The library→domain boundary, defended where it lives.
 *
 * Two rules cross here and both were previously enforced by nothing but
 * reading. The three-state price mapping must be IDENTITY-shaped — swapping
 * the Loading/Unavailable arms would tell a user "couldn't reach Play" while
 * the request is still in flight, and the whole suite stayed green when a
 * review round tried exactly that (R4-B2). And the two special-cased purchase
 * failures must keep their distinct domain types: Cancelled → silence-worthy
 * [PurchaseCancelledException], Pending → [PurchasePendingException], never
 * each other and never a generic failure (R4-O6: a pending payment may still
 * charge, so it must not inherit "nothing was charged" handling).
 */
class SubscriptionPurchaseFlowTest {
    private class FakeGateway : SubscriptionGateway {
        val productState = MutableStateFlow<StorePrices>(StorePrices.Loading)
        var purchaseResult: Result<ProviderEntitlement> = Result.success(ProviderEntitlement.Free)

        override val entitlement: Flow<ProviderEntitlement> =
            MutableStateFlow<ProviderEntitlement>(ProviderEntitlement.Loading)

        override val products: Flow<StorePrices> = productState

        override suspend fun refreshPrices() = Unit

        override suspend fun purchase(offeringId: String): Result<ProviderEntitlement> = purchaseResult

        override suspend fun restore(): Result<ProviderEntitlement> = purchaseResult
    }

    @Test
    fun `Loading maps to Loading - never to Unavailable`() =
        runTest {
            val gateway = gateway(StorePrices.Loading)

            assertThat(SubscriptionPurchaseFlow(gateway).prices.first())
                .isEqualTo(PlanPrices.Loading)
        }

    @Test
    fun `Unavailable maps to Unavailable - never to Loading`() =
        runTest {
            val gateway = gateway(StorePrices.Unavailable)

            assertThat(SubscriptionPurchaseFlow(gateway).prices.first())
                .isEqualTo(PlanPrices.Unavailable)
        }

    /** M4's other half: Known stays Known and every field survives the crossing. */
    @Test
    fun `Known preserves formattedPrice, trialDays and hasTrial per plan`() =
        runTest {
            val gateway =
                gateway(
                    StorePrices.Known(
                        mapOf(
                            "weekly" to SubscriptionProduct("weekly", "Rs 690.00"),
                            "yearly" to
                                SubscriptionProduct(
                                    offeringId = "yearly",
                                    price = "Rs 10,500.00",
                                    trialDays = 7,
                                    hasTrial = true,
                                ),
                        ),
                    ),
                )

            val mapped = SubscriptionPurchaseFlow(gateway).prices.first()

            assertThat(mapped).isInstanceOf(PlanPrices.Known::class.java)
            val weekly = mapped["weekly"]!!
            assertThat(weekly.formattedPrice).isEqualTo("Rs 690.00")
            assertThat(weekly.trialDays).isNull()
            assertThat(weekly.hasTrial).isFalse()
            val yearly = mapped["yearly"]!!
            assertThat(yearly.formattedPrice).isEqualTo("Rs 10,500.00")
            assertThat(yearly.trialDays).isEqualTo(7)
            assertThat(yearly.hasTrial).isTrue()
        }

    /** An empty answer is still an ANSWER — it must stay Known, not become Unavailable (M15). */
    @Test
    fun `Known with nothing priced stays Known-empty`() =
        runTest {
            val gateway = gateway(StorePrices.Known(emptyMap()))

            assertThat(SubscriptionPurchaseFlow(gateway).prices.first())
                .isEqualTo(PlanPrices.Known(emptyMap()))
        }

    @Test
    fun `Pending crosses as PurchasePendingException - a deferred payment may still charge`() =
        runTest {
            val gateway = gateway(StorePrices.Loading)
            gateway.purchaseResult = Result.failure(SubscriptionFailure.Pending())

            val result = SubscriptionPurchaseFlow(gateway).purchase("yearly")

            assertThat(result).isInstanceOf(AppResult.Failure::class.java)
            assertThat((result as AppResult.Failure).error)
                .isInstanceOf(PurchasePendingException::class.java)
        }

    @Test
    fun `Cancelled crosses as PurchaseCancelledException - the silence-worthy one`() =
        runTest {
            val gateway = gateway(StorePrices.Loading)
            gateway.purchaseResult = Result.failure(SubscriptionFailure.Cancelled())

            val result = SubscriptionPurchaseFlow(gateway).purchase("yearly")

            assertThat(result).isInstanceOf(AppResult.Failure::class.java)
            assertThat((result as AppResult.Failure).error)
                .isInstanceOf(PurchaseCancelledException::class.java)
        }

    /** Every OTHER provider failure crosses unchanged — no accidental widening. */
    @Test
    fun `a store error crosses untranslated`() =
        runTest {
            val gateway = gateway(StorePrices.Loading)
            gateway.purchaseResult = Result.failure(SubscriptionFailure.StoreError("store down"))

            val result = SubscriptionPurchaseFlow(gateway).purchase("yearly")

            assertThat((result as AppResult.Failure).error)
                .isInstanceOf(SubscriptionFailure.StoreError::class.java)
        }

    private fun gateway(initial: StorePrices) = FakeGateway().apply { productState.value = initial }
}
