package com.codeboxlk.tranzlate.domain.usage

import com.codeboxlk.tranzlate.core.model.Tier
import kotlinx.coroutines.flow.Flow

/** Outcome of an atomic [UsagePolicy.trySpend]. */
enum class SpendResult {
    SPENT,
    OVER,
}

/**
 * USAGE BRAIN ask-surface (issue #53 PR-4 — TEST_A11Y §1.4 rev.2).
 *
 * Daily metered counter (D-2 rev.2: FREE 5/day AI via `limit_free_ai` · PRO
 * behind the never-marketed fair-use guard), device-local-midnight reset via
 * the injectable AppClock.
 *
 * The old `isOver()` + `increment()` pair was a TOCTOU race — a double-tap
 * could pass both checks at 4/5 and land on 6/5. [trySpend] is ONE atomic
 * check-and-spend and the only gate; DECISIONS' success-only constant is
 * preserved by [refund] (spend at the gate, refund on failure — net spend
 * only on success). `warningMessage()` retired: UI derives it from [remaining].
 */
interface UsagePolicy {
    /** FREE pool's live meter for the "{left}/5 today" UI; emits on spend/refund/reset. */
    val remaining: Flow<Int>

    /**
     * ATOMIC check-and-spend — THE metered gate. [tier] picks the pool
     * (D-2 rev.2): FREE → `limit_free_ai`, PRO → `limit_pro_fair_use`.
     */
    suspend fun trySpend(tier: Tier): SpendResult

    /**
     * Returns a failed attempt's spend (DECISIONS success-only constant —
     * never charge on failure). Floors at zero-spent; never overfills a pool.
     */
    suspend fun refund(tier: Tier)
}
