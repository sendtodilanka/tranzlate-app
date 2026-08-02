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
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** DECISIONS defaults table (pre-first-emission frame only — DataStore owns the real default). */
private const val FALLBACK_SOURCE_LANG = "en"
private const val FALLBACK_TARGET_LANG = "fr"

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
         */
        private val sourceSelection: StateFlow<String> =
            translatePrefs.sourceLang
                .map(LanguageTagResolver::canonicalOrSelf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), FALLBACK_SOURCE_LANG)

        private val targetSelection: StateFlow<String> =
            translatePrefs.targetLang
                .map(LanguageTagResolver::canonicalOrSelf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), FALLBACK_TARGET_LANG)

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
         * it — nor reach [appScope], which has no `CoroutineExceptionHandler`,
         * so an escaping throw would take the process down for a missed date.
         * Same shape as `TranslateTextUseCase.stampSafely`, and for the same
         * reason: cancellation is rethrown so structured concurrency still
         * works.
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
            } catch (ignored: Exception) {
                // Manage packs misses one date; the selection itself is safe.
            }
        }

        /** Row ⬇ / ↻. Metered + no standing permission → ask first, download never starts. */
        fun download(id: String) {
            viewModelScope.launch { downloadGate.requestDownload(id) }
        }

        /** Dialog "Download once": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val consented = downloadGate.consentOnce() ?: return
            viewModelScope.launch { downloadGate.downloadConsented(consented) }
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
