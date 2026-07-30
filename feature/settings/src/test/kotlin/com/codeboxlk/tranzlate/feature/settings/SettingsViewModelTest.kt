package com.codeboxlk.tranzlate.feature.settings

import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val repo = FakeThemePrefsRepository()
    private val downloadRepo = FakeDownloadPrefsRepository()
    private lateinit var viewModel: SettingsViewModel

    // Constructed here, not as a field: the rule's starting() sets Dispatchers.Main
    // before @Before runs, so viewModelScope binds to the test dispatcher. A field
    // initialiser would run at construction, before the rule, and bind to the real
    // Main — which does not exist in a JVM unit test.
    @Before
    fun setUp() {
        viewModel = SettingsViewModel(repo, downloadRepo)
    }

    @Test
    fun `selecting a theme mode writes it through the repository`() =
        runTest {
            for (mode in ThemeMode.entries) {
                viewModel.onThemeModeSelected(mode)
                assertThat(repo.state.value.mode).isEqualTo(mode)
            }
        }

    @Test
    fun `toggling dynamic colour writes it through the repository`() =
        runTest {
            viewModel.onDynamicColorChanged(true)
            assertThat(repo.state.value.dynamicColor).isTrue()

            viewModel.onDynamicColorChanged(false)
            assertThat(repo.state.value.dynamicColor).isFalse()
        }

    /**
     * The screen renders nothing until the first stored value arrives — the
     * ViewModel must start `null`, not at a default that would flash.
     */
    @Test
    fun `settings start null and then emit the stored value`() =
        runTest {
            assertThat(viewModel.settings.value).isNull()

            repo.state.value = ThemeSettings(ThemeMode.DARK, dynamicColor = true)

            assertThat(viewModel.settings.first { it != null }).isEqualTo(repo.state.value)
        }

    /** Issue #90: the standing consent toggle round-trips through the repository. */
    @Test
    fun `mobile-data consent starts null then reflects and writes the stored value`() =
        runTest {
            assertThat(viewModel.allowMobileData.value).isNull()
            assertThat(viewModel.allowMobileData.first { it != null }).isFalse()

            viewModel.onAllowMobileDataChanged(true)
            assertThat(downloadRepo.state.value).isTrue()
            assertThat(viewModel.allowMobileData.first { it == true }).isTrue()
        }
}

private class FakeDownloadPrefsRepository : DownloadPrefsRepository {
    val state = MutableStateFlow(false)
    override val allowMobileData: Flow<Boolean> = state

    override suspend fun setAllowMobileData(value: Boolean) {
        state.value = value
    }
}

private class FakeThemePrefsRepository : ThemePrefsRepository {
    val state = MutableStateFlow(ThemeSettings.Default)
    override val settings: Flow<ThemeSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(mode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(dynamicColor = enabled)
    }
}
