package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.InMemoryConsentQuestionStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * **Which failures earn an interruption** — sheets 19d and 19b, raised by
 * `LanguagePickerViewModel` (#130 PR-18).
 *
 * The sheets themselves are drawn and clicked in `PackFailureSheetsTest`, and
 * the cause→sheet decision is a table in `DownloadFailureTest`. What is left,
 * and what lives here, is the part neither can see: **which failures the picker
 * is entitled to interrupt the user about at all.** The manager's state map is
 * shared by every screen and outlives the screen that caused a failure, so
 * "there is a `Failed` in the map" and "this user just asked for this download
 * and it failed" are different facts — and only the second one earns a sheet.
 *
 * A separate class from `LanguagePickerViewModelTest` because adding these to it
 * tripped detekt's `LargeClass`; the fakes both suites drive are in
 * `PickerViewModelFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackFailureSheetRaisingTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private val repository = FakeLanguageRepository()
    private val manager = PickerModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefsRepository()
    private val translatePrefs = FakeTranslatePrefs()
    private val clock = FakeClock()
    private val storage = FakeStorageProbe()

    /** Selection writes run on an application-lifetime scope; see `LanguagePickerViewModel.select`. */
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())

    @After
    fun stopAppScope() = appScope.cancel()

    private fun viewModel(probe: com.codeboxlk.tranzlate.core.common.StorageProbe = storage) =
        LanguagePickerViewModel(
            languageRepository = repository,
            clock = clock,
            modelManager = manager,
            downloadGate = DownloadGate(connectivity, prefs, manager, InMemoryConsentQuestionStore()),
            downloadPrefs = prefs,
            translatePrefs = translatePrefs,
            storageProbe = probe,
            dispatchers = TestDispatcherProvider(dispatcher),
            savedStateHandle = SavedStateHandle(),
            appScope = appScope,
        )

    // ---- Sheets 19d / 19b: which failures earn an interruption (#130 PR-18) ----
    //
    // Every test here collects `offlineStates` on `backgroundScope` first, and
    // that is not ceremony. The ViewModel reads "what was the row showing when
    // the user tapped" from that flow, which is `WhileSubscribed` — with nobody
    // watching it holds its empty seed, and a VM asked to report on a screen
    // nobody is looking at would be reporting on a screen nobody is looking at.
    // The collector IS the screen.

    private fun TestScope.pickerWatchingRows(): LanguagePickerViewModel {
        val subject = viewModel()
        backgroundScope.launch { subject.offlineStates.collect { } }
        advanceUntilIdle()
        return subject
    }

    /**
     * The ordinary 19d path: the transfer ran and stopped.
     *
     * The mutation this was written against (before the code, rule 11) is the
     * inverted consent guard — `if (pendingConsent.value != id) return` reads as
     * a sensible early-out and silently switches every failure report off.
     */
    @Test
    fun `a failed download opens the interrupted sheet`() =
        runTest(dispatcher) {
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()

            subject.download("hi")
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.NETWORK))
            advanceUntilIdle()

            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.Interrupted("hi", OfflineModelFailure.NETWORK))
        }

    /**
     * **The rule that keeps the sheet from being an ambush.** The manager's map
     * is shared by every screen and outlives the one that caused the failure, so
     * a picker opened after a failure in Settings must not be interrupted about
     * it. The row still says so, with its cause and its Retry.
     */
    @Test
    fun `a failure this screen did not ask for opens no sheet`() =
        runTest(dispatcher) {
            val subject = pickerWatchingRows()

            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.NETWORK))
            advanceUntilIdle()

            assertThat(subject.packFailure.value).isNull()
        }

    /** A download that succeeds is not a failure, however long it takes to get there. */
    @Test
    fun `a download that finishes opens no sheet`() =
        runTest(dispatcher) {
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()

            subject.download("hi")
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Downloaded)
            advanceUntilIdle()

            assertThat(subject.packFailure.value).isNull()
        }

    /**
     * The ruling's named PR-18 test: **STORAGE pre-flight → 19b.** The real
     * manager refuses before it enqueues anything, so the row never passes
     * through `Downloading` — a watcher that only looked for a transfer failing
     * would never see the one failure the user can actually do something about.
     *
     * The two figures are the PROBE's, in one reading, and the mutation is the
     * swap: free and total the wrong way round draws a device that is 99% empty
     * at the moment it refuses a download.
     */
    @Test
    fun `a download the disk cannot hold opens the no-space sheet`() =
        runTest(dispatcher) {
            val probe = FakeStorageProbe(free = 12L * 1024 * 1024, total = 64L * 1024 * 1024 * 1024)
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Failed(OfflineModelFailure.STORAGE))
                DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            }
            val subject = viewModel(probe = probe)
            backgroundScope.launch { subject.offlineStates.collect { } }
            advanceUntilIdle()

            subject.download("hi")
            advanceUntilIdle()

            assertThat(subject.packFailure.value)
                .isEqualTo(
                    PackFailureRequest.NoSpace(
                        freeBytes = 12L * 1024 * 1024,
                        volumeBytes = 64L * 1024 * 1024 * 1024,
                    ),
                )
        }

    /**
     * A metered tap that only RAISES the consent question started nothing, so
     * there is no outcome to report — and nothing to leave a watcher suspended
     * on a question the user may never answer.
     */
    @Test
    fun `a consent question is not a failure`() =
        runTest(dispatcher) {
            connectivity.metered = true
            prefs.state.value = false
            manager.put("hi", OfflineModelState.NotDownloaded)
            val subject = pickerWatchingRows()

            subject.download("hi")
            advanceUntilIdle()

            assertThat(subject.pendingConsent.value).isEqualTo("hi")
            assertThat(subject.packFailure.value).isNull()
            assertThat(manager.downloads).isEmpty()
        }

    /** Answering "Download once" and then failing still earns the sheet. */
    @Test
    fun `a consented download that fails opens the sheet`() =
        runTest(dispatcher) {
            connectivity.metered = true
            prefs.state.value = false
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()
            subject.download("hi")
            advanceUntilIdle()

            subject.downloadAnyway()
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.UNKNOWN))
            advanceUntilIdle()

            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.Interrupted("hi", OfflineModelFailure.UNKNOWN))
        }

    /** Close: the sheet goes, the row keeps its cause line and its Retry — no dead end. */
    @Test
    fun `dismissing the sheet leaves the row alone`() =
        runTest(dispatcher) {
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()
            subject.download("hi")
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.NETWORK))
            advanceUntilIdle()

            subject.dismissPackFailure()

            assertThat(subject.packFailure.value).isNull()
            assertThat(manager.states.value["hi"])
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))
        }

    /**
     * **Issue #234, reproduced and then closed.**
     *
     * This test used to assert the opposite — `a retry refused for the same reason
     * does not interrupt twice` — and the behaviour it pinned was the shipped
     * defect. A Retry refused for the SAME reason writes the identical
     * `Failed(STORAGE)`, `MutableStateFlow` conflates on `equals`, and the watcher
     * never passed its first step: **the Retry pill was enabled, 48 dp, and
     * produced nothing at all** — no spinner (this path never writes
     * `Downloading`), no sheet, no toast. The defence written into
     * `LanguagePickerViewModel` was that the row still carries its cause and its
     * Retry, which establishes that the row still RENDERS a Retry and not that
     * Retry DOES anything.
     *
     * The old reasoning was "the sheet they dismissed a second ago". A second ago
     * they were told 12 MB free — and they have since had the chance to change
     * that, which is what #235 is about. So the refusal is now reported on every
     * attempt, and the sheet is rebuilt rather than re-shown (see the test below).
     */
    @Test
    fun `a retry refused for the same reason raises the sheet again`() =
        runTest(dispatcher) {
            val stillFull = OfflineModelState.Failed(OfflineModelFailure.STORAGE)
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, stillFull)
                DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            }
            val subject = pickerWatchingRows()
            subject.download("hi")
            advanceUntilIdle()
            assertThat(subject.packFailure.value).isInstanceOf(PackFailureRequest.NoSpace::class.java)
            subject.dismissPackFailure()

            subject.download("hi") // the Retry pill, on a disk that is still full
            advanceUntilIdle()

            assertThat(subject.packFailure.value).isInstanceOf(PackFailureRequest.NoSpace::class.java)
            assertThat(manager.downloads).containsExactly("hi", "hi") // the retry DID run
            assertThat(manager.states.value["hi"]).isEqualTo(stillFull) // and the row still says why
        }

    /**
     * **The half of #234 that makes the re-raise worth having** — and the half
     * #235 exists for.
     *
     * `packFailureRequest` reads the probe at RAISE time, so a sheet raised again
     * is a sheet measured again. #235's user frees 130 MB from 12 MB and comes
     * back to 142 MB, which is still under the 150 MB pre-flight: the honest
     * answer is 19b again, reading the NEW figure. Silence would tell them
     * clearing three packs did not count.
     *
     * The mutation is a re-raise that reuses the first request object — same
     * shape, same type, same everything except the one number the user acted on.
     */
    @Test
    fun `the re-raised sheet reports the space freed since`() =
        runTest(dispatcher) {
            val probe = FakeStorageProbe(free = 12L * MIB, total = 64L * GIB)
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Failed(OfflineModelFailure.STORAGE))
                DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            }
            val subject = viewModel(probe = probe)
            backgroundScope.launch { subject.offlineStates.collect { } }
            advanceUntilIdle()
            subject.download("hi")
            advanceUntilIdle()
            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.NoSpace(freeBytes = 12L * MIB, volumeBytes = 64L * GIB))
            subject.dismissPackFailure()

            probe.free = 142L * MIB // three packs removed — still short of the 150 MB pre-flight
            subject.download("hi")
            advanceUntilIdle()

            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.NoSpace(freeBytes = 142L * MIB, volumeBytes = 64L * GIB))
        }

    // ---- Two downloads at once (#239) -----------------------------------------
    //
    // Every failure test above drives ONE language id, and every failure test in
    // this repository did before #239 — seven `"hi"` here, four `"fr"` in
    // `LanguagePickerViewModelTest`. That single-fixture habit is precisely what
    // hid a defect the picker's own list invites: it offers 59 downloadable
    // languages and nothing discourages tapping two.

    /**
     * **Issue #239, reproduced and then closed.**
     *
     * Hindi fails and 19d opens. Tamil then fails, and the sheet the user is
     * READING used to become a different sheet — different title, different body,
     * and a different action where their thumb already is: `Retry` became `Manage
     * packs`. `PackFailureSheetHost`'s KDoc said the ViewModel "already guarantees
     * one request at a time", which is true and is not the property that matters.
     *
     * The two ids differ in CAUSE as well as in id, deliberately: a fix that only
     * refused to replace a sheet with one of the same TYPE would pass a
     * same-cause test and fail the user in exactly the drawn case.
     */
    @Test
    fun `a second failure does not swap the sheet the user is reading`() =
        runTest(dispatcher) {
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.put("ta", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()
            subject.download("hi")
            subject.download("ta")
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.NETWORK))
            advanceUntilIdle()
            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.Interrupted("hi", OfflineModelFailure.NETWORK))

            manager.put("ta", OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            advanceUntilIdle()

            assertThat(subject.packFailure.value)
                .isEqualTo(PackFailureRequest.Interrupted("hi", OfflineModelFailure.NETWORK))
        }

    /**
     * **The half that settles WHICH of #239's two answers ships.** A failure that
     * arrives while a sheet is held is dropped to its row — it is not queued
     * behind the sheet and it does not arrive the moment the sheet closes.
     *
     * Queueing was the other coherent answer and it re-arms the same harm one beat
     * later: the second sheet lands in the space the thumb is already travelling
     * to, and it quotes figures measured before the user did anything about them,
     * which is #235. The row keeps the fact, its cause line and a Retry that now
     * works — which is where this codebase already puts a failure the user is not
     * owed an interruption about.
     *
     * This one passed before the fix as well, and says so rather than pretending
     * to be a reproduction: it exists to make the queue answer a red test rather
     * than a later refactor.
     */
    @Test
    fun `a dropped failure does not come back when the sheet is closed`() =
        runTest(dispatcher) {
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.put("ta", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag ->
                manager.put(tag, OfflineModelState.Downloading)
                DownloadAttempt.Started
            }
            val subject = pickerWatchingRows()
            subject.download("hi")
            subject.download("ta")
            advanceUntilIdle()
            manager.put("hi", OfflineModelState.Failed(OfflineModelFailure.NETWORK))
            manager.put("ta", OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            advanceUntilIdle()

            subject.dismissPackFailure()
            advanceUntilIdle()

            assertThat(subject.packFailure.value).isNull()
            // Tamil's failure is not lost — it is on Tamil's row, with its cause
            // and its Retry, which is the surface it belongs on.
            assertThat(manager.states.value["ta"])
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.STORAGE))
        }

    private companion object {
        const val MIB = 1024L * 1024
        const val GIB = 1024L * 1024 * 1024
    }
}
