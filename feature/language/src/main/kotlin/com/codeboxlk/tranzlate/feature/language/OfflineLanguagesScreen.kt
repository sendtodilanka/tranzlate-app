package com.codeboxlk.tranzlate.feature.language

import android.os.LocaleList
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo

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
        remember(data.rows, data.usage, data.targetId, locale) {
            buildManagePacksSections(data.rows, data.usage, data.targetId, locale)
        }
    val nudge =
        remember(sections.onDevice, data.nowMillis, data.nudgeDismissed) {
            if (data.nudgeDismissed) null else hygieneNudge(sections.onDevice, data.nowMillis)
        }
    // The packs the 20e "Free up space" sheet lists — the SAME [stalePacks] the
    // nudge counts, so the sheet and the nudge can never disagree. Computed here
    // (not the ViewModel) because it depends on the on-device SECTIONS the composable
    // builds from the reader's locale, exactly as [nudge] does.
    val stalePackRows =
        remember(sections.onDevice, data.nowMillis) {
            stalePacks(sections.onDevice, data.nowMillis)
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
        usageAsSource = data.usageAsSource,
        usageAsTarget = data.usageAsTarget,
        nowMillis = data.nowMillis,
        onBack = onBack,
        onGet = viewModel::download,
        onStopDownload = viewModel::stopDownload,
        onRetry = viewModel::download,
        onDismissFailure = viewModel::dismissFailure,
        onRemove = viewModel::requestRemove,
        onUseAsTarget = viewModel::useAsTarget,
        onSavedCount = viewModel::savedCountFor,
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
        stalePacks = stalePackRows,
        onRemovePacks = viewModel::removePacks,
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
    // 20b failed-row "Dismiss" (#336): drop the failed row for the session. Defaulted so
    // the render-test / preview call sites that do not exercise a failure are untouched.
    onDismissFailure: (String) -> Unit = {},
    // 20d detail-pane saved-phrases count for the SELECTED pack (#332), queried by the
    // two-pane from its screen-local selection. Defaulted to 0 so the compact-width
    // render tests and previews that draw no detail pane are untouched.
    onSavedCount: suspend (String) -> Int = { 0 },
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
    // The 20e "Free up space" cleanup (#130 PR-25): the stale packs it lists — the
    // SAME set the nudge counts — and the batch-remove it commits. Defaulted so the
    // existing render-test / preview call sites are untouched.
    stalePacks: List<PackRow> = emptyList(),
    onRemovePacks: (List<String>) -> Unit = {},
    // 20d list-detail (#130 PR-26): the per-role #122 maps and the clock the detail
    // pane's "source" line reads. Defaulted so the compact-width render tests and the
    // previews that draw no detail pane keep their existing call untouched.
    usageAsSource: Map<String, Long> = emptyMap(),
    usageAsTarget: Map<String, Long> = emptyMap(),
    nowMillis: Long = 0L,
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
    // The 20e "Free up space" sheet's open flag. Screen-local like [actionsTarget]:
    // the nudge's "Review N packs" and (a picker's) 19b "Free up space" open it, and
    // dismiss / confirm close it. The CHECKBOX selection inside the sheet is its own
    // rememberSaveable and survives process death; this flag only needs to survive a
    // recomposition, which `remember` does (#130 PR-25).
    var freeUpSpaceOpen by remember { mutableStateOf(false) }
    // 20d (#130 PR-26): the two-pane list-detail appears ONLY at EXPANDED width
    // (≥840dp — a tablet or unfolded foldable in landscape, the 1280×800 the ruling
    // names). Read through the C-13 canonical `WindowInfo`, never a hardcoded dp:
    // the picker needs sub-breakpoint dp arithmetic for the OnePlus 7 Pro (issue
    // #99), but list-detail wants exactly the M3 expanded breakpoint and nothing
    // finer, so the size class is the honest signal here.
    val windowInfo = rememberWindowInfo()
    // Which pack the detail pane shows. Screen-local and `rememberSaveable` (a
    // String survives process death) — the selection is a view concern, not
    // ViewModel state, and it must outlive a rotation that crosses the width gate.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
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
    FreeUpSpaceSheet(
        visible = freeUpSpaceOpen,
        stalePacks = stalePacks,
        storage = storage,
        nowMillis = nowMillis,
        onRemovePacks = onRemovePacks,
        onDismiss = { freeUpSpaceOpen = false },
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

                windowInfo.isExpanded -> {
                    ManagePacksTwoPane(
                        sections = sections,
                        storage = storage,
                        nudge = nudge,
                        suggestions = suggestions,
                        capable = capable,
                        total = total,
                        usageAsSource = usageAsSource,
                        usageAsTarget = usageAsTarget,
                        nowMillis = nowMillis,
                        selectedId = selectedId,
                        onSelectPack = { selectedId = it.id },
                        onSavedCount = onSavedCount,
                        onGet = onGet,
                        onStopDownload = onStopDownload,
                        onRemove = onRemove,
                        onReviewPacks = { freeUpSpaceOpen = true },
                        onBrowseAll = onBrowseAll,
                    )
                }

                sections.hasPacks -> {
                    PopulatedList(
                        sections = sections,
                        storage = storage,
                        nudge = nudge,
                        capable = capable,
                        total = total,
                        nowMillis = nowMillis,
                        onStopDownload = onStopDownload,
                        onRetry = onRetry,
                        onDismissFailure = onDismissFailure,
                        onMoreOptions = { row ->
                            actionsTarget = PackActionsTarget(row.id, row.displayName, row.hasOfflineVoice)
                        },
                        onDismissNudge = onDismissNudge,
                        onReviewPacks = { freeUpSpaceOpen = true },
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
    // The clock each on-device row reads to turn its [PackUsage.Used] stamp into a
    // relative "5 days ago" line (#325). One value per emission, from the ViewModel.
    nowMillis: Long,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
    onDismissNudge: () -> Unit,
    onReviewPacks: () -> Unit,
    onBrowseAll: () -> Unit,
    // 20b failed-row "Dismiss" (#336): the failed section's second control beside Retry.
    // Defaulted so the empty/other call sites are untouched.
    onDismissFailure: (String) -> Unit = {},
    // 20d (#130 PR-26): non-null ONLY in the expanded-width two-pane, where a row
    // tap SELECTS the pack the detail shows. Null on a phone, where the list is the
    // whole screen and the row keeps its single job — the overflow — untouched.
    selectedId: String? = null,
    onSelectPack: ((PackRow) -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(bottom = spacing.lg24),
        modifier = Modifier.fillMaxSize().testTag("tt_manage_list"),
    ) {
        storage?.let { item(key = "storage") { StorageCardView(it, Modifier.padding(vertical = spacing.sm8)) } }
        nudge?.let {
            item(key = "nudge") {
                NudgeCardView(it, onDismissNudge, onReviewPacks, Modifier.padding(bottom = spacing.sm8))
            }
        }
        if (sections.downloading.isNotEmpty()) {
            item(key = "hdr_downloading") { SectionHeaderText(stringResource(R.string.manage_section_downloading)) }
            items(sections.downloading, key = { "dl_${it.id}" }) { row ->
                SelectablePackRow(
                    row = row,
                    selected = row.id == selectedId,
                    nowMillis = nowMillis,
                    onSelectPack = onSelectPack,
                    onStopDownload = onStopDownload,
                    onRetry = onRetry,
                    onDismissFailure = onDismissFailure,
                    onMoreOptions = onMoreOptions,
                )
            }
            item(key = "downloading_note") { DownloadingNote() }
        }
        if (sections.failed.isNotEmpty()) {
            item(key = "hdr_failed") { SectionHeaderText(stringResource(R.string.manage_section_failed)) }
            items(sections.failed, key = { "fail_${it.id}" }) { row ->
                SelectablePackRow(
                    row = row,
                    selected = row.id == selectedId,
                    nowMillis = nowMillis,
                    onSelectPack = onSelectPack,
                    onStopDownload = onStopDownload,
                    onRetry = onRetry,
                    onDismissFailure = onDismissFailure,
                    onMoreOptions = onMoreOptions,
                )
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
                SelectablePackRow(
                    row = row,
                    selected = row.id == selectedId,
                    nowMillis = nowMillis,
                    onSelectPack = onSelectPack,
                    onStopDownload = onStopDownload,
                    onRetry = onRetry,
                    onDismissFailure = onDismissFailure,
                    onMoreOptions = onMoreOptions,
                )
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

// ── Two-pane list-detail (20d · #130 PR-26) ──────────────────────────────────

/**
 * The list pane's fixed width in the 20d two-pane — the rev5 frame's exact 392dp. The
 * detail takes the rest of a 1280dp tablet, separated by [D20d.PaneGap] (22dp) — a plain
 * `Row`, never `ListDetailPaneScaffold` and never the `adaptive-layout` dependency (a
 * standing REJECT, ruling :90/:238).
 */
private val ManagePacksListWidth = D20d.ListWidth

/**
 * 20d Manage packs at EXPANDED width: the existing list on the left, the selected
 * pack's detail on the right, as a plain `Row` gated by [WindowInfo.isExpanded]
 * (the caller's branch). The list pane is the SAME [PopulatedList]/[EmptyList] a
 * phone draws — only now its rows select the pack the detail shows — so there is
 * one list, not a widened rewrite of it.
 */
@Composable
private fun ManagePacksTwoPane(
    sections: ManagePacksSections,
    storage: StorageCard?,
    nudge: HygieneNudge?,
    suggestions: List<SuggestedLanguage>,
    capable: Int,
    total: Int,
    usageAsSource: Map<String, Long>,
    usageAsTarget: Map<String, Long>,
    nowMillis: Long,
    selectedId: String?,
    onSelectPack: (PackRow) -> Unit,
    onSavedCount: suspend (String) -> Int,
    onGet: (String) -> Unit,
    onStopDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReviewPacks: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    // The 20d dense list has no per-row Retry / Dismiss / overflow and the green nudge no
    // "Not now" (the frame draws none — the detail pane is the action surface at this width),
    // so the transfer/failure/nudge-dismiss callbacks the compact pane needs are not taken here.
    // Every selectable pack, in the order the list draws them, so the default
    // selection is the first row the eye lands on and the detail is never blank
    // while packs exist. A remembered stale id (its pack removed) falls back to
    // that first row rather than a dead pane.
    val displayed = remember(sections) { sections.downloading + sections.failed + sections.onDevice }
    val selectedPack = displayed.firstOrNull { it.id == selectedId } ?: displayed.firstOrNull()
    val roleUsage =
        remember(selectedPack?.id, usageAsSource, usageAsTarget) {
            selectedPack?.let { packRoleUsage(it.id, usageAsSource, usageAsTarget) }
        }
    // The selected pack's saved-phrases count for the detail pane (#332). Queried by
    // the SELECTED id only — the detail shows one pack, so this is one keyed query, not
    // a walk of every pack — and re-run when the selection changes. `0` while no pack is
    // selected, which the pane draws as an ABSENT saved line (the remove sheets' rule).
    val savedCount by
        produceState(0, selectedPack?.id) {
            value = selectedPack?.id?.let { onSavedCount(it) } ?: 0
        }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(D20d.PaneGap),
    ) {
        Box(modifier = Modifier.width(ManagePacksListWidth)) {
            if (sections.hasPacks) {
                // The DENSE 20d list (frame's own presentation): the sized storage card, the
                // green hygiene nudge, then Downloading / On-this-device / Did-not-download,
                // each a 36dp selectable row with NO trailing control — at expanded width the
                // detail pane is the action surface, so a row's one job is to select. This is a
                // deliberate sibling of the 64dp touch [PopulatedList] the phone (20b) keeps,
                // because the two frames draw the left column at different densities (20d rows
                // are 36dp / 36px avatars; 20b rows are 64dp) — verified against 20b.json/20d.json.
                ManagePacksList20d(
                    sections = sections,
                    storage = storage,
                    nudge = nudge,
                    capable = capable,
                    nowMillis = nowMillis,
                    // The RESOLVED selection, so the highlighted row and the detail always
                    // agree — even on the default-first row, before any tap.
                    selectedId = selectedPack?.id,
                    onSelectPack = onSelectPack,
                    onStopDownload = onStopDownload,
                    onReviewPacks = onReviewPacks,
                    onBrowseAll = onBrowseAll,
                )
            } else {
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
        ManagePacksDetailPane(
            pack = selectedPack,
            roleUsage = roleUsage,
            savedCount = savedCount,
            nowMillis = nowMillis,
            onRemove = onRemove,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

// ── The dense 20d left list (frame-exact) ────────────────────────────────────

/**
 * The two-pane LEFT column, at the 20d frame's exact geometry: the sized storage card, the
 * green hygiene nudge, then the sections the frame draws in its order — Downloading, On this
 * device, Did not download — each a 36dp selectable row. A row carries NO trailing control
 * (the detail pane holds the actions at expanded width); the only in-list control is the
 * downloading card's "Stop download and remove". Browse-all sits at the very bottom so a
 * user with packs can still reach the picker to add another (no dead end).
 */
@Composable
private fun ManagePacksList20d(
    sections: ManagePacksSections,
    storage: StorageCard?,
    nudge: HygieneNudge?,
    capable: Int,
    nowMillis: Long,
    selectedId: String?,
    onSelectPack: (PackRow) -> Unit,
    onStopDownload: (String) -> Unit,
    onReviewPacks: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = D20d.NudgeTop, bottom = D20d.NudgeTop),
        modifier = Modifier.fillMaxSize().testTag("tt_manage_list"),
    ) {
        storage?.let { item(key = "storage") { TwoPaneStorageCard(it) } }
        nudge?.let { item(key = "nudge") { TwoPaneNudge(it, onReviewPacks, Modifier.padding(top = D20d.NudgeTop)) } }
        if (sections.downloading.isNotEmpty()) {
            item(key = "hdr_dl") { TwoPaneSectionHeader(stringResource(R.string.manage_section_downloading)) }
            items(sections.downloading, key = { "dl_${it.id}" }) { row ->
                TwoPaneDownloadingCard(row = row, onStopDownload = onStopDownload)
            }
        }
        if (sections.onDevice.isNotEmpty()) {
            item(key = "hdr_dev") {
                TwoPaneSectionHeader(
                    text = stringResource(R.string.manage_section_on_device),
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
                TwoPaneSettledRow(
                    row = row,
                    selected = row.id == selectedId,
                    nowMillis = nowMillis,
                    onSelectPack = onSelectPack,
                )
            }
        }
        if (sections.failed.isNotEmpty()) {
            item(key = "hdr_fail") { TwoPaneSectionHeader(stringResource(R.string.manage_section_failed)) }
            items(sections.failed, key = { "fail_${it.id}" }) { row ->
                TwoPaneFailedRow(row = row, selected = row.id == selectedId, onSelectPack = onSelectPack)
            }
        }
        item(key = "browse") { BrowseAllButton(onBrowseAll) }
    }
}

/**
 * The sized storage card the frame draws in the list pane: the aggregate bytes and pack
 * count on one line ("110 MB across 5 packs · 1.4 GB free"), the device-used bar, and the
 * explainer. bg `surfaceContainerLow` (#f0f4f9), r22, p16 — the frame's own values. A
 * FreeOnly card (no measured bytes) degrades to its device-free figure in the same shell.
 */
@Composable
private fun TwoPaneStorageCard(card: StorageCard) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(D20d.StorageRadius))
                .background(scheme.surfaceContainerLow)
                .padding(D20d.StoragePad)
                .semantics(mergeDescendants = true) {}
                .testTag("tt_manage_storage"),
    ) {
        val bytesText: String
        val caption: String
        when (card) {
            is StorageCard.Sized -> {
                bytesText = Formatter.formatShortFileSize(context, card.packsBytes)
                caption =
                    pluralStringResource(R.plurals.manage_storage_across, card.packCount, card.packCount) +
                    " · " +
                    stringResource(
                        R.string.lang_sheet_space_free,
                        Formatter.formatShortFileSize(context, card.freeBytes),
                    )
            }

            is StorageCard.FreeOnly -> {
                bytesText = Formatter.formatShortFileSize(context, card.freeBytes)
                caption = stringResource(R.string.manage_storage_free_caption)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = bytesText,
                fontSize = D20d.StorageNumSize,
                lineHeight = D20d.StorageNumLine,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
            )
            Text(
                text = caption,
                fontSize = D20d.StorageCapSize,
                lineHeight = D20d.StorageCapLine,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = D20d.StorageCapStart, bottom = 3.dp),
            )
        }
        if (card is StorageCard.Sized) {
            LinearProgressIndicator(
                progress = { deviceUsedFraction(card.freeBytes, card.totalBytes) },
                color = scheme.primaryContainer,
                trackColor = scheme.surfaceContainerHigh,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = D20d.StorageBarTop)
                        .height(D20d.StorageBarHeight)
                        .clip(RoundedCornerShape(D20d.StorageBarRadius))
                        .clearAndSetSemantics {},
            )
        }
        Text(
            text = stringResource(R.string.manage_storage_explainer),
            fontSize = D20d.StorageExplainSize,
            lineHeight = D20d.StorageExplainLine,
            color = scheme.outline,
            modifier = Modifier.padding(top = D20d.StorageExplainTop),
        )
    }
}

/**
 * The green storage-hygiene nudge (frame): tertiary-fixed fill (#c4eed0), the cleaning icon +
 * title on one line, the reassurance, and a single "Review" text action (right). Theme-invariant
 * green via the `*Fixed` roles, so it stays green in dark too. "Not now" is the phone nudge's
 * action; at expanded width the frame draws Review alone.
 */
@Composable
private fun TwoPaneNudge(
    nudge: HygieneNudge,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(D20d.NudgeRadius))
                .background(scheme.tertiaryFixed)
                .padding(
                    start = D20d.NudgePadH,
                    end = D20d.NudgePadH,
                    top = D20d.NudgePadTop,
                    bottom = D20d.NudgePadBottom,
                ).testTag("tt_manage_nudge"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(D20d.NudgeIconGap),
        ) {
            Icon(
                Icons.Filled.CleaningServices,
                contentDescription = null,
                tint = scheme.onTertiaryFixed,
                modifier = Modifier.size(D20d.NudgeIcon),
            )
            Text(
                text = pluralStringResource(R.plurals.manage_nudge_title, nudge.stalePackCount, nudge.stalePackCount),
                fontSize = D20d.NudgeTitleSize,
                lineHeight = D20d.NudgeTitleLine,
                fontWeight = FontWeight.Medium,
                color = scheme.onTertiaryFixed,
            )
        }
        Text(
            text = stringResource(R.string.manage_nudge_freeing),
            fontSize = D20d.NudgeBodySize,
            lineHeight = D20d.NudgeBodyLine,
            color = scheme.onTertiaryFixed,
            modifier = Modifier.padding(top = D20d.NudgeBodyTop, start = D20d.NudgeIcon + D20d.NudgeIconGap),
        )
        TextButton(
            onClick = onReview,
            contentPadding = PaddingValues(horizontal = D20d.NudgeActionPadH),
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.onTertiaryFixed),
            modifier =
                Modifier
                    .align(Alignment.End)
                    .height(D20d.NudgeActionHeight)
                    .testTag("tt_manage_nudge_review"),
        ) {
            Text(
                text = stringResource(R.string.manage_nudge_review_short),
                fontSize = D20d.NudgeActionText,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** A dense list section header (frame): blue (`onPrimaryContainer`), uppercase, tracked, 12sp/500, with an optional " · N of M" count. */
@Composable
private fun TwoPaneSectionHeader(
    text: String,
    count: String? = null,
) {
    Text(
        text = if (count == null) text.uppercase() else (text + " · " + count).uppercase(),
        fontSize = D20d.ListHeaderSize,
        lineHeight = D20d.ListHeaderLine,
        fontWeight = FontWeight.Medium,
        letterSpacing = D20d.ListHeaderTracking,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = D20d.ListHeaderTop, bottom = D20d.ListHeaderBottom),
    )
}

/**
 * The downloading card (frame): a blue (`primaryContainer`) card with the pack's avatar, name
 * and "Downloading…" line, an indeterminate progress bar (ML Kit gives no %; the bar is
 * indeterminate on purpose, brief §"Engines"), and a "Stop download and remove" text action —
 * delete-to-cancel, because there is no cancel API.
 */
@Composable
private fun TwoPaneDownloadingCard(
    row: PackRow,
    onStopDownload: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = D20d.DownloadTop)
                .clip(RoundedCornerShape(D20d.DownloadRadius))
                .background(scheme.primaryContainer)
                .padding(
                    start = D20d.DownloadPadH,
                    end = D20d.DownloadPadH,
                    top = D20d.DownloadPadTop,
                    bottom = D20d.DownloadPadBottom,
                ).testTag("tt_manage_downloading_card"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(D20d.DenseRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackAvatar(id = row.id, downloaded = true, size = D20d.DenseAvatar, initialsSize = D20d.InitialsSm)
            Column {
                Text(
                    text = row.displayName,
                    fontSize = D20d.DownloadNameSize,
                    lineHeight = D20d.DownloadNameLine,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.text_lang_downloading),
                    fontSize = D20d.DownloadSubSize,
                    lineHeight = D20d.DownloadSubLine,
                    color = scheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.padding(start = D20d.DenseAvatar + D20d.DenseRowGap)) {
            // Indeterminate: ML Kit's download() gives no progress %, so the bar cannot claim
            // one (brief §"Engines"). The indeterminate overload has no drawStopIndicator.
            LinearProgressIndicator(
                color = scheme.primary,
                trackColor = scheme.surfaceContainerLowest,
                gapSize = 0.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = D20d.DownloadBarTop)
                        .height(D20d.DownloadBarHeight)
                        .clip(RoundedCornerShape(D20d.DownloadBarRadius))
                        .clearAndSetSemantics {},
            )
            TextButton(
                onClick = { onStopDownload(row.id) },
                contentPadding = PaddingValues(horizontal = D20d.NudgeActionPadH),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onPrimaryContainer),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .height(D20d.DownloadActionHeight)
                        .testTag("tt_manage_stop"),
            ) {
                Text(
                    text = stringResource(R.string.manage_stop_download),
                    fontSize = D20d.DownloadActionText,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * A dense 36dp on-device row (frame): avatar + name + "On device · used …" subtitle, no
 * trailing control. Selecting it drives the detail pane; the selected row is the frame's blue
 * stadium (`primaryContainer` clipped to a pill), its text recoloured to `onPrimaryContainer`.
 * A STALE pack (unused past the threshold) greys its avatar — the frame's own cue that it is a
 * cleanup candidate. An `IN USE` chip rides the name for the current target.
 */
@Composable
private fun TwoPaneSettledRow(
    row: PackRow,
    selected: Boolean,
    nowMillis: Long,
    onSelectPack: (PackRow) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stale = isStale(row.usage, nowMillis)
    val nameColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    val subColor = if (selected) scheme.onPrimaryContainer else scheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.DenseRowGap),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = D20d.DenseRowBottom)
                .heightIn(min = D20d.DenseRowHeight)
                .clip(RoundedCornerShape(D20d.DenseRowRadius))
                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                .selectable(selected = selected, onClick = { onSelectPack(row) })
                .padding(horizontal = D20d.DenseRowPadH)
                .testTag("tt_manage_select_row"),
    ) {
        PackAvatar(id = row.id, downloaded = !stale, size = D20d.DenseAvatar, initialsSize = D20d.InitialsSm)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.displayName,
                    fontSize = D20d.DenseNameSize,
                    lineHeight = D20d.DenseNameLine,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = nameColor,
                )
                if (row.inUse) {
                    InUseBadge(modifier = Modifier.padding(start = D20d.DenseNameChipGap))
                }
            }
            Text(
                text = stringResource(R.string.text_lang_on_device_size, packUsageText(row.usage, nowMillis)),
                fontSize = D20d.DenseSubSize,
                lineHeight = D20d.DenseSubLine,
                color = subColor,
            )
        }
    }
}

/**
 * A dense failed row (frame): a `cloud_off` error glyph in place of the avatar, the name, and
 * the failure line, both in `error`. Selecting it shows the failure in the detail pane. It
 * carries no inline Retry — at expanded width the actions live in the detail (the frame draws
 * the failed row control-free).
 */
@Composable
private fun TwoPaneFailedRow(
    row: PackRow,
    selected: Boolean,
    onSelectPack: (PackRow) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val failure = (row.state as? OfflineModelState.Failed)?.cause
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.DenseRowGap),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = D20d.DenseRowBottom)
                .heightIn(min = D20d.DenseRowHeight)
                .clip(RoundedCornerShape(D20d.DenseRowRadius))
                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                .selectable(selected = selected, onClick = { onSelectPack(row) })
                .padding(horizontal = D20d.DenseRowPadH)
                .testTag("tt_manage_select_row"),
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier.size(D20d.FailedIcon),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName,
                fontSize = D20d.DenseNameSize,
                lineHeight = D20d.DenseNameLine,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
            Text(
                text = failure?.let { stringResource(downloadFailureCopy(it).rowLine) }.orEmpty(),
                fontSize = D20d.DenseSubSize,
                lineHeight = D20d.DenseSubLine,
                color = scheme.error,
                modifier = Modifier.testTag("tt_manage_error_line"),
            )
        }
    }
}

