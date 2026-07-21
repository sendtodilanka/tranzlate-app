package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import kotlinx.coroutines.flow.Flow

/**
 * Offline-model download manager ask-surface (Translation brain owns model state —
 * spec 02 §5.2). Constraint (verified, spec 02 §3.1): MLKit downloads expose no
 * progress % and no true cancel — states are the 6-state [OfflineModelState] model,
 * "stop" = delete-to-cancel.
 */
interface OfflineModelManager {
    /** Per-language model states keyed by BCP-47 tag. */
    fun modelStates(): Flow<Map<String, OfflineModelState>>

    suspend fun download(languageTag: String)

    /** Also serves as delete-to-cancel while [OfflineModelState.Downloading]. */
    suspend fun delete(languageTag: String)
}
