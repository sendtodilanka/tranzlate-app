package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetScaffold
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetTone

/** Sheet root — `tt_lang_sheet_*`, the rev3 ruling's namespace for sheet controls. */
internal const val TT_SHEET_CONFIRM = "tt_lang_sheet_confirm"
internal const val TT_SHEET_CONFIRM_DOWNLOAD = "tt_lang_sheet_confirm_download"
internal const val TT_SHEET_CONFIRM_NOT_NOW = "tt_lang_sheet_confirm_not_now"

/**
 * Sheet **18a-confirm** — the download-confirm a first-run suggestion's "Get"
 * raises before anything is enqueued (#130 PR-21).
 *
 * It exists for the first-run user specifically. A row's own ⬇ goes straight to
 * the metered-data gate, because a user tapping a download control on a language
 * row has already chosen that language; a suggestion is the app's idea, not the
 * user's, so it earns one confirming tap that states what a pack costs and offers
 * a real way out ("Not now"). Get → **confirm** → gate.
 *
 * ## It states the size and NOT the network
 *
 * The export draws a Wi-Fi line — *"On Wi-Fi now. You can remove the pack again
 * from Manage packs."* The size half ships; the network half does not, and the
 * omission is deliberate rather than an oversight:
 *
 * - **The size line is 40–65 MB, not the export's "20–45 MB".** That figure is
 *   the measured on-disk footprint of the shipped `translate-17.0.3.aar` models
 *   (#219), and it is stated in four other places already
 *   (`lang_sheet_space_body`, `lang_sheet_data_body`, `offline_subtitle`,
 *   `settings_mobile_data_supporting`) — this is a fifth, kept in step with them.
 *   The frame drew the pre-#219 guess; the measurement is the authority (rule 1).
 *   On the Wi-Fi path this sheet is the ONLY place the size appears — the 19a
 *   gate that also states it does not open when the network is unmetered — so the
 *   line earns its place here.
 * - **"On Wi-Fi now" is not asserted, because this sheet does not own the network
 *   question.** The metered-data decision has exactly one home, [DownloadGate]
 *   behind [confirmSuggestionDownload], and it runs AFTER this confirm — so a
 *   sheet that claimed the connection state would either be a second copy of that
 *   gate (which the rev3 ruling's §7.8 bounces) or a fact it read too early to
 *   trust. The reversibility half of the line — that a pack can be removed again —
 *   is network-independent and true, so it stays.
 *
 * Stateless and screen-local, on the [TranzlateSheetScaffold] anatomy PR-8 built,
 * exactly as [MobileDataSheet] is. [languageName] null draws nothing: a confirm
 * sheet titled *"Download "* would be worse than none, and the id only ever
 * arrives from a suggestion the catalogue already produced, so the name resolves
 * in every real path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadConfirmSheet(
    languageName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (languageName == null) return
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_confirm_title, languageName),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_confirm_download),
                testTag = TT_SHEET_CONFIRM_DOWNLOAD,
                onClick = onConfirm,
            ),
        modifier = Modifier.testTag(TT_SHEET_CONFIRM),
        tone = TranzlateSheetTone.Neutral,
        icon = { DownloadConfirmIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_confirm_not_now),
                testTag = TT_SHEET_CONFIRM_NOT_NOW,
                onClick = onDismiss,
            ),
        body = { DownloadConfirmBody() },
    )
}

/** The tonal slot's glyph — decorative, because the title carries the meaning (scaffold KDoc). */
@Composable
private fun DownloadConfirmIcon() {
    Icon(
        imageVector = Icons.Filled.Download,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * Two facts, both true on any network: how big a pack is, and that it can be
 * removed again. The style and colour come from the scaffold's body slot, so
 * these are plain `Text` — see [TranzlateSheetScaffold].
 */
@Composable
private fun DownloadConfirmBody() {
    Column {
        Text(stringResource(R.string.lang_confirm_size))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.lang_confirm_reversible))
    }
}

// ---- Previews (rule 7) — the one meaningful state is "a confirm for a language" ----
// `ModalBottomSheet` opens a window the tooling renders nothing for, so the
// preview draws the same anatomy on the floating surface via the design system's
// `TranzlateSheetPreviewFrame`, exactly as `MobileDataSheet` does.

@PreviewLightDark
@Composable
private fun DownloadConfirmSheetPreview() {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_confirm_title, "Spanish"),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_confirm_download),
                testTag = TT_SHEET_CONFIRM_DOWNLOAD,
                onClick = {},
            ),
        icon = { DownloadConfirmIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_confirm_not_now),
                testTag = TT_SHEET_CONFIRM_NOT_NOW,
                onClick = {},
            ),
        body = { DownloadConfirmBody() },
    )
}