/**
 * One list-pane row that, in the two-pane, SELECTS the pack the detail shows.
 *
 * When [onSelectPack] is null (the phone's single pane) it is exactly the old
 * [PackRow] and nothing about the row changes. When non-null it wraps the same row
 * in a `selectable`, so a tap picks the pack and the row keeps its own overflow
 * button working (a nested control the selectable does not swallow). The picked row is
 * the frame's STADIUM highlight: `primaryContainer` fill clipped to a `RoundedCornerShape`
 * of HALF the row height (README's corner rule — the square-corner defect the owner
 * flagged was a `.background()` with no `.clip()`), its text recoloured to
 * `onPrimaryContainer`, and the "selected" state a screen reader announces.
 */
@Composable
private fun SelectablePackRow(
    row: PackRow,
    selected: Boolean,
    nowMillis: Long,
    onSelectPack: ((PackRow) -> Unit)?,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDismissFailure: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
) {
    if (onSelectPack == null) {
        PackRow(
            row = row,
            nowMillis = nowMillis,
            onStopDownload = onStopDownload,
            onRetry = onRetry,
            onMoreOptions = onMoreOptions,
            onDismissFailure = onDismissFailure,
        )
        return
    }
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(D20d.RowRadius))
                .background(background)
                .selectable(selected = selected, onClick = { onSelectPack(row) })
                .testTag("tt_manage_select_row"),
    ) {
        PackRow(
            row = row,
            nowMillis = nowMillis,
            selected = selected,
            onStopDownload = onStopDownload,
            onRetry = onRetry,
            onMoreOptions = onMoreOptions,
            onDismissFailure = onDismissFailure,
        )
    }
}

