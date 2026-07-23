package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [ThemePrefsRepository] (DATA_MODEL `prefs.theme` +
 * `prefs.dynamic_color` via [TranzlatePreferencesDataSource]).
 */
@Singleton
class ThemePrefsRepositoryImpl
    @Inject
    constructor(
        private val dataSource: TranzlatePreferencesDataSource,
    ) : ThemePrefsRepository {
        /**
         * `distinctUntilChanged` is not decoration: both upstreams are derived from
         * the same `dataStore.data`, so one stored write pushes a new value through
         * each of them and `combine` would emit twice with identical content —
         * recomposing the whole theme for nothing.
         */
        override val settings: Flow<ThemeSettings> =
            combine(dataSource.theme, dataSource.dynamicColor) { storedMode, dynamicColor ->
                ThemeSettings(
                    mode = ThemeMode.fromStoredValue(storedMode),
                    dynamicColor = dynamicColor,
                )
            }.distinctUntilChanged()

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataSource.setTheme(mode.storedValue)
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            dataSource.setDynamicColor(enabled)
        }
    }
