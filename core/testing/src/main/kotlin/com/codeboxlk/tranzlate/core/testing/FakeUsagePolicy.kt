package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.usage.SpendResult
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Deterministic Usage fake (TEST_A11Y_CONTRACT §1.4 rev.2).
 * Scenario states: Under(left=5) · Last(left=1) · AtLimit(left=0) ·
 * Unlimited(left=-1). PRO spends always succeed (the fair-use guard is a
 * RealUsagePolicy concern) and never touch the FREE meter.
 */
class FakeUsagePolicy(
    left: Int,
    private val cap: Int = 5,
) : UsagePolicy {
    /** Spy: successful spends (issue #53 A2 — cache hits must never spend). */
    var spends: Int = 0
        private set

    /** Spy: refunds (DECISIONS success-only — failures must return the spend). */
    var refunds: Int = 0
        private set

    /** Mutable so tests drive the meter directly. */
    val state: MutableStateFlow<Int> = MutableStateFlow(left)

    override val remaining: Flow<Int> get() = state

    override suspend fun trySpend(tier: Tier): SpendResult =
        when {
            tier == Tier.PRO || state.value == -1 -> {
                spends++
                SpendResult.SPENT
            }

            state.value <= 0 -> {
                SpendResult.OVER
            }

            else -> {
                spends++
                state.value -= 1
                SpendResult.SPENT
            }
        }

    override suspend fun refund(tier: Tier) {
        refunds++
        if (tier == Tier.FREE && state.value in 0 until cap) state.value += 1
    }
}
