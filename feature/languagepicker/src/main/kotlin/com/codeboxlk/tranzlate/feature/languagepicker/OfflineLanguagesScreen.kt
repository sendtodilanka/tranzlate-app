package com.codeboxlk.tranzlate.feature.languagepicker

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    OfflineLanguagesContent(
        rows = rows,
        onDownload = viewModel::download,
        onDelete = viewModel::delete,
        onBack = onBack,
        pendingConsent = pendingConsent,
        onDownloadAnyway = viewModel::downloadAnyway,
        onDismissConsent = viewModel::dismissConsent,
        modifier = modifier,
    )
}

@Composable
internal fun OfflineLanguagesContent(
    rows: List<OfflineLanguageRow>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pendingConsent: String? = null,
    onDownloadAnyway: () -> Unit = {},
    onDismissConsent: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    // Issue #90: metered-download consent — ONE dialog at peak intent, wifi
    // waiting keeps the row NotDownloaded (no spinner, no dead end).
    pendingConsent?.let { id ->
        val name = rows.firstOrNull { it.id == id }?.name ?: id
        AlertDialog(
            onDismissRequest = onDismissConsent,
            title = { Text(stringResource(R.string.offline_data_dialog_title, name)) },
            text = { Text(stringResource(R.string.offline_data_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = onDownloadAnyway,
                    modifier = Modifier.testTag("tt_offline_data_once"),
                ) { Text(stringResource(R.string.offline_data_dialog_once)) }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConsent,
                    modifier = Modifier.testTag("tt_offline_data_wait"),
                ) { Text(stringResource(R.string.offline_data_dialog_wait)) }
            },
            modifier = Modifier.testTag("tt_offline_data_dialog"),
        )
    }
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
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.offline_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.lg24).testTag("tt_offline_empty"),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("tt_offline_list")) {
                        items(rows, key = OfflineLanguageRow::id) { row ->
                            OfflineRow(row = row, onDownload = onDownload, onDelete = onDelete)
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
    row: OfflineLanguageRow,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
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
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            // Issue #90 (EDGE_CASES no-dead-end): a Failed row explains WHY —
            // ↻ on a full disk re-fails silently otherwise. This is an error
            // line, not the always-on sub-line the owner removed in #82.
            if (row.state is OfflineModelState.Failed) {
                Text(
                    text =
                        stringResource(
                            when (row.state.cause) {
                                OfflineModelFailure.STORAGE -> R.string.offline_error_storage

                                OfflineModelFailure.NETWORK,
                                OfflineModelFailure.WIFI_REQUIRED,
                                -> R.string.offline_error_network

                                OfflineModelFailure.UNKNOWN -> R.string.offline_error_generic
                            },
                        ),
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
                        contentDescription = stringResource(R.string.offline_cd_download, row.name),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OfflineModelState.Downloading -> {
                IconButton(
                    onClick = { onDelete(row.id) }, // the verified stop = delete-to-cancel
                    modifier = Modifier.testTag("tt_offline_stop"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.offline_cd_stop, row.name),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            OfflineModelState.Downloaded -> {
                IconButton(
                    onClick = { onDelete(row.id) },
                    modifier = Modifier.testTag("tt_offline_delete"),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.offline_cd_delete, row.name),
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
                        contentDescription = stringResource(R.string.offline_cd_retry, row.name),
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
        OfflineLanguageRow("fr", "French", OfflineModelState.Downloaded),
        OfflineLanguageRow("de", "German", OfflineModelState.Downloading),
        OfflineLanguageRow("es", "Spanish", OfflineModelState.NotDownloaded),
        OfflineLanguageRow("it", "Italian", OfflineModelState.Deleting),
        OfflineLanguageRow("pt", "Portuguese", OfflineModelState.Failed(OfflineModelFailure.STORAGE)),
    )

@PreviewLightDark
@Composable
private fun OfflineLanguagesScreenPreview() {
    TranzlateTheme {
        OfflineLanguagesContent(
            rows = previewRows,
            onDownload = {},
            onDelete = {},
            onBack = {},
        )
    }
}

/** The metered-consent dialog (issue #90) over the list. */
@PreviewLightDark
@Composable
private fun OfflineLanguagesConsentDialogPreview() {
    TranzlateTheme {
        OfflineLanguagesContent(
            rows = previewRows,
            onDownload = {},
            onDelete = {},
            onBack = {},
            pendingConsent = "de",
        )
    }
}

/** Empty/loading face — the rows have not arrived yet. */
@PreviewLightDark
@Composable
private fun OfflineLanguagesLoadingPreview() {
    TranzlateTheme {
        OfflineLanguagesContent(rows = emptyList(), onDownload = {}, onDelete = {}, onBack = {})
    }
}

/**
 * THE ITEM the owner called out: one row per state, so the single trailing
 * control (⬇ / spinner+stop / 🗑 / spinner / ↻ + cause line) is reviewable on
 * both themes without launching the app.
 */
@PreviewLightDark
@Composable
private fun OfflineRowStatesPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                previewRows.forEach { row ->
                    OfflineRow(row = row, onDownload = {}, onDelete = {})
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                OfflineRow(
                    row = OfflineLanguageRow("nl", "Dutch", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                    onDownload = {},
                    onDelete = {},
                )
            }
        }
    }
}
