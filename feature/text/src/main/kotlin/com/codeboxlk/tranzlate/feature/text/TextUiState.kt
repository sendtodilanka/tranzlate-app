package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.ModeId

/** Which composer chip opened the language picker sheet. */
enum class LanguagePickerTarget {
    SOURCE,
    TARGET,
}

/**
 * One fired translate ask — kept on every non-Idle state so the Result screen
 * can render the source block (and Retry can replay the EXACT request) without
 * re-reading prefs that may have changed since the tap.
 */
data class TranslateRequest(
    val text: String,
    val sourceLang: String,
    val targetLang: String,
    val mode: ModeId,
)

/**
 * Text-vertical state machine (TEST_A11Y_CONTRACT §1.8 as amended for issue #11):
 * C-2 removed the debounce states (no Typing/Ready — typing is plain input
 * state), and LimitSheet is deferred to the brains phase (unreachable while the
 * only mode is AUTO — C-10).
 *
 * ```
 * Idle ──tapTranslate(nonblank)──▶ Translating ──Success──▶ Result
 * Translating ──Error(reason)──▶ Error ──tapRetry──▶ Translating
 * any ──clear──▶ Idle
 * ```
 */
sealed interface TextUiState {
    data object Idle : TextUiState

    data class Translating(
        val request: TranslateRequest,
    ) : TextUiState

    /** @property transliteration engines don't provide one yet — null hides the line (UI_SPEC §2.4). */
    data class Result(
        val request: TranslateRequest,
        val translatedText: String,
        val transliteration: String?,
        val engine: Engine,
    ) : TextUiState

    /** @property cause deepest attempt's cause; null = non-engine failure (empty input) → generic copy. */
    data class Error(
        val request: TranslateRequest,
        val cause: AttemptCause?,
    ) : TextUiState

    /**
     * The metered gate said no (issue #53 A3): quota exhausted, or — with
     * [notEntitled] — access denial. Distinct from [Error]: nothing failed,
     * so it renders as guidance, not an error card. C-11 sheet lands with paywall.
     */
    data class Limit(
        val request: TranslateRequest,
        val notEntitled: Boolean = false,
    ) : TextUiState
}
