package com.codeboxlk.tranzlate.core.model

/**
 * Why one engine's try failed or was skipped in the waterfall
 * (issue #53 A3 · EDGE_CASES §4 outcome causes). `SKIPPED_*` entries mean the
 * waterfall never called the engine — the trace still records WHY, because the
 * owner's error dialog reads exactly that ("GCT: skipped — out of free quota").
 */
enum class AttemptCause {
    MODEL_NOT_DOWNLOADED,
    OFFLINE,
    TIMEOUT,
    UNSUPPORTED_PAIR,
    ENGINE_ERROR,
    SKIPPED_NO_QUOTA,
    SKIPPED_SOURCE_UNKNOWN,
}

/** A `SKIPPED_*` cause is a non-attempt — the waterfall never called the engine. */
val AttemptCause.isSkip: Boolean
    get() =
        when (this) {
            AttemptCause.SKIPPED_NO_QUOTA, AttemptCause.SKIPPED_SOURCE_UNKNOWN -> true

            // exhaustive `when` forces review when a cause is added
            AttemptCause.MODEL_NOT_DOWNLOADED,
            AttemptCause.OFFLINE,
            AttemptCause.TIMEOUT,
            AttemptCause.UNSUPPORTED_PAIR,
            AttemptCause.ENGINE_ERROR,
            -> false
        }

/** One engine's failed or skipped try — the error dialog's raw material (A3). */
data class EngineAttempt(
    val engine: Engine,
    val cause: AttemptCause,
)

/**
 * Outcome of a translate ask (TEST_A11Y_CONTRACT §1.1 rev.2, C-9 naming applied:
 * the contract's unified `Engine{AUTO,ML2_MINI,…}` enum is split into
 * [ModeId] (selection) / [Engine] (resolved) per the canonical convention).
 *
 * rev.2 (issue #53 PR-5): `Error` carries the full waterfall trace instead of a
 * flattened reason — no engine failure is ever masked by a later one. Empty
 * input is typed as its own outcome (it is input validation, not an engine
 * attempt), and NotEntitled ≠ LimitReached (access denial is not quota).
 */
sealed interface TranslationOutcome {
    /**
     * @property resolvedEngine the engine that actually produced [text] (C-9 resolved form).
     * @property fromCache true when the C-8 cache answered and no engine ran -
     *   by construction such a Success charged no quota and asked no ads
     *   (issue #53 / A2; ads-on-cache-hit is an open owner decision, default no).
     * @property detectedSource resolved BCP-47 source when the ask used the
     *   "auto" sentinel and detection ran; null otherwise (engines phase fills it).
     */
    data class Success(
        val text: String,
        val resolvedEngine: Engine,
        val fromCache: Boolean = false,
        val detectedSource: String? = null,
    ) : TranslationOutcome

    /** The waterfall trace — every engine that failed or was skipped, in order. */
    data class Error(
        val attempts: List<EngineAttempt>,
    ) : TranslationOutcome {
        init {
            require(attempts.isNotEmpty()) { "an Error must carry at least one attempt" }
        }

        /**
         * The deepest attempt the waterfall ACTUALLY ran — the last non-skip
         * cause, so a `SKIPPED_*` tail (e.g. a quota-gated GCT that was never
         * called) never masks the real failure the single-line UI must act on
         * (issue #94, debate-ruled). Falls back to the last entry only when
         * every attempt was skipped (AUTO-undetected + kill-switched GOT +
         * no quota).
         */
        val primaryCause: AttemptCause
            get() = attempts.lastOrNull { !it.cause.isSkip }?.cause ?: attempts.last().cause
    }

    /** Blank/whitespace ask — input validation, never an engine attempt (G9). */
    data object EmptyInput : TranslationOutcome

    /** Access denial (Access brain said no) — NOT quota; ≠ [LimitReached]. */
    data object NotEntitled : TranslationOutcome

    /** Metered path only (C-10) — surfaced as the dismissible limit sheet (C-11). */
    data object LimitReached : TranslationOutcome
}
