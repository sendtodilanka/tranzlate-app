package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateListSheet
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState

/** 20e cleanup-sheet controls — `tt_lang_sheet_free_*` (the rev3 sheet namespace, C-1). */
internal const val TT_SHEET_FREE = "tt_lang_sheet_free"
internal const val TT_SHEET_FREE_REMOVE = "tt_lang_sheet_free_remove"
internal const val TT_SHEET_FREE_CANCEL = "tt_lang_sheet_free_cancel"
internal const val TT_SHEET_FREE_CLOSE = "tt_lang_sheet_free_close"

/** One stale-pack checkbox row's tag, per id, so a test can address a single row. */
internal fun freeRowTag(id: String): String = "tt_lang_sheet_free_row_$id"

/**
 * Sheet **20e — "Free up space"** (#130 PR-25), the batch-cleanup sheet the storage
 * hygiene nudge and 19b's "Free up space" action open.
 *
 * ## What it lists, and the honesty rule that decides it
 *
 * ONLY the packs [stalePacks] returns: installed, removable, and provably stale — a
 * real translation-use date at or past the 90-day threshold. A pack with no
 * recorded use ([PackUsage.NoRecord]) is **never** here, because staleness needs a
 * real date and a pre-checked selection must not fabricate one (ruling ⑧, brief
 * §7b). The set is identical to the one the nudge counts — both route through
 * [stalePacks] — so the nudge can never say "3 packs" over a sheet listing 2.
 *
 * Each stale pack starts **checked**, in a [rememberSaveable] selection that
 * survives process death: a user who cleans up, backgrounds the app while a system
 * kill happens, and returns finds their choices intact rather than reset to all-on.
 * `OfflineLanguagesScreen`'s `rememberSaveable` is not host-scoped the way the
 * picker's is (`PickerHostAgnosticTest` bans it only there — this screen has one
 * host, the nav entry), so the ordinary saveable slot is exactly right.
 *
 * ## The storage breakdown, drawn once and reused
 *
 * The used-vs-free bar plus the packs figure standing on its own is 20e's to show
 * (README.md:15). It is the SAME [StorageCardView] Manage packs draws — reused, not
 * re-implemented, so there is one vocabulary and one colour for storage across the
 * whole feature.
 *
 * ## Removing is free to undo
 *
 * The body states plainly that re-downloading is free (packs are free and
 * unlimited): the cleanup is reversible, so no one hesitates over it. Nothing here
 * re-downloads — that path stays the honest [OfflineLanguagesViewModel.download]
 * (PR-23's `reportOutcome`); 20e only removes, in one batch, via
 * [OfflineLanguagesViewModel.removePacks].
 *
 * ## No dead end, empty included
 *
 * A `Deleting` pack shows a spinner rather than a checkbox — it is already on its
 * way out, so it is neither selectable nor counted. When nothing qualifies (every
 * pack used recently, or the stale set was just cleared), the sheet says so and
 * offers a single Close, never a blank list under a disabled Remove.
 *
 * Stateless and screen-local, following [RemovePackSheet] / [PackActionsSheet]: the
 * open/closed flag lives in the screen, and the default `SheetState` is taken rather
 * than hoisted. The `@OptIn` is for [TranzlateListSheet]'s default `sheetState`
 * argument, evaluated at this call site; nothing here names it.
 *
 * @param onRemovePacks the CHECKED pack ids, in one batch. The caller wires this to
 *   [OfflineLanguagesViewModel.removePacks] and dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreeUpSpaceSheet(
    visible: Boolean,
    stalePacks: List<PackRow>,
    storage: StorageCard?,
    onRemovePacks: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    // The pre-checked selection, saved across process death (brief · test-pinned).
    // A `Deleting` pack is never in it: it is already being removed, so offering to
    // remove it again would be a no-op affordance.
    val selected =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<String>, String>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) {
            stalePacks
                .filter { it.state != OfflineModelState.Deleting }
                .map(PackRow::id)
                .toMutableStateList()
        }

    // Count / remove from the CURRENT stale set intersected with the selection, so a
    // pack that left the list underneath (removed elsewhere) never inflates the count
    // or reaches the batch.
    val selectedIds =
        stalePacks
            .filter { it.state != OfflineModelState.Deleting && it.id in selected }
            .map(PackRow::id)
    val selectedCount = selectedIds.size

    if (stalePacks.isEmpty()) {
        EmptyFreeUpSpaceSheet(storage = storage, onDismiss = onDismiss)
        return
    }

    TranzlateListSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_free_title),
        modifier = Modifier.testTag(TT_SHEET_FREE),
        icon = { CleaningIcon() },
        primaryAction =
            TranzlateSheetAction(
                label = pluralStringResource(R.plurals.lang_sheet_free_remove, selectedCount, selectedCount),
                testTag = TT_SHEET_FREE_REMOVE,
                onClick = {
                    onRemovePacks(selectedIds)
                    onDismiss()
                },
                enabled = selectedCount > 0,
            ),
        secondaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_remove_cancel),
                testTag = TT_SHEET_FREE_CANCEL,
                onClick = onDismiss,
            ),
        body = { Text(stringResource(R.string.lang_sheet_free_body)) },
        list = {
            storage?.let { card ->
                item(key = "storage") {
                    StorageCardView(card, Modifier.padding(bottom = LocalSpacing.current.sm8))
                }
            }
            items(stalePacks, key = { "free_${it.id}" }) { row ->
                StalePackCheckRow(
                    row = row,
                    checked = row.id in selected,
                    onToggle = { if (row.id in selected) selected.remove(row.id) else selected.add(row.id) },
                )
            }
        },
    )
}

/**
 * 20e with nothing to clean up (brief edge state / EDGE_CASES no-dead-end): the
 * breakdown still shows if it can, the body says why the list is empty, and the one
 * action is a Close. No Remove — there is nothing to remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyFreeUpSpaceSheet(
    storage: StorageCard?,
    onDismiss: () -> Unit,
) {
    TranzlateListSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.lang_sheet_free_title),
        modifier = Modifier.testTag(TT_SHEET_FREE),
        icon = { CleaningIcon() },
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_free_close),
                testTag = TT_SHEET_FREE_CLOSE,
                onClick = onDismiss,
            ),
        body = { Text(stringResource(R.string.lang_sheet_free_empty)) },
        list = {
            storage?.let { card ->
                item(key = "storage") { StorageCardView(card) }
            }
        },
    )
}

/** The tonal slot's glyph — the same cleanup mark the nudge carries. Decorative; the title carries the meaning. */
@Composable
private fun CleaningIcon() {
    Icon(
        imageVector = Icons.Filled.CleaningServices,
        contentDescription = null,
        modifier = Modifier.size(TranzlateSheetDefaults.IconSize),
    )
}

