package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Seed for the two selection flows: an id no row can match, so the picker's
 * radio group ticks NOTHING until DataStore's first real value arrives (#154).
 *
 * `stateIn` emits its seed synchronously, before the preference has been read.
 * Seeding a real default (`en`/`fr`) meant a `de → ja` user saw English or
 * French carry the tick for one frame on every open — a control stating a choice
 * the app had not read yet. The empty id matches no catalog row
 * (`language.id == ""` is false for every id) and not the Detect sentinel
 * (`"" != "auto"`), so the honest "no selection read yet" state is drawn until
 * the real value lands. It can never arrive AS a real value: no language id is
 * blank, and `select` only ever writes a real id or the `auto` sentinel.
 *
 * This is the same honesty the rest of this file already keeps — `library` seeds
 * `null`, `offlineStates` `emptyMap()` — and it removes what was a THIRD copy of
 * the DECISIONS defaults table. The real default lives once, in
 * `TranzlatePreferencesDataSource` (`DEFAULT_SOURCE_LANG`/`DEFAULT_TARGET_LANG`),
 * which `translatePrefs.sourceLang`/`targetLang` already apply.
 */
private const val NO_SELECTION_YET = ""

/**
 * Saved-state keys for the picker's own screen state. Namespaced, because the
 * handle belongs to the whole ViewModel, not to any one of its jobs.
 */
private const val KEY_QUERY = "picker.query"
private const val KEY_SCROLL_ANCHOR = "picker.scroll_anchor"
private const val KEY_SCROLL_OFFSET = "picker.scroll_offset"

/**
 * The picker's OWN state holder (issue #117; decoupled from `TextViewModel`
 * per issue #130 rev.3 / #123.2): everything the picker needs to present AND
 * change the choice it was opened for — the catalog, the live per-language
 * offline-model state, the last-used stamp that feeds Recent, the row-level
 * download controls, and since the decouple the selection itself, read and
 * written straight through [TranslatePrefsRepository]. The composer's chips
 * read the SAME DataStore keys through the same repository, so the two
 * screens agree by construction, not by callback plumbing.
 *
 * Two independent [StateFlow]s rather than one `combine`: a model-state source
 * that is slow to first-emit must never be able to hold the language list
 * hostage — the list renders, the badges arrive when they arrive.
 *
 * Issue #90's consent ruling is re-honoured here rather than bypassed: a metered
 * download is a CONSENT question, and the picker gained download controls in
 * this redesign, so the picker asks it too. Shipping the same button without the
 * gate would have spent the user's data plan behind their back. The rule itself
 * lives in [DownloadGate]; this screen routes taps into it.
 *
 * **This class is also where the picker's own screen state lives** — what was
 * typed into the search field and how far the list is scrolled. That looks like
 * state a composable should own with `rememberSaveable`, and it was, until #130
 * PR-13. The reason it moved is in [query].
 */
