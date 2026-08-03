package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

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
 *  - [consentOnce] is a ONE-OFF yes: it closes the question and hands back a
 *    [ConsentedDownload], and it deliberately does not touch
 *    [DownloadPrefsRepository]. "Once" means once — the next metered tap asks
 *    again. A refactor that folds the one-off answer into the standing
 *    preference costs the user real mobile data and looks like nothing in a
 *    diff, which is why it has its own test.
 *  - [dismiss] leaves the row exactly as it was: no spinner, no half-state, no
 *    dead end (EDGE_CASES). The user can tap again.
 *
 * **This class launches nothing of its own.** Every member is either a plain
 * state transition or a `suspend` call, so the gate adds no lifetime: whatever
 * it does happens on the CALLER's scope, and the choice of scope stays visible
 * where the tap is handled.
 *
 * That is a claim about the GATE, not about the transfer. Only three things
 * actually ride the caller's scope — the standing-preference read, the metered
 * check, and the synchronous pre-flight inside
 * [OfflineModelManager.download] (capability, in-flight de-duplication, the
 * free-space probe, the hand-off to `Downloading`). The transfer itself is
 * launched by the Translation brain on its own process-lifetime scope, on
 * purpose, so navigating away cannot strand a half-finished download —
 * `RealOfflineModelManager.kt`, the `downloadScope.launch` at :255 with the
 * reasoning spelled out at :284-288. A gate that owned a scope would insert a
 * THIRD lifetime between the tap and that one, for every screen at once; this
 * project has already lost a write to a scope that died under it (#130 PR-4).
 *
 * Deliberately UNSCOPED in the Hilt graph. Each state holder gets its own
 * instance, so the question raised on one screen is that screen's question —
 * a `@Singleton` would leak a half-answered dialog across the picker and the
 * offline manager. Both halves of that — no scope of its own, no scope
 * annotation — are held by `DownloadGateTest`, against this file's source, and
 * the composition root's `@Provides` is held to the same rule from the other
 * end by `KonsistArchitectureTest`.
 *
 * The question itself is kept in a [ConsentQuestionStore] rather than in a field
 * here, so that a process death in the middle of it does not silently withdraw a
 * question the user was looking at (#130 PR-13, loss class PP-5.f).
 *
 * **No `@Inject` on the constructor, on purpose.** The gate is assembled by the
 * composition root instead, which is what keeps [ConsentQuestionStore] out of
 * the Hilt graph entirely — see that interface's KDoc for the bypass this
 * closes. An `@Inject` constructor here would force a binding for every
 * parameter, and a binding for the store is exactly the thing that must not
 * exist.
 */
class DownloadGate(
    private val connectivity: ConnectivityMonitor,
    private val downloadPrefs: DownloadPrefsRepository,
    private val modelManager: OfflineModelManager,
    private val consentQuestion: ConsentQuestionStore,
) {
    /** Language id awaiting the mobile-data consent dialog; null = no dialog. */
    val pendingConsent: StateFlow<String?> = consentQuestion.question

    /**
     * A row's ⬇ / ↻ tap. Starts the download, unless the connection is
     * metered and no standing permission exists — in which case it raises
     * the question instead and starts NOTHING.
     *
     * **An unanswerable decision ASKS (issue #238).** Neither half of it was
     * guarded before: the standing-preference read is a DataStore read, and
     * `isMetered()` is a synchronous binder call into `ConnectivityManager`.
     * A throw from either rode the caller's bare `viewModelScope.launch` to
     * `Thread.defaultUncaughtExceptionHandler` — the app vanishing on a download
     * tap, which is the one thing this class exists to make orderly.
     *
     * Raising the question is the safe side and not merely the convenient one.
     * Issue #90's ruling is that a metered download is a CONSENT question; when
     * the answer cannot be established, spending the user's data plan silently is
     * the failure that costs them money, and asking costs them one tap. It is not
     * a dead end either — the sheet carries "Download once" and "Wait for Wi-Fi",
     * so the row stays reachable whichever way they answer.
     *
     * [modelManager] is deliberately OUTSIDE the guard: its own pre-flight owns
     * its own failures (`RealOfflineModelManager.download`), and swallowing them
     * here would hide a real download failure behind a consent sheet.
     *
     * @return the manager's [DownloadAttempt], or **null** when the question was
     *   raised instead and the manager was never asked — including when it was
     *   raised because the metered question could not be answered at all. Null is
     *   the gate's own answer and not one of the manager's: nothing was refused
     *   and nothing was ignored, because nothing was requested yet. A caller that
     *   watches for an outcome must not watch for this one — the user may never
     *   answer it.
     */
    suspend fun requestDownload(id: String): DownloadAttempt? {
        val mustAsk =
            try {
                val allowed = downloadPrefs.allowMobileData.first()
                connectivity.isMetered() && !allowed
            } catch (rethrown: CancellationException) {
                throw rethrown // never break structured cancellation
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") unanswerable: Throwable,
            ) {
                true // cannot tell whether this link is metered — ask, never assume
            }
        return if (mustAsk) {
            consentQuestion.raise(id)
            null
        } else {
            modelManager.download(id)
        }
    }

    /**
     * The dialog's "Download once". Closes the question and returns the
     * answer as a [ConsentedDownload], for the caller to hand to
     * [downloadConsented] on its own scope; null when nothing was pending
     * (a second tap on an answered dialog).
     *
     * Synchronous on purpose — the dialog must be gone the instant the tap
     * lands, not one dispatch later.
     */
    fun consentOnce(): ConsentedDownload? = consentQuestion.take()?.let(::ConsentedDownload)

    /** The consented download itself — [consentOnce]'s follow-through. */
    suspend fun downloadConsented(consented: ConsentedDownload): DownloadAttempt = modelManager.download(consented.id)

    /** "Wait for Wi-Fi", or a dismiss: the row is left untouched and re-tappable. */
    fun dismiss() {
        consentQuestion.take()
    }
}

/**
 * Evidence that the user answered "Download once" for [id] — nothing else.
 *
 * The constructor is `internal`, so it cannot be produced outside
 * `:core:domain`, and inside it [DownloadGate.consentOnce] is the only thing
 * that produces one. That makes skipping the consent question a COMPILE error
 * in every feature module rather than a naming convention someone has to
 * remember: the old signature was `download(id: String)`, a public door sitting
 * one autocomplete away from `requestDownload(id: String)` and taking exactly
 * the same argument. Picking the wrong one lost issue #90's ruling silently, on
 * that screen only, and read as a correct line of code in review.
 */
class ConsentedDownload internal constructor(
    val id: String,
)