/**
 * One stale pack: a checkbox, its name, and its relative last-used age in the SAME
 * words the management row uses ([packUsageText] — never a calendar month, ruling
 * ⑧). The whole row is the 48dp+ toggle target (`toggleable` + `Role.Checkbox`), so
 * the `Checkbox` takes `onCheckedChange = null` and contributes no second semantics
 * node — the row's merged name + age is the toggle's accessible label. The same
 * shape [MobileDataSheet]'s standing-preference row uses.
 *
 * A `Deleting` pack is shown, not hidden, but as a spinner in place of the checkbox:
 * it is already being removed, so it is not a choice to make.
 */
@Composable
private fun StalePackCheckRow(
    row: PackRow,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val deleting = row.state == OfflineModelState.Deleting
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TranzlateSheetDefaults.ListRowMinHeight)
                .then(
                    if (deleting) {
                        Modifier
                    } else {
                        Modifier.toggleable(value = checked, onValueChange = { onToggle() }, role = Role.Checkbox)
                    },
                ).testTag(freeRowTag(row.id))
                .padding(vertical = spacing.sm8),
    ) {
        if (deleting) {
            CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSm), strokeWidth = 2.dp)
        } else {
            Checkbox(checked = checked, onCheckedChange = null)
        }
        Column(modifier = Modifier.weight(1f).padding(start = spacing.md16)) {
            Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = packUsageText(row.usage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Previews (rule 7 — one per meaningful STATE) ------------------------------------------------
// `ModalBottomSheet` opens a window the tooling renders nothing for, so — as
// `PackActionsSheet` does — these lay the sheet's anatomy out directly on a surface
// rather than through the modal host, drawing the title, body, breakdown, rows and
// the action so the owner reviews the whole sheet.

private const val PREVIEW_MB = 1_048_576L
private const val PREVIEW_GB = 1_073_741_824L

private fun previewRow(
    id: String,
    name: String,
    monthsAgo: Int,
    state: OfflineModelState = OfflineModelState.Downloaded,
) = PackRow(
    id = id,
    displayName = name,
    state = state,
    usage = PackUsage.MonthsAgo(monthsAgo),
    lastUsedMillis = 1L,
    inUse = false,
    isPivot = false,
)

private val previewStale =
    listOf(
        previewRow("de", "German", 4),
        previewRow("pl", "Polish", 6),
        previewRow("sv", "Swedish", 3),
    )

private val previewStorage =
    StorageCard.Sized(
        packCount = 5,
        packsBytes = 110 * PREVIEW_MB,
        freeBytes = 3 * PREVIEW_GB / 2,
        totalBytes = 64 * PREVIEW_GB,
    )

/** Several stale packs, all checked — the state the nudge opens into. */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetPreview() {
    FreeUpSpaceSheetPreviewBody(
        stalePacks = previewStale,
        checkedIds = previewStale.map(PackRow::id).toSet(),
        empty = false,
    )
}

/** A removal already in flight: one pack is `Deleting` (a spinner, not a choice), the rest still checked. */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetMidRemovePreview() {
    val midRemove =
        listOf(
            previewStale[0].copy(state = OfflineModelState.Deleting),
            previewStale[1],
            previewStale[2],
        )
    FreeUpSpaceSheetPreviewBody(
        stalePacks = midRemove,
        checkedIds = setOf("pl", "sv"),
        empty = false,
    )
}

/** Nothing to clean up — the no-dead-end empty state: body + a single Close. */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetEmptyPreview() {
    FreeUpSpaceSheetPreviewBody(stalePacks = emptyList(), checkedIds = emptySet(), empty = true)
}

@Composable
private fun FreeUpSpaceSheetPreviewBody(
    stalePacks: List<PackRow>,
    checkedIds: Set<String>,
    empty: Boolean,
) {
    val spacing = LocalSpacing.current
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(horizontal = spacing.lg24, vertical = spacing.md16)) {
                Text(
                    text = stringResource(R.string.lang_sheet_free_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (empty) R.string.lang_sheet_free_empty else R.string.lang_sheet_free_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xs4, bottom = spacing.md16),
                )
                StorageCardView(previewStorage, Modifier.padding(bottom = spacing.sm8))
                stalePacks.forEach { row ->
                    StalePackCheckRow(row = row, checked = row.id in checkedIds, onToggle = {})
                }
            }
        }
    }
}
