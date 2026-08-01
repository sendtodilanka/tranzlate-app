package com.codeboxlk.tranzlate.feature.text

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId

/**
 * Which composer chip opened the language picker — literally the picker's
 * [LanguageRole] since issue #130 rev.3 ruled ONE type across the picker
 * param, the usage store and the per-role recents. A thin alias rather than
 * an edit of the composer call sites, which PR-6 git-moves LOGIC-FROZEN; the
 * alias dies with that move (recorded in `docs/plan/issue-130-language-rev3.md`).
 */
typealias LanguagePickerTarget = LanguageRole

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
        /** RESOLVED source (never "auto") — favourite lookups + auto-detect rows (issue #68). */
        val resolvedSourceLang: String? = null,
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
