package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.common.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Deterministic clock (TEST_A11Y_CONTRACT §1.5 — verbatim). Daily-reset invariant:
 * when [today] moves past the last-reset date the usage counter must reset to 0,
 * otherwise stay unchanged.
 */
class FakeClock(
    var instant: Instant = Instant.parse("2026-07-21T09:00:00Z"),
    val zone: ZoneId = ZoneId.of("Asia/Colombo"),
) : AppClock {
    override fun nowMillis(): Long = instant.toEpochMilli()

    override fun today(): LocalDate = instant.atZone(zone).toLocalDate()

    fun advanceDays(n: Long) {
        instant = instant.plus(n, ChronoUnit.DAYS)
    }
}