@HiltViewModel
@Suppress("LongParameterList")
class LanguagePickerViewModel
    @Inject
    constructor(
        // TEN collaborators, and detekt's threshold IS ten — it trips AT the
        // threshold, not above it, so the old "eleven, one over" was wrong twice
        // over (#209 lens). Verified by removing this suppression: detekt names
        // all ten and answers "The current threshold is set to 10". The
        // suppression is necessary; the arithmetic that justified it was not. An
        // eleventh collaborator is a finding, not a threshold to raise.
        // is the honest option rather than the tidy one. The tidy one is a
        // parameter object, which would hide the same dependencies from Dagger
        // and from the next reader while the class kept doing the same amount.
        // Splitting the class is the real answer and it is NOT PR-17's: the
        // meter's three (`storageProbe`, `dispatchers`, and the `appScope` it
        // shares) arrived with PR-15, and PR-23 rewrites what reads them. The
        // rev3 ruling's anti-god-VM defence is the thing this comment is here to
        // keep visible — one more parameter and it stops being a threshold and
        // starts being a finding.
        private val languageRepository: LanguageRepository,
        private val clock: AppClock,
        private val modelManager: OfflineModelManager,
        private val downloadGate: DownloadGate,
        private val downloadPrefs: DownloadPrefsRepository,
        private val translatePrefs: TranslatePrefsRepository,
        private val storageProbe: StorageProbe,
        private val dispatchers: DispatcherProvider,
        private val savedStateHandle: SavedStateHandle,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        /**
         * What is typed in the search field.
         *
         * `rememberSaveable` would be the ordinary home for this, and it was the
         * shipped one. It survives process death, but only inside the composition
         * that declared it: every `rememberSaveable` slot is addressed through
         * the nearest `SaveableStateHolder`, and the nav shell gives each
         * destination its own (`TranzlateApp.kt`,
         * `rememberSaveableStateHolderNavEntryDecorator`). The picker is about to
         * be shown from more than one place — a full screen (15a/16a), a
         * side-by-side pane (17a/17b) and a dialog raised outside the NavDisplay
         * entirely (17c/17d) — and each of those is a different holder. Same key,
         * different slot, state gone.
         *
         * Held here, the state is addressed by the picker instead of by whoever
         * is drawing it, so a change of host is a change of host and nothing
         * else. That is the whole of the "host-agnostic saveable contract" the
         * rev.3 ruling asks PR-13 for: the screen composable owns no saveable
         * state of its own, and `PickerHostAgnosticTest` holds it to that.
         */
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

        /** The search field's every keystroke. */
        fun onQueryChange(text: String) {
            savedStateHandle[KEY_QUERY] = text
        }

        /**
         * Where the list is, read fresh — never captured once at construction.
         *
         * A configuration change destroys the composition and keeps this
         * ViewModel, so the seed for the new `LazyGridState` has to be the
         * position as of the LAST scroll, not as of whenever the ViewModel
         * happened to be built. A rotation is exactly that: it is what takes the
         * picker from 15a's one column to 17a's two, and the position has to
         * survive the trip (#130 PR-14).
         *
         * What is stored is a language id, not an item index — see
         * [PickerListPosition]. A `String?` in the handle, which the bundle takes
         * as readily as an `Int`, so process death is unaffected.
         */
        fun listPosition(): PickerListPosition =
            PickerListPosition(
                anchorId = savedStateHandle[KEY_SCROLL_ANCHOR],
                offset = savedStateHandle[KEY_SCROLL_OFFSET] ?: 0,
            )

        /** The list moved. Cheap on purpose — two map writes, no flow to collect. */
        fun onListPositionChange(position: PickerListPosition) {
            savedStateHandle[KEY_SCROLL_ANCHOR] = position.anchorId
            savedStateHandle[KEY_SCROLL_OFFSET] = position.offset
        }

        /** Catalog rows, exactly as the repository serves them (no UI shaping here). */
        val languages: StateFlow<List<Language>> =
            languageRepository
                .languages()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        /**
         * The offline-library meter's data (U-5), or null until the disk has
         * been read once.
         *
         * **Recomputed when the pack COUNT changes, and at no other time.**
         * `packsBytes()` walks ML Kit's model store file by file — 30 files for
         * a single pack, measured in E-S1 — so keying on the counts and dropping
         * repeats is not an optimisation but a deliberate trigger; collecting the
         * raw catalogue would re-walk the disk on every unrelated overlay change,
         * which is risk PP-5.b in the ruling's register. A fresh subscription
         * re-walks too, so leaving the picker and coming back always re-measures.
         *
         * ### The staleness window — a named limit (co-verify F2, PR-15)
         * Between those two triggers the card holds whatever it last measured.
         * Co-verify renamed the model store out from under a picker that stayed
         * open, and the card went on reading **"2 of 59 packs · 44 MB used"** for
         * as long as that picker lived; navigating away and back corrected it. So
         * the card can state a specific, confident, no-longer-true size, and this
         * is the disclosure of it rather than a claim it cannot happen.
         *
         * What the user could actually see is bounded by what can change the
         * store's size without changing the count, and inside the app that set is
         * empty by enumeration:
         * - a download completing, or a delete — both move the count, so both
         *   re-walk;
         * - a download interrupted part-way, which leaves debris under ML Kit's
         *   scratch directory — excluded from the sum since co-verify F3
         *   (`MLKIT_SCRATCH_DIR`), so the number does not move and there is
         *   nothing to go stale;
         * - ML Kit renaming the store beneath a live process — the co-verify
         *   reproduction, done with root over `adb`. Nothing the app does causes
         *   it, and a Play Services module update does not rename another app's
         *   private directory mid-session.
         *
         * A timer or a filesystem observer would close the last of those at the
         * cost of walking a 30-file tree on a schedule for a state no user path
         * produces. The trade is recorded here rather than made silently, and
         * `LanguagePickerViewModelTest.the meter holds its number until a pack
         * arrives or leaves` pins BOTH halves so a change of mind cannot be a
         * quiet one.
         *
         * `null` while the walk is in flight, and the card is simply not drawn
         * then. A placeholder saying "0 packs" that corrected itself a moment
         * later would have stated something false in between — the same rule
         * that keeps the first frame from labelling 194 rows "Online only".
         */
        val library: StateFlow<OfflineLibraryMeter?> =
            languages
                // An empty catalogue is the pre-emission frame, not a device with
                // no languages on it — `languages` starts at `emptyList()` and the
                // bundled catalogue is static and never empty. Counting it would
                // publish "0 of 0 packs · nothing downloaded" for a frame and then
                // correct itself, which is the same false-first-frame the row
                // states refuse (`rowStateOf`, "Online only"). The picker reads the
                // same emptiness as "loading" (`PickerSections.catalogEmpty`).
                .filter(List<Language>::isNotEmpty)
                .map(::onDeviceCount)
                .distinctUntilChanged()
                .mapLatest { counts ->
                    // `freeBytes`/`totalBytes` are StatFs calls and `packsBytes`
                    // walks a directory tree: all three are disk reads, so all
                    // three are taken off the main thread together rather than
                    // trusting the one that declares itself suspending.
                    withContext(dispatchers.io) {
                        offlineLibraryMeter(
                            downloaded = counts.downloaded,
                            capable = counts.capable,
                            packsBytes = storageProbe.packsBytes(),
                            volumeBytes = storageProbe.totalBytes(),
                            freeBytes = storageProbe.freeBytes(),
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), null)

        /**
         * Live model state keyed by BCP-47 tag — the TRANSIENT half only
         * (downloading / failed / deleting). Whether a model is on disk comes
         * from [Language.offlineDownloaded], which the repository already
         * overlays, so an empty map here means "nothing in flight", never
         * "nothing downloaded".
         */
        val offlineStates: StateFlow<Map<String, OfflineModelState>> =
            modelManager
                .modelStates()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

        /**
         * The current choice per side, straight from the SAME repository (and
         * therefore the same DataStore keys) the composer's chips read — the
         * picker no longer borrows `TextViewModel` to know what is selected.
         *
         * Each id is presented through [LanguageTagResolver]: a preference
         * persisted before write-side canonicalisation (issue #119) can still
         * hold a detector spelling ("iw", "zh-CN"), and served raw it would
         * tick NO row while the chip reads "Hebrew" — the #123.2 contradiction.
         * The "auto" sentinel resolves to null and passes through unchanged.
         *
         * Seeded with [NO_SELECTION_YET], not a language: until the preference is
         * read the radio group ticks no row rather than a possibly-wrong one
         * (#154). The first real emission from `translatePrefs` replaces it.
         */
        private val sourceSelection: StateFlow<String> =
            translatePrefs.sourceLang
                .map(LanguageTagResolver::canonicalOrSelf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), NO_SELECTION_YET)

        private val targetSelection: StateFlow<String> =
            translatePrefs.targetLang
                .map(LanguageTagResolver::canonicalOrSelf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), NO_SELECTION_YET)

        /** The id the [role] side's radio should tick right now. */
        fun selection(role: LanguageRole): StateFlow<String> =
            when (role) {
                LanguageRole.SOURCE -> sourceSelection
                LanguageRole.TARGET -> targetSelection
            }

        private val sourceRecents: StateFlow<Map<String, Long>> =
            languageRepository
                .recentSelections(LanguageRole.SOURCE)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

        private val targetRecents: StateFlow<Map<String, Long>> =
            languageRepository
                .recentSelections(LanguageRole.TARGET)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

        /**
         * Stamps for the [role] side's own recents section — 16a's "Recently
         * used as target" may only ever list languages picked as a target.
         *
         * A third independent [StateFlow] rather than a fourth `combine` source,
         * for the same reason as the two above: recents are a shortcut, and a
         * slow store must never be able to hold 194 rows behind them.
         */
        fun recents(role: LanguageRole): StateFlow<Map<String, Long>> =
            when (role) {
                LanguageRole.SOURCE -> sourceRecents
                LanguageRole.TARGET -> targetRecents
            }

        /** Language id awaiting the mobile-data consent sheet; null = no sheet. */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

        /**
         * Sheet 19a's checkbox — "Always ask before using mobile data", which is
         * the stored `allowMobileData` read from the other end ([alwaysAskOf]).
         *
         * The initial value is `true`, and that is a FACT rather than an
         * optimistic guess: the only way the sheet is on screen at all is that
         * [DownloadGate] found the standing permission off when the row was
         * tapped. A `false` seed would tick the box the wrong way for one frame
         * on a consent surface.
         */
        val alwaysAsk: StateFlow<Boolean> =
            downloadPrefs.allowMobileData
                .map(::alwaysAskOf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), true)

        /**
         * The checkbox moved: write the STANDING preference, the same one
         * Settings writes and [DownloadGate] reads.
         *
         * On `appScope`, not `viewModelScope`, for the reason [select] spells
         * out: unticking the box and tapping "Download now" pops nothing, but
         * the offline manager's sheet can be answered as the user leaves, and
         * DataStore's `edit` runs in the CALLER's context — a cancelled caller
         * drops the write. A consent preference that silently failed to save
         * would ask again next time, which is the safe direction, but it would
         * also make the checkbox a lie.
         */
        fun onAlwaysAskChange(alwaysAsk: Boolean) {
            appScope.launch { downloadPrefs.setAllowMobileData(allowMobileDataOf(alwaysAsk)) }
        }

        /**
         * A row tap: the WHOLE selection, in one place and in a fixed order —
         * the CHOICE first, then the Recent stamp (per role since #130 rev.3
         * split recents; the "auto" sentinel is not a catalog row, so it is
         * never stamped), both through the same repositories the composer's
         * swap/history writes use. One coroutine, so the stamp can never race
         * behind the write that closes the screen.
         *
         * The order is load-bearing, and it used to be the other way round.
         * These are two separate DataStore commits plus a Room write; nothing
         * makes them atomic. Whatever runs second is the half that can be lost
         * — to a throw, or to the process dying in the gap while the user is
         * already walking away from the screen. Losing the stamp costs Manage
         * packs one date. Losing the choice means the language appears under
         * Recent while the composer still shows the old one: the screen
         * contradicting itself, which is the exact defect this epic exists to
         * remove. So the half that may be lost is the cheap one.
         */
        fun select(
            id: String,
            role: LanguageRole,
        ) {
            // NOT viewModelScope. Selecting pops the screen, and the nav
            // decorator clears this ViewModel's store on pop — cancelling the
            // coroutine. DataStore's `edit` runs its transform in the CALLER's
            // context, so a cancelled caller DROPS the write: the user taps a
            // language, the screen closes, and the composer still shows the old
            // one. Worse, the recents stamp above it writes to a DIFFERENT store
            // and can survive, so the picked language turns up under Recent while
            // the choice itself was lost — the screen contradicting itself, which
            // is the exact class this epic exists to remove.
            //
            // Before the decouple this write lived on the hoisted TextViewModel,
            // whose scope outlives the pop. The ruling called the move
            // "behaviour-preserving (same DataStore keys)": true of the keys,
            // false of the durability. Found by the co-verify lens, not the tests
            // — every one of them drives the coroutine to completion.
            appScope.launch {
                when (role) {
                    LanguageRole.SOURCE -> translatePrefs.setSourceLang(id)
                    LanguageRole.TARGET -> translatePrefs.setTargetLang(id)
                }
                if (id != DETECT_LANGUAGE_ID) {
                    stampSafely(id, role)
                }
            }
        }

        /**
         * Best-effort Recent stamp. It writes to a different store than the
         * choice above, and a disk error there must not take the choice with
         * it — nor reach [appScope], whose handler (issue #238) is a backstop
         * for what nothing guarded, not a substitute for guarding this: a throw
         * caught up there would still have skipped everything left in the
         * launch. Same shape as `TranslateTextUseCase.stampSafely`, and for the
         * same reason: cancellation is rethrown so structured concurrency still
         * works.
         *
         * `Throwable`, not `Exception` (issue #236). This ends in a Room write
         * (`LanguageRepositoryImpl.kt:168`), and Room's statements end in `native`
         * methods, so the failure class this guard exists for —
         * `UnsatisfiedLinkError`, a `LinkageError`, so an `Error` — is precisely
         * the one a narrow catch lets past. Reasoning and citations:
         * `TextViewModel.kt:768-779` (#195). The KDoc above named the crash it was
         * preventing while using the catch that does not prevent it: the user taps
         * a language row, the picker pops, and the app dies a moment later — AFTER
         * the selection was written, so the harm is invisible in the stored state.
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun stampSafely(
            id: String,
            role: LanguageRole,
        ) {
            try {
                languageRepository.setLastUsed(id, role, clock.nowMillis())
            } catch (rethrown: CancellationException) {
                throw rethrown
            } catch (ignored: Throwable) {
                // Manage packs misses one date; the selection itself is safe.
            }
        }

        private val raisedFailure = MutableStateFlow<PackFailureRequest?>(null)

        /**
         * Sheet 19b or 19d, or neither — the failure the user is owed an
         * interruption about (#130 PR-18).
         *
         * **Only a download THIS screen asked for can raise it.** The manager's
         * state map is shared by every screen and outlives the one that caused
         * the failure, so a picker opened after a failure in Settings would
         * otherwise be met by a sheet about something the user did not just do.
         * A failure nobody here asked for is still reported — on the row, with
         * its cause and its Retry — which is where an unrequested fact belongs.
         *
         * **And the sheet that is up stays up until it is answered** (issue
         * **#239**). One slot is not the same guarantee as one sheet: the picker
         * lists 59 downloadable languages and nothing discourages tapping two, so
         * a second failure used to overwrite the first — the sheet the user was
         * READING became a different sheet, with a different action arriving where
         * their thumb already was (`Retry` → `Manage packs`). See [raise].
         */
        val packFailure: StateFlow<PackFailureRequest?> = raisedFailure.asStateFlow()

        /**
         * How many sheets the user has answered. Read and compared across
         * [raise]'s suspension point — see the third guard there.
         */
        private val sheetsAnswered = AtomicLong(0)

        /** "Close" on 19d, a scrim tap, a back press: the row keeps its cause line and its Retry. */
        fun dismissPackFailure() {
            raisedFailure.value = null
            sheetsAnswered.incrementAndGet()
        }

        /**
         * Row ⬇ / ↻. Metered + no standing permission → ask first, download never starts.
         *
         * The gate call is taken off the main thread (issue #238). Two blocking
         * calls hide under it — `ConnectivityMonitor.isMetered()` is a synchronous
         * binder IPC, and the manager's free-space pre-flight is a `statvfs`
         * syscall — and `viewModelScope` is `Dispatchers.Main.immediate`, so both
         * ran on the UI thread on every download tap. One `withContext` covers both,
         * because the whole call tree beneath it inherits the context.
         *
         * The wrap is HERE, not inside `DownloadGate`, because the gate's contract
         * is that it adds no context and no lifetime of its own — the choice stays
         * visible where the tap is handled (`DownloadGate`'s KDoc, held by
         * `DownloadGateTest`). It is the same move this file already makes for the
         * meter's disk reads at [library] and for 19b's at [packFailureRequest].
         */
        fun download(id: String) {
            val before = offlineStates.value[id]
            viewModelScope.launch {
                // Null = the gate only ASKED. Nothing started, so there is no
                // outcome to wait for — and waiting would leave a collector
                // suspended on a question the user may never answer. This used to
                // read `pendingConsent.value == id`, which asked a StateFlow what
                // the call just made had done; the call answers for itself now.
                val attempt = withContext(dispatchers.io) { downloadGate.requestDownload(id) } ?: return@launch
                reportOutcome(id, before, attempt)
            }
        }

        /** Dialog "Download once": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val consented = downloadGate.consentOnce() ?: return
            val before = offlineStates.value[consented.id]
            viewModelScope.launch {
                // Off the main thread for [download]'s reason: the free-space
                // pre-flight is a syscall and this path reaches it too.
                val attempt = withContext(dispatchers.io) { downloadGate.downloadConsented(consented) }
                reportOutcome(consented.id, before, attempt)
            }
        }

        /**
         * One attempt, to its end: raise a sheet if it failed, and say nothing if
         * it did not.
         *
         * The attempt has two halves and they arrive by two routes, because only
         * one of them CAN travel through the state map.
         *
         * - **[DownloadAttempt.Refused]** — the free-space pre-flight declined
         *   before enqueueing anything, and it declined synchronously, so the
         *   answer is already in hand. This is the whole of issue **#234**: the
         *   refusal writes `Failed(STORAGE)`, that is a `data class`, and a
         *   `MutableStateFlow` holding an equal value does not emit — so a Retry
         *   on a still-full disk was invisible to every watcher, and the row's
         *   enabled 48 dp Retry pill did nothing at all. Reported here instead,
         *   on EVERY attempt, and see [raise] for why re-raising 19b is the honest
         *   answer rather than a nag.
         * - **[DownloadAttempt.Started]** — the transfer is running on the
         *   Translation brain's own scope (#82/#83: leaving the screen must not
         *   strand it), so its outcome arrives the way every other pack fact does,
         *   through the shared state map. The rev3 ruling's U-1 `PackEvents`
         *   (PR-22) is the sanctioned event channel for this half; it does not
         *   exist yet and inventing one here would be the third copy REJECT §7.8
         *   bounces.
         * - **[DownloadAttempt.Ignored]** — not offline-capable, or already
         *   downloading. This attempt wrote nothing, so there is nothing of its
         *   own to watch. The two cases differ and the difference is worth being
         *   exact about: for a tag ML Kit cannot hold, nothing will EVER move that
         *   row, so a watcher parks for the life of the screen; for a tag already
         *   in flight, the row does move — when somebody else's attempt ends — and
         *   the watcher would have reported that outcome as if it were this tap's.
         *   A permanent park and a wrong answer, not one fault twice.
         *
         * [before] is the state the row was showing when the user tapped. The
         * shared map is a `StateFlow` fed by a `combine` on another scope, so it
         * can still be one emission behind the write the manager has already made
         * by the time this runs; dropping while the value is unchanged waits for
         * it to catch up, and everything after that belongs to this attempt.
         *
         * **The drop always ends, and the reason is not the one first written
         * here** (#246 co-verify). That said [before] could never be `Downloading`
         * on the `Started` path, and a lens built the interleaving where it can
         * be: a different, already-finished attempt on the same tag racing the
         * read of [before]. The conclusion survives the proof: `dropWhile` drops
         * only while the value EQUALS [before], and the attempt this function is
         * watching will move that row to `Downloaded` or `Failed` whatever it was
         * showing before — so the drop ends either way, and skipping the
         * `Downloading` step costs nothing because the branch below concludes on
         * `Failed` directly.
         *
         * `Downloaded`, `Deleting` after a Stop, or `NotDownloaded` again all
         * conclude the attempt without a failure.
         */
        private suspend fun reportOutcome(
            id: String,
            before: OfflineModelState?,
            attempt: DownloadAttempt,
        ) {
            when (attempt) {
                DownloadAttempt.Ignored -> return
                is DownloadAttempt.Refused -> raise(id, attempt.cause)
                DownloadAttempt.Started -> awaitFailure(id, before)?.let { cause -> raise(id, cause) }
            }
        }

        /** The started transfer's own outcome, or null if it did not fail. */
        private suspend fun awaitFailure(
            id: String,
            before: OfflineModelState?,
        ): OfflineModelFailure? =
            modelManager
                .modelStates()
                .map { it[id] }
                .dropWhile { it == before }
                .transformWhile { state ->
                    when (state) {
                        OfflineModelState.Downloading -> {
                            true
                        }

                        is OfflineModelState.Failed -> {
                            emit(state.cause)
                            false
                        }

                        else -> {
                            false
                        }
                    }
                }.firstOrNull()

        /**
         * Take the sheet slot, or leave the failure on its row.
         *
         * ## What is dropped, stated as conditions rather than as a slogan
         *
         * A failure raises the sheet **only if** the slot is free when its request
         * is ready **and** no sheet was answered while that request was being
         * built. Otherwise it goes to its row — red, naming its cause, offering a Retry
         * that now works — and it never comes back. That is where this class
         * already puts a failure the user is not owed an interruption about (see
         * [packFailure]), and it satisfies `EDGE_CASES.md` §7 on the surface the
         * user is actually looking at.
         *
         * The alternative answer to issue #239 — queueing the loser behind the
         * open sheet — was rejected: it re-arms the same harm one beat later, in
         * the space the thumb is already travelling to, quoting figures measured
         * before the user did anything about them, which is issue #235.
         *
         * ## Two guards, and why the rule above follows from them
         *
         * [packFailureRequest] **suspends** — the 19b branch reads the disk on IO
         * — so "is the slot free" and "here is the request" are separated by a
         * real dispatch, and everything hard about this function lives in that
         * gap. The first version was the CAS alone,
         * `compareAndSet(null, packFailureRequest(id, cause))`, and **Kotlin
         * evaluates the argument first**: the read ran, and only then was the slot
         * inspected. The #246 co-verify lens drove a dismiss through the gap and
         * got the defect out — Hindi's 19d on screen, Tamil refused for space and
         * therefore owed nothing but its row, the user taps Close while Tamil's
         * read is in flight, and Tamil's sheet lands **on the dismiss**. Answer
         * one interruption, receive another: #239's own harm through this
         * function's suspension point, and `Dispatchers.IO` contention widens the
         * window exactly when two downloads are in flight.
         *
         * 1. **Nothing was answered while this request was being built.** A sheet
         *    can only stop holding the slot by being answered, so this is what
         *    turns "the slot happens to be free now" into "the slot was free for
         *    this whole attempt". It catches the lens's case, and the narrower one
         *    where two failures both concluded free and the loser would otherwise
         *    land on the dismiss that freed the winner.
         * 2. **The slot is still free.** The CAS, for the case guard 1 cannot see:
         *    two requests built concurrently with nothing answered, where one
         *    simply has to lose.
         *
         * Between them the user-facing rule holds. If a sheet was up when a
         * failure concluded, then either it is still up when that failure's
         * request is ready — guard 2 refuses — or it was answered in the meantime
         * — guard 1 refuses. There is no third way for it to have gone.
         *
         * **A third check stood here and the mutation run deleted it.** "Is the
         * slot free right now", asked before the read, looks like the natural
         * first line and is a pure fast path: every case it rejects is rejected
         * again by one of the two above, so no mutation could redden a test by
         * removing it. It is gone rather than kept as an optimisation, because a
         * check that cannot fail a test is a check the next reader will mistake
         * for the load-bearing one — and may then "simplify" a real guard against.
         *
         * **The counter is global, not scoped to the sheet blocking THIS failure**,
         * and the co-verify lens demonstrated the consequence: a failure whose read
         * is slow can be dropped to its row because some *unrelated* sheet was
         * raised and answered while that read was in flight — even though nothing
         * was ever blocking this one. Correct against the rule as coded ("no sheet
         * was answered while this request was being built"), and harmless — the row
         * is accurate and its Retry works, which is the same fallback this file
         * already relies on. Named here because the sentence above reads as though
         * it were scoped to this failure's own blocker, and it is not.
         */
        private suspend fun raise(
            id: String,
            cause: OfflineModelFailure,
        ) {
            val answeredBefore = sheetsAnswered.get()
            val request = packFailureRequest(id, cause)
            if (sheetsAnswered.get() != answeredBefore) return
            raisedFailure.compareAndSet(expect = null, update = request)
        }

        /**
         * Which sheet, and what it needs to draw itself.
         *
         * The choice is [downloadFailureCopy]'s and not repeated here — that map
         * is the single home for "what does this cause mean", and a `when` over
         * the same enum in a ViewModel would be the second copy of it.
         *
         * 19b's two figures are read HERE rather than passed down from the
         * library meter, and the difference matters: the meter answers "how much
         * of this disk do my packs use", which is a question about packs, and
         * degrades to `null` when the model store cannot be found. 19b's bar is
         * used-against-free on the whole volume — a question about the device —
         * and both numbers come from the same probe in the same breath, so the
         * sheet cannot draw a fill and a legend that describe two moments. They
         * are disk reads, so they are taken off the main thread, exactly as the
         * meter's three are.
         *
         * **Read per RAISE, which is what makes the retry worth reporting**
         * (issues #234 + #235 together). The figures were never cached; what went
         * wrong was that a raised request SURVIVED the user's trip to Manage
         * packs, so a sheet measured before they freed anything was the sheet
         * waiting when they came back. With the request cleared on the way out and
         * a refusal reported on every attempt, each 19b on screen quotes a number
         * read at the moment it was raised — so freeing 130 MB of a 150 MB
         * requirement is answered with "142 MB free" rather than with silence.
         */
        private suspend fun packFailureRequest(
            id: String,
            cause: OfflineModelFailure,
        ): PackFailureRequest =
            when (downloadFailureCopy(cause).sheet) {
                DownloadFailureSheet.NoSpace -> {
                    withContext(dispatchers.io) {
                        PackFailureRequest.NoSpace(
                            freeBytes = storageProbe.freeBytes(),
                            volumeBytes = storageProbe.totalBytes(),
                        )
                    }
                }

                is DownloadFailureSheet.Interrupted -> {
                    PackFailureRequest.Interrupted(id = id, cause = cause)
                }
            }

        /** Dialog "Wait for Wi-Fi" (or dismiss): the row stays downloadable — no dead end. */
        fun dismissConsent() = downloadGate.dismiss()

        /**
         * The row ✕ while downloading. Named for what it DOES: ML Kit exposes no
         * cancel, so stopping is deleting whatever has landed (plan R2, #90).
         */
        fun stopAndRemove(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }
    }
