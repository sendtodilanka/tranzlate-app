package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.Language
import kotlinx.coroutines.flow.Flow

/**
 * Language catalog contract — bundled static list (180+) intersected with MLKit
 * runtime capability (spec 02 §4.1/§4.2); Picker and Offline-manager are separate
 * UX surfaces over the same catalog (D-E2).
 */
interface LanguageRepository {
    fun languages(): Flow<List<Language>>

    suspend fun setLastUsed(
        languageId: String,
        atMillis: Long,
    )
}
