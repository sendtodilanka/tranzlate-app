package com.codeboxlk.tranzlate.feature.language

import android.os.LocaleList
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import com.codeboxlk.tranzlate.core.ui.languageAvatarCode
import com.codeboxlk.tranzlate.core.ui.languageDisplayName

/**
 * Manage packs (20b/20f · spec rev 5 · #130 PR-23) — behind the SAME
 * `LanguagesNavKey` the early "Offline translation" screen used, rewritten into
 * the full management surface: the aggregate storage card, the storage-hygiene
 * nudge, the downloading / failed / on-device sections with their last-used dates,
 * the footer, and the fresh-install empty state (20f). It is the only place a pack
 * is removed or a failure retried; NEW packs are browsed from the picker.
 *
 * Every figure it states is one the app can actually source (the governing brief
 * rule): aggregate storage only (no per-pack size), relative last-used from the
 * #122 translation-success store (never a fabricated date), and a pack with no
 * recorded use says so plainly.
 *
 * @param onBrowseAll opens the picker so a first (or another) pack can be
 *   downloaded — the empty state's second way forward beside the suggestions.
 */
@Composable
fun OfflineLanguagesScreen(
    viewModel: OfflineLanguagesViewModel,
    onBack: () -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsent.collectAsStateWithLifecycle()
    val alwaysAsk by viewModel.alwaysAsk.collectAsStateWithLifecycle()
    val pendingRemoval by viewModel.pendingRemoval.collectAsStateWithLifecycle()

    val locale = LocalLocale.current.platformLocale
    // Names and the alphabetical order are applied HERE, off the ViewModel, for
    // the reason the picker localizes in Compose: a Locale is a platform read. The
    // clock instant travels IN the data so "used today" is fixed per emission, not
    // recomputed on every recomposition a fling produces.
    val sections =
        remember(data.rows, data.usage, data.targetId, data.nowMillis, locale) {
            buildManagePacksSections(data.rows, data.usage, data.targetId, data.nowMillis, locale)
        }
    val nudge =
        remember(sections.onDevice, data.nowMillis, data.nudgeDismissed) {
            if (data.nudgeDismissed) null else hygieneNudge(sections.onDevice, data.nowMillis)
        }
    val localeTags = rememberAdjustedDefaultLocaleTags()
    val suggestions =
        remember(sections.downloadable, localeTags) {
            manageEmptySuggestions(localeTags, sections.downloadable)
        }

    // The screen's OWN snackbar for a SYNCHRONOUS download refusal (#234/#250) — a
    // Retry that could not even start because the disk is still full. Drained from
    // the ViewModel's one-shot channel (never the U-1 PackEvents channel: that is for
    // a transfer that actually ran) and shown over the packs the user can remove to
    // free room. Read via `LocalResources` (captured at composition): not
    // `stringResource`, which is @Composable and illegal inside showSnackbar's
    // coroutine; and not `LocalContext.current.getString`, which lint flags as
    // `LocalContextGetResourceValueCall`. The copy is `downloadFailureCopy`'s so the
    // message matches the row.
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.refusals.collect { cause ->
            snackbarHostState.showSnackbar(
                message = resources.getString(downloadFailureCopy(cause).rowLine),
                duration = SnackbarDuration.Short,
            )
        }
    }

    ManagePacksContent(
        loading = data.loading,
        sections = sections,
        storage = data.storage,
        nudge = nudge,
        suggestions = suggestions,
        capable = data.capable,
        total = data.total,
        onBack = onBack,
        onGet = viewModel::download,
        onStopDownload = viewModel::stopDownload,
        onRetry = viewModel::download,
        onRemove = viewModel::requestRemove,
        onUseAsTarget = viewModel::useAsTarget,
        onDismissNudge = viewModel::dismissNudge,
        onBrowseAll = onBrowseAll,
        pendingConsent = pendingConsent,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = viewModel::onAlwaysAskChange,
        onDownloadAnyway = viewModel::downloadAnyway,
        onDismissConsent = viewModel::dismissConsent,
        pendingRemoval = pendingRemoval,
        onConfirmRemove = viewModel::confirmRemove,
        onDismissRemove = viewModel::dismissRemove,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
internal fun ManagePacksContent(
    loading: Boolean,
    sections: ManagePacksSections,
    storage: StorageCard?,
    nudge: HygieneNudge?,
    suggestions: List<SuggestedLanguage>,
    capable: Int,
    total: Int,
    onBack: () -> Unit,
    onGet: (String) -> Unit,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismissNudge: () -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier,
    pendingConsent: String? = null,
    alwaysAsk: Boolean = true,
    onAlwaysAskChange: (Boolean) -> Unit = {},
    onDownloadAnyway: () -> Unit = {},
    onDismissConsent: () -> Unit = {},
    pendingRemoval: PendingPackRemoval? = null,
    onConfirmRemove: () -> Unit = {},
    onDismissRemove: () -> Unit = {},
    // The 20c pack-actions sheet's "Use as target now" write. Defaulted so the render
    // tests and previews that do not exercise the sheet keep their existing call.
    onUseAsTarget: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val spacing = LocalSpacing.current
    // Issue #90's consent question and the 19f/19g remove confirm (#130 PR-19),
    // rendered here in the stateless content so a render test sees the join — which
    // sheet a question draws, and with what — the way the ViewModel and sheet tests
    // each cannot. The removal name is resolved against the composition's locale,
    // never carried in the ViewModel, so it follows a locale change like every row.
    val locale = LocalLocale.current.platformLocale
    // The 20c pack-actions sheet's open pack (#130 PR-24), or null. Screen-local
    // rather than ViewModel state: it survives a recomposition, which is all a
    // transient menu needs, and it keeps the write path the only thing that reaches
    // the ViewModel. The overflow sets it; every action clears it.
    var actionsTarget by remember { mutableStateOf<PackActionsTarget?>(null) }
    PackActionsSheet(
        target = actionsTarget,
        onUseAsTarget = { id ->
            onUseAsTarget(id)
            actionsTarget = null
        },
        onRemove = { id ->
            onRemove(id)
            actionsTarget = null
        },
        onDismiss = { actionsTarget = null },
    )
    MobileDataSheet(
        visible = pendingConsent != null,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = onAlwaysAskChange,
        onDownloadNow = onDownloadAnyway,
        onDismiss = onDismissConsent,
    )
    val removalName = pendingRemoval?.let { languageDisplayName(it.id, locale) }.orEmpty()
    RemovePackSheet(
        visible = pendingRemoval != null && pendingRemoval.inUseAsTarget == false,
        languageName = removalName,
        savedCount = pendingRemoval?.savedCount ?: 0,
        onRemove = onConfirmRemove,
        onDismiss = onDismissRemove,
    )
    RemoveInUseSheet(
        visible = pendingRemoval != null && pendingRemoval.inUseAsTarget == true,
        languageName = removalName,
        savedCount = pendingRemoval?.savedCount ?: 0,
        onRemoveAnyway = onConfirmRemove,
        onDismiss = onDismissRemove,
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = adaptiveMarginShim()),
        ) {
            ManagePacksHeader(onBack = onBack)
            when {
                loading -> {
                    Text(
                        text = stringResource(R.string.offline_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.lg24).testTag("tt_manage_loading"),
                    )
                }

                sections.hasPacks -> {
                    PopulatedList(
                        sections = sections,
                        storage = storage,
                        nudge = nudge,
                        capable = capable,
                        total = total,
                        onStopDownload = onStopDownload,
                        onRetry = onRetry,
                        onMoreOptions = { row ->
                            actionsTarget = PackActionsTarget(row.id, row.displayName, row.hasOfflineVoice)
                        },
                        onDismissNudge = onDismissNudge,
                        onBrowseAll = onBrowseAll,
                    )
                }

                else -> {
                    EmptyList(
                        storage = storage,
                        suggestions = suggestions,
                        capable = capable,
                        total = total,
                        onGet = onGet,
                        onBrowseAll = onBrowseAll,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagePacksHeader(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = spacing.xs4, vertical = spacing.xs4),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("tt_manage_back")) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.offline_cd_back))
        }
        Text(
            text = stringResource(R.string.manage_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = spacing.xs4),
        )
    }
}

