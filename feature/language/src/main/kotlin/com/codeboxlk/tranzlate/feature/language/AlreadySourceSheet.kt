package com.codeboxlk.tranzlate.feature.language

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetScaffold

/** Sheet 19m testTags — the `tt_lang_sheet_*` namespace (C-1, ruling §2). */
internal const val TT_SHEET_ALREADY = "tt_lang_sheet_already"
internal const val TT_SHEET_ALREADY_SWAP = "tt_lang_sheet_already_swap"
internal const val TT_SHEET_ALREADY_PICK = "tt_lang_sheet_already_pick"

/**
 * Sheet **19m** — already the source (#130 PR-20). The export's smallest sheet:
 * *"one sentence, two ways out, no icon fanfare"* — so no tonal icon slot.
 *
 * **Stateless, hosted at the APP SHELL.** It is raised from
 * `TextViewModel.duplicateSelection`, which turns non-null when the current
 * selection is degenerate — source and target the same real language. The picker
 * can produce that state because since the #130 rev.3 decouple it commits a
 * choice straight to `TranslatePrefsRepository` and does not itself refuse the
 * opposite side's language; this app cannot add that refusal in the picker
 * without colliding with the PR that owns it, so the guard is reactive and lives
 * in `TextViewModel` (the "TextViewModel verify" the ruling names). The sheet
 * lives here because the sheet strings are this module's authority, and the
 * composer never depends on this module — the shell hosts it, as it does 19h.
 *
 * **"Swap" is the likely intent, so it is filled.** It restores the pair the
 * duplicate displaced — the export's swap is the pre-commit one (source ⇄ the
 * language the user just picked), and `TextViewModel.onSwapLanguages` reproduces
 * it post-commit by swapping the last valid pair. **"Pick another"** reopens the
 * target picker.
 *
 * @param languageName the duplicated language, already resolved by the host — it
 *   is the source AND the just-picked target, so naming it once is unambiguous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlreadySourceSheet(
    languageName: String,
    onSwap: () -> Unit,
    onPickAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_already_title, languageName),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_already_swap),
                testTag = TT_SHEET_ALREADY_SWAP,
                onClick = onSwap,
            ),
        modifier = Modifier.testTag(TT_SHEET_ALREADY),
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_already_pick),
                testTag = TT_SHEET_ALREADY_PICK,
                onClick = onPickAnother,
            ),
        body = { Text(stringResource(R.string.lang_sheet_already_body)) },
    )
}

// ---- Preview (rule 7) — one meaningful STATE: the degenerate selection ----------
// `ModalBottomSheet` renders nothing in a static preview, so this draws the anatomy
// on the sheet surface through `TranzlateSheetPreviewFrame`. Literal fake data.

@PreviewLightDark
@Composable
private fun AlreadySourceSheetPreview() {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_already_title, "Spanish"),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_already_swap),
                testTag = TT_SHEET_ALREADY_SWAP,
                onClick = {},
            ),
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_already_pick),
                testTag = TT_SHEET_ALREADY_PICK,
                onClick = {},
            ),
        body = { Text(stringResource(R.string.lang_sheet_already_body)) },
    )
}
