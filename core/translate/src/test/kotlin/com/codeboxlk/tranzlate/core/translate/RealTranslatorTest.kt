package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.config.FeatureToggle
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.EngineAttempt
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.core.translate.engine.EngineResult
import com.codeboxlk.tranzlate.core.translate.engine.TranslateEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The waterfall's whole contract (plan issue-61 diagram): every branch below
 * asserts the EXACT trace the owner's dialog would read.
 */
class RealTranslatorTest {
    private class FakeEngine(
        override val engine: Engine,
        var result: EngineResult,
    ) : TranslateEngine {
        val calls = mutableListOf<Triple<String, String, String>>()

        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
        ): EngineResult {
            calls += Triple(text, srcLang, tgtLang)
            return result
        }
    }

    private class FixedConfig(
        private val gotEnabled: Boolean = true,
    ) : RemoteConfigSource {
        override fun limitFreeAi(): Int = 5

        override fun limitProFairUse(): Int = 2000

        override fun adNth(): Int = 2

        override fun adMinGapSeconds(): Int = 90

        override fun adDailyCap(): Int = 12

        override fun textLimitFree(): Int = 500

        override fun textLimitPro(): Int = 5000

        override fun gotEnabled(): Boolean = gotEnabled

        override fun gotTimeoutMs(): Long = 10_000L

        override fun gctTimeoutMs(): Long = 15_000L
    }

    private class TestAppConfig(
        override val gctApiKey: String = "test-key",
    ) : AppConfig {
        override val admobAppId: String = ""
        override val adUnitBanner: String = ""
        override val adUnitInterstitial: String = ""
        override val qonversionKey: String = ""
        override val defaultSourceLang: String = "en"
        override val defaultTargetLang: String = "fr"
        override val featureToggles: Set<FeatureToggle> = emptySet()
    }

    private fun mlkit(result: EngineResult) = FakeEngine(Engine.OFFLINE_MLKIT, result)

    private fun got(result: EngineResult) = FakeEngine(Engine.ONLINE_GOOGLE, result)

    private fun gct(result: EngineResult) = FakeEngine(Engine.ONLINE_CLOUD_NLP, result)

    private val modelMissing = EngineResult.Failure(AttemptCause.MODEL_NOT_DOWNLOADED)
    private val engineError = EngineResult.Failure(AttemptCause.ENGINE_ERROR)

    @Suppress("LongParameterList")
    private fun translator(
        tier1: TranslateEngine = mlkit(modelMissing),
        tier2: TranslateEngine = got(engineError),
        tier3: TranslateEngine = gct(engineError),
        identified: String? = "en",
        online: Boolean = true,
        gotEnabled: Boolean = true,
        gctKey: String = "test-key",
        access: FakeFeatureAccess = FakeFeatureAccess(),
        usage: FakeUsagePolicy = FakeUsagePolicy(left = 5),
    ) = RealTranslator(
        tier1Offline = tier1,
        tier2FreeOnline = tier2,
        tier3Paid = tier3,
        identify = { identified },
        connectivity = FakeConnectivityMonitor(initiallyOnline = online),
        config = FixedConfig(gotEnabled = gotEnabled),
        appConfig = TestAppConfig(gctApiKey = gctKey),
        featureAccess = access,
        usagePolicy = usage,
    )

    // ---- Tier 1 ----

    @Test
    fun `offline tier wins with the identified source typed into the outcome`() =
        runTest {
            val tier1 = mlkit(EngineResult.Success("Bonjour"))

            val outcome = translator(tier1 = tier1).translate("Good morning", "auto", "fr", ModeId.AUTO)

            val success = outcome as TranslationOutcome.Success
            assertThat(success.resolvedEngine).isEqualTo(Engine.OFFLINE_MLKIT)
            assertThat(success.detectedSource).isEqualTo("en")
            assertThat(tier1.calls.single().second).isEqualTo("en") // resolved, never "auto"
        }

    @Test
    fun `und detection skips MLKit with a SKIPPED_SOURCE_UNKNOWN trace entry`() =
        runTest {
            val tier1 = mlkit(EngineResult.Success("never"))
            val tier2 = got(EngineResult.Success("Bonjour", detectedSource = "fr"))

            val outcome =
                translator(tier1 = tier1, tier2 = tier2, identified = null)
                    .translate("Bnjr", "auto", "en", ModeId.AUTO)

            val success = outcome as TranslationOutcome.Success
            assertThat(success.resolvedEngine).isEqualTo(Engine.ONLINE_GOOGLE)
            assertThat(success.detectedSource).isEqualTo("fr") // the ENGINE's server-side detect
            assertThat(tier1.calls).isEmpty() // never fed a guess
            assertThat(tier2.calls.single().second).isEqualTo("auto")
        }

    // ---- Pre-flight ----

    @Test
    fun `offline pre-flight reports both online tiers dead without calling them`() =
        runTest {
            val tier2 = got(EngineResult.Success("never"))
            val tier3 = gct(EngineResult.Success("never"))

            val outcome =
                translator(tier2 = tier2, tier3 = tier3, online = false)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            val error = outcome as TranslationOutcome.Error
            assertThat(error.attempts)
                .containsExactly(
                    EngineAttempt(Engine.OFFLINE_MLKIT, AttemptCause.MODEL_NOT_DOWNLOADED),
                    EngineAttempt(Engine.ONLINE_GOOGLE, AttemptCause.OFFLINE),
                    EngineAttempt(Engine.ONLINE_CLOUD_NLP, AttemptCause.OFFLINE),
                ).inOrder() // the owner's dialog, verbatim
            assertThat(tier2.calls).isEmpty()
            assertThat(tier3.calls).isEmpty()
        }

    // ---- Tier 2 ----

    @Test
    fun `kill-switch off skips GOT silently and the tail still runs`() =
        runTest {
            val tier2 = got(EngineResult.Success("never"))
            val tier3 = gct(EngineResult.Success("Bonjour"))

            val outcome =
                translator(tier2 = tier2, tier3 = tier3, gotEnabled = false)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            assertThat((outcome as TranslationOutcome.Success).resolvedEngine)
                .isEqualTo(Engine.ONLINE_CLOUD_NLP)
            assertThat(tier2.calls).isEmpty() // ops decision — no trace entry either
        }

    // ---- Tier 3: the quota-gated tail ----

    @Test
    fun `exhausted quota records SKIPPED_NO_QUOTA and never calls GCT`() =
        runTest {
            val tier3 = gct(EngineResult.Success("never"))
            val usage = FakeUsagePolicy(left = 0)

            val outcome =
                translator(tier3 = tier3, usage = usage)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            val error = outcome as TranslationOutcome.Error
            assertThat(error.attempts.last())
                .isEqualTo(EngineAttempt(Engine.ONLINE_CLOUD_NLP, AttemptCause.SKIPPED_NO_QUOTA))
            assertThat(tier3.calls).isEmpty()
        }

    @Test
    fun `unresolved entitlement is a bounded wait then a fail-safe skip - never a spend`() =
        runTest {
            val access = FakeFeatureAccess().apply { state.value = Entitlement.Loading }
            val usage = FakeUsagePolicy(left = 5)

            val outcome =
                translator(access = access, usage = usage)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            val error = outcome as TranslationOutcome.Error
            assertThat(error.attempts.last().cause).isEqualTo(AttemptCause.SKIPPED_NO_QUOTA)
            assertThat(usage.spends).isEqualTo(0)
        }

    @Test
    fun `GCT success spends exactly once from the resolved tier`() =
        runTest {
            val tier3 = gct(EngineResult.Success("Bonjour précis"))
            val usage = FakeUsagePolicy(left = 5)
            val access = FakeFeatureAccess(Tier.PRO)

            val outcome =
                translator(tier3 = tier3, usage = usage, access = access)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            assertThat((outcome as TranslationOutcome.Success).resolvedEngine)
                .isEqualTo(Engine.ONLINE_CLOUD_NLP)
            assertThat(usage.spends).isEqualTo(1)
            assertThat(usage.state.value).isEqualTo(5) // PRO pool — FREE meter untouched
        }

    @Test
    fun `GCT failure refunds the spend and keeps the FULL trace`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)

            val outcome =
                translator(usage = usage).translate("Good morning", "en", "fr", ModeId.AUTO)

            val error = outcome as TranslationOutcome.Error
            assertThat(error.attempts)
                .containsExactly(
                    EngineAttempt(Engine.OFFLINE_MLKIT, AttemptCause.MODEL_NOT_DOWNLOADED),
                    EngineAttempt(Engine.ONLINE_GOOGLE, AttemptCause.ENGINE_ERROR),
                    EngineAttempt(Engine.ONLINE_CLOUD_NLP, AttemptCause.ENGINE_ERROR),
                ).inOrder()
            assertThat(usage.spends).isEqualTo(1)
            assertThat(usage.refunds).isEqualTo(1) // net zero on failure
        }

    @Test
    fun `no API key means the paid tier is absent from the chain entirely`() =
        runTest {
            val tier3 = gct(EngineResult.Success("never"))
            val usage = FakeUsagePolicy(left = 5)

            val outcome =
                translator(tier3 = tier3, gctKey = "", usage = usage)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)

            val error = outcome as TranslationOutcome.Error
            assertThat(error.attempts.map { it.engine })
                .containsExactly(Engine.OFFLINE_MLKIT, Engine.ONLINE_GOOGLE)
            assertThat(tier3.calls).isEmpty()
            assertThat(usage.spends).isEqualTo(0)
        }

    @Test
    fun `cancellation inside the paid tail refunds the spend`() =
        runTest {
            val usage = FakeUsagePolicy(left = 5)
            val cancelling =
                object : TranslateEngine {
                    override val engine = Engine.ONLINE_CLOUD_NLP

                    override suspend fun translate(
                        text: String,
                        srcLang: String,
                        tgtLang: String,
                    ): EngineResult = throw kotlin.coroutines.cancellation.CancellationException("nav-away")
                }

            var thrown = false
            try {
                translator(tier3 = cancelling, usage = usage)
                    .translate("Good morning", "en", "fr", ModeId.AUTO)
            } catch (expected: kotlin.coroutines.cancellation.CancellationException) {
                thrown = true
            }

            assertThat(thrown).isTrue() // structured cancellation still propagates
            assertThat(usage.spends).isEqualTo(1)
            assertThat(usage.refunds).isEqualTo(1) // the dying tail returned the spend
        }

    // ---- Direct modes ----

    @Test
    fun `direct mode failure is a single-attempt trace`() =
        runTest {
            val outcome =
                translator(tier1 = mlkit(modelMissing))
                    .translate("Good morning", "en", "fr", ModeId.ML2_MINI)

            assertThat((outcome as TranslationOutcome.Error).attempts)
                .containsExactly(EngineAttempt(Engine.OFFLINE_MLKIT, AttemptCause.MODEL_NOT_DOWNLOADED))
        }

    @Test
    fun `direct NLP35 on a keyless brand is a guarded error - no call with an empty key`() =
        runTest {
            val tier3 = gct(EngineResult.Success("never")) as FakeEngine

            val outcome =
                translator(tier3 = tier3, gctKey = "")
                    .translate("Good morning", "en", "fr", ModeId.NLP35)

            assertThat((outcome as TranslationOutcome.Error).attempts)
                .containsExactly(EngineAttempt(Engine.ONLINE_CLOUD_NLP, AttemptCause.ENGINE_ERROR))
            assertThat(tier3.calls).isEmpty()
        }

    @Test
    fun `direct NLP35 never touches the waterfall's quota gate`() =
        runTest {
            val usage = FakeUsagePolicy(left = 0) // would be OVER if the tail gated it
            val tier3 = gct(EngineResult.Success("Bonjour précis"))

            val outcome =
                translator(tier3 = tier3, usage = usage)
                    .translate("Good morning", "en", "fr", ModeId.NLP35)

            assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
            assertThat(usage.spends).isEqualTo(0) // the USE CASE meters this mode, not the brain
        }
}