@Composable
private fun PopulatedList(
    sections: ManagePacksSections,
    storage: StorageCard?,
    nudge: HygieneNudge?,
    capable: Int,
    total: Int,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
    onDismissNudge: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.lg24),
        modifier = Modifier.fillMaxSize().testTag("tt_manage_list"),
    ) {
        storage?.let { item(key = "storage") { StorageCardView(it, Modifier.padding(vertical = spacing.sm8)) } }
        nudge?.let { item(key = "nudge") { NudgeCardView(it, onDismissNudge, Modifier.padding(bottom = spacing.sm8)) } }
        if (sections.downloading.isNotEmpty()) {
            item(key = "hdr_downloading") { SectionHeaderText(stringResource(R.string.manage_section_downloading)) }
            items(sections.downloading, key = { "dl_${it.id}" }) { row ->
                PackRow(row = row, onStopDownload = onStopDownload, onRetry = onRetry, onMoreOptions = onMoreOptions)
            }
            item(key = "downloading_note") { DownloadingNote() }
        }
        if (sections.failed.isNotEmpty()) {
            item(key = "hdr_failed") { SectionHeaderText(stringResource(R.string.manage_section_failed)) }
            items(sections.failed, key = { "fail_${it.id}" }) { row ->
                PackRow(row = row, onStopDownload = onStopDownload, onRetry = onRetry, onMoreOptions = onMoreOptions)
            }
        }
        if (sections.onDevice.isNotEmpty()) {
            item(key = "hdr_on_device") {
                SectionHeaderText(
                    title = stringResource(R.string.manage_section_on_device),
                    count =
                        pluralStringResource(
                            R.plurals.manage_on_device_count,
                            sections.onDevice.size,
                            sections.onDevice.size,
                            capable,
                        ),
                )
            }
            items(sections.onDevice, key = { "dev_${it.id}" }) { row ->
                PackRow(row = row, onStopDownload = onStopDownload, onRetry = onRetry, onMoreOptions = onMoreOptions)
            }
        }
        // "Browse all languages" belongs here too, not only on the empty state (20f):
        // a user with packs still needs the picker one tap away to add ANOTHER, and
        // downloading is the picker's job, never a section on this screen.
        item(key = "browse") { BrowseAllButton(onBrowseAll) }
        item(key = "footer") { FooterView(capable = capable, total = total) }
    }
}

