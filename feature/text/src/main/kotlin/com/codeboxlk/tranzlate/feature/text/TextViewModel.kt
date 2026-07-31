package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/** DECISIONS defaults table (pre-first-emission frame only — DataStore owns the real default). */
private const val FALLBACK_SOURCE_LANG = "en"
private const val FALLBACK_TARGET_LANG = "fr"

/** Translator contract §1.1 sentinel — a RESOLVED id is never this. */
private const val AUTO_LANG = "auto"

/** Defaults table `text_limit` = 500. RemoteConfig-tunable via the brains phase (plan §6 non-goal). */
const val TEXT_CHAR_LIMIT = 500

private const val KEY_INPUT = "text_input"
private const val KEY_STATE = "text_state_kind"
private const val KEY_RESULT_TEXT = "text_result_text"
private const val KEY_ERROR_REASON = "text_error_reason"
private const val KEY_LIMIT_NOT_ENTITLED = "text_limit_not_entitled"
private const val KEY_RESULT_ENGINE = "text_result_engine"
private const val KEY_RESULT_SRC = "text_result_resolved_src"
private const val KEY_LAST_TEXT = "text_last_request_text"
private const val KEY_LAST_SRC = "text_last_request_src"
private const val KEY_LAST_TGT = "text_last_request_tgt"
private const val KEY_LAST_MODE = "text_last_request_mode"

/** Which face the read side was showing when the process died (issue #48). */
private const val STATE_TRANSLATING = "translating"
private const val STATE_RESULT = "result"
private const val STATE_ERROR = "error"
private const val STATE_LIMIT = "limit"

