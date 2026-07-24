package com.codeboxlk.tranzlate.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Appearance settings (issue #30 / #17 A4). Screens only ASK: this reads and
 * writes the one [ThemePrefsRepository]; the live re-theme is the app shell's
 * job ([com.codeboxlk.tranzlate.MainActivityViewModel] → `TranzlateTheme`), so
 * writing a choice here re-themes the whole app on its own.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themePrefs: ThemePrefsRepository,
    ) : ViewModel() {
        /**
         * `null` = not read yet, so the screen can render nothing rather than a
         * default that flickers to the real value a frame later. Same convention as
         * the shell's own theme state.
         */
        val settings: StateFlow<ThemeSettings?> =
            themePrefs.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = null,
            )

        fun onThemeModeSelected(mode: ThemeMode) {
            viewModelScope.launch { themePrefs.setThemeMode(mode) }
        }

        fun onDynamicColorChanged(enabled: Boolean) {
            viewModelScope.launch { themePrefs.setDynamicColor(enabled) }
        }

        private companion object {
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        }
    }
