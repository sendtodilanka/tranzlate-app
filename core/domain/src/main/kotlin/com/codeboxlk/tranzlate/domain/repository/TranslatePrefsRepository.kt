package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.ModeId
import kotlinx.coroutines.flow.Flow

/**
 * Translate-screen preference contract (DATA_MODEL `prefs.source_lang` /
 * `prefs.target_lang` / `prefs.text_mode`; defaults table: en → fr, AUTO).
 * Screens ask THROUGH this seam — the DataStore wiring lives in `:core:data`
 * (one-home rule; features never touch a data source directly).
 */
interface TranslatePrefsRepository {
    /** BCP-47 id, default `en` (defaults table). */
    val sourceLang: Flow<String>

    /** BCP-47 id, default `fr` (defaults table). */
    val targetLang: Flow<String>

    /** Default AUTO — never the metered mode (defaults table). */
    val textMode: Flow<ModeId>

    suspend fun setSourceLang(id: String)

    suspend fun setTargetLang(id: String)

    /** Swap-safe pair write: BOTH ids land in one atomic edit (no torn en→en state). */
    suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    )
}
