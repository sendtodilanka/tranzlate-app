package com.codeboxlk.tranzlate.core.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The key→value mapping is the part of remote config that can silently ship the
 * wrong product: a renamed key, a 0 that means "no timeout", a console value
 * bound to the wrong knob. All of it is a pure function, so all of it is tested
 * here without Firebase.
 */
class RemoteConfigSnapshotTest {
    private class MapReader(
        private val values: Map<String, Any>,
    ) : RemoteValueReader {
        override fun string(key: String): String =
            values[key] as? String ?: RemoteConfigSnapshot.asRemoteDefaults[key] as? String ?: ""

        override fun long(key: String): Long =
            (values[key] as? Number ?: RemoteConfigSnapshot.asRemoteDefaults[key] as? Number)
                ?.toLong() ?: 0L

        override fun boolean(key: String): Boolean =
            values[key] as? Boolean ?: RemoteConfigSnapshot.asRemoteDefaults[key] as? Boolean ?: false
    }

    @Test
    fun `an empty console yields exactly the confirmed defaults`() {
        assertThat(readRemoteConfig(MapReader(emptyMap()))).isEqualTo(RemoteConfigSnapshot.DEFAULTS)
    }

    @Test
    fun `credentials and legal links map from the LIVE project's key names`() {
        val snapshot =
            readRemoteConfig(
                MapReader(
                    mapOf(
                        "QonversionApiKey" to "qk_live",
                        "CloudApiKey" to "AIza_live",
                        "PrivacyPolicy" to "https://example.test/privacy",
                        "TermsAndCondition" to "https://example.test/terms",
                        "ContactEmail" to "hello@example.test",
                    ),
                ),
            )

        assertThat(snapshot.qonversionKey).isEqualTo("qk_live")
        assertThat(snapshot.gctApiKey).isEqualTo("AIza_live")
        assertThat(snapshot.privacyPolicyUrl).isEqualTo("https://example.test/privacy")
        assertThat(snapshot.termsUrl).isEqualTo("https://example.test/terms")
        assertThat(snapshot.contactEmail).isEqualTo("hello@example.test")
    }

    @Test
    fun `a console value with stray whitespace is trimmed - a padded key is a dead key`() {
        val snapshot = readRemoteConfig(MapReader(mapOf("QonversionApiKey" to "  qk_live\n")))

        assertThat(snapshot.qonversionKey).isEqualTo("qk_live")
    }

    @Test
    fun `a zero timeout falls back - OkHttp reads 0 as NO timeout, which would hang the waterfall`() {
        val snapshot =
            readRemoteConfig(MapReader(mapOf("got_timeout_ms" to 0L, "gct_timeout_ms" to -1L)))

        assertThat(snapshot.gotTimeoutMs).isEqualTo(RemoteConfigDefaults.GOT_TIMEOUT_MS)
        assertThat(snapshot.gctTimeoutMs).isEqualTo(RemoteConfigDefaults.GCT_TIMEOUT_MS)
    }

    @Test
    fun `limit_free_ai of zero is HONOURED - disabling the free AI pool is a real ops decision`() {
        val snapshot = readRemoteConfig(MapReader(mapOf("limit_free_ai" to 0L)))

        assertThat(snapshot.limitFreeAi).isEqualTo(0)
    }

    @Test
    fun `the GOT kill-switch flips from the console`() {
        assertThat(readRemoteConfig(MapReader(mapOf("got_enabled" to false))).gotEnabled).isFalse()
    }

    @Test
    fun `the OLD product's live keys are NOT bound to our new policy knobs`() {
        // The shared Firebase project still publishes FeatureLimitPerDay=20 and
        // AdGapInMinute=15 for the legacy app. Binding either would let another
        // app's console value overwrite D-2 rev.2 / D-4.
        val snapshot =
            readRemoteConfig(MapReader(mapOf("FeatureLimitPerDay" to 20L, "AdGapInMinute" to 15L)))

        assertThat(snapshot.limitFreeAi).isEqualTo(RemoteConfigDefaults.LIMIT_FREE_AI)
        assertThat(snapshot.adMinGapSeconds).isEqualTo(RemoteConfigDefaults.AD_MIN_GAP_SECONDS)
    }

