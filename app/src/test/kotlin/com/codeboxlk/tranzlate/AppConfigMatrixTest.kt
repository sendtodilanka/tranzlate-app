package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.config.FeatureToggle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Per-flavor white-label config matrix gate (plan §4, co-verify finding #2).
 *
 * Runs once per build variant, so every brand x engine combination re-validates
 * its own BuildConfig values. Assertions are GENERIC (plan §4 R3) — a new brand
 * must pass without editing this file.
 */
class AppConfigMatrixTest {
    @Test
    fun `FEATURES csv parses into a typed toggle set`() {
        val toggles = FeatureToggle.parseCsv(BuildConfig.FEATURES)

        // Every brand ships at least its core Text screen and a Settings entry.
        assertThat(toggles).contains(FeatureToggle.TEXT)
        assertThat(toggles).contains(FeatureToggle.SETTINGS)
    }

    @Test
    fun `default language pair is a valid non-identical BCP-47-style pair`() {
        assertThat(BuildConfig.DEFAULT_SOURCE_LANG).matches("[a-z]{2,3}(-[A-Za-z0-9]+)?")
        assertThat(BuildConfig.DEFAULT_TARGET_LANG).matches("[a-z]{2,3}(-[A-Za-z0-9]+)?")
        assertThat(BuildConfig.DEFAULT_SOURCE_LANG).isNotEqualTo(BuildConfig.DEFAULT_TARGET_LANG)
    }

    @Test
    fun `ad and billing key slots exist and never hold Google sample ids`() {
        // Empty is valid pre-integration; Google's published sample ids never are.
        assertThat(BuildConfig.AD_UNIT_BANNER).doesNotContain("ca-app-pub-3940256099942544")
        assertThat(BuildConfig.AD_UNIT_INTERSTITIAL).doesNotContain("ca-app-pub-3940256099942544")
    }
}