@Composable
private fun EmptyList(
    storage: StorageCard?,
    suggestions: List<SuggestedLanguage>,
    capable: Int,
    total: Int,
    onGet: (String) -> Unit,
    onBrowseAll: () -> Unit,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.lg24),
        modifier = Modifier.fillMaxSize().testTag("tt_manage_empty"),
    ) {
        item(key = "no_packs") { NoPacksBlock() }
        if (suggestions.isNotEmpty()) {
            item(key = "sug_hdr") { SectionHeaderText(stringResource(R.string.lang_first_run_suggested_header)) }
            items(suggestions, key = { "sug_${it.id}" }) { s -> SuggestionRowView(s, onGet) }
        }
        item(key = "browse") { BrowseAllButton(onBrowseAll) }
        storage?.let { item(key = "storage") { StorageCardView(it, Modifier.padding(vertical = spacing.sm8)) } }
        item(key = "footer") { FooterView(capable = capable, total = total) }
    }
}

// ── Storage card ────────────────────────────────────────────────────────────

/**
 * The aggregate storage card (brief §2/§3). It states the packs' measured
 * aggregate bytes and the device's free space — never a per-pack size, which ML
 * Kit does not expose. The bar is device-used vs free on the whole volume (the
 * only honest bar at 110 MB against a whole device — 19b's own reasoning), its
 * semantics cleared because the numerals above already say it in words.
 */
