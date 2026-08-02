package com.codeboxlk.tranzlate.feature.language

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure

/**
 * The ONE place a download failure becomes words and a surface (issue **#175**,
 * #130 rev3 PR-18 — the ruling's `shared/ … failure-cause map ×1`).
 *
 * ## What was here before, and what it cost
 *
 * Two maps, in two screens, over three sets of two string keys each:
 * `LanguagePickerScreen.failureCauseRes()` answered with `text_lang_error_*` and
 * `OfflineLanguagesScreen`'s inline `when` answered with `offline_error_*`. Same
 * module, same download, same three outcomes — six live keys, eighteen resource
 * lines across three locales, and **different sentences**. Lose the connection
 * mid-download and the picker said *"No connection. Reconnect and try again."*
 * while Settings → Offline languages said *"Download failed — check your
 * connection, then retry"*: one fault wearing two faces, which invites the
 * reading that they are two faults. Both sets shipped from the day the second
 * screen was written and nothing anywhere could go red about it.
 *
 * The rev3 ruling's REJECT §7.8 already bounces a **third** copy of the gate,
 * the failure map or the string set on sight. This file is the first one, so the
 * rule now has something to point at, and `DownloadFailureSourceTest` makes the
 * third copy a red test rather than a review reflex.
 *
 * ## Why the map answers with a SHEET as well as a line
 *
 * Because the two are the same decision. `STORAGE` and `NETWORK` are not two
 * flavours of one message; they are two different things to do next — free some
 * room, or reconnect — and the spec draws them as two different sheets (19b and
 * 19d). Deciding the line here and the sheet somewhere else would be the same
 * `when` written twice again, one file apart instead of one screen apart.
 *
 * ## The fold, stated so it is not un-folded by accident
 *
 * `NETWORK` and `WIFI_REQUIRED` share one answer. That is deliberate: from where
 * the user stands both mean *the transfer could not run over the connection you
 * have*, and the app cannot offer the one thing that would distinguish them —
 * queueing the download for Wi-Fi — because nothing in it queues anything and
 * experiment **E-W1** has never been run (`STRINGS_language.md` §5, ruling
 * REJECT §7.8). A separate `WIFI_REQUIRED` sentence would have to promise a wait
 * the app does not perform. `DownloadFailureTest` pins the fold in both
 * directions.
 */
internal fun downloadFailureCopy(cause: OfflineModelFailure): DownloadFailureCopy =
    when (cause) {
        OfflineModelFailure.STORAGE -> {
            DownloadFailureCopy(
                rowLine = R.string.lang_pack_error_storage,
                sheet = DownloadFailureSheet.NoSpace,
            )
        }

        OfflineModelFailure.NETWORK, OfflineModelFailure.WIFI_REQUIRED -> {
            DownloadFailureCopy(
                rowLine = R.string.lang_pack_error_network,
                sheet =
                    DownloadFailureSheet.Interrupted(
                        body = R.string.lang_sheet_failed_body_network,
                        cause = R.string.lang_sheet_failed_cause_network,
                    ),
            )
        }

        OfflineModelFailure.UNKNOWN -> {
            DownloadFailureCopy(
                rowLine = R.string.lang_pack_error_generic,
                sheet =
                    DownloadFailureSheet.Interrupted(
                        body = R.string.lang_sheet_failed_body_generic,
                        cause = R.string.lang_sheet_failed_cause_generic,
                    ),
            )
        }
    }

/**
 * Everything the app is entitled to say about one failed download.
 *
 * @property rowLine the failed row's supporting line, on BOTH screens — the
 *   single sentence that replaced the two that disagreed (#175).
 * @property sheet which sheet the failure opens when the user is the one who
 *   just asked for the download.
 */
@Immutable
internal data class DownloadFailureCopy(
    @param:StringRes val rowLine: Int,
    val sheet: DownloadFailureSheet,
)

/**
 * Which of the two failure sheets a cause opens.
 *
 * A closed set of two, because the spec draws two and there is nothing a third
 * could say. It is a type rather than a boolean so that adding a cause to
 * [OfflineModelFailure] cannot compile until someone decides what the user
 * should DO about it — which is the whole of EDGE_CASES' no-dead-end rule
 * expressed where it can be enforced.
 */
@Immutable
internal sealed interface DownloadFailureSheet {
    /**
     * **19b — "Not enough space".** No language name in it: the sheet is about
     * the device, and the pre-flight that raised it
     * (`RealOfflineModelManager.download`, the `REQUIRED_FREE_BYTES` check)
     * refuses every pack equally. It carries no strings of its own here because
     * it has no per-cause copy — the whole sheet is one case.
     */
    data object NoSpace : DownloadFailureSheet

    /**
     * **19d — "<Language> did not download".** The transfer started, or would
     * have, and stopped.
     *
     * @property body the sentence under the title. Per cause, because the drawn
     *   19d is written for ONE cause — *"The connection dropped…"* — and saying
     *   that about a failure ML Kit did not explain would be inventing a reason.
     * @property cause the tonal line above the actions: what happened, and what
     *   pressing Retry will do.
     */
    data class Interrupted(
        @param:StringRes val body: Int,
        @param:StringRes val cause: Int,
    ) : DownloadFailureSheet
}

/**
 * A failure the user is owed a sheet about — the request the picker's ViewModel
 * raises and the screen draws.
 *
 * It exists because "this language's state is `Failed`" and "this user just
 * asked for this download and it failed" are different facts, and only the
 * second one earns a sheet. The manager's state map is shared by every screen
 * and outlives the screen that caused the failure, so a picker opened after a
 * failure elsewhere would otherwise be interrupted by a sheet about something
 * the user did not just do. The row still reports it — with the line above, and
 * a Retry — which is where a fact nobody asked for belongs.
 */
@Immutable
sealed interface PackFailureRequest {
    /**
     * Sheet 19b. Both figures are read from `StorageProbe` at the moment the
     * download was refused, and they describe ONE volume, so the bar can plot
     * used against free without lying about either (spec rev 5: *"at 110 MB the
     * library cannot be plotted against a whole device without misstating either
     * the library or the 12 MB"*).
     */
    data class NoSpace(
        val freeBytes: Long,
        val volumeBytes: Long,
    ) : PackFailureRequest

    /**
     * Sheet 19d. The id, not the display name: the name is a presentation
     * question (locale, CLDR) and the ViewModel deliberately answers none of
     * those — the screen resolves it the same way it resolves every row.
     */
    data class Interrupted(
        val id: String,
        val cause: OfflineModelFailure,
    ) : PackFailureRequest
}
