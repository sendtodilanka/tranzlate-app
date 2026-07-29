package com.codeboxlk.tranzlate.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

/** C-13 single-column rule: phones fill, tablets centre. */
private val CONTENT_MAX_WIDTH = 600.dp

private const val TAB_HISTORY = 0
private const val TAB_SAVED = 1

/**
 * History + Saved (issue #68): two tabs over the Room flows the drawer's
 * Recents already reads. A row tap REOPENS the translation in the composer
 * (the C-8 cache answers a Retry instantly); the star persists `favourite`.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onPick: (Translation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    HistoryContent(
        history = history,
        favourites = favourites,
        onToggleFavourite = viewModel::toggleFavourite,
        onPick = onPick,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun HistoryContent(
    history: List<Translation>,
    favourites: List<Translation>,
    onToggleFavourite: (Translation) -> Unit,
    onPick: (Translation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_HISTORY) }
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tt_history_back")) {
                        Icon(
                            painterResource(DsR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.history_cd_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                SecondaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == TAB_HISTORY,
                        onClick = { tab = TAB_HISTORY },
                        text = { Text(stringResource(R.string.history_tab_all)) },
                        modifier = Modifier.testTag("tt_history_tab_history"),
                    )
                    Tab(
                        selected = tab == TAB_SAVED,
                        onClick = { tab = TAB_SAVED },
                        text = { Text(stringResource(R.string.history_tab_saved)) },
                        modifier = Modifier.testTag("tt_history_tab_saved"),
                    )
                }
                val rows = if (tab == TAB_HISTORY) history else favourites
                if (rows.isEmpty()) {
                    EmptyState(
                        text =
                            stringResource(
                                if (tab == TAB_HISTORY) {
                                    R.string.history_empty
                                } else {
                                    R.string.history_saved_empty
                                },
                            ),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("tt_history_list")) {
                        items(rows, key = Translation::id) { translation ->
                            HistoryRow(
                                translation = translation,
                                onToggleFavourite = onToggleFavourite,
                                onPick = onPick,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** EDGE_CASES no-dead-end: an empty list explains where rows come from. */
@Composable
private fun EmptyState(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .padding(spacing.lg24)
                .testTag("tt_history_empty"),
    )
}

@Composable
private fun HistoryRow(
    translation: Translation,
    onToggleFavourite: (Translation) -> Unit,
    onPick: (Translation) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(R.string.history_cd_open),
                    onClick = { onPick(translation) },
                ).padding(start = spacing.md16, end = spacing.xs4)
                .testTag("tt_history_row"),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm8)) {
            Text(
                text =
                    stringResource(
                        R.string.history_pair,
                        translation.sourceLang.uppercase(),
                        translation.targetLang.uppercase(),
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = translation.sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = translation.targetText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onToggleFavourite(translation) },
            modifier = Modifier.testTag("tt_history_star"),
        ) {
            Icon(
                painterResource(
                    if (translation.favourite) {
                        DsR.drawable.ic_bookmark_filled
                    } else {
                        DsR.drawable.ic_bookmark
                    },
                ),
                contentDescription =
                    stringResource(
                        if (translation.favourite) {
                            R.string.history_cd_unsave
                        } else {
                            R.string.history_cd_save
                        },
                    ),
                tint =
                    if (translation.favourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
