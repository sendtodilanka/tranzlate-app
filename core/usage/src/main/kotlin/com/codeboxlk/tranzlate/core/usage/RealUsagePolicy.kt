package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.datastore.PersistedUsageCounts
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
 * Counters SURVIVE process death (issue #66): the persisted facts load ONCE,
 * lazily, inside the same mutex — no new races — and every mutation block
 * ends with one atomic save. An unreadable store falls back to a fresh day
 * (fail-open once, never a crash: quota protection must not block translation).
 */
@Singleton
class RealUsagePolicy
    @Inject
    constructor(
        private val clock: AppClock,
        private val config: RemoteConfigSource,
        private val persistence: UsagePersistence,
    ) : UsagePolicy {
        private val mutex = Mutex()
        private var loaded = false
        private var day: LocalDate? = null
        private var spentFree = 0
        private var spentPro = 0
        private val freeRemaining = MutableStateFlow(config.limitFreeAi())

        override val remaining: Flow<Int> = freeRemaining.asStateFlow()

        override suspend fun trySpend(tier: Tier): SpendResult =
            mutex.withLock {
                ensureLoaded()
                rollOverIfNewDay()
                val spent = spentOf(tier)
                val result =
                    if (spent >= capOf(tier)) {
                        SpendResult.OVER
                    } else {
                        setSpent(tier, spent + 1)
                        SpendResult.SPENT
                    }
                persist()
                result
            }

        override suspend fun refund(tier: Tier) {
            mutex.withLock {
                ensureLoaded()
                rollOverIfNewDay()
                val spent = spentOf(tier)
                if (spent > 0) setSpent(tier, spent - 1)
                persist()
            }
        }

        /** Runs under the mutex only. First touch pulls the persisted facts. */
        private suspend fun ensureLoaded() {
            if (loaded) return
            val counts = persistence.load()
            spentFree = counts.freeSpent
            spentPro = counts.proSpent
            day =
                if (counts.dayEpoch == PersistedUsageCounts.NO_DAY) {
                    null // first run — the rollover below stamps today
                } else {
                    LocalDate.ofEpochDay(counts.dayEpoch)
                }
            loaded = true
            publish()
        }

        /**
         * Runs under the mutex only. A failed WRITE must be as harmless as a
         * failed read (PR-67 lens OPEN-1): quota protection never blocks or
         * crashes a translation — the in-memory decision stands and the next
         * mutation retries the save.
         *
         * `Throwable`, not `Exception` (issue #236) — and, as at [UsagePersistence]'s
         * DataStore adapter, NOT for `TextViewModel.kt:768-779`'s JNI reason: nothing
         * here is Room, and that citation is not borrowed. The reason is this
         * function's own first sentence. A guard whose stated contract is "never
         * crashes a translation" is not delivering it while a whole failure class
         * walks past the catch to `Thread.defaultUncaughtExceptionHandler`, and
         * `trySpend` is on the metered translate path.
         */
        private suspend fun persist() {
            try {
                persistence.save(
                    PersistedUsageCounts(
                        freeSpent = spentFree,
                        proSpent = spentPro,
                        dayEpoch = day?.toEpochDay() ?: PersistedUsageCounts.NO_DAY,
                    ),
                )
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Throwable,
            ) {
                // Disk-full / IO error: fail-open, matching the load path.
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
