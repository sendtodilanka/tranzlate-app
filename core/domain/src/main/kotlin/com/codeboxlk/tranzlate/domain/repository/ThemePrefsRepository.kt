package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import kotlinx.coroutines.flow.Flow

/**
 * Appearance preference contract — DATA_MODEL `prefs.theme` + `prefs.dynamic_color`.
 *
 * Separate from [TranslatePrefsRepository] on purpose: how the app looks has
 * nothing to do with translating, and the app shell needs it before any feature
 * exists (APP_STRUCTURE "every big job has ONE home").
 */
interface ThemePrefsRepository {
    /**
     * Both choices in one emission — see [ThemeSettings] for why this is not two
     * flows. Never throws on a corrupt preferences file; it falls back to the
     * documented defaults instead.
     */
    val settings: Flow<ThemeSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Stored on every API level, honoured only on 31+. Persisting the choice on
     * older devices means it is already right if that install later moves to a
     * newer one, and it keeps this seam free of platform checks — the theme
     * decides what it can actually apply.
     */
    suspend fun setDynamicColor(enabled: Boolean)
}
