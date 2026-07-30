package com.codeboxlk.tranzlate.feature.languagepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 */
@HiltViewModel
class OfflineLanguagesViewModel
    @Inject
    constructor(
        languageRepository: LanguageRepository,
        private val modelManager: OfflineModelManager,
    ) : ViewModel() {
        val rows: StateFlow<List<OfflineLanguageRow>> =
            combine(languageRepository.languages(), modelManager.modelStates()) { catalog, states ->
                catalog.mapNotNull { language ->
                    val state = states[language.id] ?: return@mapNotNull null
                    OfflineLanguageRow(id = language.id, name = language.name, state = state)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        fun download(id: String) {
            viewModelScope.launch { modelManager.download(id) }
        }

        /** Also delete-to-cancel while Downloading (the verified MLKit limit). */
        fun delete(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }
    }
