package com.codeboxlk.tranzlate.feature.languagepicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.model.OfflineModelState

private val CONTENT_MAX_WIDTH = 600.dp

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
    OfflineLanguagesContent(
        rows = rows,
        onDownload = viewModel::download,
        onDelete = viewModel::delete,
        onBack = onBack,
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
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()) {
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
                .padding(start = spacing.md16, end = spacing.xs4)
                .testTag("tt_offline_row"),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm8)) {
            Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    stringResource(
                        when (row.state) {
                            OfflineModelState.Downloaded -> R.string.offline_state_downloaded
                            OfflineModelState.Downloading -> R.string.offline_state_downloading
                            OfflineModelState.Deleting -> R.string.offline_state_deleting
                            is OfflineModelState.Failed -> R.string.offline_state_failed
                            else -> R.string.offline_state_available
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (row.state is OfflineModelState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
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
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(
                    onClick = { onDelete(row.id) }, // the verified stop = delete-to-cancel
                    modifier = Modifier.testTag("tt_offline_stop"),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.offline_cd_stop, row.name),
                    )
                }
            }

            OfflineModelState.Downloaded -> {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null, // the sub-line says Downloaded
                    tint = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = { onDelete(row.id) },
                    modifier = Modifier.testTag("tt_offline_delete"),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.offline_cd_delete, row.name),
                    )
                }
            }

            OfflineModelState.Deleting -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
