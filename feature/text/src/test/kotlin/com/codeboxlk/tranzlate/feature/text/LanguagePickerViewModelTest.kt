package com.codeboxlk.tranzlate.feature.text

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The picker's ask-seams: catalog, live model state, the last-used stamp, and
 * the issue-#90 metered-consent gate the redesigned rows inherited along with
 * their download buttons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private class FakeLanguageRepository : LanguageRepository {
        val catalog =
            MutableStateFlow(
                listOf(
                    Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                    Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                ),
            )
        val lastUsed = mutableListOf<Triple<String, LanguageRole, Long>>()

        override fun languages(): Flow<List<Language>> = catalog

        override suspend fun setLastUsed(
            languageId: String,
            role: LanguageRole,
            atMillis: Long,
        ) {
            lastUsed += Triple(languageId, role, atMillis)
        }
    }

    private class RecordingModelManager : OfflineModelManager {
        val states = MutableStateFlow<Map<String, OfflineModelState>>(emptyMap())
        val downloads = mutableListOf<String>()
        val deletes = mutableListOf<String>()

        override fun modelStates(): Flow<Map<String, OfflineModelState>> = states

        override suspend fun download(languageTag: String) {
            downloads += languageTag
        }

        override suspend fun delete(languageTag: String) {
            deletes += languageTag
        }
    }

    private class FakeDownloadPrefs : DownloadPrefsRepository {
        val state = MutableStateFlow(false)

        override val allowMobileData: Flow<Boolean> get() = state

        override suspend fun setAllowMobileData(value: Boolean) {
            state.value = value
        }
    }

    private val repository = FakeLanguageRepository()
    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefs()
    private val clock = FakeClock()

    private fun viewModel() =
        LanguagePickerViewModel(
            languageRepository = repository,
            clock = clock,
            modelManager = manager,
            connectivity = connectivity,
            downloadPrefs = prefs,
        )

    @Test
    fun `catalog is served from the repository`() =
        runTest(dispatcher) {
            viewModel().languages.test {
                assertThat(awaitItem()).isEmpty() // pre-emission frame
                assertThat(awaitItem().map { it.id }).containsExactly("en", "fr").inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `offline states are served live from the model manager`() =
        runTest(dispatcher) {
            viewModel().offlineStates.test {
                assertThat(awaitItem()).isEmpty()
                manager.states.value = mapOf("en" to OfflineModelState.Downloading)
                assertThat(awaitItem()).containsEntry("en", OfflineModelState.Downloading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The list never waits on the badges. A model-state source that never emits
     * must not be able to hold the catalog hostage — which is why this is two
     * flows and not one `combine`.
     */
    @Test
    fun `a silent model-state source does not stall the catalog`() =
        runTest(dispatcher) {
            viewModel().languages.test {
                assertThat(awaitItem()).isEmpty()
                assertThat(awaitItem()).hasSize(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `picking a language stamps it as last used under the picked role`() =
        runTest(dispatcher) {
            viewModel().onLanguagePicked("fr", LanguageRole.TARGET)
            runCurrent()
            assertThat(repository.lastUsed)
                .containsExactly(Triple("fr", LanguageRole.TARGET, clock.nowMillis()))
        }

    @Test
    fun `a source-side pick carries the SOURCE role`() =
        runTest(dispatcher) {
            viewModel().onLanguagePicked("en", LanguageRole.SOURCE)
            runCurrent()
            assertThat(repository.lastUsed)
                .containsExactly(Triple("en", LanguageRole.SOURCE, clock.nowMillis()))
        }

    /** "auto" is a Translator sentinel, not a catalog row — stamping it would write a ghost. */
    @Test
    fun `picking Detect language stamps nothing`() =
        runTest(dispatcher) {
            viewModel().onLanguagePicked(DETECT_LANGUAGE_ID, LanguageRole.SOURCE)
            runCurrent()
            assertThat(repository.lastUsed).isEmpty()
        }

    /**
     * Ruling R6's disconfirming gate at the selection end: a pick writes
     * RECENTS, never translation-usage. The strongest proof is structural —
     * the ViewModel cannot reach the usage store it does not depend on — and
     * this pins that structure so wiring `LanguageUsageRepository` into the
     * picker fails a named test instead of slipping through review.
     */
    @Test
    fun `R6 - the picker has no path to the translation-usage store`() {
        val dependencyTypes =
            LanguagePickerViewModel::class.java.constructors
                .flatMap { it.parameterTypes.toList() }
                .map { it.name }

        assertThat(dependencyTypes).isNotEmpty()
        dependencyTypes.forEach { assertThat(it).doesNotContain("LanguageUsage") }
    }

    // ---- issue #90 consent gate, re-honoured on picker rows ------------------

    @Test
    fun `unmetered network downloads immediately - no dialog`() =
        runTest(dispatcher) {
            connectivity.metered = false
            val vm = viewModel()

            vm.download("fr")
            runCurrent()

            assertThat(manager.downloads).containsExactly("fr")
            assertThat(vm.pendingConsent.value).isNull()
        }

    @Test
    fun `metered without standing consent raises the dialog and does NOT download`() =
        runTest(dispatcher) {
            connectivity.metered = true
            prefs.state.value = false
            val vm = viewModel()

            vm.download("fr")
            runCurrent()

            assertThat(manager.downloads).isEmpty()
            assertThat(vm.pendingConsent.value).isEqualTo("fr")
        }

    @Test
    fun `metered with the standing consent ON downloads without asking`() =
        runTest(dispatcher) {
            connectivity.metered = true
            prefs.state.value = true
            val vm = viewModel()

            vm.download("fr")
            runCurrent()

            assertThat(manager.downloads).containsExactly("fr")
            assertThat(vm.pendingConsent.value).isNull()
        }

    @Test
    fun `Download once downloads this one and leaves the standing pref alone`() =
        runTest(dispatcher) {
            connectivity.metered = true
            val vm = viewModel()
            vm.download("fr")
            runCurrent()

            vm.downloadAnyway()
            runCurrent()

            assertThat(manager.downloads).containsExactly("fr")
            assertThat(vm.pendingConsent.value).isNull()
            assertThat(prefs.state.value).isFalse()
        }

    /** "Wait for Wi-Fi" leaves a re-tappable row, never a stuck spinner. */
    @Test
    fun `dismissing the dialog downloads nothing`() =
        runTest(dispatcher) {
            connectivity.metered = true
            val vm = viewModel()
            vm.download("fr")
            runCurrent()

            vm.dismissConsent()
            runCurrent()

            assertThat(manager.downloads).isEmpty()
            assertThat(vm.pendingConsent.value).isNull()
        }

    /** Plan R2: ML Kit has no cancel, so the row's ✕ is a delete and is named one. */
    @Test
    fun `stop and remove deletes the model`() =
        runTest(dispatcher) {
            viewModel().stopAndRemove("fr")
            runCurrent()
            assertThat(manager.deletes).containsExactly("fr")
        }
}
