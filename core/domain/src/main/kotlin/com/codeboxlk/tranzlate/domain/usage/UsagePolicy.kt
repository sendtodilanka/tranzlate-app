package com.codeboxlk.tranzlate.domain.usage

/**
 * USAGE BRAIN ask-surface (TEST_A11Y_CONTRACT §1.4 — verbatim shape).
 * Daily metered counter (D-2: Free 20/day · Plus 100/day · Premium unlimited),
 * device-local-midnight reset via the injectable AppClock, success-only increment
 * (DECISIONS engineering constants: never on cache hit, start, or failure).
 */
interface UsagePolicy {
    /** Remaining NLP3.5 translations today; -1 = unlimited. */
    fun remaining(): Int

    fun isOver(): Boolean

    /** e.g. "You have N free NLP3.5 translations left today"; null when no warning. */
    fun warningMessage(): String?

    suspend fun increment()
}
