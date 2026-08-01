package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark

/**
 * The list-carrying variant of the spec-§5 sheet anatomy (issue #130, U-2):
 * the same skeleton as [TranzlateSheetScaffold] with a full-width [LazyColumn]
 * region between the header and the actions — 19h's ready-to-use list, 20c's
 * pack actions, 20e's cleanup checkboxes.
 *
 * Rows are CALLER-built (strings, testTags and row anatomy belong to the
 * feature module) against the spec's compact 48dp metric: apply
 * `Modifier.heightIn(min = TranzlateSheetDefaults.ListRowMinHeight)` per row.
 * The list takes at most the height left over by header and actions
 * (`weight(fill = false)`), so a long list scrolls while both actions and the
 * drag handle stay reachable — the sheet can never scroll its own dismiss or
 * decide controls out of existence (EDGE_CASES no-dead-end).
 *
 * Actions are OPTIONAL here (a pure action-list sheet like 20c decides through
 * its rows); when present the same ≤2/likely-intent-filled contract holds, and
 * a secondary without a primary is rejected ([validateSheetActions]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranzlateListSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    tone: TranzlateSheetTone = TranzlateSheetTone.Neutral,
    icon: (@Composable () -> Unit)? = null,
    primaryAction: TranzlateSheetAction? = null,
    secondaryAction: TranzlateSheetAction? = null,
    listState: LazyListState = rememberLazyListState(),
    body: @Composable () -> Unit = {},
    list: LazyListScope.() -> Unit,
) {
    TranzlateSheetHost(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        TranzlateSheetLayout(
            title = title,
            tone = tone,
            icon = icon,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            midContent = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    content = list,
                )
            },
            body = body,
        )
    }
}

// ---- Preview (Rule 7) — layout on the sheet surface, literal fake rows --------------------------

/** 20e-like cleanup shape: icon + body + compact 48dp rows + two actions. */
@PreviewLightDark
@Composable
private fun ListSheetPreview() {
    SheetPreviewSurface {
        TranzlateSheetLayout(
            title = "Free up space",
            tone = TranzlateSheetTone.Neutral,
            icon = {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    null,
                    Modifier.size(TranzlateSheetDefaults.IconSize),
                )
            },
            primaryAction = TranzlateSheetAction("Remove 2 packs", "tt_preview_primary", {}),
            secondaryAction = TranzlateSheetAction("Keep both", "tt_preview_secondary", {}),
            midContent = {
                Column(Modifier.fillMaxWidth()) {
                    ListSheetPreviewRow("German", "Last used 3 April")
                    ListSheetPreviewRow("Polish", "Last used 11 April")
                }
            },
            body = { Text("These packs have not been used since April. Removing them frees space.") },
        )
    }
}

@Composable
private fun ListSheetPreviewRow(
    name: String,
    detail: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TranzlateSheetDefaults.ListRowMinHeight),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
