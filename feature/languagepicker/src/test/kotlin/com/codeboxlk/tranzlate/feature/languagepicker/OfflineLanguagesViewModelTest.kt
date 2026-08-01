package com.codeboxlk.tranzlate.feature.languagepicker

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Issue #90 consent-gate matrix: metered is CONSENT, decided here — the
 * manager is only ever asked once the answer is yes.
 */
class OfflineLanguagesViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefs()
    private lateinit var viewModel: OfflineLanguagesViewModel

    @Before
    fun setUp() {
        viewModel =
            OfflineLanguagesViewModel(
                languageRepository = StaticLanguageRepository(),
                modelManager = manager,
                connectivity = connectivity,
                downloadPrefs = prefs,
            )
    }

    @Test
    fun `unmetered network downloads immediately - no dialog`() =
        runTest {
            connectivity.metered = false

            viewModel.download("de")
            runCurrent()

            assertThat(manager.downloads).containsExactly("de")
            assertThat(viewModel.pendingConsent.value).isNull()
        }

    @Test
    fun `metered without standing consent raises the dialog and does NOT download`() =
        runTest {
            connectivity.metered = true
            prefs.state.value = false

            viewModel.download("de")
            runCurrent()

            assertThat(manager.downloads).isEmpty()
            assertThat(viewModel.pendingConsent.value).isEqualTo("de")
        }

    @Test
    fun `metered with the standing consent ON downloads without asking`() =
        runTest {
            connectivity.metered = true
            prefs.state.value = true

            viewModel.download("de")
            runCurrent()

            assertThat(manager.downloads).containsExactly("de")
            assertThat(viewModel.pendingConsent.value).isNull()
        }

    @Test
    fun `Download once proceeds for THIS tap and leaves the standing pref untouched`() =
        runTest {
            connectivity.metered = true
            viewModel.download("de")
            runCurrent()

            viewModel.downloadAnyway()
            runCurrent()

            assertThat(manager.downloads).containsExactly("de")
            assertThat(viewModel.pendingConsent.value).isNull()
            assertThat(prefs.state.value).isFalse() // one-off yes, not a standing one

            // The NEXT metered tap must ask again.
            viewModel.download("fr")
            runCurrent()
            assertThat(viewModel.pendingConsent.value).isEqualTo("fr")
            assertThat(manager.downloads).containsExactly("de")
        }

    @Test
    fun `Wait for Wi-Fi dismisses without downloading - the row stays re-tappable`() =
        runTest {
            connectivity.metered = true
            viewModel.download("de")
            runCurrent()

            viewModel.dismissConsent()
            runCurrent()

            assertThat(manager.downloads).isEmpty()
            assertThat(viewModel.pendingConsent.value).isNull()
        }
}

private class RecordingModelManager : OfflineModelManager {
    val downloads = mutableListOf<String>()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        flowOf(
            mapOf(
                "de" to OfflineModelState.NotDownloaded,
                "fr" to OfflineModelState.NotDownloaded,
            ),
        )

    override suspend fun download(languageTag: String) {
        downloads += languageTag
    }

    override suspend fun delete(languageTag: String) = Unit
}

private class StaticLanguageRepository : LanguageRepository {
    override fun languages(): Flow<List<Language>> =
        flowOf(
            listOf(
                Language(id = "de", name = "German", offlineAvailable = true, offlineDownloaded = false),
                Language(id = "fr", name = "French", offlineAvailable = true, offlineDownloaded = false),
            ),
        )

    override suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) = Unit
}

private class FakeDownloadPrefs : DownloadPrefsRepository {
    val state = MutableStateFlow(false)
    override val allowMobileData: Flow<Boolean> = state

    override suspend fun setAllowMobileData(value: Boolean) {
        state.value = value
    }
}
