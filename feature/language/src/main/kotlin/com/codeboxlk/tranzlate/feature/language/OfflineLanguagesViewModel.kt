package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
 * Issue #90 (debate ruling): a metered download is a CONSENT question, and it
 * is decided by [DownloadGate] — never by MLKit's untested `requireWifi`. This
 * screen only routes the taps and lends the gate its scope.
 */
@HiltViewModel
class OfflineLanguagesViewModel
    @Inject
    constructor(
        languageRepository: LanguageRepository,
        private val modelManager: OfflineModelManager,
        private val downloadGate: DownloadGate,
    ) : ViewModel() {
        val rows: StateFlow<List<OfflineLanguageRow>> =
            combine(
                languageRepository.languages(),
                // Same guard LanguageRepositoryImpl.languages() puts on this exact
                // source, for the same reason: `combine` waits for EVERY source
                // before it can emit at all, and on a device without Play Services
                // the ML Kit answer may effectively never come — unprefixed, that
                // parked this screen on "Loading…" forever with no retry (the
                // EDGE_CASES dead-end class: a wait state that guides nowhere).
                // Prefixed empty, rows paint immediately at their resting state
                // and flip when the real state arrives — the contract the picker
                // already honours (its list renders; badges arrive when they do).
                modelManager.modelStates().onStart { emit(emptyMap()) },
            ) { catalog, states ->
                catalog
                    .filter(Language::offlineAvailable)
                    .map { language ->
                        OfflineLanguageRow(
                            id = language.id,
                            name = language.name,
                            // Capability is compile-time catalog truth (D-E2:
                            // `offlineAvailable` is derived from ML Kit's own tag
                            // list), so a missing map entry can only mean "no
                            // answer yet", never "not capable" — the resting
                            // state is NotDownloaded, not a hidden row.
                            state = states[language.id] ?: OfflineModelState.NotDownloaded,
                        )
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

        /** Row ⬇ / ↻. Metered + no standing permission → ask first, download never starts. */
        fun download(id: String) {
            viewModelScope.launch { downloadGate.requestDownload(id) }
        }

        /** Dialog "Download once": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val consented = downloadGate.consentOnce() ?: return
            viewModelScope.launch { downloadGate.downloadConsented(consented) }
        }

        /** Dialog "Wait for Wi-Fi" (or dismiss): the row stays NotDownloaded — no dead end. */
        fun dismissConsent() = downloadGate.dismiss()

        /** Also delete-to-cancel while Downloading (the verified MLKit limit). */
        fun delete(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }
    }
