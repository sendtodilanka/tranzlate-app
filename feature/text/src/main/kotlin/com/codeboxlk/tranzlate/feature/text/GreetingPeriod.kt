package com.codeboxlk.tranzlate.feature.text

import java.time.Instant
import java.time.ZoneId

/** Time-aware greeting band (UI_SPEC §2.1 canvas — "Morning / Afternoon / Evening"). */
enum class GreetingPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
}

private const val MORNING_FROM_HOUR = 5
private const val AFTERNOON_FROM_HOUR = 12
private const val EVENING_FROM_HOUR = 17

/**
 * Pure, clock-injectable mapping (TEST_A11Y_CONTRACT §1.5 determinism — unit
 * tests pass FakeClock millis + a fixed zone): 05–11 Morning · 12–16 Afternoon ·
 * 17–04 Evening (late night reads as Evening, matching common assistant-greeting
 * behaviour).
 */
fun greetingPeriodFor(
    nowMillis: Long,
    zone: ZoneId,
): GreetingPeriod {
    val hour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
    return when {
        hour in MORNING_FROM_HOUR until AFTERNOON_FROM_HOUR -> GreetingPeriod.MORNING
        hour in AFTERNOON_FROM_HOUR until EVENING_FROM_HOUR -> GreetingPeriod.AFTERNOON
        else -> GreetingPeriod.EVENING
    }
}
