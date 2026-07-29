package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.EngineAttempt
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.translate.Translator

/**
 * Mandated deterministic test double (TEST_A11Y_CONTRACT §1.1) backed by the
 * §1.2 golden fixture table — the single source of truth for unit, Compose UI,
 * Maestro AND the installable fake variant.
 *
 * RULE (§1.2): adding a table row requires an IDENTICAL entry in [defaultGolden];
 * tests never mutate a tuple — they add a new row.
 *
 * C-9 naming applied (canonical override): the contract's unified `Engine` input
 * is [ModeId]; the golden `resolvedEngine` column maps through C-9
 * (ML2_MINI→OFFLINE_MLKIT · ML2_ONLINE→ONLINE_GOOGLE · NLP35→ONLINE_CLOUD_NLP;
 * AUTO resolves offline-first → OFFLINE_MLKIT in the fake).
 */
class FakeTranslator(
    private val golden: Map<GoldenKey, TranslationOutcome> = defaultGolden,
    var forcedFailure: AttemptCause? = null, // test can force OFFLINE/ENGINE_ERROR (G10)
) : Translator {
    data class GoldenKey(
        val text: String,
        val src: String,
        val tgt: String,
        val mode: ModeId,
    )

    /** Spy: assert the engine/mode actually invoked (contract §1.1). */
    val calls = mutableListOf<GoldenKey>()

    override suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String,
        mode: ModeId,
    ): TranslationOutcome {
        val key = GoldenKey(text.trim(), srcLang, tgtLang, mode)
        calls += key
        forcedFailure?.let {
            return TranslationOutcome.Error(listOf(EngineAttempt(resolvedEngineFor(mode), it)))
        }
        if (text.isBlank()) return TranslationOutcome.EmptyInput // G9 (rev.2: typed, not an attempt)
        return golden[key] // G8
            ?: TranslationOutcome.Error(
                listOf(EngineAttempt(resolvedEngineFor(mode), AttemptCause.UNSUPPORTED_PAIR)),
            )
    }

    /** C-9 mode→engine map — the attempt trace names the engine that would have run. */
    private fun resolvedEngineFor(mode: ModeId): Engine =
        when (mode) {
            ModeId.AUTO, ModeId.ML2_MINI -> Engine.OFFLINE_MLKIT

            // AUTO resolves offline-first
            ModeId.ML2_ONLINE -> Engine.ONLINE_GOOGLE

            ModeId.NLP35 -> Engine.ONLINE_CLOUD_NLP
        }

    companion object {
        /** Golden fixture table §1.2 — EXACT. G8–G11 are behaviour rows (no map entry). */
        val defaultGolden: Map<GoldenKey, TranslationOutcome> =
            mapOf(
                // G1
                GoldenKey("Good morning", "en", "fr", ModeId.ML2_MINI) to
                    TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
                // G2 — AUTO resolves offline-first (→ ML2_MINI ≙ OFFLINE_MLKIT)
                GoldenKey("Good morning", "en", "fr", ModeId.AUTO) to
                    TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT),
                // G3
                GoldenKey("Good morning", "en", "fr", ModeId.NLP35) to
                    TranslationOutcome.Success("Bonjour, comment allez-vous (fake)", Engine.ONLINE_CLOUD_NLP),
                // G4
                GoldenKey("Thank you", "en", "es", ModeId.ML2_ONLINE) to
                    TranslationOutcome.Success("Gracias (fake)", Engine.ONLINE_GOOGLE),
                // G5
                GoldenKey("Hello world", "en", "de", ModeId.ML2_MINI) to
                    TranslationOutcome.Success("Hallo Welt (fake)", Engine.OFFLINE_MLKIT),
                // G6
                GoldenKey("こんにちは", "ja", "en", ModeId.NLP35) to
                    TranslationOutcome.Success("Hello (fake)", Engine.ONLINE_CLOUD_NLP),
                // G7 — src "auto", detect→en, resolves offline-first (rev.2: detect is typed)
                GoldenKey("Good morning", "auto", "fr", ModeId.AUTO) to
                    TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT, detectedSource = "en"),
                // G8  `நன்றி` ta→en ML2_MINI — intentionally NO row → Error(attempt UNSUPPORTED_PAIR)
                // G9  blank input — behaviour → EmptyInput (rev.2: validation, not an attempt)
                // G10 `Offline test` — behaviour via forcedFailure=OFFLINE → Error(attempt OFFLINE)
                // G11 `Quota text` NLP35 — LimitReached via UsagePolicy (§1.4), not a golden row
            )
    }
}
