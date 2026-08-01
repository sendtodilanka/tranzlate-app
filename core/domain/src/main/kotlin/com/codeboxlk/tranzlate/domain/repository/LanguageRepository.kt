package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import kotlinx.coroutines.flow.Flow

/**
 * Language catalog contract — bundled static list (180+) intersected with MLKit
 * runtime capability (spec 02 §4.1/§4.2); Picker and Offline-manager are separate
 * UX surfaces over the same catalog (D-E2).
 */
interface LanguageRepository {
    fun languages(): Flow<List<Language>>

    /**
     * Stamps a picker CHOICE for the Recent section, per [role] since issue
     * #130 rev.3 (16a shows source-recents and target-recents separately).
     * Selection only — actual translate USE is [LanguageUsageRepository]'s
     * job, and this method must never write there (ruling R6).
     */
    suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    )
}
