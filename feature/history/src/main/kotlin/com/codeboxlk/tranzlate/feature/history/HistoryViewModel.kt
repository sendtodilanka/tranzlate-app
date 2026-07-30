package com.codeboxlk.tranzlate.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * History/Saved state holder (issue #68) — reads the SAME Room flows the
 * drawer's Recents already uses (the write side lives in the use case; this
 * screen only ASKS, per APP_STRUCTURE).
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: TranslationRepository,
    ) : ViewModel() {
        val history: StateFlow<List<Translation>> =
            repository
                .history()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        val favourites: StateFlow<List<Translation>> =
            repository
                .favourites()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        fun toggleFavourite(translation: Translation) {
            viewModelScope.launch {
                repository.setFavourite(translation.id, !translation.favourite)
            }
        }

        /** Swipe-to-delete (issue #80). Undo re-inserts the SAME content. */
        fun delete(translation: Translation) {
            viewModelScope.launch { repository.delete(translation.id) }
        }

        fun undoDelete(translation: Translation) {
            viewModelScope.launch {
                repository.save(translation.copy(id = 0L))
            }
        }
    }
