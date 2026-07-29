package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.usage.SpendResult
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USAGE BRAIN (plan §2 — the one home for the metered daily counter).
 *
 * Every mutation happens under ONE [Mutex]: check-and-spend is a single
 * critical section (A4 — the double-tap double-spend race cannot exist), and
 * the midnight rollover ([AppClock.today] vs the last-seen date) runs inside
 * the same section so a reset can't interleave a spend.
 *
 * TODO(#4-brains): counters are in-process this batch — process death forgets
 * today's spends (strictly better than the previous placeholder, which never
 * counted at all). The DataStore-transaction persistence lands with the
 * brains implementation phase.
 */
@Singleton
class RealUsagePolicy
    @Inject
    constructor(
        private val clock: AppClock,
        private val config: RemoteConfigSource,
    ) : UsagePolicy {
        private val mutex = Mutex()
        private var day: LocalDate? = null
        private var spentFree = 0
        private var spentPro = 0
        private val freeRemaining = MutableStateFlow(config.limitFreeAi())

        override val remaining: Flow<Int> = freeRemaining.asStateFlow()

        override suspend fun trySpend(tier: Tier): SpendResult =
            mutex.withLock {
                rollOverIfNewDay()
                val spent = spentOf(tier)
                if (spent >= capOf(tier)) {
                    SpendResult.OVER
                } else {
                    setSpent(tier, spent + 1)
                    SpendResult.SPENT
                }
            }

        override suspend fun refund(tier: Tier) {
            mutex.withLock {
                rollOverIfNewDay()
                val spent = spentOf(tier)
                if (spent > 0) setSpent(tier, spent - 1)
            }
        }

        private fun rollOverIfNewDay() {
            val today = clock.today()
            if (today != day) {
                day = today
                spentFree = 0
                spentPro = 0
                publish()
            }
        }

        private fun capOf(tier: Tier): Int = if (tier == Tier.FREE) config.limitFreeAi() else config.limitProFairUse()

        private fun spentOf(tier: Tier): Int = if (tier == Tier.FREE) spentFree else spentPro

        private fun setSpent(
            tier: Tier,
            value: Int,
        ) {
            if (tier == Tier.FREE) {
                spentFree = value
                publish()
            } else {
                spentPro = value
            }
        }

        /** The meter is the FREE pool's — PRO's fair-use guard is never surfaced. */
        private fun publish() {
            freeRemaining.value = (config.limitFreeAi() - spentFree).coerceAtLeast(0)
        }
    }
