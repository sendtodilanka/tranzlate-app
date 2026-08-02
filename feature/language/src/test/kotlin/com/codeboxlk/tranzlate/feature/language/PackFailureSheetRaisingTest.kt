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
            manager.onDownload = { tag -> manager.put(tag, OfflineModelState.Downloading) }
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
            manager.onDownload = { tag -> manager.put(tag, OfflineModelState.Downloading) }
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
            manager.onDownload = { tag -> manager.put(tag, OfflineModelState.Failed(OfflineModelFailure.STORAGE)) }
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
            manager.onDownload = { tag -> manager.put(tag, OfflineModelState.Downloading) }
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
            manager.onDownload = { tag -> manager.put(tag, OfflineModelState.Downloading) }
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
     * **The named limit, pinned so a change of mind has to be a decision.**
     *
     * A Retry refused for the SAME reason already on the row writes the identical
     * value, the shared map never changes, and no second sheet opens. That is
     * deliberate: re-opening a modal sheet to say exactly what the user read and
     * dismissed a second ago is worse than the row's own line, which is still
     * there, still names the cause and still offers Retry.
     *
     * If this test ever goes red because a second sheet DID open, the question to
     * answer is not "how do I make it pass" but "is interrupting twice better" —
     * and `LanguagePickerViewModel.reportFailure` carries the reasoning.
     */
    @Test
    fun `a retry refused for the same reason does not interrupt twice`() =
        runTest(dispatcher) {
            val stillFull = OfflineModelState.Failed(OfflineModelFailure.STORAGE)
            manager.put("hi", OfflineModelState.NotDownloaded)
            manager.onDownload = { tag -> manager.put(tag, stillFull) }
            val subject = pickerWatchingRows()
            subject.download("hi")
            advanceUntilIdle()
            assertThat(subject.packFailure.value).isInstanceOf(PackFailureRequest.NoSpace::class.java)
            subject.dismissPackFailure()

            subject.download("hi") // the Retry pill, on a disk that is still full
            advanceUntilIdle()

            assertThat(subject.packFailure.value).isNull()
            assertThat(manager.downloads).containsExactly("hi", "hi") // the retry DID run
            assertThat(manager.states.value["hi"]).isEqualTo(stillFull) // and the row still says why
        }
}
