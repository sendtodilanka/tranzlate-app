package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import com.codeboxlk.tranzlate.core.ui.languageDisplayName
import java.util.Locale

/**
 * Screen B — "Offline translation" manager (spec 02 D-E2): only MLKit-capable
 * languages, one control per row honouring the VERIFIED limits: indeterminate
 * progress, stop = delete-to-cancel, never a fake %.
 */
@Composable
fun OfflineLanguagesScreen(
    viewModel: OfflineLanguagesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsent.collectAsStateWithLifecycle()
    val alwaysAsk by viewModel.alwaysAsk.collectAsStateWithLifecycle()
    val pendingRemoval by viewModel.pendingRemoval.collectAsStateWithLifecycle()
    OfflineLanguagesContent(
        rows = rows,
        onDownload = viewModel::download,
        onStopDownload = viewModel::stopDownload,
        onRequestRemove = viewModel::requestRemove,
        onBack = onBack,
        pendingConsent = pendingConsent,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = viewModel::onAlwaysAskChange,
        onDownloadAnyway = viewModel::downloadAnyway,
        onDismissConsent = viewModel::dismissConsent,
        pendingRemoval = pendingRemoval,
        onConfirmRemove = viewModel::confirmRemove,
        onDismissRemove = viewModel::dismissRemove,
        modifier = modifier,
    )
}

