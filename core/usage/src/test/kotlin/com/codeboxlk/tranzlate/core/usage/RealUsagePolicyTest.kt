package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.datastore.PersistedUsageCounts
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.domain.usage.SpendResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

/**
 * A4's whole point is the critical section — so the race test runs on REAL
 * parallel threads (Dispatchers.Default), not the single-threaded test
 * dispatcher where interleaving can't happen.
 */
class RealUsagePolicyTest {
    private class FakePersistence(
        initial: PersistedUsageCounts = PersistedUsageCounts(0, 0, PersistedUsageCounts.NO_DAY),
    ) : UsagePersistence {
        var stored: PersistedUsageCounts = initial
            private set
        var loads = 0
            private set
        var saves = 0
            private set

        override suspend fun load(): PersistedUsageCounts {
            loads++
            return stored
        }

        override suspend fun save(counts: PersistedUsageCounts) {
            saves++
            stored = counts
        }
    }

    private class FixedConfig(
        private val free: Int = 5,
        private val pro: Int = 2,
    ) : RemoteConfigSource {
        override fun limitFreeAi(): Int = free

        override fun limitProFairUse(): Int = pro

        override fun adNth(): Int = 2

        override fun adMinGapSeconds(): Int = 90

        override fun adDailyCap(): Int = 12

        override fun textLimitFree(): Int = 500

        override fun textLimitPro(): Int = 5000

        override fun gotEnabled(): Boolean = true

        override fun gotTimeoutMs(): Long = 10_000L

        override fun gctTimeoutMs(): Long = 15_000L

        override fun qonversionKey(): String = ""

        override fun gctApiKey(): String = ""

        override fun privacyPolicyUrl(): String = ""

        override fun termsUrl(): String = ""

        override fun contactEmail(): String = ""

        override suspend fun awaitFirstFetch() = Unit
    }

