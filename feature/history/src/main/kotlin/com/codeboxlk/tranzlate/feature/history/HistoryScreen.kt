package com.codeboxlk.tranzlate.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

/** C-13 single-column rule: phones fill, tablets centre. */

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.history_undo)

    // Issue #190 — a write that fails must say so. All three used to throw out of
    // a handler-less scope and end the process, which EDGE_CASES §94 names by
    // hand ("no crash-instead-of-message") and which left the user with no
    // account of what happened to their data.
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val deleteFailed = stringResource(R.string.history_delete_failed)
    val restoreFailed = stringResource(R.string.history_restore_failed)
    val favouriteFailed = stringResource(R.string.history_favourite_failed)
    val retryLabel = stringResource(R.string.history_retry)

    // The live "Translation deleted" jobs, one per row (#190, PR-194 co-verify).
    // Held so a confirmation can be WITHDRAWN rather than merely dismissed:
    // `SnackbarHostState` serialises on a mutex, so a message that has not
    // reached the screen yet is invisible to `currentSnackbarData` and would
    // still take its four seconds AFTER the error saying it never happened.
    // Cancelling covers both — showing and queued — which is what makes a
    // failure visible on every timing rather than only the lucky ones.
    //
    // Keyed by row, not one slot: two quick deletes each owe the user their own
    // Undo, and only the one whose write actually failed has become untrue.
    val deletedConfirmations = remember { mutableMapOf<Long, Job>() }
    LaunchedEffect(failure) {
        val pending = failure ?: return@LaunchedEffect
        // "Translation deleted" was shown optimistically, the instant the swipe
        // landed, because Undo has to be reachable immediately. If that row's
        // delete then failed, the message is now untrue and is withdrawn before
        // the correction is queued, so the user is never left holding a
        // confirmation for something that did not happen.
        deletedConfirmations.remove(pending.translation.id)?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        val result =
            snackbarHostState.showSnackbar(
                message =
                    when (pending.write) {
                        HistoryWrite.DELETE -> deleteFailed
                        HistoryWrite.RESTORE -> restoreFailed
                        HistoryWrite.FAVOURITE -> favouriteFailed
                    },
                actionLabel = retryLabel,
                // Long, not Short: an error the user is expected to act on needs
                // longer than the four seconds a confirmation gets.
                duration = SnackbarDuration.Long,
            )
        // Consume BEFORE retrying, in this order. The retry can fail the same way,
        // and the screen has to see that as a NEW pending failure — clearing after
        // it would wipe the second failure and leave the user with no message at all.
        viewModel.onFailureShown(pending)
        if (result == SnackbarResult.ActionPerformed) viewModel.retry(pending)
    }
    HistoryContent(
        history = history,
        favourites = favourites,
        onToggleFavourite = viewModel::toggleFavourite,
        onDelete = { translation ->
            viewModel.delete(translation)
            deletedConfirmations[translation.id] =
                scope.launch {
                    try {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoDelete(translation)
                        }
                    } finally {
                        // Cancelling a finished job is a no-op, but a map that only
                        // ever grows is a leak on a screen the user can sit on.
                        deletedConfirmations.remove(translation.id)
                    }
                }
        },
        onPick = onPick,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