@Composable
internal fun OfflineLanguagesContent(
    rows: List<OfflineLanguageRow>,
    onDownload: (String) -> Unit,
    onStopDownload: (String) -> Unit,
    onRequestRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pendingConsent: String? = null,
    alwaysAsk: Boolean = true,
    onAlwaysAskChange: (Boolean) -> Unit = {},
    onDownloadAnyway: () -> Unit = {},
    onDismissConsent: () -> Unit = {},
    pendingRemoval: PendingPackRemoval? = null,
    onConfirmRemove: () -> Unit = {},
    onDismissRemove: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val locale = LocalLocale.current.platformLocale
    // Once per data or locale change — not once per recomposition per row. The
    // picker's own KDoc makes this a rule; a CLDR lookup in a list item runs on
    // every frame a fling produces.
    val shown = remember(rows, locale) { buildOfflineRows(rows, locale) }
    // Issue #90's consent question, now asked by the SAME sheet the picker
    // raises (#130 PR-17) — one copy, one anatomy, one set of strings. Declining
    // leaves the row NotDownloaded: no spinner, no dead end.
    MobileDataSheet(
        visible = pendingConsent != null,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = onAlwaysAskChange,
        onDownloadNow = onDownloadAnyway,
        onDismiss = onDismissConsent,
    )
    // Sheets 19f/19g (#130 PR-19): the 🗑 asks before it removes. Which of the
    // two is drawn is decided in the ViewModel — the screen is handed the
    // question, not the rule. Both dismiss to the untouched row: cancelling a
    // removal leaves a downloaded pack downloaded, which is the no-dead-end
    // requirement for a destructive confirm.
    //
    // The name is resolved HERE rather than carried in the request, for the
    // reason every other row in this file resolves it here: `languageDisplayName`
    // is a CLDR lookup against the composition's locale, and a name captured in
    // a ViewModel would survive a locale change that every visible row obeys.
    val removalName = pendingRemoval?.let { languageDisplayName(it.id, locale) }.orEmpty()
    RemovePackSheet(
        visible = pendingRemoval != null && !pendingRemoval.inUseAsTarget,
        languageName = removalName,
        onRemove = onConfirmRemove,
        onDismiss = onDismissRemove,
    )
    RemoveInUseSheet(
        visible = pendingRemoval != null && pendingRemoval.inUseAsTarget,
        languageName = removalName,
        savedCount = pendingRemoval?.savedCount ?: 0,
        onRemoveAnyway = onConfirmRemove,
        onDismiss = onDismissRemove,
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Issue #88 (owner + M3 breakpoints): fill the width; the shim lands
            // the 16dp-based rows on the 24dp medium margin.
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = adaptiveMarginShim())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = spacing.xs4),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tt_offline_back")) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.offline_cd_back),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.offline_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.offline_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.offline_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.lg24).testTag("tt_offline_empty"),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("tt_offline_list")) {
                        items(shown, key = OfflinePackRow::id) { row ->
                            OfflineRow(
                                row = row,
                                onDownload = onDownload,
                                onStopDownload = onStopDownload,
                                onRequestRemove = onRequestRemove,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineRow(
    row: OfflinePackRow,
    onDownload: (String) -> Unit,
    onStopDownload: (String) -> Unit,
    onRequestRemove: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = spacing.md16, end = spacing.xs4)
                .testTag("tt_offline_row"),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The SAME name the picker's rows carry. Until PR-6 this screen
                // lived in a module with no access to `languageDisplayName`, so
                // it rendered the catalog's own English string: the picker said
                // "Bangla" and this list said "Bengali", for one language, in
                // one app, on a plain English device.
                text = row.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            // Issue #90 (EDGE_CASES no-dead-end): a Failed row explains WHY —
            // ↻ on a full disk re-fails silently otherwise. This is an error
            // line, not the always-on sub-line the owner removed in #82.
            //
            // The sentence itself comes from `DownloadFailure.kt` and is the SAME
            // one the picker's failed row shows (#175, PR-18). This screen used
            // to spell its own `when` over the same enum into its own three
            // string keys, so one dropped connection read differently depending
            // on which of the two screens the user was standing in.
            if (row.state is OfflineModelState.Failed) {
                Text(
                    text = stringResource(downloadFailureCopy(row.state.cause).rowLine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("tt_offline_error_line"),
                )
            }
        }
        // Owner (issue #82): the STATE is the single trailing control — the
        // old app's pattern (reference read, written fresh): ⬇ / ◌+⏹ / 🗑 / ↻.
        when (row.state) {
            OfflineModelState.NotDownloaded -> {
                IconButton(
                    onClick = { onDownload(row.id) },
                    modifier = Modifier.testTag("tt_offline_download"),
                ) {
                    Icon(
                        Icons.Outlined.DownloadForOffline,
                        contentDescription = stringResource(R.string.offline_cd_download, row.displayName),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OfflineModelState.Downloading -> {
                IconButton(
                    // The verified stop = delete-to-cancel, and deliberately NOT
                    // confirm-sheeted: 19f is about removing a pack the user has,
                    // and this is the way out of a download they no longer want
                    // (`OfflineLanguagesViewModel.stopDownload`).
                    onClick = { onStopDownload(row.id) },
                    modifier = Modifier.testTag("tt_offline_stop"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.offline_cd_stop, row.displayName),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            OfflineModelState.Downloaded -> {
                IconButton(
                    // Asks now (#130 PR-19). Until this PR the tap deleted the
                    // pack outright with nothing in between — the "unconfirmed 🗑"
                    // the rev3 ruling's PR-19 row names.
                    onClick = { onRequestRemove(row.id) },
                    modifier = Modifier.testTag("tt_offline_delete"),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.offline_cd_delete, row.displayName),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OfflineModelState.Deleting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("tt_offline_deleting"),
                    strokeWidth = 2.dp,
                )
            }

            is OfflineModelState.Failed -> {
                IconButton(
                    onClick = { onDownload(row.id) },
                    modifier = Modifier.testTag("tt_offline_retry"),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.offline_cd_retry, row.displayName),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OfflineModelState.OnlineOnly -> {
                Unit
            } // never listed (VM filters)
        }
    }
}

// ── Previews (owner convention: every screen AND every custom M3 item ships
// @PreviewLightDark). Literal fake rows — previews never touch DI.

private val previewRows =
    listOf(
        // `bn` earns its place. The catalog calls it "Bengali" and CLDR calls it
        // "Bangla", so it is the one row here that reads differently after this
        // change — a preview set where every name happened to match the catalog
        // would have shown the owner nothing, which is what a lens found.
        OfflineLanguageRow("bn", "Bengali", OfflineModelState.Downloaded),
        OfflineLanguageRow("fr", "French", OfflineModelState.Downloaded),
        OfflineLanguageRow("de", "German", OfflineModelState.Downloading),
        OfflineLanguageRow("es", "Spanish", OfflineModelState.NotDownloaded),
        OfflineLanguageRow("it", "Italian", OfflineModelState.Deleting),
        OfflineLanguageRow("pt", "Portuguese", OfflineModelState.Failed(OfflineModelFailure.STORAGE)),
    )

/** What the screen actually renders — same mapping, so a preview cannot drift from it. */
private val previewShown = buildOfflineRows(previewRows, Locale.ENGLISH)

@PreviewLightDark
@Composable
private fun OfflineLanguagesScreenPreview() {
    TranzlateTheme {
        OfflineLanguagesContent(
            rows = previewRows,
            onDownload = {},
            onStopDownload = {},
            onRequestRemove = {},
            onBack = {},
        )
    }
}

// EVERY sheet this screen raises is previewed somewhere else, and none of them
// can be previewed here. `ModalBottomSheet` opens a window and the preview
// tooling renders no window, so a preview passing the sheet's state would draw
// the plain list while claiming to show the sheet — worse than no preview.
//
// This note named only the metered-consent sheet (19a) and was never extended
// when PR-19 added two more, which left the newer omissions looking like
// oversights beside a documented one (#243). The full list, and where each is
// drawn:
//
//   19a  mobile-data consent   → `MobileDataSheet.kt`
//   19f  remove pack           → `RemovePackSheets.kt`
//   19g  remove the in-use pack → `RemovePackSheets.kt`
//   19b  no space             → `PackFailureSheets.kt`
//   19d  interrupted          → `PackFailureSheets.kt`
//
// Those files draw the sheet ANATOMY through `TranzlateSheetPreviewFrame` rather
// than calling the real composable, for the same windowing reason — so a change
// to a real sheet's arguments leaves its preview drawing the old sheet. That is
// a known, filed limitation, not a thing to fix here: it needs a preview scanner
// or a screenshot library this repo does not have (#243's closing section, #193).

/** Empty/loading face — the rows have not arrived yet. */
@PreviewLightDark
@Composable
private fun OfflineLanguagesLoadingPreview() {
    TranzlateTheme {
        OfflineLanguagesContent(
            rows = emptyList(),
            onDownload = {},
            onStopDownload = {},
            onRequestRemove = {},
            onBack = {},
        )
    }
}

/**
 * THE ITEM the owner called out: one row per state, so the single trailing
 * control (⬇ / spinner+stop / 🗑 / spinner / ↻ + cause line) is reviewable on
 * both themes without launching the app.
 *
 * **All THREE failure sentences, since #243.** `downloadFailureCopy` produces
 * three distinct lines and this preview drew two — `STORAGE` from
 * `previewRows` and `NETWORK` appended below — leaving
 * `lang_pack_error_generic` ("Download didn't finish. Try again.") invisible.
 * Because PR-18 made this screen and the picker read ONE map, that one omission
 * hid the sentence on both screens at once, and rule 7 exists because the owner
 * reviews UI from previews rather than from the app.
 *
 * `WIFI_REQUIRED` is deliberately absent: it folds onto the network line by
 * design (`DownloadFailure.kt`, fold pinned by `DownloadFailureTest`), so a
 * fourth row would draw a duplicate sentence and imply a fourth cause.
 */
@PreviewLightDark
@Composable
private fun OfflineRowStatesPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                previewShown.forEach { row ->
                    OfflineRow(row = row, onDownload = {}, onStopDownload = {}, onRequestRemove = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                OfflineRow(
                    row =
                        OfflinePackRow(
                            "nl",
                            "Dutch",
                            OfflineModelState.Failed(OfflineModelFailure.NETWORK),
                        ),
                    onDownload = {},
                    onStopDownload = {},
                    onRequestRemove = {},
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OfflineRow(
                    row =
                        OfflinePackRow(
                            "sv",
                            "Swedish",
                            OfflineModelState.Failed(OfflineModelFailure.UNKNOWN),
                        ),
                    onDownload = {},
                    onStopDownload = {},
                    onRequestRemove = {},
                )
            }
        }
    }
}
