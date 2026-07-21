package com.codeboxlk.tranzlate.core.testing

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** §1.5 determinism: fixed instant, Asia/Colombo zone, advanceDays moves the date. */
class FakeClockTest {

    @Test
    fun `fixed instant maps to the contract date in Asia-Colombo`() {
        val clock = FakeClock()

        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 7, 21))
    }

    @Test
    fun `advanceDays crosses the local-midnight boundary`() {
        val clock = FakeClock()
        val before = clock.today()

        clock.advanceDays(1)

        assertThat(clock.today()).isEqualTo(before.plusDays(1))
    }
}
