package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure

/**
 * A one-shot NOTICE that an offline pack reached an outcome — the U-1 `PackEvents`
 * channel the rev3 ruling reserves for the app-shell snackbars (#130 PR-22).
 *
 * ## Notice, not state
 *
 * This is deliberately NOT a second source of truth for download state. The row
 * and the storage meter stay derived from [OfflineModelManager.modelStates], which
 * is the authority for *"what is on the device right now"*. A [PackEvent] says only
 * *"this just happened"*, once, so the shell can raise a snackbar about it — and if
 * nobody is listening at that instant (the app is backgrounded), the notice is
 * dropped and the state map is still the truth on return. That drop is the whole
 * reason the manager publishes these on a `replay = 0` `SharedFlow` rather than a
 * `Channel`, whose queued delivery would replay a stale "downloaded" the moment the
 * user came back to a pack they had since deleted from Android's storage settings.
 *
 * ## What emits, and what does NOT
 *
 * Only the manager's **ownership-checked** outcome sites emit (see
 * `RealOfflineModelManager`): a download that completes, fails, or is removed while
 * its job still owns the row. A download that was Stopped and superseded — its ML
 * Kit transfer landing late on a job that no longer owns the tag — must NOT raise a
 * "ready" for a pack the user cancelled, and a synchronous pre-flight refusal
 * (no network, no disk) is reported to the caller by `download()`'s return value and
 * the row's own state, never doubled as a snackbar. [DownloadStarted] is the one
 * non-outcome member: it fires once, from the single point `download()` confirms a
 * transfer began, so 20a-1 can offer "View" without inventing a second state channel.
 *
 * Every member carries the BCP-47 [languageTag] the notice is about; the shell
 * resolves it to a display name and to a snackbar at the point it is shown.
 */
sealed interface PackEvent {
    /** The pack this notice concerns (BCP-47, e.g. `"en"`). */
    val languageTag: String

    /**
     * A transfer just began — 20a-1. Fired once from `download()`'s confirmed-start
     * point, after the job is registered as the tag's owner, so it can never come
     * from a refused or ignored request.
     */
    data class DownloadStarted(
        override val languageTag: String,
    ) : PackEvent

    /** The pack finished downloading and is on the device — 20a-2 ("ready"). */
    data class DownloadSucceeded(
        override val languageTag: String,
    ) : PackEvent

    /**
     * The transfer failed AFTER it started — 20a-4. This is the ML Kit failure the
     * owning job catches, not a pre-flight refusal (which travels by `download()`'s
     * return). [cause] is carried for the copy the shell chooses.
     */
    data class DownloadFailed(
        override val languageTag: String,
        val cause: OfflineModelFailure,
    ) : PackEvent

    /** The pack was removed from the device — 20a-3 ("removed"). */
    data class Deleted(
        override val languageTag: String,
    ) : PackEvent
}
