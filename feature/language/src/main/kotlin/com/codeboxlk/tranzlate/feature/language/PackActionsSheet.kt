package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateListSheet
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/** 20c pack-actions sheet controls — `tt_lang_sheet_pack_*` (the rev3 sheet namespace, C-1). */
internal const val TT_SHEET_PACK_ACTIONS = "tt_lang_sheet_pack_actions"
internal const val TT_SHEET_PACK_USE = "tt_lang_sheet_pack_use"
internal const val TT_SHEET_PACK_VOICE = "tt_lang_sheet_pack_voice"
internal const val TT_SHEET_PACK_REMOVE = "tt_lang_sheet_pack_remove"

/**
 * What the 20c sheet needs to draw itself — exactly that, and no more. Mapped from
 * a [PackRow] when the overflow opens. A small model of its own rather than the
 * whole row: the sheet then declares its real dependencies (id + name + voice), and
 * its previews use literal fake data without building a full `PackRow`.
 */
@Immutable
data class PackActionsTarget(
    val id: String,
    val displayName: String,
    val hasOfflineVoice: Boolean,
)

/**
 * Sheet **20c** — the pack-actions list sheet (#130 PR-24), raised from a DOWNLOADED
 * pack's overflow (`more_vert`) on Manage packs. A pure action-LIST sheet
 * ([TranzlateListSheet]): the rows ARE the actions, so there is no primary button.
 *
 * Three rows, at most two of them actions:
 * - **Use as target now** — makes this the language the app translates INTO, written
 *   through the SAME `TranslatePrefsRepository` path the picker uses
 *   (`OfflineLanguagesViewModel.useAsTarget`), so the composer's chip and this screen
 *   agree by construction. `onUseAsTarget` carries the pack id up to that write.
 * - **A voice line** — drawn IFF this device also has an offline voice for the
 *   language ([PackActionsTarget.hasOfflineVoice], the same `Language.hasOfflineVoice`
 *   the picker's speaker mark reads). Informational, NOT tappable: a voice is a
 *   separate install and nothing on this sheet changes it, so a decorative row that
 *   did nothing on tap would be the dead affordance EDGE_CASES §7 refuses.
 * - **Remove pack** — routes to the EXISTING 19f/19g remove-confirm flow through
 *   `onRemove`; it never deletes on the tap (the confirm sheet does). Loss tone —
 *   error colour, which spec §5 reserves for loss and for stopping.
 *
 * Stateless and screen-local, following [RemovePackSheet] / [MobileDataSheet]: the
 * open pack lives in the screen's own state ([target] `null` = not shown), and the
 * default `SheetState` is taken rather than hoisted (a hoisted one is
 * `rememberSaveable` underneath, which `PickerHostAgnosticTest` bans by name).
 *
 * The `@OptIn` is for [TranzlateListSheet]'s DEFAULT `sheetState` argument, whose
 * expression is evaluated at this call site; nothing here names it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PackActionsSheet(
    target: PackActionsTarget?,
    onUseAsTarget: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    TranzlateListSheet(
        onDismissRequest = onDismiss,
        title = target.displayName,
        modifier = Modifier.testTag(TT_SHEET_PACK_ACTIONS),
        list = {
            item(key = "use") {
                PackActionRow(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.manage_actions_use_target),
                    testTag = TT_SHEET_PACK_USE,
                    onClick = { onUseAsTarget(target.id) },
                )
            }
            // IFF the device has an offline voice for this language. Absent — not a
            // disabled or greyed row — when it does not, so the sheet never draws a
            // line about a capability this device lacks.
            if (target.hasOfflineVoice) {
                item(key = "voice") { PackVoiceLine() }
            }
            item(key = "remove") {
                PackActionRow(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.manage_actions_remove),
                    testTag = TT_SHEET_PACK_REMOVE,
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = { onRemove(target.id) },
                )
            }
        },
    )
}

/**
 * One tappable action row: a leading glyph and a label, both in [contentColor]
 * (error for the destructive Remove, `onSurface` otherwise). The whole row is the
 * 48 dp+ touch target ([TranzlateSheetDefaults.ListRowMinHeight]); `clickable`
 * gives it the button role and merges the label as its accessible name, so the
 * glyph stays decorative (`contentDescription = null`).
 */
@Composable
private fun PackActionRow(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TranzlateSheetDefaults.ListRowMinHeight)
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(vertical = spacing.sm8),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimensions.iconSm),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.padding(start = spacing.md16),
        )
    }
}

/**
 * The informational voice line — a speaker glyph and the same "can be spoken
 * offline" fact the picker's mark states. NOT clickable (nothing here installs or
 * removes a voice), and merged into one semantics node so TalkBack reads it as a
 * single line rather than a glyph the user could mistake for a control.
 */
@Composable
private fun PackVoiceLine() {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TranzlateSheetDefaults.ListRowMinHeight)
                .semantics(mergeDescendants = true) {}
                .testTag(TT_SHEET_PACK_VOICE)
                .padding(vertical = spacing.sm8),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.iconSm),
        )
        Text(
            text = stringResource(R.string.manage_actions_voice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = spacing.md16),
        )
    }
}

// ---- Previews (rule 7 — one per meaningful state: with / without the voice line) ----
// `ModalBottomSheet` opens a window the tooling renders nothing for, so — as the
// design system's own list-sheet preview does — these lay the rows out directly on a
// surface instead of through the modal host. The title is drawn too, so the owner
// reviews the whole sheet, not just its rows.

/** 20c for a pack whose language this device can also speak offline — the voice line shows. */
@PreviewLightDark
@Composable
private fun PackActionsSheetWithVoicePreview() {
    PackActionsSheetPreviewBody(PackActionsTarget("es", "Spanish", hasOfflineVoice = true))
}

/** 20c for a pack with no offline voice — the voice line is absent, not a greyed row. */
@PreviewLightDark
@Composable
private fun PackActionsSheetNoVoicePreview() {
    PackActionsSheetPreviewBody(PackActionsTarget("af", "Afrikaans", hasOfflineVoice = false))
}

@Composable
private fun PackActionsSheetPreviewBody(target: PackActionsTarget) {
    val spacing = LocalSpacing.current
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(horizontal = spacing.lg24, vertical = spacing.md16)) {
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = spacing.sm8),
                )
                PackActionRow(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.manage_actions_use_target),
                    testTag = TT_SHEET_PACK_USE,
                    onClick = {},
                )
                if (target.hasOfflineVoice) PackVoiceLine()
                PackActionRow(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.manage_actions_remove),
                    testTag = TT_SHEET_PACK_REMOVE,
                    contentColor = MaterialTheme.colorScheme.error,
                    onClick = {},
                )
            }
        }
    }
}
