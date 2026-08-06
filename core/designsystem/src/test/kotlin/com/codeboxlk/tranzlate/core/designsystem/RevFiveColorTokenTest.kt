package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * #347 regression lock — the design-system colour tokens must equal the rev5 colour
 * SSOT (owner ruling 2026-08-05: rev5 wins; where a token differs, the token is
 * corrected to rev5, light AND dark). The rev5 hexes below were extracted from the
 * frame markup of `docs/design/language-screens/language-screens-spec.html` and
 * cross-checked against `README.md:52-54` + `DESIGN_SYSTEM §1` (two independent passes).
 *
 * Assertions read through the ASSEMBLED [TranzlateLightColors] / [TranzlateDarkColors]
 * (the `ColorScheme` a screen actually consumes), so a wrong raw val OR a mis-wired
 * scheme slot both redden this — not just a renamed constant.
 *
 * The mutations these pins catch (each proven RED before merge, #347 PR):
 *  - `LightOnErrorContainer` back to the pre-rev5 #8C1D18  → [onErrorContainer light] fails.
 *  - `DarkSurfaceContainerHigh` back to the pre-rev5 #282A2C → [surfaceContainerHigh dark] fails.
 *  - `LightMeterFill` set to primaryContainer #D3E3FD (the 20d build's bug) → [meter fill] fails.
 *
 * This is a plain-JVM test (no Compose runtime, matching the module's other tests); the
 * `LocalMeterFillColor` light↔dark resolution in [TranzlateTheme] is pinned by its two
 * endpoints — the light raw token and the dark scheme role it resolves to.
 */
class RevFiveColorTokenTest {
    // ---- E2: onErrorContainer — light corrected to rev5 error10; dark already rev5 ----------------

    @Test
    fun `onErrorContainer light equals the rev5 SSOT error-text tone`() {
        // rev5 draws every failure text/icon on `errorContainer` in #410E0B (light).
        assertThat(TranzlateLightColors.onErrorContainer).isEqualTo(Color(0xFF410E0B))
    }

    @Test
    fun `onErrorContainer dark stays the rev5 SSOT value`() {
        // Guard: dark was already rev5-correct (#F9DEDC) and must not drift with the light fix.
        assertThat(TranzlateDarkColors.onErrorContainer).isEqualTo(Color(0xFFF9DEDC))
    }

    // ---- E1: surfaceContainerHigh — dark corrected to rev5; light unchanged ----------------------

    @Test
    fun `surfaceContainerHigh dark equals the rev5 SSOT chip-fill tone`() {
        // rev5 uses #2D2F31 for dark chips / ONLINE-ONLY fill / icon-button hover / meter track.
        assertThat(TranzlateDarkColors.surfaceContainerHigh).isEqualTo(Color(0xFF2D2F31))
    }

    @Test
    fun `surfaceContainerHigh light stays the rev5 SSOT value`() {
        // Guard: light already matched rev5 (#E9EEF6) and must not move with the dark fix.
        assertThat(TranzlateLightColors.surfaceContainerHigh).isEqualTo(Color(0xFFE9EEF6))
    }

    // ---- New extension token: storage-meter used-segment fill (#C4D7F5 light / #0842A0 dark) ------

    @Test
    fun `meter fill light equals the rev5 SSOT used-segment tint`() {
        assertThat(LightMeterFill).isEqualTo(Color(0xFFC4D7F5))
    }

    @Test
    fun `meter fill dark endpoint is primaryContainer, the rev5 used-segment tone`() {
        // LocalMeterFillColor resolves to primaryContainer in dark (TranzlateTheme); pin the value.
        assertThat(TranzlateDarkColors.primaryContainer).isEqualTo(Color(0xFF0842A0))
    }
}