/**
 * The rev5 20d frame's EXACT geometry + type, extracted from
 * `docs/design/language-screens/language-screens-spec.html` (`dv-opt id="20d"`), so the
 * pixel-match is auditable in ONE place rather than scattered as literals (owner's
 * 100%-visual-match standard, 2026-08-06). Colours are NOT here — they map to
 * `MaterialTheme.colorScheme` role tokens (README §Tokens / DESIGN_SYSTEM §1); only the
 * numbers the frame states in px live here, as dp/sp.
 *
 * Where a value breaks the README "8dp (or 4) spacing scale" (18/22/26/30/14/10), it is
 * the frame's own number and rev5 is the SSOT — kept exact, flagged in the PR for
 * system reconciliation. `RowRadius` is HALF `RowHeight` (README's stadium rule).
 */
private object D20d {
    // Two-pane
    val PaneGap = 22.dp // list ↔ detail (frame body gap; header/label wrapper was 18)
    val ListWidth = 392.dp

    // Detail pane card
    val PaneRadius = 28.dp
    val PanePadH = 30.dp
    val PanePadV = 28.dp

    // Identity
    val IdentityGap = 18.dp
    val AvatarLg = 64.dp
    val AvatarSm = 36.dp
    val InitialsLg = 15.sp
    val InitialsSm = 11.5.sp
    val InitialsTracking = 0.5.sp
    val NameSize = 28.sp
    val NameLine = 34.sp
    val StatusSize = 13.5.sp
    val StatusLine = 19.sp
    val StatusGap = 4.dp

