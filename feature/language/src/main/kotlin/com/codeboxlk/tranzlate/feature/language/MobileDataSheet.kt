package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetScaffold
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetTone
import com.codeboxlk.tranzlate.core.designsystem.sheetBodyTextStyle

/** Sheet root — `tt_lang_sheet_*`, the rev3 ruling's namespace for sheet controls. */
internal const val TT_SHEET_DATA = "tt_lang_sheet_data"
internal const val TT_SHEET_DATA_DOWNLOAD = "tt_lang_sheet_data_download"
internal const val TT_SHEET_DATA_NOT_NOW = "tt_lang_sheet_data_not_now"
internal const val TT_SHEET_DATA_ALWAYS_ASK = "tt_lang_sheet_data_always_ask"

/**
 * Sheet **19a** — the one mobile-data consent surface in the app (#130 PR-17).
 *
 * It replaces TWO dialogs that asked the same question in two files with two
 * string sets: the picker's `MeteredConsentDialog` and the offline manager's
 * inline `AlertDialog`. Two copies of a consent prompt is two places for it to
 * drift, and the half that drifts spends the user's mobile data — the same
 * argument that put the RULE in one `DownloadGate` (PR-9). This is the COPY in
 * one place, on the anatomy PR-8 built for it.
 *
 * **Stateless, and it never learns which language it is about.** The gate holds
 * the pending id; this sheet is handed [visible] and nothing else about it. That
 * is not an omission — it is the drawing (spec rev 5, frame 19a: the title is
 * *"Download over mobile data?"*, with no language name in it), and the reason
 * is the checkbox. An answer given here can be made STANDING, so a title naming
 * one language would misdescribe what unticking the box does. The row that
 * raised the sheet is directly behind it in any case.
 *
 * ## The second action does not say "Wait for Wi-Fi", and that is the honest half
 *
 * The export draws *"Wait for Wi-Fi"*. Shipping that word promises the download
 * will happen when Wi-Fi returns, and **nothing in this app queues anything** —
 * `RealOfflineModelManager` deliberately passes a bare
 * `DownloadConditions.Builder().build()` (issue #90's ruling: the metered
 * decision is ours, never ML Kit's untested `requireWifi`). Whether a
 * `requireWifi` request can even be OBSERVED starting or finishing is experiment
 * **E-W1**, and E-W1 has not been run — there is no research record for it, and
 * `docs/research/issue-90-offline-download-lifecycle.md` parks the same probe as
 * "X6, a future pass". The rev3 ruling's REJECT list §7.8 refuses the "Wait for
 * Wi-Fi" strings pre-E-W1 outright; the two dialogs this file deletes were
 * shipping them regardless, so the promise is being retired, not withheld.
 *
 * The secondary action is therefore the owner's pre-approved interim, **"Not
 * now"** (ruling 8, approved 2026-08-01): a word that describes what the button
 * does — closes the question, leaves the row exactly as it was and re-tappable —
 * instead of a promise the app cannot keep.
 *
 * ## Hosting
 *
 * Screen-local, per the rev3 ruling's two hosting layers, and it takes the
 * scaffold's DEFAULT `SheetState` rather than hoisting one. A hoisted
 * `rememberModalBottomSheetState` is `rememberSaveable` underneath, addressed
 * through whichever `SaveableStateHolder` is drawing the screen — the exact
 * thing PR-13 moved the picker's state out of, and `PickerHostAgnosticTest` bans
 * by name. The cost is that a button tap removes the sheet without the
 * slide-down; that is what both dialogs did before it, so nothing regresses.
 * Dismissal stays fully available (back, scrim, drag handle — the scaffold does
 * not expose the properties that could disable them).
 *
 * The `@OptIn` below is for that DEFAULT `sheetState` argument — an experimental
 * `SheetState` whose default expression is evaluated at this call site. Nothing
 * in this file names it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileDataSheet(
    visible: Boolean,
    alwaysAsk: Boolean,
    onAlwaysAskChange: (Boolean) -> Unit,
    onDownloadNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    TranzlateSheetScaffold(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_data_title),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_data_download),
                testTag = TT_SHEET_DATA_DOWNLOAD,
                onClick = onDownloadNow,
            ),
        modifier = Modifier.testTag(TT_SHEET_DATA),
        tone = TranzlateSheetTone.Neutral,
        icon = { MobileDataIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_data_not_now),
                testTag = TT_SHEET_DATA_NOT_NOW,
                onClick = onDismiss,
            ),
        supportingContent = { AlwaysAskRow(alwaysAsk = alwaysAsk, onAlwaysAskChange = onAlwaysAskChange) },
        body = { Text(stringResource(R.string.lang_sheet_data_body)) },
    )
}

/** The tonal slot's glyph — decorative, because the title carries the meaning (scaffold KDoc). */
@Composable
private fun MobileDataIcon() {
    Icon(
        imageVector = Icons.Filled.SignalCellularAlt,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * "Always ask before using mobile data" — the standing preference, changeable
 * in place (the spec's own caption for 19a: *"the setting that caused the prompt
 * is changeable in place, so the answer is not a one-off"*).
 *
 * **Ticked means the app keeps asking**, which is the INVERSE of the stored
 * `DownloadPrefsRepository.allowMobileData` and of the Settings row that reads
 * "Always allow mobile data". Two names for one bit pointing opposite ways is
 * how a consent default gets silently reversed, so the flip happens in exactly
 * one place — [alwaysAskOf] / [allowMobileDataOf] — and the tests assert the
 * STORED value and the next gate decision, never merely that a write happened.
 *
 * The whole row is the touch target (`toggleable` + the 48dp C-14 floor), so the
 * `Checkbox` takes `onCheckedChange = null` and contributes no second semantics
 * node: TalkBack hears one checkable row with the label on it, rather than a
 * loose box followed by unrelated text.
 */
@Composable
private fun AlwaysAskRow(
    alwaysAsk: Boolean,
    onAlwaysAskChange: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimensions.touchTargetMin)
                    .toggleable(
                        value = alwaysAsk,
                        onValueChange = onAlwaysAskChange,
                        role = Role.Checkbox,
                    ).padding(horizontal = spacing.md16, vertical = spacing.sm8)
                    .testTag(TT_SHEET_DATA_ALWAYS_ASK),
        ) {
            Checkbox(checked = alwaysAsk, onCheckedChange = null)
            Text(
                text = stringResource(R.string.lang_sheet_data_always_ask),
                style = sheetBodyTextStyle(MaterialTheme.typography),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.md16),
            )
        }
    }
}

