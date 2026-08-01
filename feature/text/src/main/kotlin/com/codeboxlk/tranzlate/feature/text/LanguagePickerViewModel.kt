package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * The picker's OWN state holder (issue #117).
 *
 * `TextViewModel` owns the *choice* (which language the composer translates
 * from/to). This class owns everything the picker needs to present that choice
 * honestly: the catalog, the live per-language offline-model state, the
 * last-used stamp that feeds Recent, and the row-level download controls the
 * redesigned rows carry.
 *
 * Two independent [StateFlow]s rather than one `combine`: a model-state source
 * that is slow to first-emit must never be able to hold the language list
 * hostage — the list renders, the badges arrive when they arrive.
 *
 * Issue #90's consent ruling is re-honoured here rather than bypassed: a metered
 * download is a CONSENT question, and the picker gained download controls in
 * this redesign, so the picker asks it too. Shipping the same button without the
 * gate would have spent the user's data plan behind their back.
 */
@HiltViewModel
class LanguagePickerViewModel
    @Inject
    constructor(
        private val languageRepository: LanguageRepository,
        private val clock: AppClock,
        private val modelManager: OfflineModelManager,
        private val connectivity: ConnectivityMonitor,
        private val downloadPrefs: DownloadPrefsRepository,
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

        private val _pendingConsent = MutableStateFlow<String?>(null)

        /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
        val pendingConsent: StateFlow<String?> = _pendingConsent.asStateFlow()

        /**
         * Records the pick so the language can surface under Recent next time —
         * under the side being picked for, since #130 rev.3 split recents per
         * role. The "auto" sentinel is not a catalog row, so it is never stamped.
         */
        fun onLanguagePicked(
            id: String,
            role: LanguageRole,
        ) {
            if (id == DETECT_LANGUAGE_ID) return
            viewModelScope.launch { languageRepository.setLastUsed(id, role, clock.nowMillis()) }
        }

        /** Row ⬇ / ↻. Metered + no standing consent → ask first, download never starts. */
        fun download(id: String) {
            viewModelScope.launch {
                val allowed = downloadPrefs.allowMobileData.first()
                if (connectivity.isMetered() && !allowed) {
                    _pendingConsent.value = id
                } else {
                    modelManager.download(id)
                }
            }
        }

        /** Dialog "Download once": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val id = _pendingConsent.value ?: return
            _pendingConsent.value = null
            viewModelScope.launch { modelManager.download(id) }
        }

        /** Dialog "Wait for Wi-Fi" (or dismiss): the row stays downloadable — no dead end. */
        fun dismissConsent() {
            _pendingConsent.value = null
        }

        /**
         * The row ✕ while downloading. Named for what it DOES: ML Kit exposes no
         * cancel, so stopping is deleting whatever has landed (plan R2, #90).
         */
        fun stopAndRemove(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }
    }
