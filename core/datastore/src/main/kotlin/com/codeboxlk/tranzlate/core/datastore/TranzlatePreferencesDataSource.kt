package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DATA_MODEL `prefs.*` typed accessors — keys exact, defaults per the DECISIONS
 * defaults table (en / fr / AUTO / system). Brand-specific default languages
 * (AppConfig) are seeded at first-run by the Settings vertical (later phase);
 * these constants are the spec fallbacks.
 */
@Singleton
class TranzlatePreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Every read derives from here rather than from `dataStore.data` directly.
         *
         * DataStore does not recover from a read failure on its own — the exception
         * is delivered to the collector, which for us is a `viewModelScope`, and an
         * unhandled one there takes the app down on launch with no way out but
         * clearing app data. The factory's `corruptionHandler` repairs a corrupt
         * *file*; this covers the transient I/O failures it does not.
         *
         * Only IOException is swallowed: anything else is a programming error and
         * must stay loud.
         */
        private val preferences: Flow<Preferences> =
            dataStore.data.catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }

        val sourceLang: Flow<String> = preferences.map { it[KEY_SOURCE_LANG] ?: DEFAULT_SOURCE_LANG }

        val targetLang: Flow<String> = preferences.map { it[KEY_TARGET_LANG] ?: DEFAULT_TARGET_LANG }

        /** ModeId name; AUTO by default — never the metered mode (defaults table). */
        val textMode: Flow<String> = preferences.map { it[KEY_TEXT_MODE] ?: DEFAULT_TEXT_MODE }

        /** 0 = system (defaults table). */
        val theme: Flow<Int> = preferences.map { it[KEY_THEME] ?: DEFAULT_THEME }

        /** Material You. Off by default — the GT-identical palette is the brand (issue #15). */
        val dynamicColor: Flow<Boolean> = preferences.map { it[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR }

        suspend fun setSourceLang(value: String) {
            dataStore.edit { it[KEY_SOURCE_LANG] = value }
        }

        suspend fun setTargetLang(value: String) {
            dataStore.edit { it[KEY_TARGET_LANG] = value }
        }

        /** Swap-safe: both ids in ONE edit so no observer ever sees a torn pair. */
        suspend fun setLanguagePair(
            sourceValue: String,
            targetValue: String,
        ) {
            dataStore.edit {
                it[KEY_SOURCE_LANG] = sourceValue
                it[KEY_TARGET_LANG] = targetValue
            }
        }

        suspend fun setTextMode(value: String) {
            dataStore.edit { it[KEY_TEXT_MODE] = value }
        }

        suspend fun setTheme(value: Int) {
            dataStore.edit { it[KEY_THEME] = value }
        }

        suspend fun setDynamicColor(value: Boolean) {
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
        }

        companion object {
            private val KEY_SOURCE_LANG = stringPreferencesKey("prefs.source_lang")
            private val KEY_TARGET_LANG = stringPreferencesKey("prefs.target_lang")
            private val KEY_TEXT_MODE = stringPreferencesKey("prefs.text_mode")
            private val KEY_THEME = intPreferencesKey("prefs.theme")
            private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("prefs.dynamic_color")

            const val DEFAULT_SOURCE_LANG = "en"
            const val DEFAULT_TARGET_LANG = "fr"
            const val DEFAULT_TEXT_MODE = "AUTO"
            const val DEFAULT_THEME = 0
            const val DEFAULT_DYNAMIC_COLOR = false
        }
    }
