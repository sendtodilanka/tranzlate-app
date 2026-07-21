package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Golden-table behaviour proof (TEST_A11Y_CONTRACT §1.2 — exact outputs). */
class FakeTranslatorTest {

    private val translator = FakeTranslator()

    @Test
    fun `G1 en-fr ML2_MINI returns exact golden output`() = runTest {
        val outcome = translator.translate("Good morning", "en", "fr", ModeId.ML2_MINI)

        val success = outcome as TranslationOutcome.Success
        assertThat(success.text).isEqualTo("Bonjour (fake)")
        assertThat(success.resolvedEngine).isEqualTo(Engine.OFFLINE_MLKIT) // C-9: ML2_MINI
        // spy: the engine/mode actually invoked (contract §1.1)
        assertThat(translator.calls.last().mode).isEqualTo(ModeId.ML2_MINI)
    }

    @Test
    fun `G2 AUTO resolves offline-first with same golden output`() = runTest {
        val outcome = translator.translate("Good morning", "en", "fr", ModeId.AUTO)

        val success = outcome as TranslationOutcome.Success
        assertThat(success.text).isEqualTo("Bonjour (fake)")
        assertThat(success.resolvedEngine).isEqualTo(Engine.OFFLINE_MLKIT)
    }

    @Test
    fun `G3 NLP35 returns the distinct advanced output`() = runTest {
        val outcome = translator.translate("Good morning", "en", "fr", ModeId.NLP35)

        val success = outcome as TranslationOutcome.Success
        assertThat(success.text).isEqualTo("Bonjour, comment allez-vous (fake)")
        assertThat(success.resolvedEngine).isEqualTo(Engine.ONLINE_CLOUD_NLP)
    }

    @Test
    fun `G7 auto source detects and translates`() = runTest {
        val outcome = translator.translate("Good morning", "auto", "fr", ModeId.AUTO)

        assertThat((outcome as TranslationOutcome.Success).text).isEqualTo("Bonjour (fake)")
    }

    @Test
    fun `G8 pair without golden row is UNSUPPORTED_PAIR`() = runTest {
        val outcome = translator.translate("நன்றி", "ta", "en", ModeId.ML2_MINI)

        assertThat(outcome).isEqualTo(TranslationOutcome.Error(FailureReason.UNSUPPORTED_PAIR))
    }

    @Test
    fun `G9 blank input is EMPTY_INPUT for any engine`() = runTest {
        val outcome = translator.translate("", "en", "fr", ModeId.ML2_MINI)

        assertThat(outcome).isEqualTo(TranslationOutcome.Error(FailureReason.EMPTY_INPUT))
    }

    @Test
    fun `G10 forcedFailure NETWORK overrides golden lookup and can recover`() = runTest {
        translator.forcedFailure = FailureReason.NETWORK

        val failed = translator.translate("Offline test", "en", "fr", ModeId.ML2_ONLINE)
        assertThat(failed).isEqualTo(TranslationOutcome.Error(FailureReason.NETWORK))

        translator.forcedFailure = null // network back → retry replays (contract §1.8)
        val retried = translator.translate("Good morning", "en", "fr", ModeId.ML2_MINI)
        assertThat((retried as TranslationOutcome.Success).text).isEqualTo("Bonjour (fake)")
    }

    @Test
    fun `input is trimmed before golden lookup`() = runTest {
        val outcome = translator.translate("  Good morning  ", "en", "fr", ModeId.ML2_MINI)

        assertThat((outcome as TranslationOutcome.Success).text).isEqualTo("Bonjour (fake)")
    }
}