    @Test
    fun `40 parallel spends against cap 5 - exactly 5 win`() =
        runBlocking {
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 5), FakePersistence())

            val results =
                withContext(Dispatchers.Default) {
                    (1..40).map { async { policy.trySpend(Tier.FREE) } }.awaitAll()
                }

            assertThat(results.count { it == SpendResult.SPENT }).isEqualTo(5)
            assertThat(results.count { it == SpendResult.OVER }).isEqualTo(35)
            assertThat(policy.remaining.first()).isEqualTo(0)
        }

    @Test
    fun `midnight rollover resets the pool inside the same transaction`() =
        runTest {
            val clock = FakeClock()
            val policy = RealUsagePolicy(clock, FixedConfig(free = 2), FakePersistence())
            repeat(2) { assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) }
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER)

            clock.advanceDays(1)

            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) // fresh pool
            assertThat(policy.remaining.first()).isEqualTo(1)
        }

    @Test
    fun `refund floors at zero-spent and never overfills`() =
        runTest {
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 5), FakePersistence())

            policy.refund(Tier.FREE) // nothing spent yet
            assertThat(policy.remaining.first()).isEqualTo(5)

            policy.trySpend(Tier.FREE)
            assertThat(policy.remaining.first()).isEqualTo(4)
            policy.refund(Tier.FREE)
            assertThat(policy.remaining.first()).isEqualTo(5)
        }

    @Test
    fun `PRO pool is independent - fair-use guard enforced, FREE meter untouched`() =
        runTest {
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 1, pro = 2), FakePersistence())
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER) // FREE exhausted

            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.OVER) // fair-use cap

            assertThat(policy.remaining.first()).isEqualTo(0) // PRO spends never move the FREE meter
        }

    // ---- issue #66: the counters survive process death ----------------------

    @Test
    fun `spends survive a policy restart on the same store`() =
        runTest {
            val store = FakePersistence()
            val clock = FakeClock()
            val first = RealUsagePolicy(clock, FixedConfig(free = 5), store)
            repeat(3) { first.trySpend(Tier.FREE) }

            val reborn = RealUsagePolicy(clock, FixedConfig(free = 5), store) // "process restart"

            assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER) // 3 + 2 = the whole pool
            assertThat(reborn.remaining.first()).isEqualTo(0)
        }

    @Test
    fun `rollover persists - a restart on the new day keeps the fresh pool`() =
        runTest {
            val store = FakePersistence()
            val clock = FakeClock()
            val first = RealUsagePolicy(clock, FixedConfig(free = 2), store)
            repeat(2) { first.trySpend(Tier.FREE) }
            clock.advanceDays(1)
            first.trySpend(Tier.FREE) // rolls over, spends 1 of the new day

            val reborn = RealUsagePolicy(clock, FixedConfig(free = 2), store)

            assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) // 1 left today
            assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER)
        }

    @Test
    fun `refunds persist too`() =
        runTest {
            val store = FakePersistence()
            val clock = FakeClock()
            val first = RealUsagePolicy(clock, FixedConfig(free = 5), store)
            first.trySpend(Tier.FREE)
            first.refund(Tier.FREE)

            val reborn = RealUsagePolicy(clock, FixedConfig(free = 5), store)
            repeat(5) { assertThat(reborn.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) }
        }

    @Test
    fun `the store loads once and saves per mutation`() =
        runTest {
            val store = FakePersistence()
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 5), store)

            policy.trySpend(Tier.FREE)
            policy.trySpend(Tier.FREE)
            policy.refund(Tier.FREE)

            assertThat(store.loads).isEqualTo(1)
            assertThat(store.saves).isEqualTo(3)
        }

    @Test
    fun `a stale persisted day loads then rolls over to a fresh pool`() =
        runTest {
            val clock = FakeClock()
            val yesterday = clock.today().toEpochDay() - 1
            val store = FakePersistence(PersistedUsageCounts(freeSpent = 5, proSpent = 9, dayEpoch = yesterday))
            val policy = RealUsagePolicy(clock, FixedConfig(free = 5), store)

            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) // yesterday's 5 don't count
            assertThat(store.stored.dayEpoch).isEqualTo(clock.today().toEpochDay())
        }

    @Test
    fun `a throwing save never blocks the gate - fail-open like the load path`() =
        runTest {
            val exploding =
                object : UsagePersistence {
                    override suspend fun load(): PersistedUsageCounts =
                        PersistedUsageCounts(0, 0, PersistedUsageCounts.NO_DAY)

                    override suspend fun save(counts: PersistedUsageCounts): Unit =
                        throw java.io.IOException("disk full")
                }
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 2), exploding)

            // The decision stands even though every save explodes.
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER)
            policy.refund(Tier.FREE) // must not throw either
            assertThat(policy.remaining.first()).isEqualTo(1)
        }

    /**
     * The same claim against an `Error` (issue #236). The test above throws
     * `IOException`, which the old `catch (Exception)` already covered, so it
     * could not tell whether the guard was wide enough to deliver its own stated
     * contract — *"quota protection never blocks or crashes a translation"*.
     *
     * `trySpend` sits on the metered translate path, so a throw that walks past
     * this catch ends at `Thread.defaultUncaughtExceptionHandler`. Widened for
     * that reason and not by borrowing #195's JNI citation: nothing here is Room.
     */
    @Test
    fun `a save that fails as an Error still never blocks the gate`() =
        runTest {
            val exploding =
                object : UsagePersistence {
                    override suspend fun load(): PersistedUsageCounts =
                        PersistedUsageCounts(0, 0, PersistedUsageCounts.NO_DAY)

                    override suspend fun save(counts: PersistedUsageCounts): Unit =
                        throw UnsatisfiedLinkError("nativeExecute")
                }
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 2), exploding)

            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER)
            policy.refund(Tier.FREE) // must not throw either
            assertThat(policy.remaining.first()).isEqualTo(1)
        }

    @Test
    fun `meter starts at the full allowance`() =
        runTest {
            assertThat(
                RealUsagePolicy(FakeClock(), FixedConfig(free = 5), FakePersistence()).remaining.first(),
            ).isEqualTo(5)
        }
}
