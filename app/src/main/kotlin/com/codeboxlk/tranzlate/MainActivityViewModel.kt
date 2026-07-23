package com.codeboxlk.tranzlate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Holds the appearance choice for the whole app.
 *
 * Activity-scoped rather than per-screen because the theme wraps every
 * destination, and because the splash screen (issue #17 A6) has to wait on the
 * same value before the first frame is drawn.
 *
 * The initial value is deliberately `null` and means **"not read yet"**, not
 * "the user chose the defaults". A6 uses exactly that distinction to hold the
 * splash; a non-null default here would make a user who picked Dark see a light
 * frame first, which is the whole problem the splash gate exists to prevent.
 */
@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(
        themePrefs: ThemePrefsRepository,
    ) : ViewModel() {
        val themeSettings: StateFlow<ThemeSettings?> =
            themePrefs.settings.stateIn(
                scope = viewModelScope,
                // Eagerly, not WhileSubscribed: the first frame cannot wait for a
                // subscriber, and this flow lives exactly as long as the Activity.
                started = SharingStarted.Eagerly,
                initialValue = null,
            )
    }
