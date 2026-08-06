package com.codeboxlk.tranzlate.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.feature.language.PackSnackbar
import com.codeboxlk.tranzlate.feature.language.allowMobileDataOf
import com.codeboxlk.tranzlate.feature.language.alwaysAskOf
import com.codeboxlk.tranzlate.feature.language.toPackSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * The app shell's pack-outcome observer (U-1, #130 PR-22) — the ruling's
 * **sanctioned screen-outliver #2**.
 *
 * Activity-scoped, like [com.codeboxlk.tranzlate.MainActivityViewModel]: a
 * download's outcome can land while the user is on any screen or none, so the
 * thing that turns it into a snackbar must outlive every destination and belong to
 * the shell. It does exactly one job — map [OfflineModelManager.packEvents] to a
 * [PackSnackbar] and answer the four 20a actions — so it is not a god-VM even with
 * the dependencies each action pulls.
 *
 * ## Why it does NOT collect the events itself
 *
 * [snackbars] is a COLD map over the hot [OfflineModelManager.packEvents]; the shell
 * collects it under `repeatOnLifecycle(STARTED)`. That is deliberate and is the
 * whole of the "backgrounded events are dropped" contract: if this class collected
 * `packEvents` eagerly in [viewModelScope], the collector would stay alive while the
 * app was backgrounded, buffering notices to replay as a burst of stale snackbars on
 * return. Leaving the collection to the shell's lifecycle means a STOP tears the
 * subscription down and `replay = 0` gives a returning collector nothing — the state
 * map is the truth, exactly as [OfflineModelManager.packEvents]'s own contract says.
 */
