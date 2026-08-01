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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
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

    // Issue #130 PR-2: the forever-Loading dead end. `combine` waits for EVERY
    // source, so a manager whose ML Kit answer never comes (no Play Services)
    // must not be able to hold the rows hostage — the onStart guard makes the
    // catalog paint at its resting state, exactly as the repository/picker do.

    @Test
    fun `silent manager - rows still paint the offline catalog at resting state`() =
        runTest {
            val viewModel =
                OfflineLanguagesViewModel(
                    languageRepository = StaticLanguageRepository(),
                    modelManager = SilentModelManager(),
                    connectivity = connectivity,
                    downloadPrefs = prefs,
                )
            viewModel.rows.launchIn(backgroundScope)
            runCurrent()

            assertThat(viewModel.rows.value)
                .containsExactly(
                    OfflineLanguageRow(id = "de", name = "German", state = OfflineModelState.NotDownloaded),
                    OfflineLanguageRow(id = "fr", name = "French", state = OfflineModelState.NotDownloaded),
                ).inOrder()
        }

    @Test
    fun `late manager answer flips the resting rows to the real states`() =
        runTest {
            val scripted = ScriptedModelManager()
            val viewModel =
                OfflineLanguagesViewModel(
                    languageRepository = StaticLanguageRepository(),
                    modelManager = scripted,
                    connectivity = connectivity,
                    downloadPrefs = prefs,
                )
            viewModel.rows.launchIn(backgroundScope)
            runCurrent()

            // Before ANY manager emission: resting rows, not an empty "Loading…".
            assertThat(viewModel.rows.value.map(OfflineLanguageRow::state))
                .containsExactly(OfflineModelState.NotDownloaded, OfflineModelState.NotDownloaded)

            scripted.states.emit(
                mapOf(
                    "de" to OfflineModelState.Downloaded,
                    "fr" to OfflineModelState.Downloading,
                ),
            )
            runCurrent()

            assertThat(viewModel.rows.value)
                .containsExactly(
                    OfflineLanguageRow(id = "de", name = "German", state = OfflineModelState.Downloaded),
                    OfflineLanguageRow(id = "fr", name = "French", state = OfflineModelState.Downloading),
                ).inOrder()
        }

    @Test
    fun `online-only languages never appear - even before the manager answers`() =
        runTest {
            val viewModel =
                OfflineLanguagesViewModel(
                    languageRepository = MixedTierLanguageRepository(),
                    modelManager = SilentModelManager(),
                    connectivity = connectivity,
                    downloadPrefs = prefs,
                )
            viewModel.rows.launchIn(backgroundScope)
            runCurrent()

            // D-E2: Screen B = catalog ∩ MLKit-capable. The resting default must
            // not smuggle online-only rows in while the state map is still empty.
            assertThat(viewModel.rows.value.map(OfflineLanguageRow::id)).containsExactly("de")
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

/** No Play Services: the ML Kit answer NEVER comes — the flow just hangs. */
private class SilentModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override suspend fun download(languageTag: String) = Unit

    override suspend fun delete(languageTag: String) = Unit
}

/** Emits nothing until the test scripts an answer — models a slow first ML Kit round-trip. */
private class ScriptedModelManager : OfflineModelManager {
    val states = MutableSharedFlow<Map<String, OfflineModelState>>()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = states

    override suspend fun download(languageTag: String) = Unit

    override suspend fun delete(languageTag: String) = Unit
}

/** One offline-capable row and one online-only row (the picker-only tier). */
private class MixedTierLanguageRepository : LanguageRepository {
    override fun languages(): Flow<List<Language>> =
        flowOf(
            listOf(
                Language(id = "de", name = "German", offlineAvailable = true, offlineDownloaded = false),
                Language(id = "yue", name = "Cantonese", offlineAvailable = false, offlineDownloaded = false),
            ),
        )

    override suspend fun setLastUsed(
        languageId: String,
        atMillis: Long,
    ) = Unit
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