    // Capability cards
    val CapRowGap = 14.dp
    val CapRowTop = 26.dp
    val CapMinWidth = 190.dp
    val CapPad = 16.dp
    val CapRadius = 20.dp
    val CapIcon = 22.dp

    // The frame draws the icon in a 26-tall line box (Material Symbols 22px glyph in a 26px
    // line), so the card content stacks to exactly h107: pad16 + iconBox26 + titleTop10 +
    // title20 + subTop2 + sub17 + pad16. An Icon(size=22) alone is 22 tall and lands the card
    // at 103 — the 4px the frame's line box carries. Reproduce it, not fudge a padding.
    val CapIconBox = 26.dp
    val CapTitleSize = 15.sp
    val CapTitleLine = 20.sp
    val CapTitleTop = 10.dp
    val CapSubSize = 12.5.sp
    val CapSubLine = 17.sp
    val CapSubTop = 2.dp

    // "Where this pack is used"
    val SectionTop = 26.dp
    val HeaderSize = 12.sp
    val HeaderLine = 16.sp
    val HeaderTracking = 0.8.sp
    val HeaderBottom = 10.dp
    val UsedLineGap = 2.dp
    val UsedIcon = 20.dp
    val UsedRowGap = 14.dp
    val UsedPadV = 10.dp
    val UsedPadH = 2.dp
    val UsedTextSize = 14.sp
    val UsedTextLine = 20.sp

    // The frame constrains the "where used" lines to a 560-wide column inside the 750-wide
    // pane (readable measure), not the full pane width. Longer lines wrap at 560.
    val UsedColWidth = 560.dp

    // Remove block
    val RemoveTop = 26.dp
    val RemovePad = 16.dp
    val RemoveRadius = 20.dp
    val RemoveGap = 12.dp
    val RemoveIcon = 20.dp
    val RemoveBodySize = 13.5.sp
    val RemoveBodyLine = 19.sp
    val RemoveBtnHeight = 44.dp
    val RemoveBtnPadH = 20.dp
    val RemoveBtnRadius = 22.dp
    val RemoveBtnText = 14.5.sp

    // List rows (stadium: radius = height / 2) — the COMPACT (phone / 20b) row.
    val RowHeight = 64.dp
    val RowRadius = 32.dp
    val RowGap = 14.dp
    val RowPadH = 12.dp
    val RowBottom = 6.dp
    val RowNameSize = 15.sp
    val RowNameLine = 20.sp
    val RowSubSize = 12.sp
    val RowSubLine = 16.sp

    // ── Two-pane LEFT list (20d dense column) — the frame's own values, distinct from the
    // 64dp touch rows above: at expanded width the list is a dense index (36dp rows), the
    // detail pane is where actions live, so a row carries no trailing control. ─────────────

    // Sized storage card
    val ListPadStart = 12.dp // frame content left pad (screen x8 + 12 → list x20)
    val ListPadEnd = 28.dp
    val StorageRadius = 22.dp
    val StoragePad = 16.dp
    val StorageNumSize = 26.sp
    val StorageNumLine = 32.sp
    val StorageCapSize = 13.sp
    val StorageCapLine = 16.sp
    val StorageCapStart = 8.dp
    val StorageBarTop = 12.dp
    val StorageBarHeight = 9.dp
    val StorageBarRadius = 5.dp
    val StorageExplainTop = 10.dp
    val StorageExplainSize = 11.5.sp
    val StorageExplainLine = 16.sp

    // Green hygiene nudge
    val NudgeTop = 12.dp
    val NudgeRadius = 20.dp
    val NudgePadH = 14.dp
    val NudgePadTop = 14.dp
    val NudgePadBottom = 10.dp
    val NudgeIcon = 20.dp
    val NudgeIconGap = 10.dp
    val NudgeTitleSize = 14.5.sp
    val NudgeTitleLine = 20.sp
    val NudgeBodyTop = 2.dp
    val NudgeBodySize = 12.sp
    val NudgeBodyLine = 17.sp
    val NudgeActionText = 13.5.sp
    val NudgeActionHeight = 40.dp
    val NudgeActionPadH = 14.dp

    // Section header (blue, uppercase, tracked)
    val ListHeaderTop = 14.dp
    val ListHeaderSize = 12.sp
    val ListHeaderLine = 16.sp
    val ListHeaderTracking = 0.8.sp
    val ListHeaderBottom = 6.dp

    // Downloading card (blue)
    val DownloadTop = 14.dp
    val DownloadRadius = 20.dp
    val DownloadPadH = 12.dp
    val DownloadPadTop = 12.dp
    val DownloadPadBottom = 8.dp
    val DownloadNameSize = 14.5.sp
    val DownloadNameLine = 20.sp
    val DownloadSubSize = 12.sp
    val DownloadSubLine = 16.sp
    val DownloadBarTop = 9.dp
    val DownloadBarHeight = 4.dp
    val DownloadBarRadius = 2.dp
    val DownloadActionText = 13.5.sp
    val DownloadActionHeight = 40.dp

    // Dense settled / failed rows
    val DenseRowHeight = 36.dp
    val DenseRowRadius = 32.dp
    val DenseAvatar = 36.dp
    val DenseRowGap = 14.dp
    val DenseRowPadH = 12.dp
    val DenseRowBottom = 6.dp
    val DenseNameSize = 15.sp
    val DenseNameLine = 20.sp
    val DenseNameChipGap = 8.dp
    val DenseSubSize = 12.sp
    val DenseSubLine = 16.sp
    val FailedIcon = 22.dp

    // IN USE chip (tertiary family)
    val ChipHeight = 19.dp
    val ChipPadH = 7.dp
    val ChipRadius = 5.dp
    val ChipText = 10.sp
    val ChipTracking = 0.4.sp
}

