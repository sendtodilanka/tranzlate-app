package com.codeboxlk.tranzlate.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** UI_SPEC §2.3 RECENTS — last 4 rows of the Room history. */
private const val RECENTS_LIMIT = 4

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * Drawer state holder: Recents from [TranslationRepository.history] (the write
 * side lives in TranslateTextUseCase — success-only, C-8 deduped).
 */
@HiltViewModel
class DrawerViewModel
    @Inject
    constructor(
        translationRepository: TranslationRepository,
    ) : ViewModel() {
        val recents: StateFlow<List<Translation>> =
            translationRepository
                .history()
                .map { history -> history.take(RECENTS_LIMIT) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())
    }
