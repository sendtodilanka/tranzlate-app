package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
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
private const val KEY_LAST_TEXT = "text_last_request_text"
private const val KEY_LAST_SRC = "text_last_request_src"
private const val KEY_LAST_TGT = "text_last_request_tgt"
private const val KEY_LAST_MODE = "text_last_request_mode"

private const val PREFS_SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * The Text vertical's ONE state holder (APP_STRUCTURE — the screen ASKS the
 * Translation brain via [TranslateTextUseCase]; it never orchestrates engines,
 * metering or ads itself).
 *
 * - C-2 (amended): translation fires ONLY from [onTranslate] — no debounce path
 *   exists in this class at all.
 * - Input + the last fired request live in [SavedStateHandle] so process death
 *   restores the composer and lets the Result screen replay its request
 *   ([restoreResultIfNeeded]).
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

        /** Computed once per VM from the injectable clock (UI_SPEC §2.1 time-aware greeting). */
        val greeting: GreetingPeriod = greetingPeriodFor(clock.nowMillis(), ZoneId.systemDefault())

        private var translateJob: Job? = null

        fun onInputChange(value: String) {
            // Defaults-table hard cap: over-limit input is unreachable, so the
            // OVER_CHAR_LIMIT blocked state can't occur (EDGE_CASES §4 row 6).
            savedStateHandle[KEY_INPUT] = value.take(TEXT_CHAR_LIMIT)
        }

        /**
         * C-2 explicit trigger. Returns true when a translation actually started —
         * the caller only navigates to the Result screen on true (a blank ask
         * fires nothing, G9 / contract §1.9).
         */
        fun onTranslate(): Boolean {
            val text = input.value
            if (text.isBlank()) return false
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

        /**
         * Process-death recovery for the Result screen (no-dead-end): the nav
         * back stack restores the Result entry but [_uiState] restarts at Idle —
         * replay the persisted last request instead of showing a blank screen.
         */
        fun restoreResultIfNeeded() {
            if (_uiState.value is TextUiState.Idle) onRetry()
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

        /** Top-bar new/clear action: composer emptied, canvas returns, state to Idle. */
        fun onClearAll() {
            translateJob?.cancel()
            savedStateHandle[KEY_INPUT] = ""
            _uiState.value = TextUiState.Idle
        }

        private fun startTranslation(request: TranslateRequest) {
            persistLastRequest(request)
            translateJob?.cancel()
            // Translating is set SYNCHRONOUSLY so the Result screen opens straight
            // into the shimmer (UI_SPEC §2.5) — never a frame of stale state.
            _uiState.value = TextUiState.Translating(request)
            translateJob =
                viewModelScope.launch {
                    val outcome =
                        withContext(dispatchers.default) {
                            translateText(request.text, request.sourceLang, request.targetLang, request.mode)
                        }
                    _uiState.value =
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
