package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.domain.usage.UsagePolicy

/**
 * Deterministic Usage fake (TEST_A11Y_CONTRACT §1.4 — verbatim behaviour).
 * Scenario states: Under(left=5) · Last(left=1) · AtLimit(left=0) · Unlimited(left=-1).
 */
class FakeUsagePolicy(
    private var left: Int,
    @Suppress("unused") private val cap: Int = 20,
) : UsagePolicy {
    /** Spy: how many times [increment] ran (issue #53 A2 — cache hits must never spend). */
    var incremented: Int = 0
        private set

    override fun remaining(): Int = left

    override fun isOver(): Boolean = left == 0

    override fun warningMessage(): String? =
        if (left in 1..3) "$left free NLP3.5 translation${if (left == 1) "" else "s"} left today" else null

    override suspend fun increment() {
        incremented++
        if (left > 0) left--
    }
}