@HiltViewModel
class DownloadEventsViewModel
    @Inject
    constructor(
        private val modelManager: OfflineModelManager,
        private val translatePrefs: TranslatePrefsRepository,
        private val downloadGate: DownloadGate,
        private val downloadPrefs: DownloadPrefsRepository,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        /**
         * The stream of snackbars to raise, one per outcome the manager announces.
         * Cold on purpose (see the class KDoc) — collecting it is what subscribes to
         * `packEvents`, and the shell does that under the lifecycle.
         */
        val snackbars: Flow<PackSnackbar> = modelManager.packEvents.map { it.toPackSnackbar() }

        /**
         * One-shot "your download could not even START" notices — the SYNCHRONOUS
         * refusal a pre-flight returns ([DownloadAttempt.Refused], issues
         * #234/#250/#314). This is the app shell's own copy of the seam the two
         * screen ViewModels already have (`OfflineLanguagesViewModel.refusals`), and
         * the shell was the one caller that IGNORED the attempt: a refusal repeated
         * for the same reason writes a value-EQUAL `Failed` map so `modelStates()`
         * does not re-emit, and a pre-flight refusal fires no `PackEvent`, so a
         * snackbar Retry / Download-again / Download-now made while still offline or
         * still full produced ZERO feedback (#314). Captured here, it reaches the
         * shell's SnackbarHost.
         *
         * A `Channel`, the one-shot seam the coroutines rule keeps for a screen
         * message — never a `StateFlow` (it conflates) and never the U-1 `PackEvents`
         * channel (that is for a transfer that actually ran). It carries the language
         * TAG the shell needs to NAME the pack in the notice; the refusal's cause is
         * still reported on the row through `modelStates()`, and on the two
         * management screens it is spelled out cause-specifically via
         * `downloadFailureCopy` (which is `internal` to `:feature:language`, so the
         * shell cannot reach that copy — see the PR note on #314).
         */
        private val refusalEvents = Channel<String>(Channel.BUFFERED)

        /** Synchronous download refusals, for the shell's snackbar. See [refusalEvents]. */
        val refusals: Flow<String> = refusalEvents.receiveAsFlow()

        /**
         * Surface the SYNCHRONOUS half of a download attempt — the four 20a actions
         * funnel their outcome through here. Only a [DownloadAttempt.Refused] needs a
         * word from the shell: a [DownloadAttempt.Started]'s outcome arrives through
         * `modelStates()` AND the U-1 `PackEvents` app snackbar, so reporting it here
         * too would double it; [DownloadAttempt.Ignored] wrote nothing and has
         * nothing to say. The same shape as `OfflineLanguagesViewModel.reportOutcome`.
         */
        private suspend fun reportOutcome(
            languageTag: String,
            attempt: DownloadAttempt,
        ) {
            if (attempt is DownloadAttempt.Refused) refusalEvents.send(languageTag)
        }

        /**
         * The metered-consent question "Download again" (20a-3) may raise, hosted at
         * the shell as a "snackbar-raised re-entry" (ruling §2). Its own gate, so its
         * `SavedStateConsentQuestionStore` is this observer's alone.
         */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

        /**
         * The 19a checkbox's value — the INVERSE of the stored `allowMobileData`,
         * flipped through the single [alwaysAskOf] the picker and Screen B also use.
         */
        val alwaysAsk: StateFlow<Boolean> =
            downloadPrefs.allowMobileData
                .map(::alwaysAskOf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), true)

        /**
         * 20a-2 "Use": switch the target to the just-downloaded pack. On
         * [viewModelScope] — the Activity's — because this VM outlives every screen,
         * so unlike the picker's own select there is no screen about to pop and
         * cancel the write (the reasoning `MainActivityViewModel.useLanguage` gives).
         */
        fun onUse(languageTag: String) {
            viewModelScope.launch { translatePrefs.setTargetLang(languageTag) }
        }

        /**
         * 20a-3 "Download again": a removed pack is a FRESH request, so it goes
         * through [DownloadGate] — which asks 19a on a metered link with no standing
         * permission — not straight to the manager. `withContext(io)` because the
         * gate's follow-through into `download()` runs a blocking `StatFs` free-space
         * probe on the caller (`StorageProbe.freeBytes`).
         */
        fun onDownloadAgain(languageTag: String) {
            viewModelScope.launch {
                // The attempt is CAPTURED, not discarded (#314). A `null` return is the
                // gate only ASKING (the metered consent sheet is up, nothing to report
                // yet); any real attempt is passed to [reportOutcome]. The same capture
                // `OfflineLanguagesViewModel.download` makes.
                val attempt = withContext(dispatchers.io) { downloadGate.requestDownload(languageTag) } ?: return@launch
                reportOutcome(languageTag, attempt)
            }
        }

        /**
         * 20a-4 "Retry": the failed download already passed the gate once, so retry
         * asks the manager directly. IO for the same free-space probe as above.
         */
        fun onRetry(languageTag: String) {
            viewModelScope.launch {
                // Captured, not discarded (#314): a Retry made while STILL offline or
                // still full is refused synchronously and must not be a silent no-op.
                val attempt = withContext(dispatchers.io) { modelManager.download(languageTag) }
                reportOutcome(languageTag, attempt)
            }
        }

        /** 19a "Always ask" toggle — stored through the single [allowMobileDataOf] flip. */
        fun onAlwaysAskChange(alwaysAsk: Boolean) {
            viewModelScope.launch { downloadPrefs.setAllowMobileData(allowMobileDataOf(alwaysAsk)) }
        }

        /** 19a "Download now": the one-off consent, then its download on IO. */
        fun onConsentOnce() {
            val consented = downloadGate.consentOnce() ?: return
            viewModelScope.launch {
                // Captured, not discarded (#314): "Download now" can also come back
                // Refused (a metered link, but the disk is full) — surface it.
                val attempt = withContext(dispatchers.io) { downloadGate.downloadConsented(consented) }
                reportOutcome(consented.id, attempt)
            }
        }

        /** 19a "Not now" / scrim / back: the question closes, the row is untouched. */
        fun onConsentDismiss() {
            downloadGate.dismiss()
        }
    }
