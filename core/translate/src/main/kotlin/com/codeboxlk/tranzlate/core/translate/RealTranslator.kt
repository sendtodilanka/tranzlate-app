package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.EngineAttempt
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.translate.Translator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TRANSLATION BRAIN orchestrator (plan §2 — the one home for engine choice).
 *
 * Phase-2 scope (spec 02 §1.2/§5.1): engine adapters (MLKit offline / GOT free
 * online — D-E1 risk-accepted, one internal class / GCT paid), the AUTO
 * free-only resolver (C-10 — never NLP35), the fallback chain, and C-8
 * cache-first lookup via TranslationRepository.
 */
@Singleton
class RealTranslator
    @Inject
    constructor() : Translator {
        // TODO(#4-brains): real implementation — placeholder reports a one-attempt
        // trace (AUTO's offline-first head). NO fake/golden behaviour here (prod).
        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
            mode: ModeId,
        ): TranslationOutcome =
            TranslationOutcome.Error(
                listOf(EngineAttempt(Engine.OFFLINE_MLKIT, AttemptCause.ENGINE_ERROR)),
            )
    }
