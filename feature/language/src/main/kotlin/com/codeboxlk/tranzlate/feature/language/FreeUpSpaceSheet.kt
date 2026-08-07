package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateListSheet
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetAction
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetDefaults
import com.codeboxlk.tranzlate.core.designsystem.TranzlateSheetPreviewFrame
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.languageAvatarCode

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
 * @param nowMillis the clock each stale row reads to turn its [PackUsage.Used] stamp
 *   into a relative "4 months ago" line ([packUsageText]) — the same instant the
 *   management list uses, so the two can never disagree (#325).
 * @param onRemovePacks the CHECKED pack ids, in one batch. The caller wires this to
 *   [OfflineLanguagesViewModel.removePacks] and dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreeUpSpaceSheet(
    visible: Boolean,
    stalePacks: List<PackRow>,
    storage: StorageCard?,
    nowMillis: Long,
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

    // The secondary action dismisses without removing anything, so it KEEPS every pack
    // the sheet lists that is not already on its way out — not only the checked ones.
    // Count-aware to the 20e frame: "Keep both" for the common two-pack case.
    val keepableCount = stalePacks.count { it.state != OfflineModelState.Deleting }

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
                label = keepActionLabel(keepableCount),
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
                    nowMillis = nowMillis,
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
 * One stale pack: a checkbox, the pack's identity avatar ([StalePackAvatar]), its
 * name, and its relative last-used age in the SAME words the management row uses
 * ([packUsageText] — never a calendar month, ruling ⑧). The whole row is the 48dp+
 * toggle target (`toggleable` + `Role.Checkbox`), so the `Checkbox` takes
 * `onCheckedChange = null` and adds no second toggle node — the row's merged content
 * is the toggle's accessible label. The avatar monogram is a decorative echo of the
 * name, drawn like the management row's avatar. The same shape [MobileDataSheet]'s
 * standing-preference row uses.
 *
 * A `Deleting` pack is shown, not hidden, but as a spinner in place of the checkbox:
 * it is already being removed, so it is not a choice to make.
 */
@Composable
private fun StalePackCheckRow(
    row: PackRow,
    checked: Boolean,
    nowMillis: Long,
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
        StalePackAvatar(id = row.id, modifier = Modifier.padding(start = spacing.md16))
        Column(modifier = Modifier.weight(1f).padding(start = spacing.md16)) {
            Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = packUsageText(row.usage, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The stale pack's identity chip — its primary subtag in a tonal circle, the SAME
 * flags-are-wrong-for-languages avatar the management list and the picker draw (size
 * [Dimensions.iconChip], full-pill shape, `primaryContainer` tone). A stale pack is
 * by definition installed, so it always wears the downloaded tone; the monogram is
 * decorative ([languageAvatarCode] is a duplicate of the name beside it, never the
 * primary label).
 *
 * A LOCAL copy rather than a shared composable, because the two existing avatars —
 * `OfflineLanguagesScreen`'s `PackAvatar` and `LanguagePickerScreen`'s
 * `LanguageAvatarCircle` — are private to files an in-flight PR owns; folding the
 * three into one shared avatar is a follow-up.
 */
@Composable
private fun StalePackAvatar(
    id: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(Dimensions.iconChip)
                .clip(TranzlateShapeFull)
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            text = languageAvatarCode(id),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * The secondary action's label. Dismiss keeps EVERY listed pack, so it is count-aware
 * to the 20e frame: "Keep it" for a lone stale pack, "Keep both" for the common
 * two-pack case, and "Keep all N" for three or more. All three are plain strings:
 * only "Keep all N" varies with the number, and it does so uniformly (no
 * singular/plural split above two), so a `%d` string expresses it without plural
 * machinery — which also sidesteps the pt-BR `one`/`many` quantities Android lint
 * would otherwise demand of a plural (`ImpliedQuantity` / `MissingQuantity`).
 */
@Composable
private fun keepActionLabel(count: Int): String =
    when (count) {
        1 -> stringResource(R.string.lang_sheet_free_keep_one)
        2 -> stringResource(R.string.lang_sheet_free_keep_both)
        else -> stringResource(R.string.lang_sheet_free_keep_all, count)
    }

// ---- Previews (rule 7 — one per meaningful STATE) ------------------------------------------------
// `ModalBottomSheet` opens a window the tooling renders nothing for, so these use the
// design system's `TranzlateSheetPreviewFrame` — the public preview-only escape that
// lays a sheet's real anatomy (icon, title, body, list region and the ACTION BAR) on
// the floating surface, so the owner reviews the whole sheet: the new per-pack avatars
// and the count-aware "Keep both" / "Keep all N" secondary together.

private const val PREVIEW_MB = 1_048_576L
private const val PREVIEW_GB = 1_073_741_824L

/** A fixed instant, and a month in millis, so each preview pack's dated "N months ago" line is stable. */
private val previewNow = 300L * DAY_MILLIS
private val previewMonthMillis = 30L * DAY_MILLIS

private fun previewRow(
    id: String,
    name: String,
    monthsAgo: Int,
    state: OfflineModelState = OfflineModelState.Downloaded,
) = PackRow(
    id = id,
    displayName = name,
    state = state,
    // A REAL stamp `monthsAgo` months in the past — coherent by construction, the honest
    // "N months ago" the collapse makes unfabricatable (closes the #325 audit sub-note).
    usage = PackUsage.Used(previewNow - monthsAgo * previewMonthMillis),
    inUse = false,
)

/** The frame's pair — German + Polish, both stale: the "Keep both" / "Remove 2 packs" state. */
private val previewTwoStale = listOf(previewRow("de", "German", 4), previewRow("pl", "Polish", 6))

/** Three stale packs — the plural "Keep all 3" / "Remove 3 packs" state. */
private val previewThreeStale = previewTwoStale + previewRow("sv", "Swedish", 3)

private val previewStorage =
    StorageCard.Sized(
        packCount = 5,
        packsBytes = 110 * PREVIEW_MB,
        freeBytes = 3 * PREVIEW_GB / 2,
        totalBytes = 64 * PREVIEW_GB,
    )

/** The frame state: two stale packs, both checked — the secondary reads "Keep both". */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetPreview() {
    FreeUpSpaceSheetPreviewFrame(stalePacks = previewTwoStale, checkedIds = setOf("de", "pl"))
}

/** Three or more stale packs — the secondary reads "Keep all 3". */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetManyPacksPreview() {
    FreeUpSpaceSheetPreviewFrame(stalePacks = previewThreeStale, checkedIds = setOf("de", "pl", "sv"))
}

/** A removal already in flight: one pack is `Deleting` (a spinner, not a choice); the two kept read "Keep both". */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetMidRemovePreview() {
    val midRemove =
        listOf(
            previewThreeStale[0].copy(state = OfflineModelState.Deleting),
            previewThreeStale[1],
            previewThreeStale[2],
        )
    FreeUpSpaceSheetPreviewFrame(stalePacks = midRemove, checkedIds = setOf("pl", "sv"))
}

/** Nothing to clean up — the no-dead-end empty state: body + a single Close. */
@PreviewLightDark
@Composable
private fun FreeUpSpaceSheetEmptyPreview() {
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_free_title),
        primaryAction =
            TranzlateSheetAction(
                label = stringResource(R.string.lang_sheet_free_close),
                testTag = TT_SHEET_FREE_CLOSE,
                onClick = {},
            ),
        icon = { CleaningIcon() },
        body = { Text(stringResource(R.string.lang_sheet_free_empty)) },
        supportingContent = { StorageCardView(previewStorage) },
    )
}

/**
 * The populated sheet on the floating surface: storage breakdown, the stale rows with
 * their avatars, and the real action bar. Remove counts the CHECKED packs; Keep is
 * count-aware over what is still keepable — the two agree when nothing is unchecked.
 */
@Composable
private fun FreeUpSpaceSheetPreviewFrame(
    stalePacks: List<PackRow>,
    checkedIds: Set<String>,
) {
    val spacing = LocalSpacing.current
    val keepable = stalePacks.count { it.state != OfflineModelState.Deleting }
    val checked = stalePacks.count { it.state != OfflineModelState.Deleting && it.id in checkedIds }
    TranzlateSheetPreviewFrame(
        title = stringResource(R.string.lang_sheet_free_title),
        primaryAction =
            TranzlateSheetAction(
                label = pluralStringResource(R.plurals.lang_sheet_free_remove, checked, checked),
                testTag = TT_SHEET_FREE_REMOVE,
                onClick = {},
                enabled = checked > 0,
            ),
        secondaryAction =
            TranzlateSheetAction(
                label = keepActionLabel(keepable),
                testTag = TT_SHEET_FREE_CANCEL,
                onClick = {},
            ),
        icon = { CleaningIcon() },
        body = { Text(stringResource(R.string.lang_sheet_free_body)) },
        supportingContent = {
            StorageCardView(previewStorage, Modifier.padding(bottom = spacing.sm8))
            stalePacks.forEach { row ->
                StalePackCheckRow(
                    row = row,
                    checked = row.id in checkedIds,
                    nowMillis = previewNow,
                    onToggle = {},
                )
            }
        },
    )
}

/** The stale-pack row in isolation — checkbox / spinner, the new monogram avatar, name + last-used. */
@PreviewLightDark
@Composable
private fun StalePackCheckRowPreview() {
    val spacing = LocalSpacing.current
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(horizontal = spacing.lg24, vertical = spacing.md16)) {
                StalePackCheckRow(row = previewThreeStale[0], checked = true, nowMillis = previewNow, onToggle = {})
                StalePackCheckRow(row = previewThreeStale[1], checked = false, nowMillis = previewNow, onToggle = {})
                StalePackCheckRow(
                    row = previewThreeStale[2].copy(state = OfflineModelState.Deleting),
                    checked = false,
                    nowMillis = previewNow,
                    onToggle = {},
                )
            }
        }
    }
}
