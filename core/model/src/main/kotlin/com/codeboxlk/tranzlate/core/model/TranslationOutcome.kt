package com.codeboxlk.tranzlate.core.model

/**
 * Outcome of a translate ask (TEST_A11Y_CONTRACT §1.1, C-9 naming applied:
 * the contract's unified `Engine{AUTO,ML2_MINI,…}` enum is split into
 * [ModeId] (selection) / [Engine] (resolved) per the canonical convention —
 * contract doc alignment is a tracked follow-up docs PR, plan §9).
 */
sealed interface TranslationOutcome {
    /**
     * @property resolvedEngine the engine that actually produced [text] (C-9 resolved form).
     * @property fromCache true when the C-8 cache answered and no engine ran -
     *   by construction such a Success charged no quota and asked no ads
     *   (issue #53 / A2; ads-on-cache-hit is an open owner decision, default no).
     */
    data class Success(
        val text: String,
        val resolvedEngine: Engine,
        val fromCache: Boolean = false,
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
