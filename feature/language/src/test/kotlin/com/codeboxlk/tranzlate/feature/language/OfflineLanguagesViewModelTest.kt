package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.InMemoryConsentQuestionStore
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    /**
     * The language SELECTION, read-only from this screen's point of view. It is
     * here so the remove flow can be asked which pack is the live target — and
     * the assertions below check that it is never WRITTEN, which is the whole
     * correction PR-19 makes to the drawn 19g.
     */
    private val translatePrefs = RecordingTranslatePrefsRepository(target = "fr")
    private val translations = FakeTranslationRepository()
    private val handle = SavedStateHandle()

    /**
     * The standing-preference write runs on the APPLICATION scope, for the
     * reason `LanguagePickerViewModelTest` gives: a preference the user just
     * changed must not be dropped because they walked off the screen. Same shape
     * here so the two screens' consent behaviour is tested the same way.
     */
    private val appScope = CoroutineScope(dispatcherRule.dispatcher + SupervisorJob())

    private lateinit var viewModel: OfflineLanguagesViewModel

    @After
    fun stopAppScope() = appScope.cancel()

    @Before
    fun setUp() {
        viewModel =
            OfflineLanguagesViewModel(
                languageRepository = StaticLanguageRepository(),
                modelManager = manager,
                downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                downloadPrefs = prefs,
                translatePrefs = translatePrefs,
                translations = translations,
                handle = handle,
                dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
                appScope = appScope,
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

    /**
     * The SAME sheet is raised here as on the picker, so it must be answered the
     * same way — including the polarity of its checkbox. This screen having its
     * own idea of what "Always ask" means is precisely the drift that made two
     * dialogs worth deleting.
     */
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

    /** "Not now" closes the gate's question from this screen too. */
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
                    downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    translations = translations,
                    handle = SavedStateHandle(),
                    dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
                    appScope = appScope,
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
                    downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    translations = translations,
                    handle = SavedStateHandle(),
                    dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
                    appScope = appScope,
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
                    downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    translations = translations,
                    handle = SavedStateHandle(),
                    dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
                    appScope = appScope,
                )
            viewModel.rows.launchIn(backgroundScope)
            runCurrent()

            // D-E2: Screen B = catalog ∩ MLKit-capable. The resting default must
            // not smuggle online-only rows in while the state map is still empty.
            assertThat(viewModel.rows.value.map(OfflineLanguageRow::id)).containsExactly("de")
        }

    // ---- #130 PR-19: the 🗑 asks first (sheets 19f / 19g) ---------------------------------------

    /** Mutation C1: wire 🗑 straight back to the manager and this reddens. */
    @Test
    fun `the bin asks and deletes nothing yet`() =
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

    /**
     * A second confirm on an answered sheet finds nothing to do — the id is read
     * from the durable handle, not carried in the call.
     */
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
     * immediate: it is the way out of a download, not the removal of a pack the
     * user has. Routing it through the confirm sheet reddens here.
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

    /**
     * Mutations B2/B3: which sheet gets drawn. `fr` is the target in this
     * fixture and `de` is not, so a rule stuck at either constant reddens on one
     * of these two assertions.
     */
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

    /**
     * Mutation B1: `inUse = (source == id)`. The fixture's source is `en` and is
     * NOT the target, so a rule that reads the wrong side of the pair reddens.
     * Removing the pack of the language you translate FROM is an ordinary
     * removal — 19f, which says everything true about it.
     */
    @Test
    fun `the source language is not in use in the sense 19g means`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("en")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.inUseAsTarget).isFalse()
        }

    /**
     * Mutation B4: reading the target once at construction. The user can change
     * their target on another screen while this one is in the back stack, so the
     * question has to be answered against the live preference.
     */
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

    /**
     * Mutation B4, second form — and the one that found the gap.
     *
     * The test above raises its question AFTER the target moves, so a
     * ViewModel that snapshots the target the first time it is asked and caches
     * it forever passes it: the first ask already sees the new value. That
     * mutation SURVIVED until this case was added, which is the whole reason the
     * mutation is decided before the test rather than after.
     *
     * The harm it leaves is a wrong sheet on the SECOND removal of a session:
     * ask about one pack, cancel, change the target elsewhere, ask about the new
     * target — and the sheet says the pack is not in use. Asserting across two
     * questions is what separates a live flow from a cached first answer.
     */
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

    /**
     * The target can only move from another screen, and if it moves while this
     * question is open the sheet has to stop saying the old thing. A live flow
     * gives that for nothing; a snapshot taken when the sheet opened does not.
     */
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
     * **The correction this PR exists for.** The export's 19g says *"Removing it
     * switches the target to English."* Nothing switches, so nothing may write a
     * language preference from this flow — not the confirm, not the request, not
     * the dismiss. The recording repository fails the test if any write arrives,
     * which is the assertion that would have caught the drawn behaviour being
     * built to match the drawn copy.
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

    /**
     * The saved count, and the two mutations around it: dropping the
     * `favourite` filter, and dropping either side of the language test. The
     * fixture is deliberately ASYMMETRIC — one row uses `fr` only as a target,
     * one only as a source, one on both sides, one is unsaved, one is another
     * language — because a fixture where every row uses the language both ways
     * lets a source-only or target-only rule pass.
     */
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

    /** 19f draws no saved line, so the ordinary removal must not pay for the query. */
    @Test
    fun `an ordinary removal reports no saved count`() =
        runTest {
            translations.seed(saved(id = 1, source = "en", target = "de"))
            viewModel.pendingRemoval.launchIn(backgroundScope)

            viewModel.requestRemove("de")
            runCurrent()

            assertThat(viewModel.pendingRemoval.value?.savedCount).isEqualTo(0)
        }

    /**
     * Mutation C6. The question is a `String` in the `SavedStateHandle`, so a
     * ViewModel rebuilt over the SAME handle — which is what a process death
     * followed by a return looks like — still has it open. The derived halves
     * are recomputed rather than restored, which this checks by moving the
     * target while the "dead" ViewModel is gone.
     */
    @Test
    fun `an open question survives the ViewModel and is re-derived`() =
        runTest {
            viewModel.pendingRemoval.launchIn(backgroundScope)
            viewModel.requestRemove("de")
            runCurrent()

            translatePrefs.target.value = "de"
            val reborn =
                OfflineLanguagesViewModel(
                    languageRepository = StaticLanguageRepository(),
                    modelManager = manager,
                    downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    translations = translations,
                    handle = handle,
                    dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
                    appScope = appScope,
                )
            reborn.pendingRemoval.launchIn(backgroundScope)
            runCurrent()

            assertThat(reborn.pendingRemoval.value?.id).isEqualTo("de")
            assertThat(reborn.pendingRemoval.value?.inUseAsTarget).isTrue()
        }

    /**
     * A database that cannot answer must not block a removal. Zero renders as an
     * absent line, which is what a user with nothing saved sees anyway — a
     * missing reassurance rather than a false one.
     */
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
     * The sibling of the test above, and the one that was missing (issue #236).
     *
     * That one throws `IllegalStateException` — an `Exception`, which the old
     * narrow catch already covered — so it passed while the crash class the
     * guard exists for went straight past it. Room here runs every statement
     * through a `native` method on `android.database.sqlite.SQLiteConnection`,
     * and a JNI link that cannot be satisfied raises `UnsatisfiedLinkError`: a
     * `LinkageError`, so an `Error`, so NOT an `Exception`
     * (`TextViewModel.kt:768-779`, verified again in this PR).
     *
     * The user-visible harm this pins: tap the trash icon on the pack for the
     * CURRENT target language and the app disappears — no sheet, no message.
     * `savedCount` is only queried when `inUseAsTarget`, so `fr` is the pack
     * that reproduces it and any other pack is safe.
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
     * `removalFor`'s `.distinctUntilChanged()` on the target, made able to fail
     * (issue #242). DataStore re-emits EVERY key's flow on ANY key write, so an
     * unrelated preference change while a 19g sheet is open re-fires `targetLang`
     * with the SAME target. The guard swallows that equal re-emission; without it
     * the saved-count query re-runs underneath the open sheet on every unrelated
     * write. `reEmitTarget()` is the fixture's model of that unrelated write — and
     * it has to exist, because a `MutableStateFlow` would conflate the equal value
     * away and the guard could not be tested at all (the tautology this issue is
     * about). The call counter uses the existing `beforeSavedCount` hook, which
     * fires once per `savedCountUsing`.
     *
     * Mutation decided first (rule 11): delete `.distinctUntilChanged()`. The
     * second assertion then reads 2 and reddens.
     */
    @Test
    fun `an unrelated write that re-fires the same target does not re-run the saved count`() =
        runTest {
            translations.seed(saved(id = 1, source = "en", target = "fr"))
            var savedCountCalls = 0
            translations.beforeSavedCount = { savedCountCalls++ }
            viewModel.pendingRemoval.launchIn(backgroundScope)

            // fr is this fixture's target, so the sheet is 19g and the count runs — once.
            viewModel.requestRemove("fr")
            runCurrent()
            assertThat(savedCountCalls).isEqualTo(1)

            // An unrelated DataStore write re-fires targetLang with the same "fr".
            translatePrefs.reEmitTarget()
            runCurrent()

            assertThat(savedCountCalls).isEqualTo(1)
        }

    /**
     * `savedCountOf`'s `CancellationException` rethrow, made able to fail (issue
     * #242 — the arm the production KDoc records as "entered by no test").
     *
     * The generic `catch (Throwable) { 0 }` beneath it exists so a database that
     * cannot answer never blocks a removal. But a `CancellationException` is not
     * "the database cannot answer" — it is "we are being torn down" — and folding
     * it to 0 breaks structured concurrency and states a saved count that was
     * never read. Widening the catch to `Throwable` protects it not at all
     * (`CancellationException` IS an `Exception`); only the rethrow does
     * (coroutines-patterns.md — never swallow `CancellationException`; the same
     * guard `TextStarFailureTest` pins for the composer star, the sibling of this
     * fix).
     *
     * The query is thrown into cancellation WHILE IN FLIGHT. With the rethrow the
     * cancellation propagates and no sheet is built from a count that never
     * completed, so this false "nothing saved" sheet for the target pack is never
     * published. Without it the generic catch folds the cancellation to 0 and it
     * is.
     *
     * Mutation decided first (rule 11): delete the
     * `catch (CancellationException) { throw }` arm. `published` then contains the
     * zero-count in-use sheet and reddens.
     */
    @Test
    fun `a cancelled saved-count query is not folded to a zero-count sheet`() =
        runTest {
            translations.seed(saved(id = 1, source = "en", target = "fr"))
            translations.beforeSavedCount = { throw CancellationException("torn down mid-count") }
            val published = mutableListOf<PendingPackRemoval?>()
            viewModel.pendingRemoval.onEach { published += it }.launchIn(backgroundScope)

            // fr is the target → 19g → the count runs, and is cancelled in flight.
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
 * A [TranslatePrefsRepository] that answers reads and RECORDS every write.
 *
 * The recording half is the point. PR-19's whole correction is that removing a
 * pack changes no language selection, and the only way to hold that is to fail
 * when a write arrives — a fake that silently accepted one would let the drawn
 * "switches the target to English" be built back in with every test still green.
 */
private class RecordingTranslatePrefsRepository(
    source: String = "en",
    target: String,
) : TranslatePrefsRepository {
    val sourceState = MutableStateFlow(source)
    val target = MutableStateFlow(target)
    val writes = mutableListOf<String>()

    /**
     * A same-value re-emission seam for [targetLang] (#242).
     *
     * Production's `targetLang` is a DataStore flow, and DataStore re-emits EVERY
     * key's flow whenever ANY key is written — so an unrelated preference change
     * makes `targetLang` fire the CURRENT target again, unchanged, which is what
     * `removalFor`'s `.distinctUntilChanged()` exists to swallow. A `MutableStateFlow`
     * cannot model that: it conflates equal consecutive values by contract
     * (testing-quick.md — a StateFlow conflates), so a StateFlow-only `targetLang`
     * could never re-deliver the same value and deleting the guard would change
     * nothing in any test. This non-conflating channel carries the unrelated-write
     * re-emission ([reEmitTarget]); real target CHANGES still go through [target],
     * which keeps the `.value` read/write the other tests already use.
     */
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

    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        flowOf(
            mapOf(
                "de" to OfflineModelState.NotDownloaded,
                "fr" to OfflineModelState.NotDownloaded,
            ),
        )

    override suspend fun download(languageTag: String): DownloadAttempt {
        downloads += languageTag
        return DownloadAttempt.Started
    }

    override suspend fun delete(languageTag: String) {
        deletes += languageTag
    }
}

/** No Play Services: the ML Kit answer NEVER comes — the flow just hangs. */
private class SilentModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

    override suspend fun delete(languageTag: String) = Unit
}

/** Emits nothing until the test scripts an answer — models a slow first ML Kit round-trip. */
private class ScriptedModelManager : OfflineModelManager {
    val states = MutableSharedFlow<Map<String, OfflineModelState>>()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = states

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
