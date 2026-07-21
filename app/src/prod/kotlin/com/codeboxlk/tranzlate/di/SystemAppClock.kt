package com.codeboxlk.tranzlate.di

import com.codeboxlk.tranzlate.core.common.AppClock
import java.time.LocalDate

/**
 * Real wall-clock (plan §6.2 — prod-side ONLY; deliberately not in `:core:common`
 * so the fake variant can bind FakeClock without duplicate-binding conflicts).
 * java.time on minSdk 24 via coreLibraryDesugaring (plan §1 last row).
 */
class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun today(): LocalDate = LocalDate.now()
}
