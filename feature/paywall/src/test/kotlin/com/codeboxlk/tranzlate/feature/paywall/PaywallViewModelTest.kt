package com.codeboxlk.tranzlate.feature.paywall

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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
        var purchases = 0
            private set

        override suspend fun purchase(offeringId: String): AppResult<Entitlement> {
            purchases++
            return purchaseResult
        }

        override suspend fun restore(): AppResult<Entitlement> = restoreResult
    }

    @Test
    fun `Yearly is pre-selected - the §4 anchor`() {
        val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess())

        assertThat(vm.selected.value).isEqualTo(PaywallPlan.YEARLY)
    }

    @Test
    fun `a purchase that resolves Free is an HONEST failure event - never fake success`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess())

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
            val vm = PaywallViewModel(flow, FakeFeatureAccess())

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
            val vm = PaywallViewModel(flow, FakeFeatureAccess())

            vm.purchase()
            vm.purchase() // still in flight — guarded
            dispatcher.scheduler.advanceUntilIdle()

            assertThat(flow.purchases).isEqualTo(1)
        }

    @Test
    fun `restore with nothing to restore says so - distinct from a store failure`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess())

            vm.events.test {
                vm.restore()
                dispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(PaywallEvent.RESTORED_FREE)
            }
        }

    @Test
    fun `PRO entitlement surfaces through isPro for the auto-dismiss`() =
        runTest {
            val vm = PaywallViewModel(FakePurchaseFlow(), FakeFeatureAccess(Tier.PRO))

            vm.isPro.test {
                // stateIn starts false, then the entitlement resolves PRO.
                assertThat(awaitItem()).isFalse()
                assertThat(awaitItem()).isTrue()
            }
        }
}
