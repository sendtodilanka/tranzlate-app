package com.codeboxlk.tranzlate.feature.text

import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

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

    private class FakeLanguageRepository(
        /** Shared call journal — proves cross-fake ordering (stamp before choice write). */
        private val journal: MutableList<String>? = null,
    ) : LanguageRepository {
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
            journal?.add("stamp:$languageId")
        }
    }

    /**
     * The selection store as the picker sees it — same interface the composer's
     * `TextViewModel` injects, so "same repository methods" is literal here.
     * Values are stored RAW on purpose: canonicalising is the production
     * implementation's job (write side) and this ViewModel's job (read side),
     * and a fake that quietly fixed ids would hide a regression in either.
     */
    private class FakeTranslatePrefs(
        private val journal: MutableList<String>? = null,
    ) : TranslatePrefsRepository {
        val source = MutableStateFlow("en")
        val target = MutableStateFlow("fr")

        override val sourceLang: Flow<String> = source
        override val targetLang: Flow<String> = target
        override val textMode: Flow<ModeId> = MutableStateFlow(ModeId.AUTO)

        override suspend fun setSourceLang(id: String) {
            source.value = id
            journal?.add("source:$id")
        }

        override suspend fun setTargetLang(id: String) {
            target.value = id
            journal?.add("target:$id")
        }

        override suspend fun setLanguagePair(
            sourceId: String,
            targetId: String,
        ) {
            source.value = sourceId
            target.value = targetId
            journal?.add("pair:$sourceId>$targetId")
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

    /** Stands in for ML Kit never answering — the flow that emits nothing, ever. */
    private class SilentModelManager : OfflineModelManager {
        override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

        override suspend fun download(languageTag: String) = Unit

        override suspend fun delete(languageTag: String) = Unit
    }

    private class FakeDownloadPrefs : DownloadPrefsRepository {
        val state = MutableStateFlow(false)

        override val allowMobileData: Flow<Boolean> get() = state

        override suspend fun setAllowMobileData(value: Boolean) {
            state.value = value
        }
    }

    private val journal = mutableListOf<String>()
    private val repository = FakeLanguageRepository(journal)
    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefs()
    private val translatePrefs = FakeTranslatePrefs(journal)
    private val clock = FakeClock()

    /**
     * The choice write runs on an APPLICATION-lifetime scope, not the VM's —
     * selecting pops the screen and would otherwise cancel the write mid-flight.
     * `backgroundScope` is the test equivalent: it outlives the subject and is
     * torn down with the test, so a write launched there still completes under
     * virtual time.
     */
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())

    @After
    fun stopAppScope() = appScope.cancel()

    private fun viewModel() = viewModelWith(appScope)

    private fun viewModelWith(appScope: CoroutineScope) =
        LanguagePickerViewModel(
            languageRepository = repository,
            clock = clock,
            modelManager = manager,
            connectivity = connectivity,
            downloadPrefs = prefs,
            translatePrefs = translatePrefs,
            appScope = appScope,
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
     * flows and not one `combine`. The manager here is genuinely SILENT (the
     * `awaitCancellation` pattern from LanguageRepositoryImplTest): the earlier
     * version of this test used a fake that emitted at once, so it passed
     * whether or not the property in its name held (issue #123 item 4).
     */
    @Test
    fun `a silent model-state source does not stall the catalog`() =
        runTest(dispatcher) {
            val vm =
                LanguagePickerViewModel(
                    languageRepository = repository,
                    clock = clock,
                    modelManager = SilentModelManager(),
                    connectivity = connectivity,
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    appScope = appScope,
                )
            vm.languages.test {
                assertThat(awaitItem()).isEmpty() // pre-emission frame
                assertThat(awaitItem().map { it.id }).containsExactly("en", "fr").inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting stamps the pick as last used under the picked role`() =
        runTest(dispatcher) {
            viewModel().select("fr", LanguageRole.TARGET)
            runCurrent()
            assertThat(repository.lastUsed)
                .containsExactly(Triple("fr", LanguageRole.TARGET, clock.nowMillis()))
        }

    @Test
    fun `a source-side selection carries the SOURCE role`() =
        runTest(dispatcher) {
            viewModel().select("en", LanguageRole.SOURCE)
            runCurrent()
            assertThat(repository.lastUsed)
                .containsExactly(Triple("en", LanguageRole.SOURCE, clock.nowMillis()))
        }

    /** "auto" is a Translator sentinel, not a catalog row — stamping it would write a ghost. */
    @Test
    fun `selecting Detect language stamps nothing but still writes the choice`() =
        runTest(dispatcher) {
            viewModel().select(DETECT_LANGUAGE_ID, LanguageRole.SOURCE)
            runCurrent()
            assertThat(repository.lastUsed).isEmpty()
            assertThat(translatePrefs.source.value).isEqualTo(DETECT_LANGUAGE_ID)
        }

    // ---- #130 rev.3 decouple (#123.2): the picker owns its selection ---------

    /** The write goes through the SAME repository methods the composer's writes use. */
    @Test
    fun `selecting writes the choice through the shared prefs repository - per role`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.select("fr", LanguageRole.SOURCE)
            runCurrent()
            vm.select("en", LanguageRole.TARGET)
            runCurrent()

            assertThat(translatePrefs.source.value).isEqualTo("fr")
            assertThat(translatePrefs.target.value).isEqualTo("en")
            assertThat(journal).containsAtLeast("source:fr", "target:en").inOrder()
        }

    /**
     * One tap, one coroutine, fixed order: the Recent stamp lands BEFORE the
     * choice write that closes the screen, so the stamp cannot race behind it
     * (two independent launches gave no such guarantee).
     */
    @Test
    fun `the recents stamp lands before the choice write`() =
        runTest(dispatcher) {
            viewModel().select("fr", LanguageRole.TARGET)
            runCurrent()

            assertThat(journal).containsExactly("stamp:fr", "target:fr").inOrder()
        }

    @Test
    fun `selection serves each role its own choice`() =
        runTest(dispatcher) {
            translatePrefs.source.value = "de"
            translatePrefs.target.value = "ja"
            val vm = viewModel()

            vm.selection(LanguageRole.SOURCE).test {
                skipItems(1) // defaults-table frame — WhileSubscribed starts on first collect
                assertThat(awaitItem()).isEqualTo("de")
                cancelAndIgnoreRemainingEvents()
            }
            vm.selection(LanguageRole.TARGET).test {
                skipItems(1)
                assertThat(awaitItem()).isEqualTo("ja")
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The #123.2 trigger, closed from the READ side: a preference persisted
     * before write-side canonicalisation (issue #119) can still hold the
     * detector's own spelling. Served raw, `"iw" != "he"` unticks the whole
     * radio group while the composer chip reads "Hebrew" — the screen
     * contradicting itself. The selection flow resolves through
     * `LanguageTagResolver`, so the Hebrew ROW (real row builder, real
     * compare) now ticks.
     */
    @Test
    fun `a raw legacy id in prefs ticks the canonical row`() =
        runTest(dispatcher) {
            translatePrefs.target.value = "iw"
            val vm = viewModel()

            vm.selection(LanguageRole.TARGET).test {
                skipItems(1) // defaults-table frame
                val selectedId = awaitItem()
                assertThat(selectedId).isEqualTo("he")

                val hebrew = Language("he", "Hebrew", offlineAvailable = true, offlineDownloaded = false)
                val rows =
                    buildPickerRows(
                        languages = listOf(hebrew),
                        modelStates = emptyMap(),
                        selectedId = selectedId,
                        locale = Locale.ENGLISH,
                    )
                assertThat(rows.single().state).isInstanceOf(LanguageRowState.Selected::class.java)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The "auto" sentinel is not a language: the read-side resolver must pass it through. */
    @Test
    fun `the Detect sentinel survives the read-side canonicaliser`() =
        runTest(dispatcher) {
            translatePrefs.source.value = DETECT_LANGUAGE_ID
            val vm = viewModel()

            vm.selection(LanguageRole.SOURCE).test {
                skipItems(1) // defaults-table frame
                assertThat(awaitItem()).isEqualTo(DETECT_LANGUAGE_ID)
                cancelAndIgnoreRemainingEvents()
            }
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

    /**
     * The durability rule, pinned. Selecting pops the screen and the nav
     * decorator clears this ViewModel's store — cancelling `viewModelScope`.
     * DataStore's `edit` runs its transform in the CALLER's context, so a write
     * launched on that scope is DROPPED and the composer keeps the old language.
     * Launching on the application scope is what survives it.
     *
     * The mutation this is the red bar for: put the write back on
     * `viewModelScope` and this test fails while every other test still passes —
     * they all drive the coroutine to completion and never simulate the pop.
     */
    @Test
    fun `a choice survives the picker ViewModel being cleared on pop`() =
        runTest(dispatcher) {
            val vmScopeCancelled = CoroutineScope(dispatcher + SupervisorJob())
            val subject = viewModelWith(appScope = vmScopeCancelled)

            subject.select("de", LanguageRole.TARGET)
            // The screen closes the instant the tap lands. This is what the nav
            // decorator's store-clear does to whatever scope the write is on —
            // so if `select` used viewModelScope, this cancel would drop it.
            vmScopeCancelled.cancel()
            advanceUntilIdle()

            // Written on a scope that DIED: the choice is gone. This half proves
            // the failure mode is real, not theoretical.
            assertThat(translatePrefs.targetLang.first()).isNotEqualTo("de")

            // And on a surviving application scope, the same call commits.
            viewModel().select("es", LanguageRole.TARGET)
            advanceUntilIdle()
            assertThat(translatePrefs.targetLang.first()).isEqualTo("es")
        }
}
