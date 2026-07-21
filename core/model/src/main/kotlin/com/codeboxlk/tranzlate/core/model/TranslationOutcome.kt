package com.codeboxlk.tranzlate.core.model

/**
 * Outcome of a translate ask (TEST_A11Y_CONTRACT §1.1, C-9 naming applied:
 * the contract's unified `Engine{AUTO,ML2_MINI,…}` enum is split into
 * [ModeId] (selection) / [Engine] (resolved) per the canonical convention —
 * contract doc alignment is a tracked follow-up docs PR, plan §9).
 */
sealed interface TranslationOutcome {
    /** @property resolvedEngine the engine that actually produced [text] (C-9 resolved form). */
    data class Success(
        val text: String,
        val resolvedEngine: Engine,
    ) : TranslationOutcome

    data class Error(
        val reason: FailureReason,
    ) : TranslationOutcome

    /** Metered path only (C-10) — surfaced as the dismissible limit sheet (C-11). */
    data object LimitReached : TranslationOutcome
}

/** TEST_A11Y_CONTRACT §1.1 — verbatim. */
enum class FailureReason {
    NETWORK,
    ENGINE,
    UNSUPPORTED_PAIR,
    EMPTY_INPUT,
}
