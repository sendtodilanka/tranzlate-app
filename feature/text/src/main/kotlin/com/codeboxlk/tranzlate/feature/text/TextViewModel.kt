package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/** DECISIONS defaults table (pre-first-emission frame only — DataStore owns the real default). */
private const val FALLBACK_SOURCE_LANG = "en"
private const val FALLBACK_TARGET_LANG = "fr"

/** Defaults table `text_limit` = 500. RemoteConfig-tunable via the brains phase (plan §6 non-goal). */
const val TEXT_CHAR_LIMIT = 500

private const val KEY_INPUT = "text_input"
private const val KEY_STATE = "text_state_kind"
private const val KEY_RESULT_TEXT = "text_result_text"
private const val KEY_ERROR_REASON = "text_error_reason"
private const val KEY_RESULT_ENGINE = "text_result_engine"
private const val KEY_LAST_TEXT = "text_last_request_text"
private const val KEY_LAST_SRC = "text_last_request_src"
private const val KEY_LAST_TGT = "text_last_request_tgt"
private const val KEY_LAST_MODE = "text_last_request_mode"

/** Which face the read side was showing when the process died (issue #48). */
private const val STATE_TRANSLATING = "translating"
private const val STATE_RESULT = "result"
private const val STATE_ERROR = "error"

