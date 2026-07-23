package com.codeboxlk.tranzlate.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `SYSTEM follows whatever the system reports`() {
        assertThat(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false)).isFalse()
    }

    /**
     * The whole point of an in-app override: an explicit choice must win over the
     * system, in both directions. A regression here is invisible until a user with
     * the opposite system setting opens the app.
     */
    @Test
    fun `an explicit choice overrides the system in both directions`() {
        assertThat(ThemeMode.LIGHT.isDark(systemInDarkTheme = true)).isFalse()
        assertThat(ThemeMode.DARK.isDark(systemInDarkTheme = false)).isTrue()
    }

    @Test
    fun `unknown stored values degrade to SYSTEM`() {
        assertThat(ThemeMode.fromStoredValue(99)).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStoredValue(-1)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `every mode round-trips through its stored value`() {
        for (mode in ThemeMode.entries) {
            assertThat(ThemeMode.fromStoredValue(mode.storedValue)).isEqualTo(mode)
        }
    }

    @Test
    fun `the default settings follow the system with the static palette`() {
        assertThat(ThemeSettings.Default.mode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeSettings.Default.dynamicColor).isFalse()
    }
}
