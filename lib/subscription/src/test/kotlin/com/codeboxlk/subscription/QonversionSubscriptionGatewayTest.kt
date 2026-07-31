package com.codeboxlk.subscription

import android.app.Activity
import android.app.Application
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * The gateway's billing rules, tested against a fake [QonversionApi] — the seam
 * exists so every one of these runs on a plain JVM with virtual time and no
 * store. Each test names the register mutation it is the red bar for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QonversionSubscriptionGatewayTest {
    // ---------------------------------------------------------------- initial state

    /** M6: the initial productState is Loading — not Known(empty), not Unavailable. */
    @Test
    fun `products and entitlement start Loading before anything has been asked`() =
        runTest {
            val gateway = gateway(FakeQonversionApi())

            // Nothing has advanced: the init fetches are queued, not run. What a
            // screen composed at this instant reads must be "no answer yet".
            assertThat(gateway.products.first()).isEqualTo(StorePrices.Loading)
            assertThat(gateway.entitlement.first()).isEqualTo(Entitlement.Loading)
        }

    // ---------------------------------------------------------------- configuration

    @Test
    fun `a blank key settles Free and Unavailable without ever building a session`() =
        runTest {
            var factoryCalls = 0
            val gateway =
                gateway(
                    api = null,
                    key = "   ",
                    sessionFactory = { _, _ ->
                        factoryCalls++
                        FakeQonversionApi()
                    },
                )
            advanceUntilIdle()

            assertThat(gateway.entitlement.first()).isEqualTo(Entitlement.Free)
            assertThat(gateway.products.first()).isEqualTo(StorePrices.Unavailable)
            // The blank-key check must sit BEFORE the factory: no store SDK may
            // ever be initialised against an empty key.
            assertThat(factoryCalls).isEqualTo(0)
            assertThat(gateway.purchase("yearly").exceptionOrNull())
                .isInstanceOf(SubscriptionFailure.NotConfigured::class.java)
            assertThat(gateway.restore().exceptionOrNull())
                .isInstanceOf(SubscriptionFailure.NotConfigured::class.java)
        }

    @Test
    fun `a session that cannot come up settles Free and Unavailable`() =
        runTest {
            val gateway = gateway(api = null)
            advanceUntilIdle()

            assertThat(gateway.entitlement.first()).isEqualTo(Entitlement.Free)
            assertThat(gateway.products.first()).isEqualTo(StorePrices.Unavailable)
        }

    // ---------------------------------------------------------------- refreshPrices

    @Test
    fun `a store error publishes Unavailable`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    productsResult = Result.failure(SubscriptionFailure.StoreError("store fell over"))
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            assertThat(gateway.products.first()).isEqualTo(StorePrices.Unavailable)
        }

    @Test
    fun `a store that never answers times out to Unavailable`() =
        runTest {
            val fake = FakeQonversionApi().apply { productsHangs = true }
            val gateway = gateway(fake)
            advanceUntilIdle() // virtual time runs past the store-call ceiling

            assertThat(gateway.products.first()).isEqualTo(StorePrices.Unavailable)
        }

    @Test
    fun `a store answer publishes Known with facts decided by eligibility`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    productsResult =
                        Result.success(
                            listOf(
                                ApiProduct("yearly", "€49,99", ApiPeriod(1, ApiPeriod.Unit.WEEK)),
                                ApiProduct("monthly", "€4,99", ApiPeriod(1, ApiPeriod.Unit.WEEK)),
                            ),
                        )
                    eligibilityResult =
                        mapOf(
                            "yearly" to ApiEligibility.ELIGIBLE,
                            "monthly" to ApiEligibility.INELIGIBLE,
                        )
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val known = gateway.products.first()
            assertThat(known).isInstanceOf(StorePrices.Known::class.java)
            val products = (known as StorePrices.Known).products
            assertThat(products.getValue("yearly").price).isEqualTo("€49,99")
            assertThat(products.getValue("yearly").trialDays).isEqualTo(7)
            assertThat(products.getValue("yearly").hasTrial).isTrue()
            assertThat(products.getValue("monthly").hasTrial).isFalse()
            assertThat(products.getValue("monthly").trialDays).isNull()
            // Eligibility was asked about exactly the catalogue's ids.
            assertThat(fake.eligibilityRequests).containsExactly(listOf("yearly", "monthly"))
        }

    @Test
    fun `an eligibility check that never answers still prices, with no trial promised`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    productsResult =
                        Result.success(listOf(ApiProduct("yearly", "€49,99", ApiPeriod(1, ApiPeriod.Unit.WEEK))))
                    eligibilityHangs = true
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val known = gateway.products.first() as StorePrices.Known
            // Under-promising is recoverable; over-promising is a policy problem.
            assertThat(known.products.getValue("yearly").hasTrial).isFalse()
            assertThat(known.products.getValue("yearly").trialDays).isNull()
        }

    /** M15: a store that priced nothing is an ANSWER — Known(empty), never Unavailable. */
    @Test
    fun `a store that priced nothing publishes Known and empty, never Unavailable`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    // The store answered for the product but attached no price.
                    productsResult = Result.success(listOf(ApiProduct("yearly", null, null)))
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val prices = gateway.products.first()
            assertThat(prices).isInstanceOf(StorePrices.Known::class.java)
            assertThat((prices as StorePrices.Known).products).isEmpty()
        }

    /** M15, the other shape: an empty catalogue is also an answer. */
    @Test
    fun `an empty catalogue publishes Known and empty, never Unavailable`() =
        runTest {
            val fake = FakeQonversionApi().apply { productsResult = Result.success(emptyList()) }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val prices = gateway.products.first()
            assertThat(prices).isInstanceOf(StorePrices.Known::class.java)
            assertThat((prices as StorePrices.Known).products).isEmpty()
        }

    /** M14: a refresh that arrives while one is in flight must not double-fetch. */
    @Test
    fun `two concurrent refreshes fetch the store exactly once`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    productsResult = Result.success(listOf(ApiProduct("yearly", "€49,99", null)))
                }
            val scope = gatewayScope()
            val gateway = gateway(fake, scope = scope)
            advanceUntilIdle() // the init-time refresh completes: one fetch so far
            assertThat(fake.productsCalls).isEqualTo(1)

            fake.productsGate = CompletableDeferred() // hold the next fetch open
            val first = scope.launch { gateway.refreshPrices() }
            val second = scope.launch { gateway.refreshPrices() }
            runCurrent() // run both up to their suspension points, no virtual time

            // The second call returned immediately; the first is still out.
            assertThat(second.isCompleted).isTrue()
            assertThat(first.isCompleted).isFalse()
            assertThat(fake.productsCalls).isEqualTo(2)

            fake.productsGate?.complete(Unit)
            advanceUntilIdle()

            // Still one fetch for the two calls, and the answer landed as Known —
            // no stale second pass wiped it back to Loading or Unavailable.
            assertThat(fake.productsCalls).isEqualTo(2)
            assertThat(gateway.products.first()).isInstanceOf(StorePrices.Known::class.java)
        }

    // ---------------------------------------------------------------- restore

    /** M1: restore() must call the store's restore, and must NOT be checkEntitlements. */
    @Test
    fun `restore calls the recovery path, not the current-identity check`() =
        runTest {
            val fake =
                FakeQonversionApi().apply {
                    restoreResult = Result.success(Entitlement.Paid("plus"))
                }
            val gateway = gateway(fake)
            advanceUntilIdle()
            val entitlementChecksBefore = fake.checkEntitlementsCalls // init's resolve

            val result = gateway.restore()

            assertThat(result.getOrNull()).isEqualTo(Entitlement.Paid("plus"))
            assertThat(fake.restoreCalls).isEqualTo(1)
            // A reinstalled subscriber's identity is fresh — asking what IT owns
            // would answer "nothing to restore". The counter must not move.
            assertThat(fake.checkEntitlementsCalls).isEqualTo(entitlementChecksBefore)
            // And the recovered entitlement is published, not just returned.
            assertThat(gateway.entitlement.first()).isEqualTo(Entitlement.Paid("plus"))
        }

    // ---------------------------------------------------------------- purchase

    @Test
    fun `a cancelled purchase fails as Cancelled and leaves entitlement untouched`() =
        runTest {
            val fake = purchasableFake().apply { purchaseOutcome = PurchaseOutcome.Cancelled }
            val gateway = gateway(fake)
            advanceUntilIdle()
            val before = gateway.entitlement.first()

            val result = gateway.purchase("yearly")

            assertThat(result.exceptionOrNull()).isInstanceOf(SubscriptionFailure.Cancelled::class.java)
            assertThat(gateway.entitlement.first()).isEqualTo(before)
        }

    @Test
    fun `a pending purchase fails as Pending and leaves entitlement untouched`() =
        runTest {
            val fake = purchasableFake().apply { purchaseOutcome = PurchaseOutcome.Pending }
            val gateway = gateway(fake)
            advanceUntilIdle()
            val before = gateway.entitlement.first()

            val result = gateway.purchase("yearly")

            assertThat(result.exceptionOrNull()).isInstanceOf(SubscriptionFailure.Pending::class.java)
            assertThat(gateway.entitlement.first()).isEqualTo(before)
        }

    @Test
    fun `a granted purchase publishes the entitlement it returns`() =
        runTest {
            val fake =
                purchasableFake().apply {
                    purchaseOutcome = PurchaseOutcome.Granted(Entitlement.Paid("plus"))
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val result = gateway.purchase("yearly")

            assertThat(result.getOrNull()).isEqualTo(Entitlement.Paid("plus"))
            // Published, so a purchase and the entitlement flow can never disagree.
            assertThat(gateway.entitlement.first()).isEqualTo(Entitlement.Paid("plus"))
        }

    @Test
    fun `an id the store does not carry fails as ProductUnavailable without launching a flow`() =
        runTest {
            val fake = purchasableFake() // catalogue carries "yearly" only
            val gateway = gateway(fake)
            advanceUntilIdle()

            val result = gateway.purchase("weekly")

            val failure = result.exceptionOrNull()
            assertThat(failure).isInstanceOf(SubscriptionFailure.ProductUnavailable::class.java)
            assertThat((failure as SubscriptionFailure.ProductUnavailable).productId).isEqualTo("weekly")
            assertThat(fake.purchaseCalls).isEqualTo(0)
        }

    @Test
    fun `the api's own absent-product backstop also fails as ProductUnavailable`() =
        runTest {
            val fake =
                purchasableFake().apply {
                    purchaseOutcome = PurchaseOutcome.Error(noStoreProductDetail("yearly"))
                }
            val gateway = gateway(fake)
            advanceUntilIdle()

            assertThat(gateway.purchase("yearly").exceptionOrNull())
                .isInstanceOf(SubscriptionFailure.ProductUnavailable::class.java)
        }

    @Test
    fun `any other store-reported purchase error fails as StoreError`() =
        runTest {
            val fake = purchasableFake().apply { purchaseOutcome = PurchaseOutcome.Error("billing exploded") }
            val gateway = gateway(fake)
            advanceUntilIdle()

            val failure = gateway.purchase("yearly").exceptionOrNull()
            assertThat(failure).isInstanceOf(SubscriptionFailure.StoreError::class.java)
            assertThat(failure).hasMessageThat().isEqualTo("billing exploded")
        }

    @Test
    fun `no foreground activity fails before any store call`() =
        runTest {
            val fake = purchasableFake()
            val gateway = gateway(fake, activityProvider = { null })
            advanceUntilIdle()
            val fetchesBefore = fake.productsCalls

            val result = gateway.purchase("yearly")

            assertThat(result.exceptionOrNull())
                .isInstanceOf(SubscriptionFailure.NoForegroundActivity::class.java)
            assertThat(fake.productsCalls).isEqualTo(fetchesBefore)
            assertThat(fake.purchaseCalls).isEqualTo(0)
        }

    // ---------------------------------------------------------------- helpers

    private val gatewayScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        gatewayScopes.forEach { it.cancel() }
    }

    /**
     * A scope for the gateway's own eager work, on the test scheduler but NOT
     * [TestScope.backgroundScope]: background tasks are exactly what
     * `advanceUntilIdle` does not wait for, so a gateway launched there never
     * runs its init-time resolves and every state assertion reads Loading.
     */
    private fun TestScope.gatewayScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            .also { gatewayScopes += it }

    /**
     * R5-O2: the seam's contract is answer-by-value, but a provider SDK that
     * throws synchronously must not ride the host's bare launch to a crash.
     * The proof is structural: an exception ESCAPING refreshPrices would fail
     * this runTest — so this passing IS the no-crash claim. And the state must
     * land where the retry lives, never strand at Loading.
     */
    @Test
    fun `a seam that throws lands at Unavailable instead of crashing the scope`() =
        runTest {
            val api = FakeQonversionApi().apply { productsThrows = IllegalStateException("SDK misbehaved") }
            val gateway = gateway(api)
            advanceUntilIdle()

            assertThat(gateway.products.first()).isEqualTo(StorePrices.Unavailable)

            // And the guard was released — a later retry still runs.
            api.productsThrows = null
            api.productsResult = Result.success(listOf(ApiProduct("yearly", "€49,99", null)))
            gateway.refreshPrices()
            advanceUntilIdle()
            assertThat(gateway.products.first()).isInstanceOf(StorePrices.Known::class.java)
        }

    private fun TestScope.gateway(
        api: QonversionApi?,
        key: String = "qon_test_key",
        activityProvider: ActivityProvider = ActivityProvider { Activity() },
        scope: CoroutineScope = gatewayScope(),
        sessionFactory: suspend (Application, SubscriptionConfig) -> QonversionApi? = { _, _ -> api },
    ): QonversionSubscriptionGateway =
        QonversionSubscriptionGateway(
            application = Application(),
            activityProvider = activityProvider,
            scope = scope,
            configProvider = { SubscriptionConfig(projectKey = key, offeringIds = listOf("yearly", "monthly")) },
            sessionFactory = sessionFactory,
        )

    /** A fake whose store carries one purchasable product, "yearly". */
    private fun purchasableFake(): FakeQonversionApi =
        FakeQonversionApi().apply {
            productsResult = Result.success(listOf(ApiProduct("yearly", "€49,99", null)))
        }
}
