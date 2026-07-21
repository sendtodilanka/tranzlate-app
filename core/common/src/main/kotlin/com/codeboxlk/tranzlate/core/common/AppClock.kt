package com.codeboxlk.tranzlate.core.common

import java.time.LocalDate

/**
 * Injectable time seam (TEST_A11Y_CONTRACT §1.5) — daily-usage reset logic
 * (device-local midnight, DECISIONS defaults table) must never read wall-clock
 * time directly.
 *
 * NOTE: `SystemAppClock` intentionally does NOT live in this module — the real
 * implementation and its binding are prod-side wiring in `:app/src/prod`
 * (plan §6.2); the fake variant binds `FakeClock` from `:core:testing`.
 */
interface AppClock {
    fun nowMillis(): Long

    fun today(): LocalDate
}
