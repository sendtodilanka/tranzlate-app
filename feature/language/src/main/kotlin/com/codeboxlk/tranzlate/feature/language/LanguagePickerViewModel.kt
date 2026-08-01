package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** DECISIONS defaults table (pre-first-emission frame only — DataStore owns the real default). */
private const val FALLBACK_SOURCE_LANG = "en"
private const val FALLBACK_TARGET_LANG = "fr"

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
 */
@HiltViewModel
class LanguagePickerViewModel
    @Inject
    constructor(
        private val languageRepository: LanguageRepository,
        private val clock: AppClock,
        private val modelManager: OfflineModelManager,
        private val downloadGate: DownloadGate,
        private val translatePrefs: TranslatePrefsRepository,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        /** Catalog rows, exactly as the repository serves them (no UI shaping here). */
        val languages: StateFlow<List<Language>> =
            languageRepository
                .languages()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

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

        /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

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
            val id = downloadGate.consentOnce() ?: return
            viewModelScope.launch { downloadGate.download(id) }
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
