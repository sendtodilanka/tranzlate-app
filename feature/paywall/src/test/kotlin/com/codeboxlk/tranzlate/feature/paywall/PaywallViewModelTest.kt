package com.codeboxlk.tranzlate.feature.paywall

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.core.model.PlanPrices
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeRemoteConfig
import com.codeboxlk.tranzlate.domain.access.PurchaseCancelledException
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePurchaseFlow(
        var purchaseResult: AppResult<Entitlement> = AppResult.Success(Entitlement.Free),
        var restoreResult: AppResult<Entitlement> = AppResult.Success(Entitlement.Free),
    ) : PurchaseFlow {
        var refreshes = 0
            private set

        val priceState = MutableStateFlow<PlanPrices>(PlanPrices.Loading)

        override val prices: Flow<PlanPrices> = priceState

        var purchases = 0
            private set

        override suspend fun refreshPrices() {
            refreshes++
        }

        override suspend fun purchase(offeringId: String): AppResult<Entitlement> {
            purchases++
            return purchaseResult
        }

        override suspend fun restore(): AppResult<Entitlement> = restoreResult
    }

    @Test
    fun `Yearly is pre-selected - the §4 anchor`() {
        val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(), FakeRemoteConfig())

        assertThat(vm.selected.value).isEqualTo(PaywallPlan.YEARLY)
    }

    @Test
    fun `a purchase that resolves Free is an HONEST failure event - never fake success`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.purchase()
                dispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(PaywallEvent.PURCHASE_FAILED)
            }
        }

    @Test
    fun `a Paid purchase emits no failure - the entitlement flow dismisses the screen`() =
        runTest {
            val flow = FakePurchaseFlow(purchaseResult = AppResult.Success(Entitlement.Paid(Tier.PRO)))
            val vm = PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.purchase()
                dispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
        }

    @Test
    fun `double-tap fires one purchase`() =
        runTest {
            val flow = FakePurchaseFlow()
            val vm = PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())

            vm.purchase()
            vm.purchase() // still in flight — guarded
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(flow.purchases).isEqualTo(1)
        }

    @Test
    fun `restore with nothing to restore says so - distinct from a store failure`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.restore()
                dispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(PaywallEvent.RESTORED_FREE)
            }
        }

    @Test
    fun `a user-cancelled purchase says NOTHING - an error toast on a back-tap reads as broken`() =
        runTest {
            val flow =
                FakePurchaseFlow(purchaseResult = AppResult.Failure(PurchaseCancelledException()))
            val vm = PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.purchase()
                dispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
        }

    @Test
    fun `a store failure that is NOT a cancellation still reports`() =
        runTest {
            val flow =
                FakePurchaseFlow(purchaseResult = AppResult.Failure(IllegalStateException("store down")))
            val vm = PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.purchase()
                dispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(PaywallEvent.PURCHASE_FAILED)
            }
        }

    @Test
    fun `legal links come from remote config - Play requires them on the purchase screen`() =
        runTest {
            val vm =
                PaywallViewModel(
                    FakePurchaseFlow(),
                    FakeFeatureAccess(),
                    FakeRemoteConfig(
                        termsUrl = "https://example.test/terms",
                        privacyPolicyUrl = "https://example.test/privacy",
                    ),
                )
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(vm.legalLinks.value.termsUrl).isEqualTo("https://example.test/terms")
            assertThat(vm.legalLinks.value.privacyUrl).isEqualTo("https://example.test/privacy")
        }

    @Test
    fun `an unopenable legal link surfaces a message - never a silent no-op`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(), FakeRemoteConfig())

            vm.events.test {
                vm.onLegalLinkUnavailable()
                assertThat(awaitItem()).isEqualTo(PaywallEvent.LINK_UNAVAILABLE)
            }
        }

    @Test
    fun `PRO entitlement surfaces through isPro for the auto-dismiss`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(Tier.PRO), FakeRemoteConfig())

            vm.isPro.test {
                // stateIn starts false, then the entitlement resolves PRO.
                assertThat(awaitItem()).isFalse()
                assertThat(awaitItem()).isTrue()
            }
        }

    /**
     * The invariant the price work exists to protect: prices are re-asked every
     * time the paywall opens. As a one-shot, an offline first launch left the
     * screen unable to sell anything for the whole process — "getting prices"
     * forever, over a button that could never arm.
     */
    @Test
    fun `opening the paywall re-asks the store for prices`() =
        runTest {
            val flow = FakePurchaseFlow()

            PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())
            runCurrent()

            assertThat(flow.refreshes).isEqualTo(1)
        }

    /**
     * Plumbing only — that what the flow emits reaches the screen unchanged. The
     * RULES it carries are tested where they live: the blank-price filter in
     * `:lib:subscription`'s StorePricesTest, the purchase gate in CanPurchaseTest.
     * This test used to claim both and could reach neither.
     */
    @Test
    fun `store prices reach the screen as published`() =
        runTest {
            val flow = FakePurchaseFlow()
            val model = PaywallViewModel(flow, FakeFeatureAccess(), FakeRemoteConfig())
            model.prices.collectIn(backgroundScope)
            runCurrent()

            flow.priceState.value =
                PlanPrices.Known(mapOf(PaywallPlan.YEARLY.offeringId to PlanPrice("Rs 10,500.00")))
            runCurrent()

            assertThat(model.prices.value[PaywallPlan.YEARLY.offeringId]?.formattedPrice)
                .isEqualTo("Rs 10,500.00")
            assertThat(model.prices.value[PaywallPlan.WEEKLY.offeringId]).isNull()
        }
}

/** `stateIn(WhileSubscribed)` only runs while something collects; this is that something. */
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectIn(scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch { collect { } }
}
