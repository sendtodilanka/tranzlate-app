package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject

/**
 * Issue #90's owner ruling, in ONE home: a model download over a METERED
 * connection is a CONSENT question. The answer comes from the user or from the
 * standing preference they set in Settings — never from ML Kit's untested
 * `requireWifi` flag, and never by quietly spending the data plan.
 *
 * The rules, all of them:
 *  - Wi-Fi (or any unmetered link) never asks — [requestDownload] just starts.
 *  - Metered AND no standing permission → [pendingConsent] carries the id the
 *    question is about and the download does NOT start. The screen owns nothing
 *    but the dialog it draws from that one value.
 *  - Metered AND the standing permission is on → starts, no question.
 *  - [consentOnce] is a ONE-OFF yes: it closes the question and hands back the
 *    id, and it deliberately does not touch [DownloadPrefsRepository]. "Once"
 *    means once — the next metered tap asks again. A refactor that folds the
 *    one-off answer into the standing preference costs the user real mobile
 *    data and looks like nothing in a diff, which is why it has its own test.
 *  - [dismiss] leaves the row exactly as it was: no spinner, no half-state, no
 *    dead end (EDGE_CASES). The user can tap again.
 *
 * **This class launches nothing.** Every method is either a plain state
 * transition or a `suspend` call, so the download always runs on the CALLER's
 * scope and the choice of scope stays visible where the tap is handled. That is
 * not stylistic: this project has already lost a write to a scope that died
 * under it (issue #130 PR-4), and a gate that owned a scope of its own could
 * re-introduce that class of defect for every screen at once.
 *
 * Deliberately UNSCOPED in the Hilt graph. Each state holder gets its own
 * instance, so the question raised on one screen is that screen's question —
 * a `@Singleton` would leak a half-answered dialog across the picker and the
 * offline manager.
 */
class DownloadGate
    @Inject
    constructor(
        private val connectivity: ConnectivityMonitor,
        private val downloadPrefs: DownloadPrefsRepository,
        private val modelManager: OfflineModelManager,
    ) {
        private val _pendingConsent = MutableStateFlow<String?>(null)

        /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
        val pendingConsent: StateFlow<String?> = _pendingConsent.asStateFlow()

        /**
         * A row's ⬇ / ↻ tap. Starts the download, unless the connection is
         * metered and no standing permission exists — in which case it raises
         * the question instead and starts NOTHING.
         */
        suspend fun requestDownload(id: String) {
            val allowed = downloadPrefs.allowMobileData.first()
            if (connectivity.isMetered() && !allowed) {
                _pendingConsent.value = id
            } else {
                modelManager.download(id)
            }
        }

        /**
         * The dialog's "Download once". Closes the question and returns the id
         * it was raised for, for the caller to pass to [download] on its own
         * scope; null when nothing was pending (a second tap on an answered
         * dialog).
         *
         * Synchronous on purpose — the dialog must be gone the instant the tap
         * lands, not one dispatch later.
         */
        fun consentOnce(): String? = _pendingConsent.getAndUpdate { null }

        /** The consented download itself — [consentOnce]'s follow-through. */
        suspend fun download(id: String) = modelManager.download(id)

        /** "Wait for Wi-Fi", or a dismiss: the row is left untouched and re-tappable. */
        fun dismiss() {
            _pendingConsent.value = null
        }
    }
