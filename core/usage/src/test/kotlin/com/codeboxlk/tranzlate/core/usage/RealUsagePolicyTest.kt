package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
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
    }

    @Test
    fun `40 parallel spends against cap 5 - exactly 5 win`() =
        runBlocking {
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 5))

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
            val policy = RealUsagePolicy(clock, FixedConfig(free = 2))
            repeat(2) { assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) }
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER)

            clock.advanceDays(1)

            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT) // fresh pool
            assertThat(policy.remaining.first()).isEqualTo(1)
        }

    @Test
    fun `refund floors at zero-spent and never overfills`() =
        runTest {
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 5))

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
            val policy = RealUsagePolicy(FakeClock(), FixedConfig(free = 1, pro = 2))
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.FREE)).isEqualTo(SpendResult.OVER) // FREE exhausted

            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.SPENT)
            assertThat(policy.trySpend(Tier.PRO)).isEqualTo(SpendResult.OVER) // fair-use cap

            assertThat(policy.remaining.first()).isEqualTo(0) // PRO spends never move the FREE meter
        }

    @Test
    fun `meter starts at the full allowance`() =
        runTest {
            assertThat(RealUsagePolicy(FakeClock(), FixedConfig(free = 5)).remaining.first()).isEqualTo(5)
        }
}