/**
 * The 20d detail pane for the SELECTED pack (conformance #332), top→bottom: the
 * pack's identity with its on-device status subtitle, the offline-capability cards,
 * the "where this pack is used" section (saved phrases + per-role last-used from the
 * #122 store), and — for a removable pack — the Remove block.
 *
 * `internal` so the render test and the preview can mount it without the two-pane
 * `Row` around it. Two frame elements stay deliberately ABSENT: the camera card names
 * a feature that does not exist (#78/#112), and the pair-share/`sd_card` line an
 * undocumented version-fragile layout — both standing REJECTs of the rev.3 ruling
 * (§7, :238/:249/:252). The aggregate storage card the frame draws in the LIST pane is
 * NOT duplicated here (#331 / the #330 co-verify). Every date shown is a real usage
 * stamp or the honest "no recorded use yet"; the pane never fabricates one (ruling ⑧),
 * and each capability card states its offline status in WORDS, never colour alone.
 *
 * @param savedCount saved phrases in this language, on either side — the bookmark
 *   line, ABSENT at zero (a missing reassurance, never a false one).
 * @param onRemove routes to the EXISTING 19f/19g remove-confirm flow (the same action
 *   the 20c sheet takes); it never deletes on tap.
 */
@Composable
internal fun ManagePacksDetailPane(
    pack: PackRow?,
    roleUsage: PackRoleUsage?,
    savedCount: Int,
    nowMillis: Long,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pack == null || roleUsage == null) {
        DetailNoSelection(modifier)
        return
    }
    val detail = packDetail(pack)
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(D20d.PaneRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = D20d.PanePadH, vertical = D20d.PanePadV)
                .testTag("tt_manage_detail"),
    ) {
        DetailIdentity(pack = pack, detail = detail)
        DetailCapabilities(detail = detail, modifier = Modifier.padding(top = D20d.CapRowTop))
        Column(modifier = Modifier.padding(top = D20d.SectionTop)) {
            Text(
                text = stringResource(R.string.manage_detail_usage_header).uppercase(),
                fontSize = D20d.HeaderSize,
                lineHeight = D20d.HeaderLine,
                fontWeight = FontWeight.Medium,
                letterSpacing = D20d.HeaderTracking,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = D20d.HeaderBottom),
            )
            Column(
                modifier = Modifier.width(D20d.UsedColWidth),
                verticalArrangement = Arrangement.spacedBy(D20d.UsedLineGap),
            ) {
                if (savedCount > 0) {
                    DetailSavedLine(count = savedCount, languageName = pack.displayName)
                }
                // Owner override: ONE last-used line, framed as source (not an As-source/
                // As-target split). It reads the SOURCE-role #122 stamp, honest per role.
                DetailLastUsedSource(usage = roleUsage.asSource, nowMillis = nowMillis)
                // The pair-share line the frame draws (owner override). Only meaningful for a
                // non-pivot pack that is on the device — English IS the pivot, and a pack not
                // yet on disk has no stored data to share.
                if (detail.onDevice && !isPivotLanguage(pack.id)) {
                    DetailSharesLine()
                }
            }
        }
        if (detail.removable) {
            DetailRemoveBlock(
                pack = pack,
                onRemove = onRemove,
                modifier = Modifier.padding(top = D20d.RemoveTop),
            )
        }
    }
}

/** The detail pane's identity: avatar + name + IN-USE badge, and an honest status subtitle for the state. */
@Composable
private fun DetailIdentity(
    pack: PackRow,
    detail: PackDetail,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.IdentityGap),
    ) {
        PackAvatar(
            id = pack.id,
            downloaded = pack.state == OfflineModelState.Downloaded,
            size = D20d.AvatarLg,
            initialsSize = D20d.InitialsLg,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pack.displayName,
                    fontSize = D20d.NameSize,
                    lineHeight = D20d.NameLine,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("tt_manage_detail_name"),
                )
                if (pack.inUse) {
                    InUseBadge(modifier = Modifier.padding(start = LocalSpacing.current.sm8))
                }
            }
            detailStatusSubtitle(pack = pack, onDevice = detail.onDevice)?.let { subtitle ->
                Text(
                    text = subtitle,
                    fontSize = D20d.StatusSize,
                    lineHeight = D20d.StatusLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = D20d.StatusGap).testTag("tt_manage_detail_status"),
                )
            }
        }
    }
}

/**
 * The status subtitle under the identity — "On device · ready to use with no
 * connection" for a pack on disk, else the honest word for the state it IS in
 * (downloading, or a failure), never "On device" for a pack that is not (ruling ⑧).
 */
@Composable
private fun detailStatusSubtitle(
    pack: PackRow,
    onDevice: Boolean,
): String? {
    val state = pack.state
    return when {
        onDevice -> stringResource(R.string.manage_detail_status_on_device)
        state == OfflineModelState.Downloading -> stringResource(R.string.text_lang_downloading)
        state is OfflineModelState.Failed -> stringResource(downloadFailureCopy(state.cause).rowLine)
        else -> null
    }
}

/**
 * The three offline-capability cards, in the frame's order: Text offline (a downloaded pack),
 * Voice offline (IFF a device voice), and Camera offline (owner override 2026-08-06 — GREEN
 * where the pack's script is one ML Kit reads on-device, mirroring the voice pattern, instead
 * of the frame's grey placeholder). Each is [CapabilityState.Supported] → green, else grey.
 */
@Composable
private fun DetailCapabilities(
    detail: PackDetail,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(D20d.CapRowGap),
    ) {
        CapabilityCard(
            icon = Icons.Outlined.Translate,
            title = stringResource(R.string.manage_detail_cap_text_title),
            subtitle = capabilitySubtitle(detail.textOffline, R.string.manage_detail_cap_text_ready),
            state = detail.textOffline,
            subtitleTag = "tt_manage_detail_cap_text",
            modifier = Modifier.weight(1f),
        )
        CapabilityCard(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.manage_detail_cap_voice_title),
            subtitle = capabilitySubtitle(detail.voiceOffline, R.string.manage_detail_cap_voice_ready),
            state = detail.voiceOffline,
            subtitleTag = "tt_manage_detail_cap_voice",
            modifier = Modifier.weight(1f),
        )
        CapabilityCard(
            icon = Icons.Outlined.PhotoCamera,
            title = stringResource(R.string.manage_detail_cap_camera_title),
            subtitle = capabilitySubtitle(detail.cameraOffline, R.string.manage_detail_cap_camera_ready),
            state = detail.cameraOffline,
            subtitleTag = "tt_manage_detail_cap_camera",
            modifier = Modifier.weight(1f),
        )
    }
}

/** A capability card's subtitle: its own "ready" line when supported, else the shared "needs a connection". */
@Composable
private fun capabilitySubtitle(
    state: CapabilityState,
    readyRes: Int,
): String =
    stringResource(
        if (state == CapabilityState.Supported) readyRes else R.string.manage_detail_cap_offline_unavailable,
    )

/**
 * One capability card: `icon / title / subtitle`, at the rev5 frame's exact geometry
 * (padding 16, radius 20, icon 22, title 15/500, subtitle 12.5). A
 * [CapabilityState.Supported] capability draws GREEN — `tertiaryContainer` fill,
 * `onTertiaryContainer` on everything — with its own "ready" subtitle; an
 * [CapabilityState.Unavailable] one draws NEUTRAL: `surfaceContainerHigh` fill, with the
 * frame's three-tone foreground (icon `onSurfaceVariant`, title `onSurface`, subtitle
 * `outline`) and "Needs a connection". The state is in the WORDS as well as the tint, so
 * a screen reader and a colour-blind reader both get it (a11y: never colour alone).
 */
@Composable
private fun CapabilityCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    state: CapabilityState,
    subtitleTag: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val supported = state == CapabilityState.Supported
    // The green is the THEME-INVARIANT tertiary-FIXED pair (rev5 §B: #C4EED0 / #072711,
    // identical light+dark), so the supported card stays green in dark too — not the
    // *Container roles, whose dark values flip to a dark green with poor text contrast.
    val container = if (supported) scheme.tertiaryFixed else scheme.surfaceContainerHigh
    val iconTint = if (supported) scheme.onTertiaryFixed else scheme.onSurfaceVariant
    val titleColor = if (supported) scheme.onTertiaryFixed else scheme.onSurface
    val subtitleColor = if (supported) scheme.onTertiaryFixed else scheme.outline
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(D20d.CapRadius))
                .background(container)
                .padding(D20d.CapPad)
                .semantics(mergeDescendants = true) {},
    ) {
        // The 22px glyph sits in the frame's 26px icon line box, so the card stacks to h107.
        Box(modifier = Modifier.height(D20d.CapIconBox), contentAlignment = Alignment.TopStart) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(D20d.CapIcon))
        }
        Text(
            text = title,
            fontSize = D20d.CapTitleSize,
            lineHeight = D20d.CapTitleLine,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            modifier = Modifier.padding(top = D20d.CapTitleTop),
        )
        Text(
            text = subtitle,
            fontSize = D20d.CapSubSize,
            lineHeight = D20d.CapSubLine,
            color = subtitleColor,
            modifier = Modifier.padding(top = D20d.CapSubTop).testTag(subtitleTag),
        )
    }
}

