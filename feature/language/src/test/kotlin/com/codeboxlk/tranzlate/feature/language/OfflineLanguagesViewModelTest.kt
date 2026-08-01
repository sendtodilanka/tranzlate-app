package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Screen B's own seams. The issue-#90 consent RULE is no longer proved here —
 * it lives in `DownloadGate` with one matrix over it (`DownloadGateTest`), so
 * what is left to pin is the wiring: that this screen's taps actually reach
 * that gate and that its dialog is the gate's own question.
 */
class OfflineLanguagesViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefsRepository()
    private lateinit var viewModel: OfflineLanguagesViewModel

    @Before
    fun setUp() {
        viewModel =
            OfflineLanguagesViewModel(
                languageRepository = StaticLanguageRepository(),
                modelManager = manager,
                downloadGate = DownloadGate(connectivity, prefs, manager),
            )
    }

    /**
     * The whole route in one pass: a metered tap reaches the gate (dialog up,
     * nothing downloaded), and the dialog's yes reaches it too (downloaded,
     * dialog gone). Wiring the row's ⬇ straight to the manager, or exposing a
     * `pendingConsent` of the screen's own, is red here.
     */
    @Test
    fun `row taps and the dialog answer are routed through the gate`() =
        runTest {
            connectivity.metered = true

            viewModel.download("de")
            runCurrent()
            assertThat(manager.downloads).isEmpty()
            assertThat(viewModel.pendingConsent.value).isEqualTo("de")

            viewModel.downloadAnyway()
            runCurrent()
            assertThat(manager.downloads).containsExactly("de")
            assertThat(viewModel.pendingConsent.value).isNull()
        }

    /** "Wait for Wi-Fi" closes the gate's question from this screen too. */
    @Test
    fun `dismissing the dialog reaches the gate`() =
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
                    downloadGate = DownloadGate(connectivity, prefs, manager),
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
                    downloadGate = DownloadGate(connectivity, prefs, manager),
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
                    downloadGate = DownloadGate(connectivity, prefs, manager),
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
        role: LanguageRole,
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