private const val PREFS_SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * The Text vertical's ONE state holder (APP_STRUCTURE — the screen ASKS the
 * Translation brain via [TranslateTextUseCase]; it never orchestrates engines,
 * metering or ads itself).
 *
 * - C-2 (amended): translation fires ONLY from [onTranslate] — no debounce path
 *   exists in this class at all.
 * - Input, the last fired request AND the last result live in [SavedStateHandle],
 *   so process death restores the composer exactly as the user left it —
 *   without spending another translation to get the text back (issue #48).
 * - Languages are DataStore-backed prefs (defaults en→fr) via
 *   [TranslatePrefsRepository]; [onSwapLanguages] writes both ids atomically.
 */
@HiltViewModel
class TextViewModel
    @Inject
    constructor(
        private val translateText: TranslateTextUseCase,
        private val prefs: TranslatePrefsRepository,
        languageRepository: LanguageRepository,
        private val dispatchers: DispatcherProvider,
        clock: AppClock,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        /** Composer text — SavedStateHandle-backed (process-death safe). */
        val input: StateFlow<String> = savedStateHandle.getStateFlow(KEY_INPUT, "")

        val sourceLang: StateFlow<String> =
            prefs.sourceLang.stateIn(viewModelScope, SharingStarted.Eagerly, FALLBACK_SOURCE_LANG)

        val targetLang: StateFlow<String> =
            prefs.targetLang.stateIn(viewModelScope, SharingStarted.Eagerly, FALLBACK_TARGET_LANG)

        private val textMode: StateFlow<ModeId> =
            prefs.textMode.stateIn(viewModelScope, SharingStarted.Eagerly, ModeId.AUTO)

        /** Picker catalog (LanguageRepository — bundled-minimal fallback until #4-brains seeds Room). */
        val languages: StateFlow<List<Language>> =
            languageRepository
                .languages()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIBE_TIMEOUT_MS), emptyList())

        private val _uiState = MutableStateFlow<TextUiState>(TextUiState.Idle)
        val uiState: StateFlow<TextUiState> = _uiState.asStateFlow()

        /**
         * The ONE way this class changes state, so what is persisted can never
         * drift from what is shown. **Every** non-Idle state is recorded in
         * [SavedStateHandle] and Idle erases the record — that erasure is what
         * makes a fresh composer safe: after [onComposerDismissed] there is
         * nothing left to restore, so a later open cannot resurrect a stale
         * translation (issue #48).
         *
         * Only what cannot be recomputed is stored: the request is already
         * persisted for Retry and `transliteration` is always null. Input is
         * capped at [TEXT_CHAR_LIMIT], so the saved-state Bundle stays small.
         */
        private var state: TextUiState
            get() = _uiState.value
            set(value) {
                _uiState.value = value
                savedStateHandle[KEY_STATE] =
                    when (value) {
                        is TextUiState.Translating -> STATE_TRANSLATING
                        is TextUiState.Result -> STATE_RESULT
                        is TextUiState.Error -> STATE_ERROR
                        TextUiState.Idle -> null
                    }
                savedStateHandle[KEY_RESULT_TEXT] = (value as? TextUiState.Result)?.translatedText
                savedStateHandle[KEY_RESULT_ENGINE] = (value as? TextUiState.Result)?.engine?.name
                savedStateHandle[KEY_ERROR_REASON] = (value as? TextUiState.Error)?.reason?.name
            }

        /**
         * Process death restarts [_uiState] at Idle while the composer's own
         * saveable state correctly restores its READ face — a face with nothing
         * on it (issue #48). **Every** face the read side can show has to come
         * back, not just the successful one: a shimmer and an error card are just
         * as blank when they go missing.
         */
        private fun restoreState() {
            val rebuilt =
                when (savedStateHandle.get<String>(KEY_STATE)) {
                    STATE_RESULT -> restoreResult()?.also { _uiState.value = it } != null

                    STATE_ERROR -> restoreError()?.also { _uiState.value = it } != null

                    // The system interrupted a translation the user asked for and
                    // never received. Resuming it is honest — reporting a failure
                    // that never happened is not — and it costs the same single
                    // call they already asked for, not an extra one.
                    STATE_TRANSLATING -> lastRequest()?.also(::startTranslation) != null

                    else -> return
                }
            // A record we cannot rebuild (an Engine or FailureReason constant
            // renamed by an app update) would otherwise linger in saved state
            // until some later transition happened to overwrite it.
            if (!rebuilt) state = TextUiState.Idle
        }

        /** Computed once per VM from the injectable clock (UI_SPEC §2.1 time-aware greeting). */
        val greeting: GreetingPeriod = greetingPeriodFor(clock.nowMillis(), ZoneId.systemDefault())

        private var translateJob: Job? = null

        // Kept below translateJob on purpose: restoring an interrupted
        // translation assigns it, and initializers run in textual order. Today
        // that is only tidiness — `= null` on a nullable var is elided, verified
        // in the constructor bytecode (no putfield for this field) — but give
        // translateJob a non-null default and an init block above it would wipe
        // the assignment, leaving a live coroutine nothing could cancel.
        init {
            restoreState()
        }

        fun onInputChange(value: String) {
            // NEVER truncate (spec-01 §8/§9: "input not truncated", "no silent
            // truncation"). Over-limit text is kept and the Translate action is
            // blocked with inline guidance instead (EDGE_CASES OVER_CHAR_LIMIT).
            savedStateHandle[KEY_INPUT] = value
        }

        /**
         * C-2 explicit trigger. Returns true when a translation actually started —
         * the caller only navigates to the Result screen on true (a blank ask
         * fires nothing, G9 / contract §1.9).
         */
        fun onTranslate(): Boolean {
            val text = input.value
            if (text.isBlank() || text.length > TEXT_CHAR_LIMIT) return false
            startTranslation(
                TranslateRequest(
                    text = text,
                    sourceLang = sourceLang.value,
                    targetLang = targetLang.value,
                    mode = textMode.value,
                ),
            )
            return true
        }

        /** Contract §1.7 row 13: re-runs the LAST request (exact text + langs + mode). */
        fun onRetry() {
            val request = lastRequest() ?: return
            startTranslation(request)
        }

        // ── Contract behaviour with no affordance in the approved 5a design.
        // [onReverse] (C-7) and [onClearAll] are required by the foundations and
        // stay covered by TextViewModelTest, but the design's read face carries
        // only copy/speak/star — so nothing calls them yet. Kept deliberately:
        // deleting them would drop a documented convention, not dead weight.

        /**
         * C-7 Reverse: move the result text into the composer, swap source↔target
         * and re-translate. Post-condition: input == prior result, languages
         * swapped, new result = the reverse translation.
         */
        fun onReverse() {
            val done = state as? TextUiState.Result ?: return
            val newSource = done.request.targetLang
            val newTarget = done.request.sourceLang
            savedStateHandle[KEY_INPUT] = done.translatedText
            viewModelScope.launch {
                prefs.setLanguagePair(sourceId = newSource, targetId = newTarget)
            }
            startTranslation(
                TranslateRequest(
                    text = done.translatedText,
                    sourceLang = newSource,
                    targetLang = newTarget,
                    mode = textMode.value,
                ),
            )
        }

        /** UI_SPEC §2.2 swap ⇄ — one atomic pair write; always available. */
        fun onSwapLanguages() {
            val newSource = targetLang.value
            val newTarget = sourceLang.value
            viewModelScope.launch {
                prefs.setLanguagePair(sourceId = newSource, targetId = newTarget)
            }
        }

        fun onSelectSourceLanguage(id: String) {
            viewModelScope.launch { prefs.setSourceLang(id) }
        }

        fun onSelectTargetLanguage(id: String) {
            viewModelScope.launch { prefs.setTargetLang(id) }
        }

        /**
         * Leaving 5a for Home discards the draft — text cleared, any in-flight
         * translation cancelled, state back to Idle, so Home never shows text the
         * user already walked away from. Every way out of 5a routes here (the
         * in-screen arrow and the shell's back guard), so the clearing rule lives
         * in exactly one place.
         */
        fun onComposerDismissed() {
            translateJob?.cancel()
            savedStateHandle[KEY_INPUT] = ""
            state = TextUiState.Idle
        }

        /** Top-bar new/clear action: composer emptied, canvas returns, state to Idle. */
        fun onClearAll() {
            translateJob?.cancel()
            savedStateHandle[KEY_INPUT] = ""
            state = TextUiState.Idle
        }

        private fun startTranslation(request: TranslateRequest) {
            persistLastRequest(request)
            translateJob?.cancel()
            // Translating is set SYNCHRONOUSLY so the Result screen opens straight
            // into the shimmer (UI_SPEC §2.5) — never a frame of stale state.
            state = TextUiState.Translating(request)
            translateJob =
                viewModelScope.launch {
                    val outcome =
                        withContext(dispatchers.default) {
                            translateText(request.text, request.sourceLang, request.targetLang, request.mode)
                        }
                    state =
                        when (outcome) {
                            is TranslationOutcome.Success -> {
                                TextUiState.Result(
                                    request = request,
                                    translatedText = outcome.text,
                                    transliteration = null,
                                    engine = outcome.resolvedEngine,
                                )
                            }

                            is TranslationOutcome.Error -> {
                                TextUiState.Error(request, outcome.reason)
                            }

                            // Unreachable while the only selectable mode is AUTO
                            // (C-10 — AUTO never meters). The dismissible LimitSheet
                            // (C-11) ships with the brains phase; until then surface
                            // the guided error path rather than a dead end.
                            TranslationOutcome.LimitReached -> {
                                TextUiState.Error(request, FailureReason.ENGINE)
                            }
                        }
                }
        }

        private fun persistLastRequest(request: TranslateRequest) {
            savedStateHandle[KEY_LAST_TEXT] = request.text
            savedStateHandle[KEY_LAST_SRC] = request.sourceLang
            savedStateHandle[KEY_LAST_TGT] = request.targetLang
            savedStateHandle[KEY_LAST_MODE] = request.mode.name
        }

        /** Rebuilds the last [TextUiState.Result] after process death, or null. */
        private fun restoreResult(): TextUiState.Result? {
            val text = savedStateHandle.get<String>(KEY_RESULT_TEXT) ?: return null
            val engine =
                savedStateHandle.get<String>(KEY_RESULT_ENGINE)?.let { name ->
                    Engine.entries.firstOrNull { it.name == name }
                } ?: return null
            val request = lastRequest() ?: return null
            return TextUiState.Result(
                request = request,
                translatedText = text,
                transliteration = null,
                engine = engine,
            )
        }

        /** Rebuilds the last [TextUiState.Error] after process death, or null. */
        private fun restoreError(): TextUiState.Error? {
            val reason =
                savedStateHandle.get<String>(KEY_ERROR_REASON)?.let { name ->
                    FailureReason.entries.firstOrNull { it.name == name }
                } ?: return null
            val request = lastRequest() ?: return null
            return TextUiState.Error(request, reason)
        }

        private fun lastRequest(): TranslateRequest? {
            val text = savedStateHandle.get<String>(KEY_LAST_TEXT) ?: return null
            val src = savedStateHandle.get<String>(KEY_LAST_SRC) ?: return null
            val tgt = savedStateHandle.get<String>(KEY_LAST_TGT) ?: return null
            val mode =
                savedStateHandle.get<String>(KEY_LAST_MODE)?.let { name ->
                    ModeId.entries.firstOrNull { it.name == name }
                } ?: ModeId.AUTO
            return TranslateRequest(text, src, tgt, mode)
        }
    }
