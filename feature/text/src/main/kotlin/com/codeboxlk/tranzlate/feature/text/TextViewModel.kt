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
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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

// The last valid (both real, distinct) selection pair, kept so sheet 19m's Swap
// can restore what a duplicate selection displaced — even across process death
// (#130 PR-20). See [TextViewModel.onSwapLanguages]'s degenerate branch.
private const val KEY_VALID_SRC = "text_valid_pair_src"
private const val KEY_VALID_TGT = "text_valid_pair_tgt"

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
 * What the star tap asked the app to do (issue #195).
 *
 * The toggle runs in both directions, so the failure message has to as well:
 * telling a user who was un-saving that we "couldn't save" is a small lie about
 * the one thing they just did.
 */
enum class StarIntent {
    SAVE,
    REMOVE,
}

/**
 * A star write that did not land, held until the composer has shown it (#195).
 *
 * @property id makes two consecutive identical failures DIFFERENT values.
 *   Without it, "save failed → Retry → failed the same way" produces an equal
 *   [StarFailure], `StateFlow` conflates the repeat away, and the second failure
 *   is never announced — a silent failure introduced by the fix for silence.
 * @property result the translation the write was asked for. Retry checks it is
 *   still the one on screen: the snackbar outlives a translation (the user can
 *   retype and translate again while it is up), and starring a DIFFERENT result
 *   than the message named is a write nobody asked for.
 */
data class StarFailure(
    val id: Long,
    val intent: StarIntent,
    val result: TextUiState.Result,
)

/**
 * How a degenerate selection (source == target == [duplicated]) resolves when 19m
 * asks to Swap, or is dismissed — **ALWAYS to a non-degenerate pair**, so 19m is
 * never a dead end.
 *
 * #299 co-verify proved the old `?: return false` WAS a dead end: process death
 * drops the in-memory-written last-valid pair (the init collector had not re-run)
 * while DataStore keeps the degenerate selection, so on relaunch Swap and the
 * scrim/back — both of which call [TextViewModel.onSwapLanguages] — did nothing,
 * and only "Pick another" escaped. This resolution never fails:
 *
 * - With the last valid pair known, it is that pair **swapped**: the duplicated
 *   language lands on the side the user picked it for (the drawn pre-commit swap).
 * - Without it, the duplicated language is kept as the **target** (where "already
 *   the source" put it) and paired with a fallback **source** that differs from it
 *   — a valid pair to translate with now, or to replace via "Pick another".
 *
 * Pure so the process-death case is reproducible without fighting the collector's
 * timing (`DuplicateSelectionTest`). `@return` (source, target), source != target.
 */
internal fun degenerateResolution(
    duplicated: String,
    lastValidSource: String?,
    lastValidTarget: String?,
): Pair<String, String> =
    if (lastValidSource != null && lastValidTarget != null && lastValidSource != lastValidTarget) {
        lastValidTarget to lastValidSource
    } else {
        val fallbackSource = if (duplicated != FALLBACK_SOURCE_LANG) FALLBACK_SOURCE_LANG else FALLBACK_TARGET_LANG
        fallbackSource to duplicated
    }

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

        /**
         * Whether the shell that hoists this ViewModel is between `ON_START` and
         * `ON_STOP` (issue #159 co-verify, block 1).
         *
         * `onCleared()` alone was not the release the issue asked for: this VM is
         * hoisted OUTSIDE the NavDisplay entries, so it lives in the Activity's
         * ViewModelStore and `onCleared()` runs only when the Activity finishes.
         * Backgrounding a result left the state at `Result`, so the engine was
         * still held — re-measuring HOME with a result on screen reproduced the
         * issue's own "before" row. Only the BACK path was ever fixed.
         *
         * False until the shell says otherwise, and declared ABOVE the `state`
         * setter that reads it: `restoreState()` runs from `init{}`, and a
         * property initializer below that block would still be the JVM default
         * when the setter fires (the same order trap [translateJob] carries).
         */
        private var hostStarted = false

        // Declared BEFORE the `state` setter's first use (restoreState runs in
        // init): a process-death restore crashed on the later declaration
        // (caught by the resume/unreadable-record tests before it shipped).
        private val _resultFavourite = MutableStateFlow(false)

        /** Whether the CURRENT result's row is favourited — drives the star icon. */
        val resultFavourite: StateFlow<Boolean> = _resultFavourite.asStateFlow()

        // Declared HERE, above the `state` setter, for the same reason
        // [_resultFavourite] is: `restoreState()` runs from `init{}` and reaches
        // the setter, and a property initializer below that block would still be
        // the JVM default when the setter fires.
        private val starFailureIds = AtomicLong()
        private val _starFailure = MutableStateFlow<StarFailure?>(null)

        /**
         * The star write that did not land, or null (issue #195).
         *
         * The three calls behind the star used to run in a `viewModelScope`
         * coroutine with no try and no catch. `viewModelScope` installs no
         * `CoroutineExceptionHandler`, so a `SQLiteException` — disk full,
         * database locked, `SQLiteDatabaseCorruptException` — went straight to
         * the process handler: the user taps the star on the translation they
         * are reading and the app disappears. EDGE_CASES §94 forbids exactly
         * that ("no crash-instead-of-message") and EDGE_CASES:114 already ruled
         * on this control by name — `Save / star … offline write fails →
         * [Retry]` — in the composer's own result-actions table.
         *
         * A retained StateFlow, NOT a one-shot event flow, and here the reason
         * is stronger than it was for History (#190): this ViewModel is hoisted
         * OUTSIDE the NavDisplay entries, so it lives in the Activity's
         * ViewModelStore and outlives the composer's composition every time the
         * user opens the language picker or goes Home. A failure emitted into
         * that gap has no subscriber and is dropped forever. Held state is shown
         * when the composer is next composed; [onStarFailureShown] retires it.
         */
        val starFailure: StateFlow<StarFailure?> = _starFailure.asStateFlow()

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
                syncSpeaker()
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
                // A held star failure names ONE result (issue #195). The composer
                // can leave that result behind while the failure is still waiting
                // to be shown — Back to Home clears to Idle, the top-bar action
                // clears to Idle, a new Translate replaces it — and the message
                // would then arrive about a translation the user can no longer
                // see, carrying a Retry with nothing to act on. Retired with the
                // result it belongs to, for the same reason a cancelled write
                // says nothing: there is nobody left to tell.
                if (_starFailure.value?.result != value) _starFailure.value = null
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
            // Remember the last VALID selection pair (both real, distinct) so sheet
            // 19m's Swap can restore what a duplicate displaced (#130 PR-20). The
            // picker writes the selection straight to prefs without calling this VM,
            // so the only way to know the pre-duplicate pair is to watch the flows.
            // A pair containing Detect, or an already-degenerate one, is not a pair a
            // Swap could sensibly restore, so it is not recorded. viewModelScope, so
            // it dies with the VM — not one of the sanctioned screen-outlivers.
            viewModelScope.launch {
                combine(sourceLang, targetLang) { source, target -> source to target }
                    .collect { (source, target) ->
                        if (source != AUTO_LANG && target != AUTO_LANG && source != target) {
                            savedStateHandle[KEY_VALID_SRC] = source
                            savedStateHandle[KEY_VALID_TGT] = target
                        }
                    }
            }
        }

        /**
         * The ONE place that decides whether an engine is worth holding
         * (issue #149 / #159). Two inputs, both necessary:
         *
         * - a face that can speak — `Translating` prepares rather than `Result`,
         *   so the ~500ms bind runs alongside the translation instead of in
         *   front of the audio;
         * - a host that is on screen — a bound engine keeps another process at
         *   visible-app importance whether or not ours is visible, so holding
         *   one for a backgrounded app is the leak this issue opened for.
         */
        private fun syncSpeaker() {
            val canSpeak = state is TextUiState.Translating || state is TextUiState.Result
            if (hostStarted && canSpeak) speaker.prepare() else speaker.release()
        }

        /** Shell `ON_START`: re-bind for a result the user has come back to. */
        fun onHostStarted() {
            hostStarted = true
            syncSpeaker()
        }

        /**
         * Shell `ON_STOP`: nothing on this screen can be listened to any more, so
         * the engine goes back — the app is not a background audio player, and a
         * held engine outlives our visibility by design.
         */
        fun onHostStopped() {
            hostStarted = false
            // Kills any tap still waiting on a bind, so returning to the app
            // cannot be greeted by a message about a request already abandoned.
            speakJob?.cancel()
            syncSpeaker()
        }

        /**
         * The last resort (issue #149). [onHostStopped] covers backgrounding, but
         * a host cleared without a stop — and any wiring mistake in the shell —
         * still must not leak: a text-to-speech engine held past its consumer
         * keeps another process pinned at visible-app importance until this
         * process dies. This is also where Google's own guidance puts the release
         * ("call this method in the onDestroy() method of an Activity").
         */
        override fun onCleared() {
            super.onCleared()
            speaker.release()
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
         * Sheet 19m's trigger (#130 PR-20): the duplicated language id when the
         * current selection is **degenerate** — source and target the same real
         * language — else null.
         *
         * The picker can reach this state because since the #130 rev.3 decouple
         * (#123.2) it commits a choice straight to [prefs] and does not itself
         * refuse the opposite side's language; this app cannot add that refusal in
         * the picker without colliding with the PR that owns it, so the guard is
         * here, and the app shell turns a non-null value into the stateless 19m
         * sheet (`:feature:language`) so `:feature:text` gains no dependency on it.
         *
         * The "auto" sentinel is never a real target, so a Detect source can never
         * equal the target — 19m cannot fire with Detect, which is why [onSwapLanguages]'s
         * degenerate branch does not have to think about it.
         */
        val duplicateSelection: StateFlow<String?> =
            combine(sourceLang, targetLang) { source, target ->
                if (source != AUTO_LANG && source == target) source else null
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /**
         * UI_SPEC §2.2 swap ⇄ — one atomic pair write. With source = Detect the
         * target must NEVER become "auto" (issue #70): the swap resolves through
         * the shown result's detected language. The false branch is a race
         * safety-net (result cleared between render and tap) — the UI guides.
         */
        fun onSwapLanguages(): Boolean {
            val currentSource = sourceLang.value
            val currentTarget = targetLang.value
            // Sheet 19m's Swap (#130 PR-20). The selection is degenerate — the user
            // picked the language already on the other side — so there is nothing to
            // swap in the CURRENT pair (swapping X with X is a no-op, the dead
            // affordance EDGE_CASES §7 forbids). [degenerateResolution] ALWAYS
            // produces a valid pair, so this branch cannot dead-end even when the
            // remembered pair is gone (process death, #299 co-verify); Swap and the
            // scrim/back both come through here and both always resolve. The Detect
            // and plain-pair paths below are untouched, and their existing tests stay
            // green because they never reach this branch.
            if (currentSource != AUTO_LANG && currentSource == currentTarget) {
                val (newSource, newTarget) =
                    degenerateResolution(
                        duplicated = currentSource,
                        lastValidSource = savedStateHandle.get<String>(KEY_VALID_SRC),
                        lastValidTarget = savedStateHandle.get<String>(KEY_VALID_TGT),
                    )
                viewModelScope.launch {
                    prefs.setLanguagePair(sourceId = newSource, targetId = newTarget)
                }
                return true
            }
            val newTarget =
                if (currentSource == AUTO_LANG) {
                    (state as? TextUiState.Result)?.resolvedSourceLang ?: return false
                } else {
                    currentSource
                }
            val newSource = currentTarget
            viewModelScope.launch {
                prefs.setLanguagePair(sourceId = newSource, targetId = newTarget)
            }
            return true
        }

        // onSelectSourceLanguage/onSelectTargetLanguage lived here until the
        // #130 rev.3 decouple (#123.2): the picker now writes its own choice
        // through LanguagePickerViewModel → TranslatePrefsRepository — the same
        // DataStore keys [sourceLang]/[targetLang] read — so the chips stay
        // coherent without this class lending the picker a write path.

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
                                    // Canonicalised for the SAME reason the use
                                    // case does it (issue #151), and it has to
                                    // happen on both sides or neither: the star
                                    // below looks the history row up by exactly
                                    // the tuple the use case wrote. Detect `iw`,
                                    // canonicalise only the write, and the lookup
                                    // misses its own row — the star reads
                                    // unsaved and then saves a second copy.
                                    resolvedSourceLang =
                                        if (request.sourceLang == AUTO_LANG) {
                                            outcome.detectedSource?.let(LanguageTagResolver::canonicalOrSelf)
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
                val stored = readFavourite(result, src)
                // Answers ONE result, so it may only paint that result (#195
                // co-verify). A slow lookup outlives the translation it was made
                // for — the user re-opens a saved row, the disk takes its time,
                // they translate something else — and publishing then would put
                // the OLD translation's bookmark on the new one, which is the
                // same lie the write half was caught telling.
                if (state == result) _resultFavourite.value = stored
            }
        }

        /**
         * The star's READ half, guarded for the same reason as its write half
         * (issue #195). This runs from the `state` setter on EVERY result, so an
         * unguarded lookup ends the process the moment a translation lands —
         * before the user can reach the star at all. Fixing only the tap would
         * have left the issue's own harm alive on the path nobody checked.
         *
         * Failure shows the star UNFILLED and says nothing. Nothing was asked
         * for, so there is nothing to report and nothing to retry; and unfilled
         * is the same face "no row found" already produces, where leaving the
         * previous result's value would paint THIS result with the last one's
         * bookmark. It is not a dead end either: the star stays live, and the
         * tap that follows goes through [onToggleFavourite], which does speak.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun readFavourite(
            result: TextUiState.Result,
            src: String,
        ): Boolean =
            try {
                translationRepository
                    .cached(result.request.text, src, result.request.targetLang, result.engine)
                    ?.favourite ?: false
            } catch (rethrown: CancellationException) {
                throw rethrown // never break structured cancellation
            } catch (unused: Throwable) {
                false
            }

        // ---- issue #84: result actions — speak + reverse ---------------------

        /** True while TTS reads the result — the speak button's play ⇄ stop state. */
        val speaking: StateFlow<Boolean> get() = speaker.speaking

        private var speakJob: Job? = null

        private val _speakNotice = MutableStateFlow<SpeakOutcome?>(null)

        /**
         * What the speak button owes the user, or null (issue #159 co-verify,
         * block 2). Only ever a FAILURE the user can act on: a request still
         * waiting for its engine is not one, because it is going to play.
         *
         * A one-shot the UI acknowledges with [onSpeakNoticeShown], so the same
         * failure twice in a row still speaks twice.
         */
        val speakNotice: StateFlow<SpeakOutcome?> = _speakNotice.asStateFlow()

        /** The UI has shown [speakNotice]; clear it. */
        fun onSpeakNoticeShown() {
            _speakNotice.value = null
        }

        /**
         * Toggle: reading (or waiting to read) → stop; idle → read the RESULT in
         * the target language.
         *
         * Asynchronous because [ResultSpeaker.speak] waits out a bind that has
         * not finished — the tap that lands on a cache-hit result before the
         * ~500ms engine bind completes used to be told the LANGUAGE was
         * unsupported, which was false, and the same button worked seconds later
         * (issue #159 co-verify, block 2). A request in flight counts as
         * "reading" for the toggle: without that, the second tap would start a
         * second utterance instead of stopping the first.
         */
        fun onSpeak() {
            if (speakJob?.isActive == true || speaker.speaking.value) {
                speakJob?.cancel()
                speaker.stop()
                return
            }
            val result = state as? TextUiState.Result ?: return
            speakJob =
                viewModelScope.launch {
                    when (val outcome = speaker.speak(result.translatedText, result.request.targetLang)) {
                        SpeakOutcome.STARTED, SpeakOutcome.CANCELLED -> Unit
                        else -> _speakNotice.value = outcome
                    }
                }
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
         * The star write in flight, if any (issue #195 co-verify, F3).
         *
         * Held for the same reason [translateJob] and [speakJob] are: a control
         * whose work outlives the tap needs a handle on that work. Here the
         * handle is read rather than cancelled — see [onToggleFavourite] for why
         * a star write is never cancelled once it has started.
         */
        private var starJob: Job? = null

        /**
         * Star on the composer result: flips the history row's `favourite`; a
         * row the history write skipped (rare undetected-auto) is SAVED as a
         * favourite directly — the star always does something (no dead end).
         *
         * ONE write at a time (issue #195 co-verify, F3). A second tap while the
         * first write is still open used to start a second write from the same
         * unmoved icon: both read "not saved", both asked to SAVE, and the
         * second insert lost the C-8 tuple race and got `-1` back — so the star
         * reported UNFILLED for a row that was, in the database, saved and
         * favourited. Icon and row disagreed and nothing was said, which is the
         * silent misreport #179 closed and #189 closed again.
         *
         * The second tap is dropped rather than queued. On a slow disk the icon
         * has not moved yet, so both taps meant the same thing, and running the
         * pair would undo the save the user could see themselves asking for. It
         * is not a dead end either: the first write still finishes visibly — a
         * filled star, or a failure with Retry — and the star stays live.
         *
         * The write itself is NOT cancelled when the composer moves on, and that
         * is deliberate. A star is a durable ask, not a superseded one: abandon
         * it mid-flight and the row the user starred is silently never saved,
         * which is the harm this whole issue is about, one level down. Staleness
         * is handled where it actually bites — at the moment of publishing (see
         * [writeFavourite]) — because cancellation is cooperative and cannot
         * stop a coroutine that is already past its last suspension point.
         */
        fun onToggleFavourite() {
            val result = state as? TextUiState.Result ?: return
            val src = result.resolvedSourceLang ?: return
            if (starJob?.isActive == true) return
            // Read from the icon the user actually pressed, so the message names
            // what THEY were doing rather than which statement the database
            // happened to be on when it gave up.
            val asked = if (_resultFavourite.value) StarIntent.REMOVE else StarIntent.SAVE
            starJob = viewModelScope.launch { writeFavourite(result, src, asked) }
        }

        /**
         * The snackbar's Retry — the same write, on the result it failed for.
         *
         * The staleness guard is not defensive tidying: a snackbar showing an
         * action the user is expected to take stays up long enough for them to
         * type something new and translate it, and this ViewModel keeps ONE
         * `resultFavourite` for whatever is on screen. Without the guard, Retry
         * would star a translation the message never mentioned. When the result
         * has moved on there is nothing owed — the failure was about a face that
         * is gone, and the new result's star is live and tappable.
         *
         * The intent is re-derived rather than replayed, which is safe because
         * the failure handler puts the icon back to the truth it last verified:
         * the second run therefore asks for exactly what the first one did.
         */
        fun retryStar(failure: StarFailure) {
            if (state != failure.result) return
            onToggleFavourite()
        }

        /**
         * The composer has shown [shown] and does not need it again.
         *
         * Compare-and-set, not a plain clear: a Retry can fail while the first
         * message is still on screen, and clearing blind would throw that newer
         * failure away unread.
         */
        fun onStarFailureShown(shown: StarFailure) {
            _starFailure.compareAndSet(shown, null)
        }

        /**
         * The star's write half, guarded (issue #195).
         *
         * `Throwable`, not `Exception`. The point of this guard is that NOTHING
         * escapes into a scope with no handler, and `Exception` is not that:
         * Room here is Room-over-framework-SQLite (`Room.databaseBuilder` with no
         * driver override), and every statement it runs ends in a `native` method
         * on `android.database.sqlite.SQLiteConnection` — `nativeExecute`,
         * `nativePrepareStatement`. A JNI link that cannot be satisfied raises
         * `UnsatisfiedLinkError`: a `LinkageError`, so an `Error`, so NOT an
         * `Exception`, and a narrow catch hands it straight back to the process
         * handler this whole guard exists to keep it away from. (Verified here,
         * not inherited from #190: SDK sources `SQLiteConnection.java:138-167`
         * declare the natives, `:754` calls one, and the JDK on this machine
         * reports `UnsatisfiedLinkError → LinkageError → Error → Throwable`.)
         *
         * `CancellationException` is re-thrown FIRST and by name, because it is
         * an `Exception` — widening the catch protects it not at all, and only
         * the re-throw does. #142 shipped a worker that died permanently because
         * a `catch (e: Exception)` ate the cancellation meant to stop it
         * cleanly. Same shape as `TranslateTextUseCase.stampSafely` (#141).
         *
         * [verified] is what makes ONE catch honest about THREE failure points.
         * A star that lies about whether the tap landed is issue #179 again, so
         * it may only ever show what the database has actually confirmed:
         *
         * - the LOOKUP fails → nothing was written and nothing was learned, so
         *   the icon does not move at all;
         * - the INSERT fails → nothing was written, and the lookup just proved
         *   there is no row, so the star is unfilled;
         * - the UPDATE fails → nothing was written, and the lookup gave us the
         *   row's stored flag, so the star shows THAT and the flip is undone.
         *
         * That table is about WHAT is published; the re-check below the `try` is
         * about WHETHER anything may be (issue #195 co-verify). All three rows
         * are correct only for the result the write was asked for, and the write
         * outlives it whenever the disk is slow — which is the condition the
         * whole issue is about.
         *
         * RESIDUAL, named rather than hidden: a write that fails for a result the
         * user has already left says nothing at all. The alternative is a message
         * about a translation that is no longer on screen with a Retry that
         * cannot act, which is the dead end EDGE_CASES §94 forbids — and it is
         * the same ruling the `state` setter already makes when it retires a
         * failure the transition overtook. The star for that translation still
         * tells the truth the next time it is opened, because the row was never
         * written.
         *
         * KNOWN LIMIT, deliberately unchanged here: [TranslationRepository.save]
         * answers -1 when the C-8 tuple was taken between the lookup and the
         * insert, and that leaves the star unfilled with nothing said. It is not
         * a throw and it is not this issue; the honest fix is the merge #189
         * gave the sibling case, which is a behaviour decision of its own.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun writeFavourite(
            result: TextUiState.Result,
            src: String,
            asked: StarIntent,
        ) {
            var verified = _resultFavourite.value
            var failed = false
            val stored =
                try {
                    val row =
                        translationRepository.cached(
                            result.request.text,
                            src,
                            result.request.targetLang,
                            result.engine,
                        )
                    // The lookup answered, so the stored truth is known from here on.
                    verified = row?.favourite == true
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
                        id > 0
                    } else {
                        translationRepository.setFavourite(row.id, !row.favourite)
                        !row.favourite
                    }
                } catch (rethrown: CancellationException) {
                    // Leaving the composer cancels the write. Nobody is left to tell,
                    // so no message is invented and no snackbar is queued against a
                    // screen that has gone.
                    throw rethrown // never break structured cancellation
                } catch (unused: Throwable) {
                    failed = true
                    verified
                }
            // ONE re-check, and it stands between the database and BOTH things
            // the user can see (issue #195 co-verify, F1 + F2). The check at the
            // top of the tap is not enough: a slow disk is exactly the condition
            // this issue exists to survive, so the composer routinely moves on
            // while a write is still open, and this answer is about the result
            // that was on screen when the star was tapped — not the one on screen
            // now.
            //
            // Unguarded, the icon flipped to filled for a translation with no row
            // at all, and the failure message named a translation the user could
            // no longer see, carrying a Retry that `retryStar`'s own staleness
            // guard then refused without a word.
            //
            // Both publishes sit behind the SAME check on purpose: they were
            // three separate assignments, and a guard repeated three times is a
            // guard that gets applied twice.
            if (state != result) return
            _resultFavourite.value = stored
            if (failed) {
                // The cause is deliberately not shown: "database disk image is
                // malformed (code 11)" is not a sentence for a user. What they
                // get is which action failed, and a way to run it again.
                _starFailure.value = StarFailure(starFailureIds.incrementAndGet(), asked, result)
            }
        }

        /**
         * A History row reopens IN the composer (issue #68): input + pair +
         * the stored answer — Retry replays through the C-8 cache instantly.
         *
         * The row's ids are used EXACTLY as stored, deliberately (issue #151).
         * A row written before the detect door was closed can carry a legacy
         * spelling, and re-spelling it here is the one thing that would break
         * it: every lookup it takes part in — the star's `cached`, the use
         * case's `cachedAny` — is keyed on the id the row actually holds, so a
         * canonicalised copy would query past its own row and start a duplicate.
         * Legacy rows are tolerated on read, which means their ids travel
         * unchanged; the prefs seam canonicalises what it stores (#141), so the
         * chips still read the language the user expects.
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
