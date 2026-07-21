package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DATA_MODEL `prefs.*` typed accessors — keys exact, defaults per the DECISIONS
 * defaults table (en / fr / AUTO / system). Brand-specific default languages
 * (AppConfig) are seeded at first-run by the Settings vertical (later phase);
 * these constants are the spec fallbacks.
 */
@Singleton
class TranzlatePreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val sourceLang: Flow<String> = dataStore.data.map { it[KEY_SOURCE_LANG] ?: DEFAULT_SOURCE_LANG }

    val targetLang: Flow<String> = dataStore.data.map { it[KEY_TARGET_LANG] ?: DEFAULT_TARGET_LANG }

    /** ModeId name; AUTO by default — never the metered mode (defaults table). */
    val textMode: Flow<String> = dataStore.data.map { it[KEY_TEXT_MODE] ?: DEFAULT_TEXT_MODE }

    /** 0 = system (defaults table). */
    val theme: Flow<Int> = dataStore.data.map { it[KEY_THEME] ?: DEFAULT_THEME }

    suspend fun setSourceLang(value: String) {
        dataStore.edit { it[KEY_SOURCE_LANG] = value }
    }

    suspend fun setTargetLang(value: String) {
        dataStore.edit { it[KEY_TARGET_LANG] = value }
    }

    suspend fun setTextMode(value: String) {
        dataStore.edit { it[KEY_TEXT_MODE] = value }
    }

    suspend fun setTheme(value: Int) {
        dataStore.edit { it[KEY_THEME] = value }
    }

    companion object {
        private val KEY_SOURCE_LANG = stringPreferencesKey("prefs.source_lang")
        private val KEY_TARGET_LANG = stringPreferencesKey("prefs.target_lang")
        private val KEY_TEXT_MODE = stringPreferencesKey("prefs.text_mode")
        private val KEY_THEME = intPreferencesKey("prefs.theme")

        const val DEFAULT_SOURCE_LANG = "en"
        const val DEFAULT_TARGET_LANG = "fr"
        const val DEFAULT_TEXT_MODE = "AUTO"
        const val DEFAULT_THEME = 0
    }
}
