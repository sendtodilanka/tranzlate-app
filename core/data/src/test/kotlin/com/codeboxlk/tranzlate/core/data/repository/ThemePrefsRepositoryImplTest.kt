package com.codeboxlk.tranzlate.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThemePrefsRepositoryImplTest {
    private fun repository(): Pair<ThemePrefsRepositoryImpl, TranzlatePreferencesDataSource> {
        val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())
        return ThemePrefsRepositoryImpl(source) to source
    }

    @Test
    fun `defaults to following the system with the static palette`() =
        runTest {
            val (repository, _) = repository()

            assertThat(repository.settings.first()).isEqualTo(ThemeSettings.Default)
        }

    @Test
    fun `each mode round-trips through its stored value`() =
        runTest {
            for (mode in ThemeMode.entries) {
                val (repository, _) = repository()

                repository.setThemeMode(mode)

                assertThat(repository.settings.first().mode).isEqualTo(mode)
            }
        }

    @Test
    fun `dynamic colour round-trips`() =
        runTest {
            val (repository, _) = repository()

            repository.setDynamicColor(true)

            assertThat(repository.settings.first().dynamicColor).isTrue()
        }

    /**
     * A value written by a newer build, or a file replaced after corruption, must
     * not leave the app with no theme at all.
     */
    @Test
    fun `an unknown stored mode degrades to SYSTEM`() =
        runTest {
            val (repository, source) = repository()

            source.setTheme(99)

            assertThat(repository.settings.first().mode).isEqualTo(ThemeMode.SYSTEM)
        }

    /**
     * The wire format is a contract with every existing install, so it is asserted
     * here rather than left to the enum's declaration order.
     */
    @Test
    fun `stored values are pinned to the DATA_MODEL numbering`() {
        assertThat(ThemeMode.SYSTEM.storedValue).isEqualTo(0)
        assertThat(ThemeMode.LIGHT.storedValue).isEqualTo(1)
        assertThat(ThemeMode.DARK.storedValue).isEqualTo(2)
    }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
