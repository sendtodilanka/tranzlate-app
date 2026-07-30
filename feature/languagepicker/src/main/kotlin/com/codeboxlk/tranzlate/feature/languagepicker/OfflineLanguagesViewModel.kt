package com.codeboxlk.tranzlate.feature.languagepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/** One Screen-B row: a catalog language that MLKit can hold offline. */
data class OfflineLanguageRow(
    val id: String,
    val name: String,
    val state: OfflineModelState,
)

/**
 * Screen B state holder (spec 02 D-E2): rows = bundled catalog ∩ MLKit-capable —
 * online-only languages NEVER appear here (they live in the picker with a badge).
 * The screen only ASKS the Translation brain's model manager.
 *
 * Issue #90 (debate ruling): a metered download is a CONSENT question, gated
 * HERE — never via MLKit's untested `requireWifi`. Metered + standing consent
 * absent → [pendingConsent] raises the one-tap dialog; [downloadAnyway] is a
 * one-off yes; the standing answer lives in Settings.
 */
@HiltViewModel
class OfflineLanguagesViewModel
    @Inject
    constructor(
        languageRepository: LanguageRepository,
        private val modelManager: OfflineModelManager,
        private val connectivity: ConnectivityMonitor,
        private val downloadPrefs: DownloadPrefsRepository,
    ) : ViewModel() {
        val rows: StateFlow<List<OfflineLanguageRow>> =
            combine(languageRepository.languages(), modelManager.modelStates()) { catalog, states ->
                catalog.mapNotNull { language ->
                    val state = states[language.id] ?: return@mapNotNull null
                    OfflineLanguageRow(id = language.id, name = language.name, state = state)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        private val _pendingConsent = MutableStateFlow<String?>(null)

        /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
        val pendingConsent: StateFlow<String?> = _pendingConsent.asStateFlow()

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

        /** Dialog "Wait for Wi-Fi" (or dismiss): the row stays NotDownloaded — no dead end. */
        fun dismissConsent() {
            _pendingConsent.value = null
        }

        /** Also delete-to-cancel while Downloading (the verified MLKit limit). */
        fun delete(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }
    }
