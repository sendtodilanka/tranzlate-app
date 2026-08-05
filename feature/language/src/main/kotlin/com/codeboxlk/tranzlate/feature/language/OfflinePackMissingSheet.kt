package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateListSheet
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.ui.languageLabel

/** Sheet 19h testTags — the `tt_lang_sheet_*` namespace (C-1, ruling §2). */
internal const val TT_SHEET_OFFLINE = "tt_lang_sheet_offline"
internal const val TT_SHEET_OFFLINE_USE = "tt_lang_sheet_offline_use"
internal const val TT_SHEET_OFFLINE_CLOSE = "tt_lang_sheet_offline_close"
internal const val TT_SHEET_OFFLINE_ROW_PREFIX = "tt_lang_sheet_offline_row_"

/**
 * Sheet **19h** — offline, pack missing (#130 PR-20). The export calls it *"the
 * case the whole app hangs on… it does not just refuse — it offers what is
 * already on the device, so there is always a way to finish the task."*
 *
 * **Stateless, and hosted at the APP SHELL — not at the composer.** The trigger
 * is the Text composer: it raises `onOfflinePackMissing(langId)` when a
 * translation fails offline, and `MainActivityViewModel` turns that into this
 * sheet only while `ConnectivityMonitor.online` is false and there are other
 * on-device packs to offer. The reason it lives at the shell rather than
 * screen-local is the ruling's own (§0 P3, :26): the `app` module is the
 * composition root that already depends on every feature, so it can host a
 * composer-raised cross-feature sheet without `:feature:text` gaining a
 * dependency on `:feature:language`.
 *
 * A [TranzlateListSheet] — the on-device languages are its rows, each a tappable
 * "Use X" that switches the target so the next translation runs on the device.
 * The filled primary is the first of them (the export's *"Use Spanish"*), a
 * shortcut for the common case; the row list is how the user reaches any of the
 * others. The offered list already excludes the current source
 * ([MainActivityViewModel]), so no choice here can produce the same-language
 * pair 19m guards — a no-dead-end refinement of the drawn list.
 *
 * @param missingLangId the target the composer could not translate to offline —
 *   named in the body, never offered as a row (it has no pack).
 * @param onDeviceLangIds the packs the device already has, minus the source.
 *   Never empty when this sheet is composed (the host drops the request when it
 *   would be), but guarded so a stray empty list draws nothing rather than a
 *   sheet with no way to finish the task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePackMissingSheet(
    missingLangId: String,
    onDeviceLangIds: List<String>,
    onUse: (String) -> Unit,
    onClose: () -> Unit,
) {
    val firstId = onDeviceLangIds.firstOrNull() ?: return
    TranzlateListSheet(
        onDismissRequest = onClose,
        title = stringResource(R.string.lang_sheet_offline_title),
        modifier = Modifier.testTag(TT_SHEET_OFFLINE),
        icon = { WifiOffIcon() },
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_offline_use, languageLabel(firstId)),
                testTag = TT_SHEET_OFFLINE_USE,
                onClick = { onUse(firstId) },
            ),
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_offline_close),
                testTag = TT_SHEET_OFFLINE_CLOSE,
                onClick = onClose,
            ),
        body = {
            Text(stringResource(R.string.lang_sheet_offline_body, languageLabel(missingLangId)))
        },
        list = {
            items(onDeviceLangIds, key = { it }) { id ->
                OfflineLanguageRow(
                    name = languageLabel(id),
                    testTag = "$TT_SHEET_OFFLINE_ROW_PREFIX$id",
                    onUse = { onUse(id) },
                )
            }
        },
    )
}

/** The tonal slot's glyph — decorative, the title carries the meaning (scaffold KDoc). */
@Composable
private fun WifiOffIcon() {
    Icon(
        imageVector = Icons.Filled.WifiOff,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * One "ready to use" row: `cloud_done` mark + the language name, the whole 48dp
 * row a "Use %1$s" button. The mark is decorative — the row's own description
 * carries "Use Spanish" so a screen reader hears the action, not a loose glyph.
 */
@Composable
private fun OfflineLanguageRow(
    name: String,
    testTag: String,
    onUse: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val useLabel = stringResource(R.string.lang_sheet_offline_use, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TranzlateSheetDefaults.ListRowMinHeight)
                .clickable(onClick = onUse)
                .testTag(testTag)
                .semantics {
                    contentDescription = useLabel
                    role = Role.Button
                },
    ) {
        Icon(
            imageVector = Icons.Filled.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
        )
        Spacer(Modifier.width(spacing.md16))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- Preview (rule 7) — one meaningful STATE: offline with packs to offer -------
// `ModalBottomSheet` opens a window and the tooling renders nothing for one, so the
// preview draws the same anatomy on the sheet surface through `TranzlateSheetPreviewFrame`
// (the list region is a plain Column of the same rows). Literal fake data.

@PreviewLightDark
@Composable
private fun OfflinePackMissingSheetPreview() {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_offline_title),
        icon = { WifiOffIcon() },
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_offline_use, "Spanish"),
                testTag = TT_SHEET_OFFLINE_USE,
                onClick = {},
            ),
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_offline_close),
                testTag = TT_SHEET_OFFLINE_CLOSE,
                onClick = {},
            ),
        supportingContent = {
            Column(Modifier.fillMaxWidth().padding(top = LocalSpacing.current.sm8)) {
                OfflineLanguageRow(name = "Spanish", testTag = "${TT_SHEET_OFFLINE_ROW_PREFIX}es", onUse = {})
                OfflineLanguageRow(name = "English", testTag = "${TT_SHEET_OFFLINE_ROW_PREFIX}en", onUse = {})
                OfflineLanguageRow(name = "Afrikaans", testTag = "${TT_SHEET_OFFLINE_ROW_PREFIX}af", onUse = {})
            }
        },
        body = { Text(stringResource(R.string.lang_sheet_offline_body, "French")) },
    )
}
