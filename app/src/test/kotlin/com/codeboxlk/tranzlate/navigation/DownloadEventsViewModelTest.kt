package com.codeboxlk.tranzlate.navigation

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeDownloadPrefsRepository
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.InMemoryConsentQuestionStore
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The app-shell pack-outcome observer's REFUSAL honesty (#314). The audit's
 * silent-failure-hunter found the shell VM to be the one caller of `download()` /
 * `requestDownload()` / `downloadConsented()` that DISCARDED the returned
 * [DownloadAttempt] — so a snackbar Retry / Download-again / Download-now made
 * while still offline or still full was refused synchronously and produced no
 * feedback at all: the manager writes a value-EQUAL `Failed` map (no re-emit) and
 * fires no PackEvent. The two screen ViewModels already captured it
 * (`OfflineLanguagesViewModelTest`); this pins the shell doing the same.
 *
 * Each of the three entry points is a SEPARATE capture site, tested independently
 * so a mutation that re-breaks one reddens only its own test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadEventsViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val manager = RecordingModelManager()
    private val connectivity = FakeConnectivityMonitor()
    private val downloadPrefs = FakeDownloadPrefsRepository()
    private val translatePrefs = NoopTranslatePrefsRepository()

    private lateinit var viewModel: DownloadEventsViewModel

    @Before
    fun setUp() {
        viewModel =
            DownloadEventsViewModel(
                modelManager = manager,
                translatePrefs = translatePrefs,
                downloadGate = DownloadGate(connectivity, downloadPrefs, manager, InMemoryConsentQuestionStore()),
                downloadPrefs = downloadPrefs,
                dispatchers = TestDispatcherProvider(dispatcherRule.dispatcher),
            )
    }

    /**
     * The #314 core. A snackbar Retry (20a-4) made while STILL offline is refused
     * synchronously (`DownloadAttempt.Refused(NETWORK)`) — the manager writes a
     * value-equal `Failed(NETWORK)` map (no re-emit) and fires no PackEvent, so the
     * shell's DISCARDED return left the Retry a silent no-op. Captured, the pack's
     * tag reaches [DownloadEventsViewModel.refusals] for the shell snackbar.
     *
     * Mutation decided first (rule 11): revert `onRetry` to
     * `viewModelScope.launch { withContext(io) { modelManager.download(languageTag) } }`
     * (discard the return) — `received` is empty and this reddens.
     */
    @Test
    fun `a retry refused while offline surfaces the pack, not silence`() =
        runTest {
            manager.attempt = DownloadAttempt.Refused(OfflineModelFailure.NETWORK)
            val received = mutableListOf<String>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.onRetry("fr")
            advanceUntilIdle()

            assertThat(received).containsExactly("fr")
        }

    /**
     * The second capture site: 20a-3 "Download again" runs through the gate's
     * `requestDownload`, which on an UNMETERED link forwards to the manager and can
     * come back Refused (the disk is still full). Mutation: discard the return in
     * `onDownloadAgain` — this reddens while the Retry test stays green, so the two
     * sites fail independently.
     */
    @Test
    fun `a download-again refused for space surfaces the pack`() =
        runTest {
            manager.attempt = DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            val received = mutableListOf<String>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.onDownloadAgain("fr")
            advanceUntilIdle()

            assertThat(received).containsExactly("fr")
        }

    /**
     * Non-vacuity for the tests above: the notice is tied to a REFUSAL, not fired on
     * every tap. A download that STARTS surfaces nothing here — its outcome travels
     * through the row's `modelStates()` and the U-1 PackEvents app snackbar instead,
     * so reporting it here would double it. Mutation: report unconditionally in
     * `reportOutcome` (drop the `is Refused` guard) and this reddens.
     */
    @Test
    fun `a retry that starts surfaces no refusal`() =
        runTest {
            manager.attempt = DownloadAttempt.Started
            val received = mutableListOf<String>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.onRetry("fr")
            advanceUntilIdle()

            assertThat(received).isEmpty()
        }

    /**
     * The third capture site: 19a "Download now" on a metered link raises the consent
     * sheet first (nothing downloaded yet), and the follow-through `downloadConsented`
     * can also come back Refused. Mutation: discard the return in `onConsentOnce` and
     * this reddens while the other two stay green.
     */
    @Test
    fun `download now, then refused for space, surfaces the pack`() =
        runTest {
            connectivity.metered = true // metered → 19a asks before anything downloads
            manager.attempt = DownloadAttempt.Refused(OfflineModelFailure.STORAGE)
            val received = mutableListOf<String>()
            viewModel.refusals.onEach { received += it }.launchIn(backgroundScope)

            viewModel.onDownloadAgain("fr") // raises the consent sheet, nothing reported yet
            runCurrent()
            assertThat(received).isEmpty()
            assertThat(viewModel.pendingConsent.value).isEqualTo("fr")

            viewModel.onConsentOnce() // "Download now" → downloadConsented → Refused
            advanceUntilIdle()

            assertThat(received).containsExactly("fr")
        }
}

/** A recording [OfflineModelManager] whose `download()` decision is scripted per test. */
private class RecordingModelManager : OfflineModelManager {
    val downloads = mutableListOf<String>()

    /** What `download()` decides. Default [DownloadAttempt.Started]; the refusal tests script a [DownloadAttempt.Refused]. */
    var attempt: DownloadAttempt = DownloadAttempt.Started

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flowOf(emptyMap())

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // never emits in this test

    override suspend fun download(languageTag: String): DownloadAttempt {
        downloads += languageTag
        return attempt
    }

    override suspend fun delete(languageTag: String) = Unit
}

/**
 * A no-op [TranslatePrefsRepository]: this VM only writes it from `onUse`, which the
 * refusal tests never call. It answers the defaults so construction is valid.
 */
private class NoopTranslatePrefsRepository : TranslatePrefsRepository {
    override val sourceLang: Flow<String> = flowOf("en")
    override val targetLang: Flow<String> = flowOf("fr")
    override val textMode: Flow<ModeId> = flowOf(ModeId.AUTO)

    override suspend fun setSourceLang(id: String) = Unit

    override suspend fun setTargetLang(id: String) = Unit

    override suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    ) = Unit
}