@Composable
private fun StorageCardView(
    card: StorageCard,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(spacing.md16))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(spacing.md16)
                .semantics(mergeDescendants = true) {}
                .testTag("tt_manage_storage"),
    ) {
        when (card) {
            is StorageCard.Sized -> {
                Text(
                    text = stringResource(R.string.manage_storage_packs_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = spacing.xs4)) {
                    Text(
                        text = Formatter.formatShortFileSize(context, card.packsBytes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.manage_storage_across, card.packCount, card.packCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = spacing.sm8, bottom = spacing.xs4 / 2),
                    )
                }
                DeviceUsedBar(free = card.freeBytes, total = card.totalBytes)
                Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm8)) {
                    Text(
                        text = stringResource(R.string.lang_sheet_space_used),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.lang_sheet_space_free,
                                Formatter.formatShortFileSize(context, card.freeBytes),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.manage_storage_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm8),
                )
            }

            is StorageCard.FreeOnly -> {
                Text(
                    text = stringResource(R.string.manage_storage_device_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Formatter.formatShortFileSize(context, card.freeBytes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = spacing.xs4),
                )
                Text(
                    text = stringResource(R.string.manage_storage_free_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.packCount <= 0) {
                    Text(
                        text = stringResource(R.string.manage_storage_packs_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm8),
                    )
                    Text(
                        text = stringResource(R.string.manage_storage_empty_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm8),
                    )
                } else {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.manage_storage_packs_count,
                                card.packCount,
                                card.packCount,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm8),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceUsedBar(
    free: Long,
    total: Long,
) {
    val spacing = LocalSpacing.current
    LinearProgressIndicator(
        progress = { deviceUsedFraction(free, total) },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm8)
                .height(Dimensions.pickerMeterBarHeight)
                .clip(RoundedCornerShape(spacing.xs4))
                .clearAndSetSemantics {},
    )
}

// ── Hygiene nudge ───────────────────────────────────────────────────────────

/**
 * The storage-hygiene nudge (brief §6.1): N packs unused past the stale
 * threshold. It informs and offers "Not now"; the stale packs it counts are
 * listed just below with their "used N months ago" lines and are removable there.
 *
 * The frame's "Review N packs" action opens the batch 20e "Free up space" sheet,
 * which is **PR-25** — omitted rather than wired to nothing (EDGE_CASES §7, the
 * same call PR-18 made for 19b's second action). Carried as a #250/PR-25 residual.
 */
@Composable
private fun NudgeCardView(
    nudge: HygieneNudge,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(spacing.md16))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(spacing.md16)
                .testTag("tt_manage_nudge"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CleaningServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Text(
                text = pluralStringResource(R.plurals.manage_nudge_title, nudge.stalePackCount, nudge.stalePackCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.sm8),
            )
        }
        Text(
            text = stringResource(R.string.manage_nudge_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs4),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End).testTag("tt_manage_nudge_dismiss"),
        ) {
            Text(stringResource(R.string.manage_nudge_dismiss))
        }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeaderText(
    title: String,
    count: String? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier.fillMaxWidth().padding(
                start = spacing.md16,
                end = spacing.md16,
                top = spacing.md16,
                bottom = spacing.xs4,
            ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One Manage-packs row. Its trailing control is chosen by state:
 * - **Downloading** → stop (⏹, delete-to-cancel — no cancel API).
 * - **Failed (any cause, out-of-space included, #250)** → a labelled **Retry**
 *   pill. A space-failed retry does not silently re-fail: the ViewModel captures
 *   the manager's synchronous refusal and the screen shows a "not enough space"
 *   snackbar over the removable packs, while a retry after space is freed actually
 *   downloads (see `OfflineLanguagesViewModel.reportOutcome`).
 * - **Downloaded, non-pivot** → `more_vert` opening the 20c pack-actions sheet
 *   (Use as target now · a voice line when this device can also speak the language ·
 *   Remove pack, which routes on to the 19f/19g confirm — #130 PR-24).
 * - **Downloaded, pivot (English, #224)** → no control; it cannot be removed.
 * - **Deleting** → a spinner.
 */
@Composable
private fun PackRow(
    row: PackRow,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.pickerRowHeightTall)
                .padding(start = spacing.md16, end = spacing.xs4)
                .testTag("tt_manage_row"),
    ) {
        PackAvatar(id = row.id, downloaded = row.state == OfflineModelState.Downloaded)
        Column(modifier = Modifier.weight(1f).padding(start = spacing.md16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
                if (row.inUse) {
                    InUseBadge(modifier = Modifier.padding(start = spacing.sm8))
                }
            }
            PackRowSupporting(row)
        }
        PackRowControl(row = row, onStopDownload = onStopDownload, onRetry = onRetry, onMoreOptions = onMoreOptions)
    }
}

@Composable
private fun PackRowSupporting(row: PackRow) {
    // #224: the ML Kit pivot (English) is included with every pack and cannot be
    // removed on its own (a measured no-op). Owner ruling (2026-08-05): keep the
    // row but say WHY it has no control, in place of a usage line. Guarded by id,
    // so the story holds whatever state ML Kit reports for the pivot.
    if (row.isPivot) {
        Text(
            text = stringResource(R.string.offline_included),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tt_manage_included"),
        )
        return
    }
    when (val state = row.state) {
        OfflineModelState.Downloading -> {
            Text(
                text = stringResource(R.string.text_lang_downloading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is OfflineModelState.Failed -> {
            Text(
                text = stringResource(downloadFailureCopy(state.cause).rowLine),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("tt_manage_error_line"),
            )
        }

        else -> {
            // Downloaded / Deleting: "On device · used today" (or "· no recorded use yet").
            Text(
                text = stringResource(R.string.text_lang_on_device_size, packUsageText(row.usage)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("tt_manage_usage_line"),
            )
        }
    }
}

@Composable
private fun PackRowControl(
    row: PackRow,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
) {
    when (row.state) {
        OfflineModelState.Downloading -> {
            StopControl(row, onStopDownload)
        }

        is OfflineModelState.Failed -> {
            // #250: EVERY failed cause, out-of-space included, keeps its Retry pill.
            // Removing it left the STORAGE row a permanent dead-end — its line promises
            // an action with no control to tap, and freeing space elsewhere never
            // restored one (the transient state only moves on a `download()`). The
            // #234 concern is a Retry that SILENTLY does nothing, not the absence of
            // Retry; the ViewModel now makes it HONEST — a still-full retry surfaces
            // a snackbar (`reportOutcome`), a freed-disk retry actually downloads.
            Button(
                onClick = { onRetry(row.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = LocalSpacing.current.md16),
                modifier = Modifier.heightIn(min = Dimensions.touchTargetMin).testTag("tt_manage_retry"),
            ) {
                Text(stringResource(R.string.lang_sheet_failed_retry))
            }
        }

        OfflineModelState.Deleting -> {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimensions.iconSm).testTag("tt_manage_deleting"),
                strokeWidth = 2.dp,
            )
        }

        OfflineModelState.Downloaded -> {
            // The pivot (English, #224) is non-actionable — no overflow, because none
            // of the 20c pack actions apply to it (it cannot be removed, and it is
            // always the pivot, never a chosen target). Every other downloaded pack
            // gets the overflow, which opens the 20c pack-actions sheet.
            if (!row.isPivot) {
                IconButton(onClick = { onMoreOptions(row) }, modifier = Modifier.testTag("tt_manage_options")) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.manage_cd_options, row.displayName),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // NotDownloaded / OnlineOnly never reach a Manage-packs section.
        else -> {
            Unit
        }
    }
}

@Composable
private fun StopControl(
    row: PackRow,
    onStopDownload: (String) -> Unit,
) {
    IconButton(onClick = { onStopDownload(row.id) }, modifier = Modifier.testTag("tt_manage_stop")) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Icon(
                Icons.Filled.Stop,
                contentDescription = stringResource(R.string.cd_text_lang_stop, row.displayName),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun InUseBadge(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Box(
        modifier =
            modifier
                .clip(TranzlateShapeFull)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = spacing.sm8, vertical = spacing.xs4 / 2),
    ) {
        Text(
            text = stringResource(R.string.manage_in_use),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PackAvatar(
    id: String,
    downloaded: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val background = if (downloaded) scheme.primaryContainer else scheme.surfaceContainerHighest
    val foreground = if (downloaded) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(Dimensions.iconChip).clip(TranzlateShapeFull).background(background),
    ) {
        Text(
            text = languageAvatarCode(id),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun DownloadingNote() {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md16, vertical = spacing.xs4),
    ) {
        Icon(
            Icons.Outlined.DownloadForOffline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.iconSm),
        )
        Text(
            text = stringResource(R.string.manage_downloading_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = spacing.sm8),
        )
    }
}

/** Relative last-used as the row reads it. `NoRecord` is the honest date-less line (ruling ⑧). */
@Composable
private fun packUsageText(usage: PackUsage): String =
    when (usage) {
        PackUsage.NoRecord -> stringResource(R.string.manage_used_never)
        PackUsage.Today -> stringResource(R.string.manage_used_today)
        is PackUsage.DaysAgo -> pluralStringResource(R.plurals.manage_used_days, usage.days, usage.days)
        is PackUsage.WeeksAgo -> pluralStringResource(R.plurals.manage_used_weeks, usage.weeks, usage.weeks)
        is PackUsage.MonthsAgo -> pluralStringResource(R.plurals.manage_used_months, usage.months, usage.months)
    }

// ── Footer ──────────────────────────────────────────────────────────────────

@Composable
private fun FooterView(
    capable: Int,
    total: Int,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md16, vertical = spacing.md16),
    ) {
        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.iconSm),
        )
        Text(
            text = stringResource(R.string.manage_footer, capable, total, (total - capable).coerceAtLeast(0)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = spacing.sm8),
        )
    }
}

// ── Empty state (20f) ─────────────────────────────────────────────────────────

@Composable
private fun NoPacksBlock() {
    val spacing = LocalSpacing.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(spacing.lg24)
                .semantics(mergeDescendants = true) {}
                .testTag("tt_manage_no_packs"),
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.iconChip),
        )
        Text(
            text = stringResource(R.string.lang_first_run_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = spacing.sm8),
        )
        Text(
            text = stringResource(R.string.lang_first_run_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs4),
        )
        Text(
            text = stringResource(R.string.lang_first_run_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs4),
        )
    }
}

@Composable
private fun SuggestionRowView(
    suggestion: SuggestedLanguage,
    onGet: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val reason =
        when (suggestion.reason) {
            SuggestionReason.DEVICE_LANGUAGE -> stringResource(R.string.lang_suggested_reason_device)
            SuggestionReason.COMMON_WHERE_YOU_ARE -> stringResource(R.string.lang_suggested_reason_local)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.pickerRowHeight)
                .padding(start = spacing.md16, end = spacing.md16)
                .testTag("tt_manage_suggestion"),
    ) {
        PackAvatar(id = suggestion.id, downloaded = false)
        Column(modifier = Modifier.weight(1f).padding(start = spacing.md16)) {
            Text(text = suggestion.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { onGet(suggestion.id) },
            contentPadding = PaddingValues(horizontal = spacing.md16),
            modifier =
                Modifier
                    .heightIn(min = Dimensions.touchTargetMin)
                    .testTag("tt_manage_get"),
        ) {
            Icon(
                Icons.Outlined.DownloadForOffline,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Text(
                text = stringResource(R.string.lang_suggested_get),
                modifier = Modifier.padding(start = spacing.xs4),
            )
        }
    }
}

@Composable
private fun BrowseAllButton(onBrowseAll: () -> Unit) {
    val spacing = LocalSpacing.current
    OutlinedButton(
        onClick = onBrowseAll,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md16, vertical = spacing.sm8)
                .heightIn(min = Dimensions.touchTargetMin)
                .testTag("tt_manage_browse_all"),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(Dimensions.iconSm))
        Text(
            text = stringResource(R.string.manage_browse_all),
            modifier = Modifier.padding(start = spacing.sm8),
        )
    }
}

/**
 * The device's preferred locales, most-preferred first — the empty state's
 * suggestion signal (18a), the SAME `LocaleList.getAdjustedDefault()` the picker
 * reads. The platform read lives in Compose so the derivation ([manageEmptySuggestions])
 * stays a pure, unit-testable function; keyed on [LocalConfiguration] so a locale
 * change re-reads it.
 */
@Composable
private fun rememberAdjustedDefaultLocaleTags(): List<String> {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locales = LocaleList.getAdjustedDefault()
        List(locales.size()) { index -> locales[index].toLanguageTag() }
    }
}

// ── Previews (rule 7: every screen AND every custom item, one per meaningful
// state, private, TranzlateTheme, literal fake data — never DI, never a VM) ────

private const val MB = 1_048_576L
private const val GB = 1_073_741_824L

private fun row(
    id: String,
    name: String,
    state: OfflineModelState,
    usage: PackUsage = PackUsage.Today,
    lastUsedMillis: Long? = 0L,
    inUse: Boolean = false,
    isPivot: Boolean = false,
) = PackRow(id, name, state, usage, lastUsedMillis, inUse, isPivot)

private val previewOnDevice =
    listOf(
        row("es", "Spanish", OfflineModelState.Downloaded, PackUsage.Today, inUse = true),
        row("en", "English", OfflineModelState.Downloaded, PackUsage.Today, isPivot = true),
        row("af", "Afrikaans", OfflineModelState.Downloaded, PackUsage.DaysAgo(5)),
        row("de", "German", OfflineModelState.Downloaded, PackUsage.MonthsAgo(4)),
        row("pl", "Polish", OfflineModelState.Downloaded, PackUsage.NoRecord, lastUsedMillis = null),
    )

private val previewSizedStorage =
    StorageCard.Sized(packCount = 5, packsBytes = 110 * MB, freeBytes = 3 * GB / 2, totalBytes = 64 * GB)

private fun previewSections(
    downloading: List<PackRow> = emptyList(),
    failed: List<PackRow> = emptyList(),
    onDevice: List<PackRow> = previewOnDevice,
) = ManagePacksSections(downloading = downloading, failed = failed, onDevice = onDevice, downloadable = emptyList())

@PreviewLightDark
@Composable
private fun ManagePacksScreenNudgePreview() {
    TranzlateTheme {
        ManagePacksContent(
            loading = false,
            sections = previewSections(),
            storage = previewSizedStorage,
            nudge = HygieneNudge(stalePackCount = 2),
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            onBack = {},
            onGet = {},
            onStopDownload = {},
            onRetry = {},
            onRemove = {},
            onDismissNudge = {},
            onBrowseAll = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ManagePacksScreenNoNudgePreview() {
    TranzlateTheme {
        ManagePacksContent(
            loading = false,
            sections =
                previewSections(
                    onDevice =
                        listOf(
                            row("es", "Spanish", OfflineModelState.Downloaded, PackUsage.Today, inUse = true),
                            row("en", "English", OfflineModelState.Downloaded, PackUsage.Today, isPivot = true),
                            row("af", "Afrikaans", OfflineModelState.Downloaded, PackUsage.DaysAgo(3)),
                        ),
                ),
            storage = previewSizedStorage,
            nudge = null,
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            onBack = {},
            onGet = {},
            onStopDownload = {},
            onRetry = {},
            onRemove = {},
            onDismissNudge = {},
            onBrowseAll = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ManagePacksScreenDownloadingPreview() {
    TranzlateTheme {
        ManagePacksContent(
            loading = false,
            sections = previewSections(downloading = listOf(row("ar", "Arabic", OfflineModelState.Downloading))),
            storage = previewSizedStorage,
            nudge = null,
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            onBack = {},
            onGet = {},
            onStopDownload = {},
            onRetry = {},
            onRemove = {},
            onDismissNudge = {},
            onBrowseAll = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ManagePacksScreenFailedPreview() {
    TranzlateTheme {
        ManagePacksContent(
            loading = false,
            sections =
                previewSections(
                    failed =
                        listOf(
                            row("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                            row("ta", "Tamil", OfflineModelState.Failed(OfflineModelFailure.STORAGE)),
                        ),
                ),
            storage = previewSizedStorage,
            nudge = null,
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            onBack = {},
            onGet = {},
            onStopDownload = {},
            onRetry = {},
            onRemove = {},
            onDismissNudge = {},
            onBrowseAll = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ManagePacksScreenEmptyPreview() {
    TranzlateTheme {
        ManagePacksContent(
            loading = false,
            sections = previewSections(onDevice = emptyList()),
            storage = StorageCard.FreeOnly(packCount = 0, freeBytes = 23 * GB),
            nudge = null,
            suggestions =
                listOf(
                    SuggestedLanguage("es", "Spanish", LanguageAvatar.Code("ES"), SuggestionReason.DEVICE_LANGUAGE),
                    SuggestedLanguage("fr", "French", LanguageAvatar.Code("FR"), SuggestionReason.COMMON_WHERE_YOU_ARE),
                ),
            capable = 59,
            total = 194,
            onBack = {},
            onGet = {},
            onStopDownload = {},
            onRetry = {},
            onRemove = {},
            onDismissNudge = {},
            onBrowseAll = {},
        )
    }
}

/** The six meaningful Manage-packs row states, plus the pivot and the deleting spinner (rule 7). */
@PreviewLightDark
@Composable
private fun PackRowStatesPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                PackRow(row("es", "Spanish", OfflineModelState.Downloaded, PackUsage.Today, inUse = true), {}, {}, {})
                PackRow(row("de", "German", OfflineModelState.Downloaded, PackUsage.MonthsAgo(4)), {}, {}, {})
                PackRow(
                    row("pl", "Polish", OfflineModelState.Downloaded, PackUsage.NoRecord, lastUsedMillis = null),
                    {},
                    {},
                    {},
                )
                PackRow(row("en", "English", OfflineModelState.Downloaded, PackUsage.Today, isPivot = true), {}, {}, {})
                PackRow(row("it", "Italian", OfflineModelState.Deleting), {}, {}, {})
                PackRow(row("ar", "Arabic", OfflineModelState.Downloading), {}, {}, {})
                PackRow(row("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)), {}, {}, {})
                PackRow(row("ta", "Tamil", OfflineModelState.Failed(OfflineModelFailure.STORAGE)), {}, {}, {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StorageCardPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                StorageCardView(previewSizedStorage)
                StorageCardView(StorageCard.FreeOnly(packCount = 0, freeBytes = 23 * GB))
                StorageCardView(StorageCard.FreeOnly(packCount = 2, freeBytes = 8 * GB))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NudgeCardPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                NudgeCardView(HygieneNudge(stalePackCount = 1), {})
                NudgeCardView(HygieneNudge(stalePackCount = 2), {})
            }
        }
    }
}
