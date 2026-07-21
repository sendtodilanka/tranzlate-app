// FROZEN — do not rename (TEST_A11Y_CONTRACT §1.1 line 26 mandates this package).
// Package `com.codeboxlk.tranzlate.domain.translate` is contract-frozen: unit,
// Compose and Maestro tests all bind fakes against it. A rename = breaking-change
// PR + contract doc update (plan §8 FROZEN markers; Konsist-asserted).
package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome

/**
 * TRANSLATION BRAIN ask-surface (TEST_A11Y_CONTRACT §1.1 mandated injectable seam).
 * The real orchestrator sits behind this interface so a deterministic fake can be
 * `@Provides`-swapped (§0 grounding rule — a real engine on any test path is a
 * contract violation).
 *
 * C-9 naming applied: the contract's unified `Engine{AUTO,ML2_MINI,ML2_ONLINE,NLP35}`
 * parameter is the canonical [ModeId] here (selection space, AUTO included); the
 * resolved [com.codeboxlk.tranzlate.core.model.Engine] is carried in
 * [TranslationOutcome.Success.resolvedEngine]. Contract doc alignment = follow-up
 * docs PR (plan §9).
 */
interface Translator {
    /** Deterministic in tests. [srcLang]/[tgtLang] are BCP-47 ("en","fr","zh"); "auto" = detect. */
    suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String,
        mode: ModeId,
    ): TranslationOutcome
}