/**
 * One "where this pack is used" row — `icon + sentence`, at the frame's exact geometry
 * (icon 20 `onSurfaceVariant`, text 14/20 `onSurface`, row gap 14, padding 10×2). The
 * saved-phrases and per-role usage lines share it so they read as one list.
 */
@Composable
private fun UsedLine(
    icon: ImageVector,
    text: String,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.UsedRowGap),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = D20d.UsedPadV, horizontal = D20d.UsedPadH)
                .semantics(mergeDescendants = true) {},
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(D20d.UsedIcon),
        )
        Text(
            text = text,
            fontSize = D20d.UsedTextSize,
            lineHeight = D20d.UsedTextLine,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).testTag(tag),
        )
    }
}

/** "N saved phrases are in <lang>" — the bookmark line of the "where used" section, shown only above zero. */
@Composable
private fun DetailSavedLine(
    count: Int,
    languageName: String,
) {
    UsedLine(
        icon = Icons.Outlined.Bookmark,
        text = pluralStringResource(R.plurals.manage_detail_saved, count, count, languageName),
        tag = "tt_manage_detail_saved",
    )
}

/**
 * The Remove block (error/loss tone): what removing costs, and the button. The button
 * ROUTES to the existing 19f/19g remove-confirm sheet via [onRemove] — it never deletes
 * on tap — exactly as the 20c pack-actions sheet's Remove row does.
 */
@Composable
private fun DetailRemoveBlock(
    pack: PackRow,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.RemoveGap),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(D20d.RemoveRadius))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(D20d.RemovePad),
    ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(D20d.RemoveIcon),
        )
        Text(
            text = stringResource(R.string.manage_detail_remove_body, pack.displayName),
            fontSize = D20d.RemoveBodySize,
            lineHeight = D20d.RemoveBodyLine,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onRemove(pack.id) },
            shape = RoundedCornerShape(D20d.RemoveBtnRadius),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            contentPadding = PaddingValues(horizontal = D20d.RemoveBtnPadH),
            modifier = Modifier.height(D20d.RemoveBtnHeight).testTag("tt_manage_detail_remove"),
        ) {
            Text(
                text = stringResource(R.string.manage_detail_remove_action),
                fontSize = D20d.RemoveBtnText,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * The single last-used line (owner override 2026-08-06): "Last used as a source language
 * last week", in the frame's `history`-icon + sentence style. It reads the SOURCE-role #122
 * stamp; a role with no stamp reads the honest date-less line ([R.string.manage_detail_never_source]),
 * never a fabricated date (ruling ⑧). The relative span is bucketed elapsed time
 * ([packWhenText]), never a calendar month.
 */
@Composable
private fun DetailLastUsedSource(
    usage: PackUsage,
    nowMillis: Long,
) {
    val text =
        when (usage) {
            PackUsage.NoRecord -> {
                stringResource(R.string.manage_detail_never_source)
            }

            is PackUsage.Used -> {
                stringResource(R.string.manage_detail_last_used_source, packWhenText(usage, nowMillis))
            }
        }
    UsedLine(icon = Icons.Outlined.History, text = text, tag = "tt_manage_detail_last_used")
}

/**
 * The pair-share line the frame draws (owner override): "Shares stored data with English —
 * the pair is kept once". Every non-pivot pack stores its data as an English↔X pair, so this
 * is true for every pack this line is drawn for (a non-pivot on-device pack). The pivot name
 * resolves against the composition's locale, like every other name on the screen.
 */
@Composable
private fun DetailSharesLine() {
    val locale = LocalLocale.current.platformLocale
    UsedLine(
        icon = Icons.Outlined.SdCard,
        text = stringResource(R.string.manage_detail_shares_data, languageDisplayName(PIVOT_LANGUAGE_ID, locale)),
        tag = "tt_manage_detail_shares",
    )
}

/**
 * The bare relative span for the single source line — "today", "last week", "4 months ago" —
 * with no "used" prefix, so [R.string.manage_detail_last_used_source] reads as one sentence.
 * Derived from the stamp at [nowMillis] ([PackUsage.Used.bucket]); a bucketed elapsed span,
 * never a calendar month (ruling ⑧). Distinct from [packUsageText] (the list rows' "used …").
 */
@Composable
private fun packWhenText(
    usage: PackUsage.Used,
    nowMillis: Long,
): String =
    when (val bucket = usage.bucket(nowMillis)) {
        UsageBucket.Today -> stringResource(R.string.manage_when_today)
        is UsageBucket.DaysAgo -> pluralStringResource(R.plurals.manage_when_days, bucket.days, bucket.days)
        is UsageBucket.WeeksAgo -> pluralStringResource(R.plurals.manage_when_weeks, bucket.weeks, bucket.weeks)
        is UsageBucket.MonthsAgo -> pluralStringResource(R.plurals.manage_when_months, bucket.months, bucket.months)
    }

/** The detail pane with nothing selected (no packs yet, or a removed pack) — never a dead end. */
@Composable
private fun DetailNoSelection(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxSize()
                .padding(spacing.lg24)
                .testTag("tt_manage_detail_empty"),
    ) {
        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.iconChip),
        )
        Text(
            text = stringResource(R.string.manage_detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm8),
        )
    }
}

// ── Storage card ────────────────────────────────────────────────────────────

/**
 * The aggregate storage card (brief §2/§3). It states the packs' measured
 * aggregate bytes and the device's free space — never a per-pack size, which ML
 * Kit does not expose. The bar is device-used vs free on the whole volume (the
 * only honest bar at 110 MB against a whole device — 19b's own reasoning), its
 * semantics cleared because the numerals above already say it in words.
 *
 * `internal`, not `private`: the 20e "Free up space" sheet (FreeUpSpaceSheet.kt)
 * reuses this EXACT card for its storage breakdown, which is how "one vocabulary,
 * one colour" is guaranteed rather than re-drawn (#130 PR-25, README.md:15 — the
 * used-vs-free breakdown belongs to 20e, and it is this same card).
 */
@Composable
internal fun StorageCardView(
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
 * threshold. It informs, offers "Not now", and — now that 20e exists (#130 PR-25) —
 * a "Review N packs" action that opens the batch "Free up space" cleanup sheet over
 * exactly the [stalePacks] this count is drawn from. The same stale packs are also
 * listed just below with their "used N months ago" lines and removable one by one,
 * so the nudge was never a dead end even before this action was wired.
 *
 * "Review N packs" is the emphasised (filled) action — reviewing the cleanup is the
 * likely intent of a nudge about clutter — with "Not now" the text action beside it.
 */
@Composable
private fun NudgeCardView(
    nudge: HygieneNudge,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.End).padding(top = spacing.xs4),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tt_manage_nudge_dismiss"),
            ) {
                Text(stringResource(R.string.manage_nudge_dismiss))
            }
            Button(
                onClick = onReview,
                contentPadding = PaddingValues(horizontal = spacing.md16),
                modifier = Modifier.heightIn(min = Dimensions.touchTargetMin).testTag("tt_manage_nudge_review"),
            ) {
                Text(pluralStringResource(R.plurals.manage_nudge_review, nudge.stalePackCount, nudge.stalePackCount))
            }
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
    nowMillis: Long,
    onStopDownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
    onDismissFailure: (String) -> Unit = {},
    selected: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D20d.RowGap),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = D20d.RowHeight)
                .padding(horizontal = D20d.RowPadH)
                .testTag("tt_manage_row"),
    ) {
        PackAvatar(id = row.id, downloaded = row.state == OfflineModelState.Downloaded)
        val scheme = MaterialTheme.colorScheme
        // Selected-row name = onPrimaryFixed (#041E49), the component-library colour for a
        // selected pack name (higher contrast on the primaryContainer fill than onPrimaryContainer).
        val nameColor = if (selected) scheme.onPrimaryFixed else scheme.onSurface
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.displayName,
                    fontSize = D20d.RowNameSize,
                    lineHeight = D20d.RowNameLine,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = nameColor,
                )
                if (row.inUse) {
                    InUseBadge(modifier = Modifier.padding(start = spacing.sm8))
                }
            }
            PackRowSupporting(row = row, nowMillis = nowMillis, selected = selected)
        }
        PackRowControl(
            row = row,
            onStopDownload = onStopDownload,
            onRetry = onRetry,
            onDismissFailure = onDismissFailure,
            onMoreOptions = onMoreOptions,
        )
    }
}

