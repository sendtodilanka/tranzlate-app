package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.FakeLanguageUsageRepository
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.InMemoryConsentQuestionStore
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Manage packs' ViewModel seams (#130 PR-23, the rewrite of Screen B). The pure
 * classification, nudge, storage and usage LOGIC is pinned in
 * `ManagePacksModelTest`; what is left to hold here is the WIRING — that the four
 * brains reach `uiState`, that the consent taps reach the gate, and that the
 * remove flow behaves exactly as PR-19 built it (it never writes a language
 * preference).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLanguagesViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefsRepository()

    /**
     * The language SELECTION, read-only from this screen's point of view. It is
     * here so the remove flow can be asked which pack is the live target — and the
     * assertions below check that it is never WRITTEN, the whole PR-19 correction.
     */
    private val translatePrefs = RecordingTranslatePrefsRepository(target = "fr")
    private val translations = FakeTranslationRepository()
    private val usage = FakeLanguageUsageRepository()
    private val storage = FakeStorageProbe()
    private val clock = FakeClock()
    private val handle = SavedStateHandle()

    /**
     * The standing-preference write runs on the APPLICATION scope, for the reason
     * `LanguagePickerViewModelTest` gives: a preference the user just changed must
     * not be dropped because they walked off the screen.
     */
    private val appScope = CoroutineScope(dispatcherRule.dispatcher + SupervisorJob())

    private lateinit var viewModel: OfflineLanguagesViewModel

    @After
    fun stopAppScope() = appScope.cancel()

    @Before
    fun setUp() {
        viewModel = buildViewModel()
    }

    /**
     * The DownloadGate always drives the recording [manager] so the consent tests
     * observe its downloads, even when the ViewModel under test is handed a silent
     * or scripted model manager to script the row states.
     */
    private fun buildViewModel(
        languageRepository: LanguageRepository = StaticLanguageRepository(),
        modelManager: OfflineModelManager = manager,
        handle: SavedStateHandle = this.handle,
    ): OfflineLanguagesViewModel =
        OfflineLanguagesViewModel(
            languageRepository = languageRepository,
            modelManager = modelManager,
            downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
            downloadPrefs = prefs,
            translatePrefs = translatePrefs,
            translations = translations,
            usageRepository = usage,
            storageProbe = storage,
            clock = clock,
            handle = handle,
            dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
            appScope = appScope,
        )

    // ── uiState wiring: the four brains reach the snapshot ─────────────────────

    /**
     * Issue #130 PR-2 carried through: the forever-Loading dead end. `combine`
     * waits for EVERY source, so a manager whose ML Kit answer never comes (no Play
     * Services) must not hold the rows hostage — the `onStart` guard makes the
     * catalogue paint at its resting state.
     */
    @Test
    fun `silent manager - rows still paint the offline catalog at resting state`() =
        runTest {
            val viewModel = buildViewModel(modelManager = SilentModelManager(), handle = SavedStateHandle())
            viewModel.uiState.launchIn(backgroundScope)
            runCurrent()

            assertThat(viewModel.uiState.value.rows)
                .containsExactly(
                    OfflineLanguageRow(id = "de", name = "German", state = OfflineModelState.NotDownloaded),
                    OfflineLanguageRow(id = "fr", name = "French", state = OfflineModelState.NotDownloaded),
                ).inOrder()
            assertThat(viewModel.uiState.value.loading).isFalse()
        }

    @Test
    fun `late manager answer flips the resting rows to the real states`() =
        runTest {
            val scripted = ScriptedModelManager()
            val viewModel = buildViewModel(modelManager = scripted, handle = SavedStateHandle())
            viewModel.uiState.launchIn(backgroundScope)
            runCurrent()

            assertThat(
                viewModel.uiState.value.rows
                    .map(OfflineLanguageRow::state),
            ).containsExactly(OfflineModelState.NotDownloaded, OfflineModelState.NotDownloaded)

            scripted.states.emit(mapOf("de" to OfflineModelState.Downloaded, "fr" to OfflineModelState.Downloading))
            runCurrent()

            assertThat(viewModel.uiState.value.rows)
                .containsExactly(
                    OfflineLanguageRow(id = "de", name = "German", state = OfflineModelState.Downloaded),
                    OfflineLanguageRow(id = "fr", name = "French", state = OfflineModelState.Downloading),
                ).inOrder()
        }

    @Test
    fun `online-only languages never appear - even before the manager answers`() =
        runTest {
            val viewModel =
                buildViewModel(
                    languageRepository = MixedTierLanguageRepository(),
                    modelManager = SilentModelManager(),
                    handle = SavedStateHandle(),
                )
            viewModel.uiState.launchIn(backgroundScope)
            runCurrent()

            // D-E2: Manage packs = catalog ∩ MLKit-capable. The resting default must
            // not smuggle online-only rows in while the state map is still empty.
            assertThat(
                viewModel.uiState.value.rows
                    .map(OfflineLanguageRow::id),
            ).containsExactly("de")
        }

    /**
     * The storage probe reaches the card, count and bytes and all. Scripted so one
     * pack is on device (count 1), and the probe answers a real byte figure, so the
     * card is Sized with exactly what the probe said. A ViewModel that never called
     * the probe, or built the count off the wrong set, reddens here.
     */
    @Test
    fun `the storage probe reaches the card`() =
        runTest {
            storage.packs = 110L
            storage.free = 900L
            storage.total = 1000L
            val scripted = ScriptedModelManager()
            val viewModel = buildViewModel(modelManager = scripted, handle = SavedStateHandle())
            viewModel.uiState.launchIn(backgroundScope)
            runCurrent() // subscribe before emitting into the replay-0 SharedFlow
            scripted.states.emit(mapOf("de" to OfflineModelState.Downloaded, "fr" to OfflineModelState.NotDownloaded))
            advanceUntilIdle()

            val card = viewModel.uiState.value.storage
            assertThat(card).isInstanceOf(StorageCard.Sized::class.java)
            assertThat((card as StorageCard.Sized).packCount).isEqualTo(1)
            assertThat(card.packsBytes).isEqualTo(110L)
        }

    /**
     * A usage stamp reaches `uiState.usage`, merged across roles. The stamp is
     * written as a TARGET use; a ViewModel that only read the source role, or read
     * neither, reddens.
     */
    @Test
    fun `a translation-use stamp reaches uiState usage`() =
        runTest {
            usage.stampUse("de", LanguageRole.TARGET, atMillis = 1234L)
            viewModel.uiState.launchIn(backgroundScope)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.usage).containsEntry("de", 1234L)
        }

    /** "Not now" on the nudge sets a durable flag the screen reads to hide it. Mutation: dismiss does nothing. */
    @Test
    fun `dismissing the nudge sets the durable flag`() =
        runTest {
            viewModel.uiState.launchIn(backgroundScope)
            runCurrent()
            assertThat(viewModel.uiState.value.nudgeDismissed).isFalse()

            viewModel.dismissNudge()
            runCurrent()

            assertThat(viewModel.uiState.value.nudgeDismissed).isTrue()
        }

    // ── Consent gate wiring (#90) ──────────────────────────────────────────────

    /**
     * The whole route in one pass: a metered tap reaches the gate (dialog up,
     * nothing downloaded), and the dialog's yes reaches it too.
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

    @Test
    fun `unticking Always ask grants the standing permission here too`() =
        runTest {
            connectivity.metered = true

            viewModel.onAlwaysAskChange(false)
            runCurrent()
            assertThat(prefs.allowMobileData.first()).isTrue()

            viewModel.download("de")
            runCurrent()
            assertThat(manager.downloads).containsExactly("de")
            assertThat(viewModel.pendingConsent.value).isNull()
        }

    @Test
    fun `re-ticking Always ask revokes the standing permission here too`() =
        runTest {
            connectivity.metered = true
            prefs.state.value = true

            viewModel.onAlwaysAskChange(true)
            runCurrent()
            assertThat(prefs.allowMobileData.first()).isFalse()

            viewModel.download("de")
            runCurrent()
            assertThat(manager.downloads).isEmpty()
            assertThat(viewModel.pendingConsent.value).isEqualTo("de")
        }

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

    // ── Retry honesty: a synchronous refusal reaches the screen (#234/#250) ─────

    /**
     * The other half of the #250 fix (the first is that the STORAGE row keeps a
     * Retry pill at all — an `OfflineLanguagesScreen` render test). A Retry whose
     * disk is still full is REFUSED synchronously (`DownloadAttempt.Refused(STORAGE)`),
     * which writes a value-equal `Failed(STORAGE)` map (no re-emit) and fires no
     * PackEvent — so a discarded return leaves the retry a silent no-op behind an
     * enabled pill. Captured, it reaches [refusals] for the screen's snackbar.
     *
     * Mutation decided first: revert `download()` to
     * `viewModelScope.launch { withContext(io) { downloadGate.requestDownload(id) } }`
     * (discard the return, drop the `reportOutcome` capture) — `received` is empty
     * and this reddens.
     */
    @Test
    fun `a retry refused for space surfaces a message, not silence`() =
        runTest {
            manager.attempt = DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            val received = mutableListOf<OfflineModelFailure>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.download("de")
            advanceUntilIdle()

            assertThat(received).containsExactly(OfflineModelFailure.STORAGE)
        }

    /**
     * Non-vacuity for the test above: the message is tied to a REFUSAL, not emitted
     * on every tap. A download that STARTS surfaces nothing here — its outcome
     * travels through the row and the U-1 PackEvents app snackbar instead. Mutation:
     * report unconditionally in `reportOutcome` (drop the `is Refused` guard) and
     * this reddens.
     */
    @Test
    fun `a download that starts surfaces no refusal message`() =
        runTest {
            manager.attempt = DownloadAttempt.Started
            val received = mutableListOf<OfflineModelFailure>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.download("de")
            advanceUntilIdle()

            assertThat(received).isEmpty()
        }

    /**
     * The second capture site: "Download now" on a metered link runs
     * `downloadConsented`, which can also come back `Refused`. Mutation: discard the
     * return in `downloadAnyway()` and this reddens while the row-tap test above
     * stays green — the two sites fail independently.
     */
    @Test
    fun `a consented retry refused for space also surfaces a message`() =
        runTest {
            connectivity.metered = true
            manager.attempt = DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            val received = mutableListOf<OfflineModelFailure>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.download("de") // metered → consent sheet, nothing downloaded yet
            runCurrent()
            viewModel.downloadAnyway() // "Download now" → downloadConsented → Refused
            advanceUntilIdle()

            assertThat(received).containsExactly(OfflineModelFailure.STORAGE)
        }

    // ── Remove flow (#130 PR-19), unchanged ────────────────────────────────────

    /** Mutation C1: wire the overflow straight back to the manager and this reddens. */
    @Test
    fun `the overflow asks and deletes nothing yet`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()

            assertThat(manager.deletes).isEmpty()
            assertThat(viewModel.pendingRemoval.value?.id).isEqualTo("de")
        }

    /** Mutation C2: Cancel must not be a delete. */
    @Test
    fun `cancelling removes nothing and closes the question`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            viewModel.requestRemove("de")
            runCurrent()

            viewModel.dismissRemove()
            runCurrent()

            assertThat(manager.deletes).isEmpty()
            assertThat(viewModel.pendingRemoval.value).isNull()
        }

    /** Mutations C3 + C4: confirm deletes exactly once AND closes the sheet. */
    @Test
    fun `confirming removes the pack once and closes the question`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            viewModel.requestRemove("de")
            runCurrent()

            viewModel.confirmRemove()
            runCurrent()

            assertThat(manager.deletes).containsExactly("de")
            assertThat(viewModel.pendingRemoval.value).isNull()
        }

    @Test
    fun `confirming twice removes the pack once`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            viewModel.requestRemove("de")
            runCurrent()

            viewModel.confirmRemove()
            viewModel.confirmRemove()
            runCurrent()

            assertThat(manager.deletes).containsExactly("de")
        }

    /**
     * Mutation C5. The ⏹ on a DOWNLOADING row is delete-to-cancel and stays
     * immediate — the way out of a download, not the removal of a pack the user
     * has. Routing it through the confirm sheet reddens here.
     */
    @Test
    fun `stopping a download still happens immediately with no sheet`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.stopDownload("de")
            runCurrent()

            assertThat(manager.deletes).containsExactly("de")
            assertThat(viewModel.pendingRemoval.value).isNull()
        }

    @Test
    fun `only the live target raises the in-use sheet`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()
            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()

            viewModel.requestRemove("fr")
            runCurrent()
            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    @Test
    fun `the source language is not in use in the sense 19g means`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("en")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()
        }

    @Test
    fun `a target changed after the screen was built still decides the sheet`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            runCurrent()

            translatePrefs.target.value = "de"
            viewModel.requestRemove("de")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    @Test
    fun `a second question is answered against the target as it is now`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()
            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()
            viewModel.dismissRemove()
            runCurrent()

            translatePrefs.target.value = "de"
            viewModel.requestRemove("de")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    @Test
    fun `an open question follows the target if it moves underneath it`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()
            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()

            translatePrefs.target.value = "de"
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    /**
     * The correction PR-19 exists for: removing a pack writes NO language
     * preference. The recording repository fails if any write arrives.
     */
    @Test
    fun `removing the in-use pack changes no language selection`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()
            viewModel.confirmRemove()
            runCurrent()

            assertThat(manager.deletes).containsExactly("fr")
            assertThat(translatePrefs.writes).isEmpty()
            assertThat(translatePrefs.target.value).isEqualTo("fr")
        }

    @Test
    fun `the in-use sheet counts saved phrases on either side of the pair`() =
        runTest {
            translations.seed(
                saved(id = 1, source = "en", target = "fr"),
                saved(id = 2, source = "fr", target = "en"),
                saved(id = 3, source = "fr", target = "fr"),
                saved(id = 4, source = "en", target = "de"),
                unsaved(id = 5, source = "en", target = "fr"),
            )
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.savedCount).isEqualTo(3)
        }

    @Test
    fun `an ordinary removal reports the saved count too`() =
        runTest {
            translations.seed(
                saved(id = 1, source = "en", target = "de"),
                saved(id = 2, source = "de", target = "en"),
                unsaved(id = 3, source = "en", target = "de"),
                saved(id = 4, source = "en", target = "fr"),
            )
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()
            assertThat(viewModel.pendingRemoval.value?.savedCount).isEqualTo(2)
        }

    @Test
    fun `an open question survives the ViewModel and is re-derived`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            viewModel.requestRemove("de")
            runCurrent()

            translatePrefs.target.value = "de"
            val reborn = buildViewModel(handle = handle)
            reborn.pendingRemoval.launchIn(backgroundScope)
            runCurrent()

            assertThat(reborn.pendingRemoval.value?.id).isEqualTo("de")
            assertThat(reborn.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    @Test
    fun `a broken saved-count query still lets the sheet open`() =
        runTest {
            translations.beforeSavedCount = { error("database disk image is malformed") }
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
            assertThat(viewModel.pendingRemoval.value?.savedCount).isEqualTo(0)
        }

    /**
     * The sibling that was missing (#236): an `UnsatisfiedLinkError` is an `Error`,
     * NOT an `Exception`, so a narrow catch would let the app disappear on the
     * trash tap. The widened `Throwable` catch keeps the sheet opening.
     */
    @Test
    fun `a saved-count query that fails to link still lets the sheet open`() =
        runTest {
            translations.beforeSavedCount = { throw UnsatisfiedLinkError("nativeExecute") }
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isTrue()
            assertThat(viewModel.pendingRemoval.value?.savedCount).isEqualTo(0)
        }

    /**
     * `removalFor`'s `.distinctUntilChanged()` on the target (#242): an unrelated
     * preference write re-fires `targetLang` with the SAME target, and the guard
     * swallows the equal re-emission so the saved-count query does not re-run under
     * an open sheet. Mutation: delete `.distinctUntilChanged()` — the count runs
     * twice and the second assertion reddens.
     */
    @Test
    fun `an unrelated write that re-fires the same target does not re-run the saved count`() =
        runTest {
            translations.seed(saved(id = 1, source = "en", target = "fr"))
            var savedCountCalls = 0
            translations.beforeSavedCount = { savedCountCalls++ }
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()
            assertThat(savedCountCalls).isEqualTo(1)

            translatePrefs.reEmitTarget()
            runCurrent()

            assertThat(savedCountCalls).isEqualTo(1)
        }

    /**
     * `savedCountOf`'s `CancellationException` rethrow (#242): folding a
     * cancellation to 0 would publish a false zero-count sheet. Mutation: delete the
     * `catch (CancellationException) { throw }` arm — `published` then contains it.
     */
    @Test
    fun `a cancelled saved-count query is not folded to a zero-count sheet`() =
        runTest {
            translations.seed(saved(id = 1, source = "en", target = "fr"))
            translations.beforeSavedCount = { throw CancellationException("torn down mid-count") }
            val published = mutableListOf<PendingPackRemoval?>()
            viewModel.pendingRemoval.onEach { published += it }.launchIn(backgroundScope)

            viewModel.requestRemove("fr")
            runCurrent()

            assertThat(published)
                .doesNotContain(PendingPackRemoval(id = "fr", inUseAsTarget = true, savedCount = 0))
        }

    private fun saved(
        id: Long,
        source: String,
        target: String,
    ) = row(id, source, target, favourite = true)

    private fun unsaved(
        id: Long,
        source: String,
        target: String,
    ) = row(id, source, target, favourite = false)

    private fun row(
        id: Long,
        source: String,
        target: String,
        favourite: Boolean,
    ) = Translation(
        id = id,
        sourceLang = source,
        sourceText = "phrase $id",
        targetLang = target,
        targetText = "answer $id",
        engine = Engine.OFFLINE_MLKIT,
        favourite = favourite,
        createdAt = id,
    )
}

