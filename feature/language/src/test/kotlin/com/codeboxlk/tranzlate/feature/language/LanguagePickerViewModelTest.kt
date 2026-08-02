package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.InMemoryConsentQuestionStore
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * The picker's ask-seams: catalog, live model state, the last-used stamp, and
 * the routing of the row download buttons into the issue-#90 consent gate the
 * redesigned rows inherited along with them. The consent RULE itself is proved
 * once, in `DownloadGateTest`, where it now lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private val journal = mutableListOf<String>()
    private val repository = FakeLanguageRepository(journal)
    private val manager = PickerModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefsRepository()
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

    /**
     * The meter's storage answers. Default = a fresh install: the model store
     * dir does not exist until the first pack lands, verified on
     * `emulator-5554` (E-S1), so `packs` is null out of the box.
     */
    private val storage = FakeStorageProbe()

    private fun viewModelWith(
        appScope: CoroutineScope,
        handle: SavedStateHandle = SavedStateHandle(),
        probe: com.codeboxlk.tranzlate.core.common.StorageProbe = storage,
    ) = LanguagePickerViewModel(
        languageRepository = repository,
        clock = clock,
        modelManager = manager,
        downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
        downloadPrefs = prefs,
        translatePrefs = translatePrefs,
        storageProbe = probe,
        dispatchers = TestDispatcherProvider(dispatcher),
        savedStateHandle = handle,
        appScope = appScope,
    )

    /**
     * The 16a wiring: the target side's recents section reads the TARGET flow
     * and nothing else. The fake keeps two independent maps for exactly this —
     * a fake serving one map for both roles could not tell a scoped section
     * from a merged one, which is the whole claim the header makes.
     */
    @Test
    fun `each side reads its own recents`() =
        runTest(dispatcher) {
            repository.sourceRecents.value = mapOf("en" to 10L)
            repository.targetRecents.value = mapOf("fr" to 20L)
            val subject = viewModel()

            subject.recents(LanguageRole.TARGET).test {
                assertThat(awaitItem()).isEmpty() // pre-emission frame
                assertThat(awaitItem()).containsExactly("fr", 20L)
            }
            subject.recents(LanguageRole.SOURCE).test {
                assertThat(awaitItem()).isEmpty() // pre-emission frame
                assertThat(awaitItem()).containsExactly("en", 10L)
            }
        }

    /** No target picks yet → the empty map that makes the section ABSENT, not a header over nothing. */
    @Test
    fun `an untouched target side serves an empty recents map`() =
        runTest(dispatcher) {
            repository.sourceRecents.value = mapOf("en" to 10L)

            viewModel().recents(LanguageRole.TARGET).test {
                assertThat(awaitItem()).isEmpty()
                expectNoEvents()
            }
        }

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
                    modelManager = SilentPickerModelManager(),
                    downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    storageProbe = storage,
                    dispatchers = TestDispatcherProvider(dispatcher),
                    savedStateHandle = SavedStateHandle(),
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
     * One tap, one coroutine, fixed order — and the order is the CHOICE first.
     * Neither write is atomic with the other, so whichever runs second is the
     * one a throw or a process death can take. Losing the stamp costs Manage
     * packs a date; losing the choice puts the language under Recent while the
     * composer still shows the old one. The cheap half goes last.
     *
     * The second assertion is the one a "two independent launches" regression
     * cannot pass: it reads the choice store from INSIDE the stamp, so the
     * choice must already be durable by the time the stamp runs — not merely
     * enqueued ahead of it.
     */
    @Test
    fun `the choice is already committed when the recents stamp runs`() =
        runTest(dispatcher) {
            repository.choiceAtStampTime = { translatePrefs.target.value }

            viewModel().select("fr", LanguageRole.TARGET)
            runCurrent()

            assertThat(journal).containsExactly("target:fr", "stamp:fr").inOrder()
            assertThat(repository.observedChoice).isEqualTo("fr")
        }

    /**
     * The stamp writes to a DIFFERENT store than the choice, and it can fail on
     * its own — a full disk, a locked database. When it does, the user must
     * still get the language they tapped.
     *
     * Both lenses on this change found the same defect and this is its red bar:
     * with the stamp running first and unguarded, the throw skipped the choice
     * write entirely AND escaped to an application scope that carries no
     * `CoroutineExceptionHandler` — a missed date taking the process with it.
     */
    @Test
    fun `a failing recents stamp costs the date, never the choice`() =
        runTest(dispatcher) {
            repository.failWith = IllegalStateException("disk full")

            viewModel().select("de", LanguageRole.TARGET)
            advanceUntilIdle()

            assertThat(translatePrefs.targetLang.first()).isEqualTo("de")
            assertThat(repository.lastUsed).isEmpty() // the stamp really did fail
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

    // ---- issue #90 consent gate, routed from the picker's rows ---------------

    /**
     * The whole route in one pass: a metered tap reaches the gate (dialog up,
     * nothing downloaded), and the dialog's yes reaches it too (downloaded,
     * dialog gone). Wiring the row's ⬇ straight to the manager, or exposing a
     * `pendingConsent` of the screen's own, is red here. The five-cell rule
     * behind it is `DownloadGateTest`'s to prove, once.
     */
    @Test
    fun `row taps and the dialog answer are routed through the gate`() =
        runTest(dispatcher) {
            connectivity.metered = true
            val vm = viewModel()

            vm.download("fr")
            runCurrent()
            assertThat(manager.downloads).isEmpty()
            assertThat(vm.pendingConsent.value).isEqualTo("fr")

            vm.downloadAnyway()
            runCurrent()
            assertThat(manager.downloads).containsExactly("fr")
            assertThat(vm.pendingConsent.value).isNull()
        }

    /**
     * Sheet 19a's checkbox writes the STANDING preference, and this asserts what
     * that preference then DOES — not merely that a write happened.
     *
     * Two mutations are covered, both decided before the test was written:
     * dropping the repository write, and writing `alwaysAsk` where
     * `!alwaysAsk` belongs. The box says "Always **ask**" and the store says
     * "Always **allow**"; a polarity slip there turns a consent prompt into a
     * standing permission the user believes they just tightened, and it reads as
     * a correct line in a diff. So the second half of this test asks the GATE:
     * the next metered tap must download without asking.
     */
    @Test
    fun `unticking Always ask grants the standing permission`() =
        runTest(dispatcher) {
            connectivity.metered = true
            val vm = viewModel()

            vm.onAlwaysAskChange(false)
            runCurrent()

            assertThat(prefs.allowMobileData.first()).isTrue()

            vm.download("fr")
            runCurrent()
            assertThat(manager.downloads).containsExactly("fr")
            assertThat(vm.pendingConsent.value).isNull()
        }

    /** …and back again: re-ticking the box must restore the question. */
    @Test
    fun `re-ticking Always ask revokes the standing permission`() =
        runTest(dispatcher) {
            connectivity.metered = true
            prefs.state.value = true
            val vm = viewModel()

            vm.onAlwaysAskChange(true)
            runCurrent()

            assertThat(prefs.allowMobileData.first()).isFalse()

            vm.download("fr")
            runCurrent()
            assertThat(manager.downloads).isEmpty()
            assertThat(vm.pendingConsent.value).isEqualTo("fr")
        }

    /**
     * What the sheet's checkbox is DRAWN from, seeded honestly.
     *
     * `true` before the store has answered is a fact rather than an optimistic
     * guess: the only way the sheet is on screen is that the gate found the
     * standing permission off. A `false` seed would draw the box clear for one
     * frame on a consent surface — the same class of first-frame lie that keeps
     * 194 rows from being labelled "Online only".
     */
    @Test
    fun `the checkbox reads the standing preference from the other end`() =
        runTest(dispatcher) {
            prefs.state.value = false
            val vm = viewModel()

            // The seed, before any collector has driven the store.
            assertThat(vm.alwaysAsk.value).isTrue()

            vm.alwaysAsk.test {
                assertThat(awaitItem()).isTrue() // still asking
                prefs.state.value = true
                assertThat(awaitItem()).isFalse() // the box clears when permission stands
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * #130 PR-13's loss class, driven through the ViewModel the SCREEN reads
     * rather than through the store alone: a question asked before the process
     * died is still open after it, and it is still a QUESTION — nothing has
     * downloaded.
     */
    @Test
    fun `a rebuilt picker still shows the sheet it asked before`() =
        runTest(dispatcher) {
            val restored = InMemoryConsentQuestionStore().apply { raise("de") }
            val vm =
                LanguagePickerViewModel(
                    languageRepository = repository,
                    clock = clock,
                    modelManager = manager,
                    downloadGate = DownloadGate(connectivity, prefs, manager, restored),
                    downloadPrefs = prefs,
                    translatePrefs = translatePrefs,
                    storageProbe = storage,
                    dispatchers = TestDispatcherProvider(dispatcher),
                    savedStateHandle = SavedStateHandle(),
                    appScope = appScope,
                )

            assertThat(vm.pendingConsent.value).isEqualTo("de")
            assertThat(manager.downloads).isEmpty()

            vm.downloadAnyway()
            runCurrent()

            assertThat(manager.downloads).containsExactly("de")
            assertThat(vm.pendingConsent.value).isNull()
        }

    /** "Not now" closes the gate's question from this screen too. */
    @Test
    fun `dismissing the dialog reaches the gate`() =
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

    // ---- the picker's own screen state (#130 PR-13) --------------------------

    /**
     * What is typed in the search field and how far the list is scrolled used to
     * live in the composable, in `rememberSaveable`. They survived a process
     * death — but only inside the composition that declared them, because every
     * `rememberSaveable` slot is addressed through the nearest
     * `SaveableStateHolder`, and the nav shell gives each destination its own
     * (`TranzlateApp.kt`). The picker is about to be drawn from three different
     * ones (PR-14 pane, PR-15 leaf, PR-16 dialog), so "same key, different slot,
     * state gone" was queued up three times over.
     *
     * **How process death is simulated, and what it is worth.** A new ViewModel
     * over the SAME [SavedStateHandle] — the house pattern, from
     * `TextViewModelTest`'s issue-#48 recovery suite. It faithfully tests the
     * half that can be got wrong in code: whether the state travels as DATA in
     * the handle or dies with the object. It does not exercise the Bundle
     * round-trip (no Android runtime here) and it does not mount two hosts (#186
     * added a Compose runtime to this module; no host-swap test is written yet).
     * The other half of the promise — that the SCREEN keeps nothing of its own
     * for a host to lose — is `PickerHostAgnosticTest`, as a source rule, stated
     * honestly there.
     */
    @Test
    fun `a fresh picker opens with an empty query at the top of the list`() {
        val subject = viewModel()

        assertThat(subject.query.value).isEmpty()
        assertThat(subject.listPosition()).isEqualTo(PickerListPosition.Top)
    }

    @Test
    fun `the search query survives process death`() {
        val handle = SavedStateHandle()
        viewModelWith(appScope, handle).onQueryChange("span")

        assertThat(viewModelWith(appScope, handle).query.value).isEqualTo("span")
    }

    /** Clearing the field is a real state, not "no state" — it must survive as cleared. */
    @Test
    fun `a cleared query survives process death as cleared`() {
        val handle = SavedStateHandle()
        val first = viewModelWith(appScope, handle)
        first.onQueryChange("span")
        first.onQueryChange("")

        assertThat(viewModelWith(appScope, handle).query.value).isEmpty()
    }

    /**
     * The mutation this was written against, decided first: make
     * `onListPositionChange` a no-op. Rebuilding the screen — which is what a
     * host change, a rotation and a process death all do — then drops the user
     * back at Afrikaans after they had scrolled to Swedish.
     *
     * The stored value is the LANGUAGE at the top of the catalog, not an item
     * index (#198 co-verify F1) — so this also pins that a `String?` travels the
     * handle as readily as the `Int` it replaced.
     */
    @Test
    fun `the list position survives a rebuild of the screen`() {
        val handle = SavedStateHandle()
        viewModelWith(appScope, handle).onListPositionChange(PickerListPosition(anchorId = "sv", offset = 17))

        assertThat(viewModelWith(appScope, handle).listPosition())
            .isEqualTo(PickerListPosition("sv", 17))
    }

    /**
     * Read live, never captured when the ViewModel was built. A rotation keeps
     * this object and destroys the composition, so the seed for the new
     * `LazyGridState` has to be the position as of the last scroll.
     */
    @Test
    fun `the list position is read live, not captured at construction`() {
        val subject = viewModel()
        assertThat(subject.listPosition()).isEqualTo(PickerListPosition.Top)

        subject.onListPositionChange(PickerListPosition(anchorId = "de", offset = 3))

        assertThat(subject.listPosition()).isEqualTo(PickerListPosition("de", 3))
    }

    /** Independent slots: one may not carry, clobber or resurrect the other. */
    @Test
    fun `query and list position survive together`() {
        val handle = SavedStateHandle()
        val first = viewModelWith(appScope, handle)
        first.onQueryChange("swe")
        first.onListPositionChange(PickerListPosition(anchorId = "pt", offset = 40))

        val restored = viewModelWith(appScope, handle)

        assertThat(restored.query.value).isEqualTo("swe")
        assertThat(restored.listPosition()).isEqualTo(PickerListPosition("pt", 40))
    }

    // ---- the offline-library meter (#130 PR-15, U-5) ------------------------

    /**
     * The meter is null until the disk has been read once, and the card is not
     * drawn while it is. A placeholder reading "0 packs" that corrected itself a
     * frame later would have stated something false in between — the same rule
     * that stops the first frame labelling 194 rows "Online only".
     */
    @Test
    fun `the meter is absent until the disk has been read`() =
        runTest(dispatcher) {
            val subject = viewModel()

            subject.library.test {
                assertThat(awaitItem()).isNull()
                advanceUntilIdle()
                assertThat(awaitItem()).isNotNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The ordinary case, wired end to end: counts from the catalogue, bytes from the probe. */
    @Test
    fun `the meter reports the catalogue count and the measured size`() =
        runTest(dispatcher) {
            repository.catalog.value =
                listOf(
                    Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
                    Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                    Language("xx", "Xhosa-ish", offlineAvailable = false, offlineDownloaded = false),
                )
            storage.packs = ONE_PACK_BYTES
            storage.total = VOLUME_BYTES
            val subject = viewModel()

            subject.library.test {
                skipItems(1) // the pre-read null
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(
                    OfflineLibraryMeter.Sized(
                        downloaded = 1,
                        // 2 offline-capable of 3 rows — the counter's denominator is
                        // capability, not catalogue size (C-11).
                        capable = 2,
                        usedBytes = ONE_PACK_BYTES,
                        volumeBytes = VOLUME_BYTES,
                    ),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * **The disk is walked when the pack COUNT changes, and at no other time.**
     *
     * `packsBytes()` walks the model store file by file — 30 files for a single
     * pack, measured in E-S1 — so a meter that re-derived from the raw catalogue
     * would re-walk on every unrelated overlay change the repository publishes
     * (a `lastUsedAt` stamp, a transient download state). That is risk PP-5.b in
     * the ruling's register, and the fake counts the calls so the claim is
     * measured rather than asserted.
     */
    @Test
    fun `an unrelated catalogue change does not re-walk the disk`() =
        runTest(dispatcher) {
            val counting = CountingStorageProbe()
            val subject = viewModelWith(appScope, probe = counting)

            subject.library.test {
                skipItems(1)
                advanceUntilIdle()
                assertThat(awaitItem()).isNotNull()
                val afterFirst = counting.walks

                // Same two languages, same download state — only the stamp moved.
                repository.catalog.value =
                    repository.catalog.value.map { it.copy(lastUsedAt = 99L) }
                advanceUntilIdle()

                assertThat(counting.walks).isEqualTo(afterFirst)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** …and it IS re-walked when a pack actually lands, or the number would go stale. */
    @Test
    fun `a pack arriving re-walks the disk`() =
        runTest(dispatcher) {
            val counting = CountingStorageProbe()
            val subject = viewModelWith(appScope, probe = counting)

            subject.library.test {
                skipItems(1)
                advanceUntilIdle()
                assertThat(awaitItem()).isNotNull()
                val afterFirst = counting.walks

                repository.catalog.value =
                    repository.catalog.value.map {
                        if (it.id == "en") it.copy(offlineDownloaded = true) else it
                    }
                advanceUntilIdle()
                assertThat(awaitItem().hashCode()).isNotEqualTo(0) // a new snapshot arrived

                assertThat(counting.walks).isEqualTo(afterFirst + 1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * **The staleness window, pinned in both directions (co-verify F2).**
     *
     * The card is re-measured when the pack count moves and when a fresh
     * subscription starts, and NOT in between. Co-verify renamed the model store
     * under a picker that stayed open and watched it go on reading "2 of 59
     * packs · 44 MB used" while the directory was already gone. That is a
     * documented limit rather than a defect — the ViewModel's KDoc enumerates
     * why nothing a user does can reach it — and a documented limit needs a test
     * or the document is the only thing holding it.
     *
     * Both halves are asserted here: the number HOLDS while the count is
     * unchanged, and it MOVES the moment a pack lands. A future decision to
     * re-walk more often, or less, reddens one of the two.
     */
    @Test
    fun `the meter holds its number until a pack arrives or leaves`() =
        runTest(dispatcher) {
            repository.catalog.value =
                listOf(
                    Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
                    Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = false),
                )
            storage.packs = ONE_PACK_BYTES
            storage.total = VOLUME_BYTES
            storage.free = FREE_BYTES
            val subject = viewModel()

            subject.library.test {
                skipItems(1) // the pre-read null
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(
                    OfflineLibraryMeter.Sized(
                        downloaded = 1,
                        capable = 2,
                        usedBytes = ONE_PACK_BYTES,
                        volumeBytes = VOLUME_BYTES,
                    ),
                )

                // The store is renamed away — the disk's answer changes completely
                // — but no pack arrived or left, so the count is the same and the
                // card keeps the number it measured. This is the window.
                storage.packs = null
                repository.catalog.value = repository.catalog.value.map { it.copy(lastUsedAt = 7L) }
                advanceUntilIdle()
                expectNoEvents()
                assertThat(subject.library.value).isEqualTo(
                    OfflineLibraryMeter.Sized(
                        downloaded = 1,
                        capable = 2,
                        usedBytes = ONE_PACK_BYTES,
                        volumeBytes = VOLUME_BYTES,
                    ),
                )

                // …and the next pack to land closes it, because the count moved.
                repository.catalog.value =
                    repository.catalog.value.map {
                        if (it.id == "af") it.copy(offlineDownloaded = true) else it
                    }
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(
                    OfflineLibraryMeter.Unsized(downloaded = 2, capable = 2, freeBytes = FREE_BYTES),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The R8 degrade, wired end to end: ML Kit's store is not where research
     * measured it, so the probe answers null and the meter reports free space
     * rather than "0 B used".
     */
    @Test
    fun `an unreadable model store degrades rather than reporting zero`() =
        runTest(dispatcher) {
            repository.catalog.value =
                listOf(Language("en", "English", offlineAvailable = true, offlineDownloaded = true))
            storage.packs = null
            storage.free = FREE_BYTES
            val subject = viewModel()

            subject.library.test {
                skipItems(1)
                advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(
                    OfflineLibraryMeter.Unsized(downloaded = 1, capable = 1, freeBytes = FREE_BYTES),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Counts the walks so "walked once per count change" is measured, not asserted. */
    private inner class CountingStorageProbe : com.codeboxlk.tranzlate.core.common.StorageProbe {
        var walks = 0
            private set

        override fun freeBytes(): Long = FREE_BYTES

        override fun totalBytes(): Long = VOLUME_BYTES

        override suspend fun packsBytes(): Long? {
            walks++
            return ONE_PACK_BYTES
        }
    }

    private companion object {
        /** E-S1 measurements on `emulator-5554` — one af↔en pack, and the volume it sat on. */
        const val ONE_PACK_BYTES = 44_169_505L
        const val VOLUME_BYTES = 10_411_143_168L
        const val FREE_BYTES = 8_651_702_272L
    }
}