@Composable
private fun PackRowSupporting(
    row: PackRow,
    nowMillis: Long,
    selected: Boolean = false,
) {
    // #224: the ML Kit pivot (English) is included with every pack and cannot be
    // removed on its own (a measured no-op). Owner ruling (2026-08-05): keep the
    // row but say WHY it has no control, in place of a usage line. Guarded by id
    // ([isPivotLanguage], not a stored flag), so the story holds whatever state ML
    // Kit reports for the pivot and a row can never disagree with its own id (#325).
    // The frame's row subtitle is `outline` for a settled row, `error` for a failure,
    // and `onPrimaryContainer` on the selected (blue) row. Size 12/16.
    val subColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
    if (isPivotLanguage(row.id)) {
        Text(
            text = stringResource(R.string.offline_included),
            fontSize = D20d.RowSubSize,
            lineHeight = D20d.RowSubLine,
            color = subColor,
            modifier = Modifier.testTag("tt_manage_included"),
        )
        return
    }
    when (val state = row.state) {
        OfflineModelState.Downloading -> {
            Text(
                text = stringResource(R.string.text_lang_downloading),
                fontSize = D20d.RowSubSize,
                lineHeight = D20d.RowSubLine,
                color = subColor,
            )
        }

        is OfflineModelState.Failed -> {
            Text(
                text = stringResource(downloadFailureCopy(state.cause).rowLine),
                fontSize = D20d.RowSubSize,
                lineHeight = D20d.RowSubLine,
                color = if (selected) subColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("tt_manage_error_line"),
            )
        }

        else -> {
            // Downloaded / Deleting: "On device · used today" (or "· no recorded use yet").
            Text(
                text = stringResource(R.string.text_lang_on_device_size, packUsageText(row.usage, nowMillis)),
                fontSize = D20d.RowSubSize,
                lineHeight = D20d.RowSubLine,
                color = subColor,
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
    onDismissFailure: (String) -> Unit,
    onMoreOptions: (PackRow) -> Unit,
) {
    when (row.state) {
        OfflineModelState.Downloading -> {
            StopControl(row, onStopDownload)
        }

        is OfflineModelState.Failed -> {
            // 20b (#336): the failed row draws TWO actions, as the frame does — Dismiss
            // then Retry. Dismiss hides the failure for the session (`onDismissFailure`)
            // without retrying or deleting; a lower-emphasis TextButton, so Retry stays
            // the one filled call to action.
            //
            // #250: EVERY failed cause, out-of-space included, keeps its Retry pill.
            // Removing it left the STORAGE row a permanent dead-end — its line promises
            // an action with no control to tap, and freeing space elsewhere never
            // restored one (the transient state only moves on a `download()`). The
            // #234 concern is a Retry that SILENTLY does nothing, not the absence of
            // Retry; the ViewModel now makes it HONEST — a still-full retry surfaces
            // a snackbar (`reportOutcome`), a freed-disk retry actually downloads.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onDismissFailure(row.id) },
                    contentPadding = PaddingValues(horizontal = LocalSpacing.current.sm8),
                    modifier = Modifier.heightIn(min = Dimensions.touchTargetMin).testTag("tt_manage_dismiss"),
                ) {
                    Text(stringResource(R.string.manage_failed_dismiss))
                }
                Button(
                    onClick = { onRetry(row.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = LocalSpacing.current.md16),
                    modifier = Modifier.heightIn(min = Dimensions.touchTargetMin).testTag("tt_manage_retry"),
                ) {
                    Text(stringResource(R.string.lang_sheet_failed_retry))
                }
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
            // gets the overflow, which opens the 20c pack-actions sheet. Pivot-ness is
            // asked of the id ([isPivotLanguage]), never a stored flag (#325).
            if (!isPivotLanguage(row.id)) {
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
    // The frame's IN USE chip is GREEN, theme-invariant: bg tertiaryFixed (#C4EED0),
    // fg onTertiaryFixed (#072711), radius 5, 10sp, height 19, padding 0×7, tracking .4.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(D20d.ChipHeight)
                .clip(RoundedCornerShape(D20d.ChipRadius))
                .background(MaterialTheme.colorScheme.tertiaryFixed)
                .padding(horizontal = D20d.ChipPadH),
    ) {
        Text(
            text = stringResource(R.string.manage_in_use),
            fontSize = D20d.ChipText,
            fontWeight = FontWeight.Medium,
            letterSpacing = D20d.ChipTracking,
            color = MaterialTheme.colorScheme.onTertiaryFixed,
        )
    }
}

@Composable
private fun PackAvatar(
    id: String,
    downloaded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = D20d.AvatarSm,
    initialsSize: TextUnit = D20d.InitialsSm,
) {
    val scheme = MaterialTheme.colorScheme
    // Frame avatars: on-device blue (primaryContainer / onPrimaryContainer), else grey
    // (surfaceContainerHigh / onSurfaceVariant). Fully round (radius = size / 2).
    val background = if (downloaded) scheme.primaryContainer else scheme.surfaceContainerHigh
    val foreground = if (downloaded) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(TranzlateShapeFull).background(background),
    ) {
        Text(
            text = languageAvatarCode(id),
            fontSize = initialsSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = D20d.InitialsTracking,
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

/**
 * Relative last-used as the row reads it. [PackUsage.NoRecord] is the honest date-less
 * line (ruling ⑧); a [PackUsage.Used] derives its relative bucket from the stamp at
 * [nowMillis] ([PackUsage.Used.bucket]) — so a date-less pack can never print a month,
 * by construction (#325). `internal` so the 20e cleanup sheet reads a stale pack's age
 * with the SAME vocabulary this screen's rows do — never a calendar month (#130 PR-25).
 */
@Composable
internal fun packUsageText(
    usage: PackUsage,
    nowMillis: Long,
): String =
    when (usage) {
        PackUsage.NoRecord -> {
            stringResource(R.string.manage_used_never)
        }

        is PackUsage.Used -> {
            when (val bucket = usage.bucket(nowMillis)) {
                UsageBucket.Today -> {
                    stringResource(R.string.manage_used_today)
                }

                is UsageBucket.DaysAgo -> {
                    pluralStringResource(R.plurals.manage_used_days, bucket.days, bucket.days)
                }

                is UsageBucket.WeeksAgo -> {
                    pluralStringResource(R.plurals.manage_used_weeks, bucket.weeks, bucket.weeks)
                }

                is UsageBucket.MonthsAgo -> {
                    pluralStringResource(R.plurals.manage_used_months, bucket.months, bucket.months)
                }
            }
        }
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

/** A fixed instant for preview rows, so a relative date ("5 days ago") is stable across renders. */
private val previewNow = 200L * DAY_MILLIS

/** A preview stamp [days] before [previewNow] — bucketed against `previewNow` at render (#325). */
private fun usedDaysAgo(days: Long): PackUsage = PackUsage.Used(previewNow - days * DAY_MILLIS)

private fun row(
    id: String,
    name: String,
    state: OfflineModelState,
    usage: PackUsage = PackUsage.Used(previewNow),
    inUse: Boolean = false,
    hasOfflineVoice: Boolean = false,
) = PackRow(id, name, state, usage, inUse, hasOfflineVoice)

private val previewOnDevice =
    listOf(
        row("es", "Spanish", OfflineModelState.Downloaded, inUse = true),
        row("en", "English", OfflineModelState.Downloaded),
        row("af", "Afrikaans", OfflineModelState.Downloaded, usedDaysAgo(5)),
        row("de", "German", OfflineModelState.Downloaded, usedDaysAgo(120)),
        row("pl", "Polish", OfflineModelState.Downloaded, PackUsage.NoRecord),
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
            nowMillis = previewNow,
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
                            row("es", "Spanish", OfflineModelState.Downloaded, inUse = true),
                            row("en", "English", OfflineModelState.Downloaded),
                            row("af", "Afrikaans", OfflineModelState.Downloaded, usedDaysAgo(3)),
                        ),
                ),
            storage = previewSizedStorage,
            nudge = null,
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            nowMillis = previewNow,
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
            nowMillis = previewNow,
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
            nowMillis = previewNow,
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
            nowMillis = previewNow,
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
                PackRow(row("es", "Spanish", OfflineModelState.Downloaded, inUse = true), previewNow, {}, {}, {})
                PackRow(row("de", "German", OfflineModelState.Downloaded, usedDaysAgo(120)), previewNow, {}, {}, {})
                PackRow(row("pl", "Polish", OfflineModelState.Downloaded, PackUsage.NoRecord), previewNow, {}, {}, {})
                PackRow(row("en", "English", OfflineModelState.Downloaded), previewNow, {}, {}, {})
                PackRow(row("it", "Italian", OfflineModelState.Deleting), previewNow, {}, {}, {})
                PackRow(row("ar", "Arabic", OfflineModelState.Downloading), previewNow, {}, {}, {})
                PackRow(
                    row("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                    previewNow,
                    {},
                    {},
                    {},
                )
                PackRow(
                    row("ta", "Tamil", OfflineModelState.Failed(OfflineModelFailure.STORAGE)),
                    previewNow,
                    {},
                    {},
                    {},
                )
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
                NudgeCardView(HygieneNudge(stalePackCount = 1), {}, {})
                NudgeCardView(HygieneNudge(stalePackCount = 2), {}, {})
            }
        }
    }
}

// ── 20d list-detail previews (#130 PR-26) ─────────────────────────────────────
// A preview cannot resize the window it renders in, so — exactly as the picker's
// landscape previews do — these borrow the ruling's 1280×800 tablet geometry in a
// sized frame and call [ManagePacksTwoPane] directly, past the width gate the real
// screen reads from [rememberWindowInfo]. The emulator pass (ruling :144) is what
// proves the gate itself; these show the composition the owner reviews.

/** The ruling's 1280×800 tablet-landscape frame, the geometry the emulator pass captures. */
private val previewListDetailWidth = 1280.dp
private val previewListDetailHeight = 800.dp

/** The detail previews reuse [previewNow] as their clock, so a "3 days ago" line is stable across renders. */
private val previewDetailNow = previewNow

private val previewDetailOnDevice =
    listOf(
        row("es", "Spanish", OfflineModelState.Downloaded, usedDaysAgo(3), inUse = true),
        row("de", "German", OfflineModelState.Downloaded, usedDaysAgo(120)),
        row("af", "Afrikaans", OfflineModelState.Downloaded, usedDaysAgo(14)),
        row("en", "English", OfflineModelState.Downloaded),
    )

// Spanish (the default selection) was translated FROM three days ago and never
// INTO — so the detail shows one real date and one honest "no recorded use yet",
// the two cases the ruling's honesty rule (⑧) turns on, in one frame.
private val previewDetailSource = mapOf("es" to previewDetailNow - 3 * DAY_MILLIS)
private val previewDetailTarget = emptyMap<String, Long>()

@Composable
private fun ListDetailPreviewFrame(content: @Composable () -> Unit) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.size(width = previewListDetailWidth, height = previewListDetailHeight)) {
                content()
            }
        }
    }
}