/**
 * The one place the sheet's checkbox and the stored preference are translated
 * into each other.
 *
 * The box says "Always **ask**"; the store says "Always **allow**". They are the
 * same bit read from opposite ends, and a screen that flipped it inline would
 * put the polarity in as many places as there are screens — which is two today
 * and more from PR-18 on. Both directions are pure functions so a unit test can
 * hold them, and both ViewModels call these rather than spelling `!`.
 */
internal fun alwaysAskOf(allowMobileData: Boolean): Boolean = !allowMobileData

/** The inverse of [alwaysAskOf] — what the checkbox's new value means for the store. */
internal fun allowMobileDataOf(alwaysAsk: Boolean): Boolean = !alwaysAsk

// ---- Previews (rule 7 — one per meaningful STATE; the state here is the toggle) ----
// `ModalBottomSheet` opens a window and the tooling renders nothing for a
// window, so these draw the same anatomy on the same floating surface the host
// paints — `TranzlateSheetPreviewFrame`, which exists for exactly this.

/** Ticked: the app is still asking, which is every sheet the user has not changed. */
@PreviewLightDark
@Composable
private fun MobileDataSheetPreview() {
    MobileDataSheetPreviewBody(alwaysAsk = true)
}

/** Unticked: the answer has been made standing, and the next metered tap will not ask. */
@PreviewLightDark
@Composable
private fun MobileDataSheetAlwaysAllowedPreview() {
    MobileDataSheetPreviewBody(alwaysAsk = false)
}

@Composable
private fun MobileDataSheetPreviewBody(alwaysAsk: Boolean) {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_data_title),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_data_download),
                testTag = TT_SHEET_DATA_DOWNLOAD,
                onClick = {},
            ),
        icon = { MobileDataIcon() },
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_data_not_now),
                testTag = TT_SHEET_DATA_NOT_NOW,
                onClick = {},
            ),
        supportingContent = { AlwaysAskRow(alwaysAsk = alwaysAsk, onAlwaysAskChange = {}) },
        body = { Text(stringResource(R.string.lang_sheet_data_body)) },
    )
}