/**
 * A [TranslatePrefsRepository] that answers reads and RECORDS every write. The
 * recording half is the point: PR-19's whole correction is that removing a pack
 * changes no language selection, held only by failing when a write arrives.
 */
private class RecordingTranslatePrefsRepository(
    source: String = "en",
    target: String,
) : TranslatePrefsRepository {
    val sourceState = MutableStateFlow(source)
    val target = MutableStateFlow(target)
    val writes = mutableListOf<String>()

    private val targetReemits =
        MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val sourceLang: Flow<String> get() = sourceState

    override val targetLang: Flow<String> get() = merge(target, targetReemits)

    override val textMode: Flow<ModeId> = flowOf(ModeId.AUTO)

    /** An unrelated DataStore write: re-emit the CURRENT target unchanged. */
    fun reEmitTarget() {
        targetReemits.tryEmit(target.value)
    }

    override suspend fun setSourceLang(id: String) {
        writes += "source=$id"
    }

    override suspend fun setTargetLang(id: String) {
        writes += "target=$id"
    }

    override suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    ) {
        writes += "pair=$sourceId/$targetId"
    }
}

private class RecordingModelManager : OfflineModelManager {
    val downloads = mutableListOf<String>()
    val deletes = mutableListOf<String>()

    /** What `download()` decides. Default [DownloadAttempt.Started]; the refusal tests script a [Refused]. */
    var attempt: DownloadAttempt = DownloadAttempt.Started

    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        flowOf(
            mapOf(
                "de" to OfflineModelState.NotDownloaded,
                "fr" to OfflineModelState.NotDownloaded,
            ),
        )

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // never emits in this test

    override suspend fun download(languageTag: String): DownloadAttempt {
        downloads += languageTag
        return attempt
    }

    override suspend fun delete(languageTag: String) {
        deletes += languageTag
    }
}

/** No Play Services: the ML Kit answer NEVER comes — the flow just hangs. */
private class SilentModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // never emits in this test

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

    override suspend fun delete(languageTag: String) = Unit
}

/** Emits nothing until the test scripts an answer — models a slow first ML Kit round-trip. */
private class ScriptedModelManager : OfflineModelManager {
    val states = MutableSharedFlow<Map<String, OfflineModelState>>()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = states

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // never emits in this test

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

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

    override fun recentSelections(role: LanguageRole): Flow<Map<String, Long>> = flowOf(emptyMap())

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

    override fun recentSelections(role: LanguageRole): Flow<Map<String, Long>> = flowOf(emptyMap())

    override suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) = Unit
}