    @Test
    fun `every default is published under its remote key - a missing entry means the SDK static default wins`() {
        assertThat(RemoteConfigSnapshot.asRemoteDefaults).hasSize(15)
        assertThat(RemoteConfigSnapshot.asRemoteDefaults.keys)
            .containsExactly(
                RemoteConfigKeys.LIMIT_FREE_AI,
                RemoteConfigKeys.LIMIT_PRO_FAIR_USE,
                RemoteConfigKeys.AD_NTH,
                RemoteConfigKeys.AD_MIN_GAP_S,
                RemoteConfigKeys.AD_DAILY_CAP,
                RemoteConfigKeys.TEXT_LIMIT_FREE,
                RemoteConfigKeys.TEXT_LIMIT_PRO,
                RemoteConfigKeys.GOT_ENABLED,
                RemoteConfigKeys.GOT_TIMEOUT_MS,
                RemoteConfigKeys.GCT_TIMEOUT_MS,
                RemoteConfigKeys.QONVERSION_API_KEY,
                RemoteConfigKeys.CLOUD_API_KEY,
                RemoteConfigKeys.PRIVACY_POLICY,
                RemoteConfigKeys.TERMS_AND_CONDITION,
                RemoteConfigKeys.CONTACT_EMAIL,
            )
    }
}

/** The one precedence rule both credentials share (remote rotates, build config floors). */
class CredentialResolutionTest {
    private class Config(
        override val qonversionKey: String = "",
        override val gctApiKey: String = "",
    ) : AppConfig {
        override val admobAppId: String = ""
        override val adUnitBanner: String = ""
        override val adUnitInterstitial: String = ""
        override val defaultSourceLang: String = "en"
        override val defaultTargetLang: String = "fr"
        override val defaultAllowMobileData: Boolean = false
        override val featureToggles: Set<FeatureToggle> = emptySet()
    }

    private class Source(
        private val qonversion: String = "",
        private val gct: String = "",
    ) : RemoteConfigSource by StubSource() {
        override fun qonversionKey(): String = qonversion

        override fun gctApiKey(): String = gct
    }

    @Test
    fun `remote wins when present - that is the rotation channel`() {
        val resolved = Source(qonversion = "remote").effectiveQonversionKey(Config(qonversionKey = "built-in"))

        assertThat(resolved).isEqualTo("remote")
    }

    @Test
    fun `build config is the first-launch floor when remote has not fetched`() {
        val resolved = Source().effectiveGctApiKey(Config(gctApiKey = "built-in"))

        assertThat(resolved).isEqualTo("built-in")
    }

    @Test
    fun `both blank stays blank - not configured must stay visible, never a fabricated key`() {
        assertThat(Source().effectiveQonversionKey(Config())).isEmpty()
    }
}

/** Minimal delegate so the tests above only state the fields they care about. */
private class StubSource : RemoteConfigSource {
    override fun limitFreeAi(): Int = RemoteConfigDefaults.LIMIT_FREE_AI

    override fun limitProFairUse(): Int = RemoteConfigDefaults.LIMIT_PRO_FAIR_USE

    override fun adNth(): Int = RemoteConfigDefaults.AD_NTH

    override fun adMinGapSeconds(): Int = RemoteConfigDefaults.AD_MIN_GAP_SECONDS

    override fun adDailyCap(): Int = RemoteConfigDefaults.AD_DAILY_CAP

    override fun textLimitFree(): Int = RemoteConfigDefaults.TEXT_LIMIT_FREE

    override fun textLimitPro(): Int = RemoteConfigDefaults.TEXT_LIMIT_PRO

    override fun gotEnabled(): Boolean = RemoteConfigDefaults.GOT_ENABLED

    override fun gotTimeoutMs(): Long = RemoteConfigDefaults.GOT_TIMEOUT_MS

    override fun gctTimeoutMs(): Long = RemoteConfigDefaults.GCT_TIMEOUT_MS

    override fun qonversionKey(): String = ""

    override fun gctApiKey(): String = ""

    override fun privacyPolicyUrl(): String = ""

    override fun termsUrl(): String = ""

    override fun contactEmail(): String = ""

    override suspend fun awaitFirstFetch() = Unit
}