/** 20d with a pack selected: the list on the left, Spanish's per-role usage and the storage bar on the right. */
@PreviewLightDark
@Composable
private fun ManagePacksListDetailPreview() {
    ListDetailPreviewFrame {
        ManagePacksTwoPane(
            sections = previewSections(onDevice = previewDetailOnDevice),
            storage = previewSizedStorage,
            nudge = null,
            suggestions = emptyList(),
            capable = 59,
            total = 194,
            usageAsSource = previewDetailSource,
            usageAsTarget = previewDetailTarget,
            nowMillis = previewDetailNow,
            selectedId = null,
            onSelectPack = {},
            onSavedCount = { 4 },
            onGet = {},
            onStopDownload = {},
            onRemove = {},
            onReviewPacks = {},
            onBrowseAll = {},
        )
    }
}

/** 20d with no packs: the empty list on the left, the no-selection placeholder on the right (no dead end). */
@PreviewLightDark
@Composable
private fun ManagePacksListDetailEmptyPreview() {
    ListDetailPreviewFrame {
        ManagePacksTwoPane(
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
            usageAsSource = emptyMap(),
            usageAsTarget = emptyMap(),
            nowMillis = previewDetailNow,
            selectedId = null,
            onSelectPack = {},
            onSavedCount = { 0 },
            onGet = {},
            onStopDownload = {},
            onRemove = {},
            onReviewPacks = {},
            onBrowseAll = {},
        )
    }
}

/**
 * The FULL detail (rule 7): an on-device, in-use, voiced pack with saved phrases —
 * identity + status, both capability cards supported, the saved line, per-role usage,
 * and the Remove block — beside the no-selection placeholder.
 */
@PreviewLightDark
@Composable
private fun ManagePacksDetailPanePreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row {
                ManagePacksDetailPane(
                    pack =
                        row(
                            "es",
                            "Spanish",
                            OfflineModelState.Downloaded,
                            usedDaysAgo(3),
                            inUse = true,
                            hasOfflineVoice = true,
                        ),
                    roleUsage = PackRoleUsage(asSource = usedDaysAgo(3), asTarget = PackUsage.NoRecord),
                    savedCount = 4,
                    nowMillis = previewDetailNow,
                    onRemove = {},
                    modifier = Modifier.width(420.dp),
                )
                VerticalDivider()
                ManagePacksDetailPane(
                    pack = null,
                    roleUsage = null,
                    savedCount = 0,
                    nowMillis = previewDetailNow,
                    onRemove = {},
                    modifier = Modifier.width(420.dp).height(320.dp),
                )
            }
        }
    }
}

/**
 * More detail states (rule 7): a downloaded pack with NO on-device voice and no saved
 * phrases (voice card muted, Remove present), and a FAILED pack (a failure subtitle,
 * both cards muted, no Remove).
 */
@PreviewLightDark
@Composable
private fun ManagePacksDetailPaneMutedStatesPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row {
                ManagePacksDetailPane(
                    pack = row("de", "German", OfflineModelState.Downloaded, usedDaysAgo(14)),
                    roleUsage = PackRoleUsage(asSource = PackUsage.NoRecord, asTarget = usedDaysAgo(14)),
                    savedCount = 0,
                    nowMillis = previewDetailNow,
                    onRemove = {},
                    modifier = Modifier.width(420.dp),
                )
                VerticalDivider()
                ManagePacksDetailPane(
                    pack = row("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                    roleUsage = PackRoleUsage(asSource = PackUsage.NoRecord, asTarget = PackUsage.NoRecord),
                    savedCount = 0,
                    nowMillis = previewDetailNow,
                    onRemove = {},
                    modifier = Modifier.width(420.dp),
                )
            }
        }
    }
}

// ── 20d dense list-item previews (rule 7: every custom item, one per meaningful state) ─

/**
 * The 20d left-column item vocabulary in one 392dp frame (rule 7): the sized storage card, the
 * green nudge, a plain and a counted section header, the downloading card, and the failed row.
 */
@PreviewLightDark
@Composable
private fun TwoPaneListItemsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.width(ManagePacksListWidth)) {
                TwoPaneStorageCard(previewSizedStorage)
                TwoPaneNudge(
                    HygieneNudge(stalePackCount = 2),
                    onReview = {},
                    modifier = Modifier.padding(top = D20d.NudgeTop),
                )
                TwoPaneSectionHeader(text = "On this device", count = "5 of 59 packs")
                TwoPaneDownloadingCard(
                    row = row("ar", "Arabic", OfflineModelState.Downloading, PackUsage.NoRecord),
                    onStopDownload = {},
                )
                TwoPaneSectionHeader(text = "Did not download")
                TwoPaneFailedRow(
                    row = row("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK), PackUsage.NoRecord),
                    selected = false,
                    onSelectPack = {},
                )
            }
        }
    }
}

/**
 * The dense on-device row in its meaningful states (rule 7): selected (blue stadium), in-use
 * (IN USE chip), stale (grey avatar), and plain.
 */
@PreviewLightDark
@Composable
private fun TwoPaneSettledRowPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.width(ManagePacksListWidth)) {
                TwoPaneSettledRow(
                    row = row("af", "Afrikaans", OfflineModelState.Downloaded, usedDaysAgo(5)),
                    selected = true,
                    nowMillis = previewNow,
                    onSelectPack = {},
                )
                TwoPaneSettledRow(
                    row = row("es", "Spanish", OfflineModelState.Downloaded, PackUsage.Used(previewNow), inUse = true),
                    selected = false,
                    nowMillis = previewNow,
                    onSelectPack = {},
                )
                TwoPaneSettledRow(
                    row = row("de", "German", OfflineModelState.Downloaded, usedDaysAgo(120)),
                    selected = false,
                    nowMillis = previewNow,
                    onSelectPack = {},
                )
                TwoPaneSettledRow(
                    row = row("en", "English", OfflineModelState.Downloaded),
                    selected = false,
                    nowMillis = previewNow,
                    onSelectPack = {},
                )
            }
        }
    }
}