private const val PREFS_SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * Minimum time the translating shimmer stays on screen before a NON-success
 * outcome replaces it (issue #103, debate-ruled). 500ms sits in the
 * practitioner-tested 300-600ms minimum-display band and under Nielsen's 1s
 * flow-of-thought limit; successes are never delayed by it.
 */
private const val BUSY_FLOOR_MS = 500L

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
    @Suppress("LongParameterList") // the Text vertical's ONE state holder aggregates the brains' ask-seams
    constructor(
        private val translateText: TranslateTextUseCase,
        private val prefs: TranslatePrefsRepository,
        private val translationRepository: TranslationRepository,
        languageRepository: LanguageRepository,
        usagePolicy: UsagePolicy,
        featureAccess: FeatureAccess,
        config: RemoteConfigSource,
        private val dispatchers: DispatcherProvider,
        private val clock: AppClock,
        private val speaker: ResultSpeaker,
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

        /**
         * How many catalog languages can actually be held offline — Home's download
         * row prints THIS instead of the design mock's invented "133 available".
         *
         * Deliberately derived from the catalog, not from
         * [com.codeboxlk.tranzlate.domain.translate.OfflineModelManager]: that seam
         * reaches ML Kit's `RemoteModelManager`, and Home is the cold-start screen.
         * The count of already-DOWNLOADED models needs that seam, which is why the
         * offline card no longer claims one at all (plan launch-blockers §2).
         *
         * 0 while the first catalog emission is in flight — the plural resource
         * reads correctly for it, so there is no placeholder frame to hide.
         */
        val offlineLanguageCount: StateFlow<Int> =
            languages
                .map { catalog -> catalog.count(Language::offlineAvailable) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIBE_TIMEOUT_MS), 0)

        private val _uiState = MutableStateFlow<TextUiState>(TextUiState.Idle)
        val uiState: StateFlow<TextUiState> = _uiState.asStateFlow()

        // Declared BEFORE the `state` setter's first use (restoreState runs in
        // init): a process-death restore crashed on the later declaration
        // (caught by the resume/unreadable-record tests before it shipped).
        private val _resultFavourite = MutableStateFlow(false)

        /** Whether the CURRENT result's row is favourited — drives the star icon. */
        val resultFavourite: StateFlow<Boolean> = _resultFavourite.asStateFlow()

        /** FREE pool cap for the "{left}/{cap}" meter (BUSINESS_MODEL §5 goal-gradient). */
        val aiCap: Int = config.limitFreeAi()

        /** Live AI-quality quota meter — read-only ask, the Usage brain owns the count. */
        val aiRemaining: StateFlow<Int> =
            usagePolicy.remaining.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(PREFS_SUBSCRIBE_TIMEOUT_MS),
                aiCap,
            )

        /** PRO hides the meter (unlimited). Loading counts as not-PRO for DISPLAY only. */
        val isPro: StateFlow<Boolean> =
            featureAccess.entitlement
                .map { it is Entitlement.Paid }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PREFS_SUBSCRIBE_TIMEOUT_MS), false)

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
                        is TextUiState.Limit -> STATE_LIMIT
                        TextUiState.Idle -> null
                    }
                savedStateHandle[KEY_RESULT_TEXT] = (value as? TextUiState.Result)?.translatedText
                savedStateHandle[KEY_RESULT_ENGINE] = (value as? TextUiState.Result)?.engine?.name
                savedStateHandle[KEY_RESULT_SRC] = (value as? TextUiState.Result)?.resolvedSourceLang
                refreshResultFavourite(value as? TextUiState.Result)
                savedStateHandle[KEY_ERROR_REASON] = (value as? TextUiState.Error)?.cause?.name
                savedStateHandle[KEY_LIMIT_NOT_ENTITLED] = (value as? TextUiState.Limit)?.notEntitled
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
                    STATE_RESULT -> restoreResult()?.also { state = it } != null

                    STATE_ERROR -> restoreError()?.also { state = it } != null

                    STATE_LIMIT -> restoreLimit()?.also { state = it } != null

                    // The system interrupted a translation the user asked for and
                    // never received. Resuming it is honest — reporting a failure
                    // that never happened is not — and it costs the same single
                    // call they already asked for, not an extra one.
                    STATE_TRANSLATING -> lastRequest()?.also(::startTranslation) != null

                    else -> return
                }
            // A record we cannot rebuild (an Engine or AttemptCause constant
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
         * Whether swap can act right now (issue #70, lens OPEN-1): a concrete
         * source always can; Detect can once a result carries its detected
         * language — the UI's enabled-state reads THIS, so the resolve path is
         * actually reachable instead of sitting behind a disabled button.
         */
        val swapAvailable: StateFlow<Boolean> =
            combine(sourceLang, uiState) { src, st ->
                src != AUTO_LANG || (st as? TextUiState.Result)?.resolvedSourceLang != null
            }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        /**
         * UI_SPEC §2.2 swap ⇄ — one atomic pair write. With source = Detect the
         * target must NEVER become "auto" (issue #70): the swap resolves through
         * the shown result's detected language. The false branch is a race
         * safety-net (result cleared between render and tap) — the UI guides.
         */
        fun onSwapLanguages(): Boolean {
            val currentSource = sourceLang.value
            val newTarget =
                if (currentSource == AUTO_LANG) {
                    (state as? TextUiState.Result)?.resolvedSourceLang ?: return false
                } else {
                    currentSource
                }
            val newSource = targetLang.value
            viewModelScope.launch {
                prefs.setLanguagePair(sourceId = newSource, targetId = newTarget)
            }
            return true
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
            speaker.stop()
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
                    // The floor runs ALONGSIDE the work (not measured after it):
                    // coroutine time, so it is real elapsed time in production and
                    // virtual time under test — no clock skew either way.
                    val busyFloor = launch { delay(BUSY_FLOOR_MS) }
                    val outcome =
                        // io, not default: the engine call becomes network/SDK IO in the
                        // brains phase (A5) — Default is the CPU-sized pool.
                        withContext(dispatchers.io) {
                            translateText(request.text, request.sourceLang, request.targetLang, request.mode)
                        }
                    // Issue #103 (owner + debate ruling): a failure that returns in
                    // milliseconds used to flash the shimmer and slam an error over
                    // it. The shimmer gets a MINIMUM visible time — but only when the
                    // outcome is NOT a success: a result the user could already have
                    // (cache hit, offline MLKit) is never held back, and the floor
                    // stays under Nielsen's 1s flow-of-thought limit. Inside
                    // translateJob, so clear/retry cancels the floor with the work.
                    if (outcome is TranslationOutcome.Success) busyFloor.cancel() else busyFloor.join()
                    state =
                        when (outcome) {
                            is TranslationOutcome.Success -> {
                                TextUiState.Result(
                                    request = request,
                                    translatedText = outcome.text,
                                    transliteration = null,
                                    engine = outcome.resolvedEngine,
                                    resolvedSourceLang =
                                        if (request.sourceLang == AUTO_LANG) {
                                            outcome.detectedSource
                                        } else {
                                            request.sourceLang
                                        },
                                )
                            }

                            is TranslationOutcome.Error -> {
                                TextUiState.Error(request, outcome.primaryCause)
                            }

                            TranslationOutcome.EmptyInput -> {
                                TextUiState.Error(request, cause = null)
                            }

                            // A3: the old `LimitReached → generic ENGINE error`
                            // masking dies here — the gate's answers get their own
                            // face (and NotEntitled ≠ LimitReached). Both are
                            // unreachable while the only selectable mode is AUTO
                            // (C-10 — AUTO never meters); the C-11 sheet lands
                            // with the paywall.
                            TranslationOutcome.LimitReached -> {
                                TextUiState.Limit(request)
                            }

                            TranslationOutcome.NotEntitled -> {
                                TextUiState.Limit(request, notEntitled = true)
                            }
                        }
                }
        }

        // ---- issue #68: star-to-save + tap-to-reopen -------------------------

        private fun refreshResultFavourite(result: TextUiState.Result?) {
            val src = result?.resolvedSourceLang
            if (result == null || src == null) {
                _resultFavourite.value = false
                return
            }
            viewModelScope.launch {
                _resultFavourite.value =
                    translationRepository
                        .cached(result.request.text, src, result.request.targetLang, result.engine)
                        ?.favourite ?: false
            }
        }

        // ---- issue #84: result actions — speak + reverse ---------------------

        /** True while TTS reads the result — the speak button's play ⇄ stop state. */
        val speaking: StateFlow<Boolean> get() = speaker.speaking

        /**
         * Toggle: reading → stop; idle → read the RESULT in the target language.
         * False = engine/language unavailable (UI guides — no dead end).
         */
        fun onSpeak(): Boolean {
            val result = state as? TextUiState.Result ?: return false
            if (speaker.speaking.value) {
                speaker.stop()
                return true
            }
            return speaker.speak(result.translatedText, result.request.targetLang)
        }

        /**
         * C-7 reverse (owner, FT behaviour observed): the RESULT becomes the
         * input, the pair swaps, and it re-translates. Auto-detect reverses
         * through the RESOLVED source; unresolved → false (UI guides).
         */
        fun onReverse(): Boolean {
            val result = state as? TextUiState.Result ?: return false
            val newTarget =
                result.resolvedSourceLang
                    ?: result.request.sourceLang.takeIf { it != AUTO_LANG }
                    ?: return false
            val newSource = result.request.targetLang
            speaker.stop() // a reverse restarts the flow — never keep old audio
            savedStateHandle[KEY_INPUT] = result.translatedText
            viewModelScope.launch { prefs.setLanguagePair(newSource, newTarget) }
            val request =
                TranslateRequest(
                    text = result.translatedText,
                    sourceLang = newSource,
                    targetLang = newTarget,
                    mode = ModeId.AUTO,
                )
            persistLastRequest(request)
            startTranslation(request)
            return true
        }

        /**
         * Star on the composer result: flips the history row's `favourite`; a
         * row the history write skipped (rare undetected-auto) is SAVED as a
         * favourite directly — the star always does something (no dead end).
         */
        fun onToggleFavourite() {
            val result = state as? TextUiState.Result ?: return
            val src = result.resolvedSourceLang ?: return
            viewModelScope.launch {
                val row =
                    translationRepository.cached(
                        result.request.text,
                        src,
                        result.request.targetLang,
                        result.engine,
                    )
                if (row == null) {
                    val id =
                        translationRepository.save(
                            Translation(
                                sourceLang = src,
                                sourceText = result.request.text,
                                targetLang = result.request.targetLang,
                                targetText = result.translatedText,
                                engine = result.engine,
                                favourite = true,
                                createdAt = clock.nowMillis(),
                            ),
                        )
                    _resultFavourite.value = id > 0
                } else {
                    translationRepository.setFavourite(row.id, !row.favourite)
                    _resultFavourite.value = !row.favourite
                }
            }
        }

        /**
         * A History row reopens IN the composer (issue #68): input + pair +
         * the stored answer — Retry replays through the C-8 cache instantly.
         */
        fun onHistoryPick(translation: Translation) {
            translateJob?.cancel()
            savedStateHandle[KEY_INPUT] = translation.sourceText
            viewModelScope.launch {
                prefs.setLanguagePair(translation.sourceLang, translation.targetLang)
            }
            val request =
                TranslateRequest(
                    text = translation.sourceText,
                    sourceLang = translation.sourceLang,
                    targetLang = translation.targetLang,
                    mode = ModeId.AUTO,
                )
            persistLastRequest(request)
            state =
                TextUiState.Result(
                    request = request,
                    translatedText = translation.targetText,
                    transliteration = null,
                    engine = translation.engine,
                    resolvedSourceLang = translation.sourceLang,
                )
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
                resolvedSourceLang = savedStateHandle.get<String>(KEY_RESULT_SRC),
            )
        }

        /**
         * Rebuilds the last [TextUiState.Error] after process death, or null.
         * A cause-less error face (empty input) intentionally doesn't survive
         * death — the user retypes anyway.
         */
        private fun restoreError(): TextUiState.Error? {
            val cause =
                savedStateHandle.get<String>(KEY_ERROR_REASON)?.let { name ->
                    AttemptCause.entries.firstOrNull { it.name == name }
                } ?: return null
            val request = lastRequest() ?: return null
            return TextUiState.Error(request, cause)
        }

        /** Rebuilds the last [TextUiState.Limit] after process death, or null. */
        private fun restoreLimit(): TextUiState.Limit? {
            val notEntitled = savedStateHandle.get<Boolean>(KEY_LIMIT_NOT_ENTITLED) ?: return null
            val request = lastRequest() ?: return null
            return TextUiState.Limit(request, notEntitled)
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