internal fun HistoryContent(
    history: List<Translation>,
    favourites: List<Translation>,
    onToggleFavourite: (Translation) -> Unit,
    onDelete: (Translation) -> Unit,
    onPick: (Translation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val spacing = LocalSpacing.current
    var filter by rememberSaveable { mutableIntStateOf(TAB_HISTORY) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                    modifier = Modifier.padding(horizontal = spacing.md16),
                ) {
                    FilterChip(
                        selected = filter == TAB_HISTORY,
                        onClick = { filter = TAB_HISTORY },
                        label = { Text(stringResource(R.string.history_filter_all)) },
                        modifier = Modifier.testTag("tt_history_filter_all"),
                    )
                    FilterChip(
                        selected = filter == TAB_SAVED,
                        onClick = { filter = TAB_SAVED },
                        label = { Text(stringResource(R.string.history_tab_saved)) },
                        modifier = Modifier.testTag("tt_history_filter_saved"),
                    )
                }
                val rows = if (filter == TAB_HISTORY) history else favourites
                if (rows.isEmpty()) {
                    EmptyState(
                        text =
                            stringResource(
                                if (filter == TAB_HISTORY) {
                                    R.string.history_empty
                                } else {
                                    R.string.history_saved_empty
                                },
                            ),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().testTag("tt_history_list")) {
                        items(rows, key = Translation::id) { translation ->
                            SwipeableHistoryRow(
                                translation = translation,
                                onToggleFavourite = onToggleFavourite,
                                onDelete = onDelete,
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

/**
 * Swipe actions (issue #80, owner): leading (→) toggles Saved, trailing (←)
 * DELETES — the caller shows the Undo snackbar. Both swipes return the row to
 * its resting place; the row leaves the list only when the DATABASE loses it.
 *
 * ## Why the action is not run from `confirmValueChange` (#190, PR-194 co-verify)
 *
 * It used to be, and one swipe therefore performed the write **several times.**
 * Measured on `emulator-5556` against `TranslationRepositoryImpl.delete` faulted
 * with `withContext(Dispatchers.IO) { throw … }`: one delete swipe called
 * `onDelete` **4 times** in 218 ms (three runs, identical), and one save swipe
 * called `setFavourite` **9 times**, flip-flopping the star and settling on the
 * value the user did not ask for.
 *
 * `confirmValueChange` is a PREDICATE, not a callback: AOSP asks it "may I move
 * to this value?" at every decision point of a gesture. The four stack traces
 * came from four different ones — `updateIfNeeded` during the drag
 * (`AnchoredDraggable.kt:1107`), `settle` on finger-up (`:1077`),
 * `updateIfNeeded` again inside the settle animation (`:1456`), and the
 * `anchoredDrag` completion block (`:1210`). Nothing recomposed; the repeat is
 * the gesture pipeline asking again. A successful delete only fired once because
 * the row left the list before the later stages ran, which is why the crash this
 * PR replaced hid the whole thing.
 *
 * Material3 1.4.0 says the same in its own words: `confirmValueChange` is
 * `@Deprecated` — *"deprecated without replacement. Rather than relying on a
 * callback to veto state changes, the anchor set should not include disallowed
 * anchors."* — and [SwipeToDismissBox] grew an `onDismiss` parameter, which it
 * fires from `LaunchedEffect(settledValue, onDismiss)`: once per settle, and
 * never mid-gesture.
 */
@Composable
private fun SwipeableHistoryRow(
    translation: Translation,
    onToggleFavourite: (Translation) -> Unit,
    onDelete: (Translation) -> Unit,
    onPick: (Translation) -> Unit,
) {
    val spacing = LocalSpacing.current
    // PR-81 lens O1: the dismiss lambda is captured ONCE (see below) — without
    // rememberUpdatedState the closure freezes the first composition's row and a
    // second consecutive save-swipe silently no-ops on the stale favourite.
    val currentRow by rememberUpdatedState(translation)
    val currentToggleFavourite by rememberUpdatedState(onToggleFavourite)
    val currentDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState()

    // `remember` with NO keys, deliberately: `SwipeToDismissBox` runs this from
    // `LaunchedEffect(settledValue, onDismiss)`, so a lambda rebuilt on each
    // recomposition is a NEW key and re-fires the write — the same "one gesture,
    // several writes" defect by another route. One instance for the row's whole
    // lifetime is what makes "at most one write per gesture" a guarantee rather
    // than a hope about lambda memoisation; `rememberUpdatedState` above is what
    // keeps that one instance from going stale.
    val onDismiss =
        remember {
            { direction: SwipeToDismissBoxValue ->
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> currentToggleFavourite(currentRow)
                    SwipeToDismissBoxValue.EndToStart -> currentDelete(currentRow)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        }

    // EDGE_CASES §94, no dead end: a swiped row must never be left as a hole. The
    // screen cannot know yet whether the write landed, so it does not pretend —
    // the row always comes back, and disappears only when the Room flow stops
    // listing it. A delete that fails therefore leaves the row exactly where it
    // was, next to the snackbar that says so, instead of an empty red band the
    // user reads as "gone" for a translation that is still there.
    LaunchedEffect(state.settledValue) {
        if (state.settledValue != SwipeToDismissBoxValue.Settled) state.reset()
    }

    SwipeToDismissBox(
        state = state,
        onDismiss = onDismiss,
        modifier = Modifier.testTag("tt_history_swipe"),
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (toDelete) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ).padding(horizontal = spacing.lg24),
            ) {
                Icon(
                    painterResource(
                        if (toDelete) {
                            DsR.drawable.ic_delete
                        } else if (translation.favourite) {
                            DsR.drawable.ic_bookmark
                        } else {
                            DsR.drawable.ic_bookmark_filled
                        },
                    ),
                    contentDescription = null, // announced via the swipe semantics
                    tint =
                        if (toDelete) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                )
            }
        },
    ) {
        // PR-81 lens O2: swipes are invisible to TalkBack/switch access — the
        // destructive delete needs a first-class accessibility action.
        val deleteLabel = stringResource(R.string.history_action_delete)
        val saveLabel =
            stringResource(
                if (translation.favourite) R.string.history_cd_unsave else R.string.history_cd_save,
            )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(deleteLabel) {
                                onDelete(translation)
                                true
                            },
                            CustomAccessibilityAction(saveLabel) {
                                onToggleFavourite(translation)
                                true
                            },
                        )
                },
        ) {
            HistoryRow(
                translation = translation,
                onToggleFavourite = onToggleFavourite,
                onPick = onPick,
            )
        }
    }
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

// ── Previews (owner convention: screens AND custom M3 items, light + dark).

private fun previewTranslation(
    id: Long,
    source: String,
    target: String,
    favourite: Boolean = false,
) = Translation(
    id = id,
    sourceLang = "en",
    sourceText = source,
    targetLang = "fr",
    targetText = target,
    engine = Engine.OFFLINE_MLKIT,
    favourite = favourite,
    createdAt = 0L,
)

private val previewHistory =
    listOf(
        previewTranslation(1, "Good morning", "Bonjour", favourite = true),
        previewTranslation(2, "See you tomorrow", "À demain"),
        previewTranslation(3, "Where is the station?", "Où est la gare ?"),
    )

@PreviewLightDark
@Composable
private fun HistoryContentPreview() {
    TranzlateTheme {
        HistoryContent(
            history = previewHistory,
            favourites = previewHistory.filter(Translation::favourite),
            onToggleFavourite = {},
            onDelete = {},
            onPick = {},
            onBack = {},
        )
    }
}

/** The empty face — both filter chips can reach it (EDGE_CASES: no dead end). */
@PreviewLightDark
@Composable
private fun HistoryEmptyPreview() {
    TranzlateTheme {
        HistoryContent(
            history = emptyList(),
            favourites = emptyList(),
            onToggleFavourite = {},
            onDelete = {},
            onPick = {},
            onBack = {},
        )
    }
}

/**
 * The write-failure faces (issue #190), one preview per STATE.
 *
 * `SnackbarHost` builds its own `Snackbar` from the queued data, and a static
 * preview runs no effects — so nothing would render if these previewed the screen.
 * They render the same M3 component the host does, with the same strings and the
 * same Retry action, which is what the owner has to be able to look at.
 */
@Composable
private fun FailureSnackbar(message: String) {
    Snackbar(action = { TextButton(onClick = {}) { Text(stringResource(R.string.history_retry)) } }) {
        Text(message)
    }
}

@PreviewLightDark
@Composable
private fun HistoryDeleteFailedPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FailureSnackbar(stringResource(R.string.history_delete_failed))
        }
    }
}

/** The costly one: the delete already happened, so a failed Undo loses the row. */
@PreviewLightDark
@Composable
private fun HistoryRestoreFailedPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FailureSnackbar(stringResource(R.string.history_restore_failed))
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryFavouriteFailedPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FailureSnackbar(stringResource(R.string.history_favourite_failed))
        }
    }
}

/** THE ITEM: one row, starred and unstarred, on both themes. */
@PreviewLightDark
@Composable
private fun HistoryRowPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                HistoryRow(
                    translation = previewHistory[0],
                    onToggleFavourite = {},
                    onPick = {},
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HistoryRow(
                    translation = previewHistory[1],
                    onToggleFavourite = {},
                    onPick = {},
                )
            }
        }
    }
}
