package com.codeboxlk.tranzlate.feature.language

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.DETECT_LANGUAGE_ID
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import com.codeboxlk.tranzlate.core.ui.languageLabel
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo
import com.codeboxlk.tranzlate.core.ui.searchNormalize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CONTENT_TYPE_ROW = "lang_row"
private const val CONTENT_TYPE_HEADER = "lang_header"

/** "no finger on the rail" — an index no slot can equal. */
private const val NO_SLOT = -1

/** Which rail letter the finger is over: pure arithmetic, so it is trivially checkable. */
private fun railSlot(
    y: Float,
    railHeight: Int,
    letterCount: Int,
): Int =
    if (railHeight <= 0 || letterCount <= 0) {
        0
    } else {
        (y / railHeight * letterCount).toInt().coerceIn(0, letterCount - 1)
    }

/**
 * Full-screen language picker — the production redesign of issue #117
 * (Claude Design "Language Picker 15a", 412×892, light + dark).
 *
 * DI shell over [LanguagePickerContent]. ONE state holder since the #130
 * rev.3 decouple (#123.2): [LanguagePickerViewModel] owns the selection
 * read/write through `TranslatePrefsRepository` — the same DataStore keys the
 * composer's chips read, so no `TextViewModel` handle is borrowed to keep the
 * two screens coherent — plus everything the picker needs to present that
 * choice honestly: catalog, live offline-model state, the per-role last-used
 * stamp and the row-level download controls.
 *
 * **Nothing in this file is `rememberSaveable`, and that is deliberate**
 * (#130 PR-13). The search text and the list position live in the ViewModel's
 * `SavedStateHandle`; the reason is written out in
 * [LanguagePickerViewModel.query]. In one line: `rememberSaveable` is addressed
 * through whichever `SaveableStateHolder` is drawing this screen, and the same
 * picker is about to be drawn from three different ones.
 *
 * The file does not IMPORT either saveable helper either — not even in a preview
 * — because that import list is what `PickerHostAgnosticTest` reads, and the
 * import is the one spelling an alias cannot disguise (#192 co-verify).
 */
@Composable
fun LanguagePickerScreen(
    target: LanguageRole,
    onDone: () -> Unit,
    onManagePacks: () -> Unit,
    modifier: Modifier = Modifier,
    host: PickerHost = PickerHost.NAV_ENTRY,
    viewModel: LanguagePickerViewModel = hiltViewModel(),
) {
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val packFailure by viewModel.packFailure.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsent.collectAsStateWithLifecycle()
    val alwaysAsk by viewModel.alwaysAsk.collectAsStateWithLifecycle()
    val selectedId by viewModel.selection(target).collectAsStateWithLifecycle()
    val recents by viewModel.recents(target).collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    LanguagePickerContent(
        target = target,
        languages = languages,
        selectedId = selectedId,
        recents = recents,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onSelect = { id ->
            viewModel.select(id, target)
            onDone()
        },
        onBack = onDone,
        modifier = modifier,
        host = host,
        offlineStates = offlineStates,
        onDownload = viewModel::download,
        onStop = viewModel::stopAndRemove,
        pendingConsent = pendingConsent,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = viewModel::onAlwaysAskChange,
        onDownloadAnyway = viewModel::downloadAnyway,
        onDismissConsent = viewModel::dismissConsent,
        // Read here, not captured in the ViewModel: a rotation rebuilds this
        // composition and keeps the ViewModel, so the seed has to be the position
        // as of the last scroll.
        listPosition = viewModel.listPosition(),
        onListPositionChange = viewModel::onListPositionChange,
        library = library,
        packFailure = packFailure,
        onDismissFailure = viewModel::dismissPackFailure,
        onManagePacks = onManagePacks,
    )
}

/**
 * Stateless picker layout: back + title · permanent search field · "Detect
 * language" (source side only) · the offline-voice legend and speaker marks
 * (target side only, 16a) · Recent · All languages + on-device counter, with an
 * A–Z rail down the right edge.
 *
 * ONE screen serves both sides rather than two. 15a and 16a share their chrome,
 * their search, their six row states, their counter and their rail; the spec
 * calls the differences "three deliberate differences", which is a description
 * of one screen with a parameter, not of a second screen. A parallel
 * `TargetPickerScreen` would also put the language surface in a second home,
 * which the rev.3 ruling rejected P1's module stance for (§7.1) and CLAUDE.md
 * states as "every big job has ONE home". Every difference is therefore a
 * branch on [target], and each of those branches is decided in [pickerListPlan]
 * or [showsVoiceMark] so it can be unit-tested without a Compose test rule.
 *
 * The row pill deliberately sits 8dp from the screen edge rather than on the
 * 16dp screen-margin line (#88/#92): the margin rule governs CONTENT, and the
 * design keeps content on 20dp+ while letting the selectable container bleed
 * outward — which is how M3 draws list containers. The margin rule still
 * governs the app bar and the search field.
 *
 * **Landscape (17a) is an ARRANGEMENT of this same screen, not a second one.**
 * Each landscape frame in the export carries one title — "Translate from" OR
 * "Translate to" — so 17a is one picker in one role, split into a shortcut pane
 * and a catalog pane; it is emphatically not a source picker beside a target
 * picker. Composing this screen twice would therefore draw a screen the design
 * does not have, and would hand two panes one ViewModel's single search query
 * and single scroll position. So the branch lives here, guarded by
 * [pickerArrangement], and the catalog, the rows, the search and the state stay
 * in one place.
 *
 * **The tablet dialog (17c/17d) is an arrangement of this same screen too**, and
 * it is the third host rather than a third screen for the same reason. What the
 * card changes is decided by [pickerArrangement] from the CARD's constraints —
 * one column or two, and no A–Z rail — plus the one thing a measurement cannot
 * tell it, which is that its way out is a Close button rather than a back arrow.
 * That is what [host] carries, and it is the only branch it opens here.
 *
 * @param sizes measured on-disk bytes per tag; empty in production today, see
 *   [LanguageRowState.Downloaded].
 * @param host which host is drawing this picker ([PickerHost]) — production
 *   passes the answer [pickerHost] gave the shell.
 * @param arrangementOverride PREVIEWS ONLY. Production leaves it null so the
 *   window answers; a preview cannot resize its host, so it says which
 *   arrangement it is showing.
 */
@Composable
fun LanguagePickerContent(
    target: LanguageRole,
    languages: List<Language>,
    selectedId: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    host: PickerHost = PickerHost.NAV_ENTRY,
    recents: Map<String, Long> = emptyMap(),
    offlineStates: Map<String, OfflineModelState> = emptyMap(),
    sizes: Map<String, Long> = emptyMap(),
    onDownload: (String) -> Unit = {},
    onStop: (String) -> Unit = {},
    pendingConsent: String? = null,
    alwaysAsk: Boolean = true,
    onAlwaysAskChange: (Boolean) -> Unit = {},
    onDownloadAnyway: () -> Unit = {},
    onDismissConsent: () -> Unit = {},
    listPosition: PickerListPosition = PickerListPosition.Top,
    onListPositionChange: (PickerListPosition) -> Unit = {},
    library: OfflineLibraryMeter? = null,
    packFailure: PackFailureRequest? = null,
    onDismissFailure: () -> Unit = {},
    onManagePacks: () -> Unit = {},
    arrangementOverride: PickerArrangement? = null,
) {
    val title =
        when (target) {
            LanguageRole.SOURCE -> stringResource(R.string.text_lang_sheet_source_title)
            LanguageRole.TARGET -> stringResource(R.string.text_lang_sheet_target_title)
        }
    val sections =
        rememberPickerSections(
            target = target,
            languages = languages,
            offlineStates = offlineStates,
            selectedId = selectedId,
            query = query,
            sizes = sizes,
            recents = recents,
            detectLabel = languageLabel(DETECT_LANGUAGE_ID),
        )
    // Sheet 19a. It is handed WHETHER a question is open, never which language
    // it is about: the sheet's own copy names none, because unticking its
    // checkbox answers for every language at once (see `MobileDataSheet`).
    MobileDataSheet(
        visible = pendingConsent != null,
        alwaysAsk = alwaysAsk,
        onAlwaysAskChange = onAlwaysAskChange,
        onDownloadNow = onDownloadAnyway,
        onDismiss = onDismissConsent,
    )
    // Sheets 19d / 19b (PR-18). The NAME comes from `sections.all`, which is the
    // unfiltered row list, so the sheet says exactly what the row says and a
    // search running when the download failed cannot empty the title.
    PackFailureSheetHost(
        request = packFailure,
        nameOf = { id -> sections.all.firstOrNull { it.id == id }?.displayName },
        onRetry = onDownload,
        onManagePacks = onManagePacks,
        onDismiss = onDismissFailure,
    )
    // BOTH sizes are read from the CONSTRAINTS this screen was handed, never from
    // the window: a nav rail, or a host that draws the picker beside something
    // else, has already been subtracted here and has not been there — and, the
    // reason the height moved here too, a window snapshot and a layout pass
    // disagree for a few frames after a rotation. `pickerArrangement` carries the
    // measurement. Posture has no second source and stays where it is; it is a
    // question about the hinge, not about size, so it cannot go half a rotation
    // out of step with the width.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val window = rememberWindowInfo()
        val arrangement =
            arrangementOverride
                ?: pickerArrangement(maxWidth, maxHeight, window.posture, window.hinged, host)
        // TWO questions that used to be one, and the device run is what separated
        // them (#130 PR-16). "The catalog is whole" decides the "All languages"
        // header, the on-device counter under it and therefore `catalogOffset`;
        // "the rail is up" decides the letters and the inset they need. They were
        // the same boolean until the dialog became the first host that wants the
        // header WITHOUT the rail — and tying them together silently dropped
        // "5 of 59 packs on device" from all four tablet frames, which the export
        // draws in every one of them.
        val wholeCatalog = !sections.searching && !sections.catalogEmpty
        val railed = arrangement.rail && wholeCatalog
        val plan =
            remember(
                target,
                sections.detect,
                sections.recent,
                sections.anyVoiceMark,
                wholeCatalog,
                arrangement,
                library,
            ) {
                pickerListPlan(
                    role = target,
                    detectRowPresent = sections.detect != null,
                    recentCount = sections.recent.size,
                    anyVoiceMark = sections.anyVoiceMark,
                    wholeCatalog = wholeCatalog,
                    arrangement = arrangement,
                    libraryReady = library != null,
                )
            }
        PickerScaffold(
            title = title,
            target = target,
            sections = sections,
            plan = plan,
            arrangement = arrangement,
            host = host,
            railed = railed,
            query = query,
            onQueryChange = onQueryChange,
            catalogSize = languages.size,
            onSelect = onSelect,
            onBack = onBack,
            onDownload = onDownload,
            onStop = onStop,
            listPosition = listPosition,
            onListPositionChange = onListPositionChange,
            library = library,
        )
    }
}

/**
 * Which failure sheet is on screen, if any — the one place the request type
 * becomes a drawing.
 *
 * It is a `when` over [PackFailureRequest] rather than two `if`s at the call
 * site so that the two sheets cannot both be up: they answer the same question
 * ("your download did not happen") and a user looking at two of them is looking
 * at a bug.
 *
 * This KDoc used to justify that with *"the ViewModel already guarantees one
 * request at a time"*, which was true and was **not the property that matters**
 * (issue #239): one at a time did not mean the one on screen stayed. The
 * guarantee it now leans on is the stronger one — a raised request holds its slot
 * until the user answers it, so a second language failing cannot swap the sheet
 * under a thumb already moving. See `LanguagePickerViewModel.raise`.
 *
 * **Every action here dismisses before it does anything else.** Both branches
 * lead somewhere the sheet must not be waiting when the user arrives or returns
 * — Retry re-runs the download the sheet is about, and Manage packs leaves the
 * screen entirely. Manage packs did not (issue #235), and only the tablet
 * dialog host hid it: that host's shell dismisses the card first, which clears
 * the picker's ViewModel and the request with it, while the nav host PUSHES and
 * clears nothing. A user who freed 130 MB came back to a sheet still reporting
 * the 12 MB they had before. `PackFailureSheetsTest` pins the order in both
 * branches, because the order is the fix — calling both the other way round
 * leaves a sheet floating over the destination.
 *
 * @param nameOf the language's display name as the ROW spells it. A `null`
 *   answer draws nothing: the catalogue has not arrived yet (or, impossibly, the
 *   id is not in it), and a sheet titled *" did not download"* would be worse
 *   than a sheet that waits a frame — the row underneath still reports the
 *   failure and still offers Retry, so nothing is lost while it does.
 */
@Composable
private fun PackFailureSheetHost(
    request: PackFailureRequest?,
    nameOf: (String) -> String?,
    onRetry: (String) -> Unit,
    onManagePacks: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return
    when (request) {
        is PackFailureRequest.NoSpace -> {
            NoSpaceSheet(
                freeBytes = request.freeBytes,
                volumeBytes = request.volumeBytes,
                onManagePacks = {
                    onDismiss()
                    onManagePacks()
                },
                onDismiss = onDismiss,
            )
        }

        is PackFailureRequest.Interrupted -> {
            val name = nameOf(request.id)
            val sheet = downloadFailureCopy(request.cause).sheet
            if (name != null && sheet is DownloadFailureSheet.Interrupted) {
                InterruptedSheet(
                    languageName = name,
                    sheet = sheet,
                    onRetry = {
                        onDismiss()
                        onRetry(request.id)
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * The chrome, and which of the two bodies goes under it.
 *
 * Split out of [LanguagePickerContent] only so the arrangement can be decided
 * above it — everything here is the same screen either way.
 */
@Composable
private fun PickerScaffold(
    title: String,
    target: LanguageRole,
    sections: PickerSections,
    plan: PickerListPlan,
    arrangement: PickerArrangement,
    host: PickerHost,
    railed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    catalogSize: Int,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    listPosition: PickerListPosition,
    onListPositionChange: (PickerListPosition) -> Unit,
    library: OfflineLibraryMeter?,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        // The card paints its own floating surface and this Scaffold is INSIDE
        // it, so a second opaque fill here would cover it — the picker would draw
        // the page colour over a white card and the two would look like one flat
        // sheet. On a screen there is nothing underneath, so the page colour is
        // exactly right.
        containerColor =
            if (host == PickerHost.DIALOG) Color.Transparent else MaterialTheme.colorScheme.surface,
        topBar = {
            if (arrangement.twoPane) {
                PickerCompactBar(
                    title = title,
                    onBack = onBack,
                    query = query,
                    onQueryChange = onQueryChange,
                    catalogSize = catalogSize,
                    counts = if (plan.counterInTopBar) sections.counts else null,
                )
            } else {
                PickerTopBar(title = title, onBack = onBack, host = host)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = adaptiveMarginShim()),
        ) {
            // Portrait keeps the search field in the body; 17a has already put it
            // in the bar, where the row it saves is a row of languages.
            if (!arrangement.twoPane) {
                PickerSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    catalogSize = catalogSize,
                    modifier =
                        Modifier.padding(
                            start = spacing.md16,
                            end = spacing.md16,
                            bottom = spacing.sm8,
                        ),
                )
            }
            PickerBody(
                target = target,
                sections = sections,
                plan = plan,
                arrangement = arrangement,
                railed = railed,
                query = query,
                onSelect = onSelect,
                onDownload = onDownload,
                onStop = onStop,
                onClearQuery = { onQueryChange("") },
                listPosition = listPosition,
                onListPositionChange = onListPositionChange,
                library = library,
            )
        }
    }
}

/** Everything the list needs, derived once per data/query change. */
private class PickerSections(
    val all: List<LanguagePickerRow>,
    val results: List<LanguagePickerRow>,
    val recent: List<LanguagePickerRow>,
    val detect: LanguagePickerRow?,
    val counts: OnDeviceCount,
    val searching: Boolean,
    val catalogEmpty: Boolean,
    /** At least one row in the CATALOG would draw the speaker (never just the filtered view). */
    val anyVoiceMark: Boolean,
) {
    /** A fruitless SEARCH — distinct from a catalog that has not arrived yet. */
    val nothingFound: Boolean get() = searching && !catalogEmpty && results.isEmpty() && detect == null

    /**
     * The catalog rows the grid is emitting right now. ONE definition, because
     * two readers have to agree on it exactly: the A–Z rail's letter index and
     * the index a saved [PickerListPosition] restores to.
     */
    val visible: List<LanguagePickerRow> get() = if (searching) results else all
}

/**
 * The locale-collated sort and the per-row normalization run once per DATA
 * change — never per frame, and never per keystroke (only the cheap `contains`
 * scan re-runs while typing). That is what keeps 194 rows plus a live state
 * overlay off the critical path.
 *
 * The localized name is derived HERE and not in the ViewModel on purpose: a
 * ViewModel survives configuration changes, so a locale switch would leave
 * VM-computed names stale in the user's previous language.
 */
@Composable
private fun rememberPickerSections(
    target: LanguageRole,
    languages: List<Language>,
    offlineStates: Map<String, OfflineModelState>,
    selectedId: String,
    query: String,
    sizes: Map<String, Long>,
    recents: Map<String, Long>,
    detectLabel: String,
): PickerSections {
    val locale = LocalLocale.current.platformLocale
    val rows =
        remember(languages, offlineStates, selectedId, sizes, recents, locale) {
            buildPickerRows(languages, offlineStates, selectedId, locale, sizes, recents)
        }
    val normalizedQuery = remember(query) { searchNormalize(query) }
    val results = remember(rows, normalizedQuery) { rows.matching(normalizedQuery) }
    val recent =
        remember(rows, normalizedQuery) {
            if (normalizedQuery.isEmpty()) rows.recentRows() else emptyList()
        }
    // "Detect language" is a source-only pseudo-entry (spec 02 §4.5) and it is
    // searchable like any other row.
    val detect =
        remember(target, detectLabel, selectedId, normalizedQuery) {
            detectRow(detectLabel, selected = selectedId == DETECT_LANGUAGE_ID)
                .takeIf { target == LanguageRole.SOURCE }
                ?.takeIf { normalizedQuery.isEmpty() || it.searchKey.contains(normalizedQuery) }
        }
    val counts = remember(languages) { onDeviceCount(languages) }
    // Over the whole catalog, not the filtered results: the legend explains a
    // mark the user meets while scrolling, and it must not blink out because a
    // three-letter query happens to match only unvoiced languages.
    val anyVoiceMark = remember(rows, target) { rows.any { it.showsVoiceMark(target) } }
    return PickerSections(
        all = rows,
        results = results,
        recent = recent,
        detect = detect,
        counts = counts,
        searching = normalizedQuery.isNotEmpty(),
        catalogEmpty = languages.isEmpty(),
        anyVoiceMark = anyVoiceMark,
    )
}

/**
 * Which of the two bodies is drawn — everything below this point is shared.
 *
 * Portrait stacks the shortcuts on top of the catalog in one scroller. 17a puts
 * the shortcuts in a side pane and gives the catalog the rest, in one or two
 * columns depending on what fits ([PickerArrangement.columns]).
 */
@Composable
private fun PickerBody(
    target: LanguageRole,
    sections: PickerSections,
    plan: PickerListPlan,
    arrangement: PickerArrangement,
    railed: Boolean,
    query: String,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    onClearQuery: () -> Unit,
    listPosition: PickerListPosition,
    onListPositionChange: (PickerListPosition) -> Unit,
    library: OfflineLibraryMeter?,
) {
    val spacing = LocalSpacing.current
    // The saved language, turned into an index against THIS arrangement's grid —
    // which is what makes a position captured in the other one still mean the
    // same language (see `PickerListPosition`).
    val seedIndex = pickerAnchorIndex(listPosition.anchorId, sections.visible, plan.catalogOffset)
    // An anchor cannot be resolved against a catalog that has not arrived, and
    // after process death it has not: the ViewModel comes back with the language,
    // the repository has not answered yet. So the seed is keyed on the catalog's
    // arrival — false → true, exactly once — and the grid is rebuilt at the right
    // place the moment there is a place to be. A rotation never takes that branch:
    // the catalog is already in the ViewModel's StateFlow, so the seed is right on
    // the first composition and nothing moves.
    val catalogArrived = sections.all.isNotEmpty()
    // ONE grid state for both arrangements, and one home for the position that
    // seeds it. `remember`, NOT `rememberLazyGridState()`: the saveable version
    // keeps a SECOND copy inside the host's SaveableStateHolder, and on a host
    // change it is the copy that comes back empty — while quietly winning over
    // the seed passed in here, because a restored saveable ignores its initial
    // arguments. One home for the position, and it is the caller's.
    val gridState =
        remember(catalogArrived) { LazyGridState(seedIndex, listPosition.offset) }
    // The callback is read through `rememberUpdatedState` and is NOT an effect
    // key. A bound method reference is a fresh object on a ViewModel the compiler
    // cannot prove stable, so keying on it would tear down and restart the
    // collection below on every keystroke in the search field.
    val reportPosition by rememberUpdatedState(onListPositionChange)
    LaunchedEffect(gridState, catalogArrived) {
        // A grid with no languages in it has no position worth reporting, and
        // reporting one would be worse than useless: the loading placeholder is a
        // real item with a real key, so the collection below would answer "no
        // anchor" and overwrite the very language it is about to restore. The old
        // index-based report leaned on `LazyGridScrollPosition`'s own
        // `hadFirstNotEmptyLayout` guard for this; a key-based one has to say it.
        if (!catalogArrived) return@LaunchedEffect
        // Read in a snapshot observer rather than in composition: a fling changes
        // these every frame, and reading them up here would recompose the whole
        // list for each one.
        //
        // The KEY of the first visible item, not its index: the grid already
        // anchors its own scroll position to that key, so reading it is reading
        // the grid's own answer rather than re-deriving one.
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.let { first ->
                PickerListPosition(pickerAnchorOf(first.key), gridState.firstVisibleItemScrollOffset)
            }
        }.collect { position -> position?.let(reportPosition) }
    }
    if (arrangement.twoPane) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (plan.sidePane) {
                PickerSidePane(
                    target = target,
                    sections = sections,
                    plan = plan,
                    width = arrangement.sidePaneWidth,
                    library = library,
                    onSelect = onSelect,
                    onDownload = onDownload,
                    onStop = onStop,
                )
                // 17a: 8dp, a gap. 17b: 24dp, the strip of window the crease
                // runs down — content on either side of it, nothing in it. The
                // gate that decided the arrangement is what carries the number,
                // so there is no second place that has to know which layout this
                // is (`PickerArrangement.gutter`).
                Spacer(Modifier.width(arrangement.gutter).testTag("tt_lang_pane_gutter"))
            }
            PickerCatalog(
                target = target,
                sections = sections,
                plan = plan,
                columns = arrangement.columns,
                shortcutsInGrid = false,
                railed = railed,
                query = query,
                gridState = gridState,
                onSelect = onSelect,
                onDownload = onDownload,
                onStop = onStop,
                onClearQuery = onClearQuery,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // ABOVE the grid, not inside it. The device's voice answer lands
            // after the list has been laid out, and an item that appears at index
            // 0 of an already-anchored lazy list is placed above the viewport and
            // never seen — see `pickerListPlan` for the measurement. Here it
            // simply appears, in the same place the 16a frame draws it, and the
            // list below keeps its own scroll position.
            if (plan.showVoiceLegend) {
                VoiceLegend(
                    modifier =
                        Modifier.padding(
                            start = spacing.sm8,
                            // Matches the list's own end inset so the legend pill
                            // and the row pills line up while the rail is up.
                            end = if (railed) Dimensions.touchTargetMin else spacing.sm8,
                        ),
                )
            }
            PickerCatalog(
                target = target,
                sections = sections,
                plan = plan,
                columns = arrangement.columns,
                shortcutsInGrid = true,
                railed = railed,
                query = query,
                gridState = gridState,
                onSelect = onSelect,
                onDownload = onDownload,
                onStop = onStop,
                onClearQuery = onClearQuery,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * The shortcut pane, in both two-pane arrangements: the recents section, then
 * the role's own extra — the "Detect language" row on the source side, the
 * offline-voice legend on the target side — and, on a foldable leaf, the
 * offline-library meter at the very bottom. Both landscape frames put the role's
 * extra at the FOOT of the pane, which is where the export draws it; portrait
 * keeps it at the top, where the export draws it there.
 *
 * **One pane, two widths, and the order is the same in both.** The foldable
 * frames draw the same sequence as the landscape ones with the meter card added
 * under it, so this is 17a's pane with one more tenant rather than a second pane
 * — which is the "every big job has ONE home" rule applied at the level below a
 * screen.
 *
 * It scrolls with a plain [ScrollState] rather than `rememberScrollState()` for
 * the same reason the grid does not use `rememberLazyGridState()`: that helper
 * is `rememberSaveable` underneath, and every saveable in this file would be
 * addressed through whichever host is drawing the picker.
 *
 * @param width [PickerArrangement.sidePaneWidth] — 272dp in landscape, 296dp on
 *   a leaf, both measured off the export.
 * @param library the meter's data, or null while the storage snapshot is still
 *   being taken. Null draws NO card rather than an empty one: the walk runs on
 *   IO and a card that appeared saying "0 packs" and then corrected itself would
 *   have told the user something false in between.
 */
@Composable
private fun PickerSidePane(
    target: LanguageRole,
    sections: PickerSections,
    plan: PickerListPlan,
    width: Dp,
    library: OfflineLibraryMeter?,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier =
            Modifier
                .width(width)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(remember { ScrollState(0) })
                .padding(horizontal = spacing.xs4, vertical = spacing.sm8)
                .testTag("tt_lang_side_pane"),
    ) {
        plan.recentHeader?.let { header ->
            SectionHeader(recentHeaderRes(header))
            sections.recent.forEach { row ->
                LanguageRow(row, target, onSelect, onDownload, onStop)
            }
        }
        sections.detect?.let { detect ->
            LanguageRow(detect, target, onSelect, onDownload, onStop)
        }
        if (plan.showVoiceLegend) {
            VoiceLegend(modifier = Modifier.padding(top = spacing.sm8))
        }
        // `plan.showMeter` already knows the snapshot arrived — it is the same
        // fact `plan.sidePane` had to weigh, so it is answered once, there.
        if (plan.showMeter && library != null) {
            OfflineLibraryMeterCard(
                meter = library,
                modifier = Modifier.padding(top = spacing.md16, start = spacing.xs4, end = spacing.xs4),
            )
        }
    }
}

/**
 * The "Offline library" card (U-5): how many packs are on this device, out of
 * how many could be, and — when the disk can be read — how much room they take.
 *
 * **Every sentence it can print is a fact it can support.** Which one it prints
 * is decided in [offlineLibraryMeter], away from Compose so a plain unit test
 * can drive all three; this composable only spells the chosen one. The
 * degrade case is the reason for the split: `packsBytes` returns null when ML
 * Kit's model store is not where issue #90's research measured it, and the
 * tempting thing to draw there is `0 MB`, which is a claim about the disk that
 * nothing has checked. Experiment E-S1 is what tells us which case a real device
 * is in — see `docs/research/issue-130-e-s1-storage-walk.md`.
 *
 * The bar is `LinearProgressIndicator` rather than a hand-drawn box: it is a
 * determinate progress bar, it is what M3 draws for one, and it carries the
 * platform's own reduced-motion and theming behaviour for free. Its semantics
 * are cleared because the three lines above it already say the whole thing in
 * words — a screen reader announcing "4 percent" after "5 of 59 packs, 110 MB
 * used" is repeating the least useful version of the fact.
 */
@Composable
private fun OfflineLibraryMeterCard(
    meter: OfflineLibraryMeter,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val detail =
        when (meter) {
            is OfflineLibraryMeter.Empty -> {
                pluralStringResource(R.plurals.text_lang_library_none, meter.capable, meter.capable)
            }

            is OfflineLibraryMeter.Sized -> {
                pluralStringResource(
                    R.plurals.text_lang_library_used,
                    meter.capable,
                    meter.capable,
                    Formatter.formatShortFileSize(context, meter.usedBytes),
                )
            }

            is OfflineLibraryMeter.Unsized -> {
                pluralStringResource(
                    R.plurals.text_lang_library_free,
                    meter.capable,
                    meter.capable,
                    Formatter.formatShortFileSize(context, meter.freeBytes),
                )
            }
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = spacing.md16, vertical = spacing.md16 - spacing.xs4)
                // One announcement in reading order — "Offline library, 5, of 59
                // packs · 110 MB used" — instead of three separate stops on a
                // card that is one statement.
                .semantics(mergeDescendants = true) {}
                .testTag("tt_lang_library_meter"),
    ) {
        Text(
            text = stringResource(R.string.text_lang_library_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(top = spacing.sm8),
        ) {
            Text(
                text = stringResource(R.string.text_lang_library_downloaded, meter.downloaded),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.xs4 / 2),
            )
        }
        LinearProgressIndicator(
            progress = { meter.fraction },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            // No gap and no stop indicator: at 4dp the M3 default stop dot is
            // wider than the fill it sits beside on a nearly-empty library, and
            // it would read as a second, contradictory mark.
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm8 + spacing.xs4 / 2)
                    .height(Dimensions.pickerMeterBarHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clearAndSetSemantics {},
        )
    }
}

/**
 * The catalog grid plus its A–Z rail. The rail overlays the grid rather than
 * taking a column of its own, exactly as the design draws it; it is hidden while
 * a search is running, because an index over five results indexes nothing.
 *
 * A grid at one column, rather than a `LazyColumn`, in BOTH arrangements — and
 * that is the point rather than a convenience. A grid indexes ITEMS, so item 40
 * is the 41st language whether it is drawn in one column or two, and the single
 * [PickerListPosition] the ViewModel holds therefore means the same place on
 * both sides of a rotation. A two-column list of paired rows would have indexed
 * PAIRS, and every restored position would have landed at twice its language.
 *
 * @param shortcutsInGrid the detect row and the recents section are emitted here
 *   (portrait). In 17a they live in [PickerSidePane] instead, and
 *   [PickerListPlan.catalogOffset] stops counting them.
 */
@Composable
private fun PickerCatalog(
    target: LanguageRole,
    sections: PickerSections,
    plan: PickerListPlan,
    columns: Int,
    shortcutsInGrid: Boolean,
    railed: Boolean,
    query: String,
    gridState: LazyGridState,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val visibleRows = sections.visible
    val letters =
        remember(visibleRows, plan.catalogOffset, railed) {
            if (railed) {
                visibleRows.letterIndex(plan.catalogOffset).toList().sortedBy { it.second }
            } else {
                emptyList()
            }
        }
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            contentPadding =
                PaddingValues(
                    start = spacing.sm8,
                    // The rail is an OVERLAY with a 48dp touch strip on this
                    // edge, and it wins the hit test. At an 8dp inset the rows
                    // ran under it and it swallowed the right half of every
                    // trailing control: tapping the outer edge of a row's ⬇
                    // scrolled the list instead of starting the download —
                    // deterministic, not a race. While the rail is up, the list
                    // ends where the rail begins.
                    end = if (railed) Dimensions.touchTargetMin else spacing.sm8,
                    bottom = spacing.sm8,
                ),
            modifier = Modifier.fillMaxSize().testTag("tt_lang_list"),
        ) {
            // Emission order IS the order pickerListPlan counts in, and the order
            // `PickerListPositionTest` reads back out of this file — it models the
            // block below, and a model of a moving target has to be pinned to it.
            if (shortcutsInGrid) {
                sections.detect?.let { detect ->
                    item(key = "detect_${detect.id}", contentType = CONTENT_TYPE_ROW) {
                        LanguageRow(detect, target, onSelect, onDownload, onStop)
                    }
                }
            }
            if (sections.catalogEmpty) {
                fullSpanItem(key = "catalog_loading") { CatalogLoading() }
            }
            if (sections.nothingFound) {
                fullSpanItem(key = "empty_result") { NoSearchResults(query = query, onShowAll = onClearQuery) }
            }
            if (shortcutsInGrid) {
                plan.recentHeader?.let { header ->
                    fullSpanItem(key = "header_recent", contentType = CONTENT_TYPE_HEADER) {
                        SectionHeader(recentHeaderRes(header))
                    }
                    pickerRows(sections.recent, RECENT_ROW_KEY_PREFIX, target, onSelect, onDownload, onStop)
                }
            }
            // No "All languages" banner over a filtered list: the results ARE
            // the list, and a counter beside them would count the catalog, not
            // them.
            if (plan.showAllHeader) {
                fullSpanItem(key = "header_all", contentType = CONTENT_TYPE_HEADER) {
                    SectionHeader(
                        titleRes = R.string.text_lang_all_header,
                        // In 17a the counter is in the bar: this header names the
                        // catalog PANE, and a screen-wide count beside it would be
                        // counting something the pane does not contain.
                        counts = if (plan.counterInTopBar) null else sections.counts,
                    )
                }
            }
            pickerRows(visibleRows, CATALOG_ROW_KEY_PREFIX, target, onSelect, onDownload, onStop)
        }
        if (letters.isNotEmpty()) {
            AlphabetRail(
                letters = letters,
                gridState = gridState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = spacing.lg24),
            )
        }
    }
}

/**
 * A header or a message spans the whole grid — at one column that is what an
 * ordinary item already does, and at two an un-spanned header would sit in the
 * left column with a language beside it.
 *
 * It is still ONE item either way, which is what keeps
 * [PickerListPlan.catalogOffset]'s arithmetic true in both arrangements.
 */
private fun LazyGridScope.fullSpanItem(
    key: String,
    contentType: String? = null,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = contentType) { content() }

/**
 * The same language legitimately appears under Recent AND under All languages
 * (GT does the same), so [prefix] is what keeps the grid's keys unique —
 * duplicate keys are a hard crash, not a warning.
 *
 * [prefix] carries its own separator ([CATALOG_ROW_KEY_PREFIX] /
 * [RECENT_ROW_KEY_PREFIX]) rather than having one added here, so
 * [catalogRowKey] spells the emitted key exactly and a saved position and the
 * grid cannot drift apart over a punctuation mark.
 */
private fun LazyGridScope.pickerRows(
    rows: List<LanguagePickerRow>,
    prefix: String,
    target: LanguageRole,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) = items(rows, key = { prefix + it.id }, contentType = { CONTENT_TYPE_ROW }) { row ->
    LanguageRow(row, target, onSelect, onDownload, onStop)
}

/** 15a says "Recent"; 16a names the role, so its header can be checked against its rows. */
@StringRes
private fun recentHeaderRes(header: RecentHeader): Int =
    when (header) {
        RecentHeader.GENERIC -> R.string.text_lang_recent_header
        RecentHeader.TARGET -> R.string.text_lang_target_recent_header
    }

// ---- pieces ----------------------------------------------------------------

/**
 * 64dp bar, left-aligned 22sp title, 48dp back target, no trailing action
 * (design §2).
 *
 * **The dialog's bar is the same bar with the other way out** (17c/17d). A back
 * arrow promises a screen behind it that the arrow will take you back to; in the
 * card that screen is already on show behind the scrim and nothing is going to
 * be popped, so the export draws a Close cross — leading, where the arrow was,
 * because the export puts it trailing and M3 puts a full-screen dialog's Close
 * at the start. Trailing here would also collide with the trailing edge the rail
 * has just been taken off. Same testTag either way: it is one control with one
 * job, and a test that had to know which host it was in would be testing the
 * host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerTopBar(
    title: String,
    onBack: () -> Unit,
    host: PickerHost = PickerHost.NAV_ENTRY,
) {
    val dialog = host == PickerHost.DIALOG
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("tt_lang_back")) {
                Icon(
                    if (dialog) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription =
                        stringResource(
                            if (dialog) R.string.lang_dialog_close else R.string.cd_lang_back,
                        ),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * 17a's bar: back · title · the search field · the on-device counter, in ONE
 * row.
 *
 * A plain `Row` rather than a `TopAppBar`, because an M3 top app bar is 64dp
 * with a title slot and an actions slot, and this bar has to be shorter than
 * that AND hold a text field between the two. At 412dp of window height the
 * 8dp difference is a whole row of languages — see
 * [Dimensions.pickerCompactBarHeight] for the arithmetic and for why the field
 * inside it is 48dp rather than the export's 40dp.
 *
 * The counter reads the whole catalog, not the pane it sits above, which is
 * exactly why 17a moves it up here out of the "All languages" header.
 */
@Composable
private fun PickerCompactBar(
    title: String,
    onBack: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    catalogSize: Int,
    counts: OnDeviceCount?,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier =
            Modifier
                .fillMaxWidth()
                // The insets an M3 `TopAppBar` would have applied for us. A plain
                // Row applies none, and the first device run drew this bar's title
                // straight through the status-bar clock and its counter through the
                // signal icons. `TopAppBarDefaults.windowInsets` is systemBars top
                // AND horizontal, which is the right pair here: landscape is exactly
                // where a display cutout eats into the leading edge.
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .height(Dimensions.pickerCompactBarHeight)
                .padding(horizontal = spacing.xs4),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("tt_lang_back")) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_lang_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        // The cap needs a Box between it and the weight, and that is not a style
        // choice. `Modifier.weight(1f)` measures its child with a FIXED width —
        // min and max both the weighted share — and `widthIn(max = …)` can only
        // narrow within the incoming range, which at a fixed width is a single
        // point. Written directly on the weighted child the cap is silently
        // inert: measured on `emulator-5554` the field came out 1544px ≈ 588dp
        // at 2.625 px/dp, not the 420dp `pickerSearchMaxWidth` documents. The Box
        // takes the share, the field takes the cap inside it, and the leftover
        // stays where it was — between the field and the counter, which is what
        // the token's own line ("stops here rather than stretching to the
        // counter") asks for.
        Box(modifier = Modifier.weight(1f)) {
            PickerSearchField(
                query = query,
                onQueryChange = onQueryChange,
                catalogSize = catalogSize,
                modifier = Modifier.widthIn(max = Dimensions.pickerSearchMaxWidth),
            )
        }
        if (counts != null) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.text_lang_on_device_count,
                        counts.downloaded,
                        counts.downloaded,
                        counts.capable,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = spacing.sm8).testTag("tt_lang_counter"),
            )
        }
    }
}

/**
 * The permanent filter field: a 48dp `surfaceContainerHigh` pill, not an M3
 * `SearchBar` — a SearchBar is an expanding overlay that owns its own results
 * surface, and this field filters a list that is already on screen.
 *
 * Built on [BasicTextField] because `TextField` cannot be 48dp tall without
 * fighting its own decoration; everything the pill draws is a token.
 */
@Composable
private fun PickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    catalogSize: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.cd_text_lang_search)
    // "Search 194 languages", never the design's hardcoded 65 — and never
    // "Search 0 languages" on the frame before the catalog arrives.
    val placeholder =
        if (catalogSize == 0) {
            label
        } else {
            pluralStringResource(R.plurals.text_lang_search_hint, catalogSize, catalogSize)
        }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("tt_lang_search")
                .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.touchTargetMin)
                        .clip(TranzlateShapeFull)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(start = spacing.md16, end = spacing.sm8),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.iconSm),
                )
                Spacer(Modifier.width(Dimensions.pickerLeadingInset))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                // Only when there is something to clear — an always-on clear
                // button on an empty field is a control that does nothing.
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("tt_lang_clear"),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_text_lang_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.iconSm),
                        )
                    }
                } else {
                    Spacer(Modifier.width(spacing.sm8))
                }
            }
        },
    )
}

/**
 * Section label, optionally with the on-device counter on the same baseline.
 *
 * The label is drawn UPPERCASE (design) but announced in its written case: a
 * screen reader given "ALL LANGUAGES" may spell it out letter by letter.
 */
@Composable
private fun SectionHeader(
    @StringRes titleRes: Int,
    counts: OnDeviceCount? = null,
) {
    val spacing = LocalSpacing.current
    val locale = LocalLocale.current.platformLocale
    val label = stringResource(titleRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = spacing.md16,
                    end = spacing.md16,
                    top = spacing.md16,
                    bottom = spacing.xs4,
                ),
    ) {
        Text(
            text = label.uppercase(locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.semantics {
                    heading()
                    contentDescription = label
                },
        )
        if (counts != null) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.text_lang_on_device_count,
                        counts.downloaded,
                        counts.downloaded,
                        counts.capable,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("tt_lang_counter"),
            )
        }
    }
}

/**
 * One picker row. The container, the avatar and the trailing control are all
 * decided by ONE [LanguageRowState] — there is no combination of flags to get
 * wrong, and no state without a rendering.
 *
 * TalkBack gets the language AND its state from the row's own description; the
 * avatar is decorative (the code is not read aloud) and the trailing control,
 * when it is a control, keeps its own focusable label.
 */
@Composable
private fun LanguageRow(
    row: LanguagePickerRow,
    target: LanguageRole,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val selected = row.state is LanguageRowState.Selected
    val supporting = rowSupportingText(row.state)
    val voiceMark = row.showsVoiceMark(target)
    val description = rowContentDescription(row, voiceMark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                // Decided in `pickerRowMinHeight`, not here: the "voice, no pack"
                // row must keep the two-line box, and a lens proved that living
                // inside this composable meant no test in the module could reach
                // the rule at all.
                .heightIn(min = pickerRowMinHeight(hasSupportingText = supporting != null, voiceMark = voiceMark))
                .clip(TranzlateShapeFull)
                .background(rowContainerColor(row.state))
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(row.id) },
                ).padding(start = Dimensions.pickerLeadingInset, end = spacing.md16)
                .testTag("tt_lang_row_${row.id}")
                .semantics { contentDescription = description },
    ) {
        LanguageAvatarCircle(avatar = row.avatar, state = row.state)
        Spacer(Modifier.width(spacing.md16))
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm8)) {
            Text(
                text = row.displayName,
                style =
                    if (selected) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RowSupportingLine(state = row.state, text = supporting, voiceMark = voiceMark)
        }
        Spacer(Modifier.width(spacing.sm8))
        RowTrailing(row = row, onDownload = onDownload, onStop = onStop)
    }
}

/** 40dp circle carrying the primary subtag — flags are wrong for languages. */
@Composable
private fun LanguageAvatarCircle(
    avatar: LanguageAvatar,
    state: LanguageRowState,
) {
    val background =
        when (state) {
            is LanguageRowState.Selected -> {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }

            is LanguageRowState.Downloaded -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            else -> {
                if (avatar is LanguageAvatar.Detect) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            }
        }
    val foreground =
        when (state) {
            is LanguageRowState.Selected, is LanguageRowState.Downloaded -> {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            else -> {
                if (avatar is LanguageAvatar.Detect) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(Dimensions.iconChip)
                .clip(TranzlateShapeFull)
                .background(background),
    ) {
        when (avatar) {
            is LanguageAvatar.Code -> {
                Text(
                    text = avatar.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    maxLines = 1,
                )
            }

            LanguageAvatar.Detect -> {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(Dimensions.iconSm),
                )
            }
        }
    }
}

/**
 * The supporting line's WORDS, or null when the state has nothing to add.
 *
 * `Selected` delegates to what it wraps rather than answering for itself: that
 * is the whole point of the wrapper, and it is what puts "On device" under the
 * selected Spanish row in 16a.
 */
@Composable
private fun rowSupportingText(state: LanguageRowState): String? {
    val context = LocalContext.current
    return when (state) {
        is LanguageRowState.Selected -> rowSupportingText(state.inner)

        is LanguageRowState.Downloaded -> onDeviceLine(context, state.sizeBytes)

        LanguageRowState.Downloading -> stringResource(R.string.text_lang_downloading)

        // The SAME sentence the offline manager's failed row shows — one map,
        // one string set, `DownloadFailure.kt` (#175, PR-18).
        is LanguageRowState.Failed -> stringResource(downloadFailureCopy(state.cause).rowLine)

        LanguageRowState.Downloadable, LanguageRowState.OnlineOnly -> null
    }
}

/**
 * "On device · 45.7 MB" when the bytes were MEASURED, plain "On device" when
 * they were not (plan R3). Nothing here invents a number: ML Kit exposes no
 * per-model size, and its store is pair-keyed (`de_en`, verified in the #90
 * research), so a per-language figure has to be measured or omitted.
 */
@Composable
private fun onDeviceLine(
    context: android.content.Context,
    sizeBytes: Long?,
): String =
    if (sizeBytes == null) {
        stringResource(R.string.text_lang_on_device)
    } else {
        stringResource(R.string.text_lang_on_device_size, Formatter.formatShortFileSize(context, sizeBytes))
    }

/**
 * Supporting line. The downloading row carries an INDETERMINATE bar and the word
 * "Downloading…" where the design drew "42%": `RemoteModelManager.download()`
 * returns `Task<Void>`, so a percentage would be fiction (plan R1).
 */
@Composable
private fun RowSupportingLine(
    state: LanguageRowState,
    text: String?,
    voiceMark: Boolean,
) {
    if (text == null && !voiceMark) return
    val spacing = LocalSpacing.current
    val color =
        when {
            state is LanguageRowState.Selected -> MaterialTheme.colorScheme.onPrimaryContainer
            state is LanguageRowState.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val downloading =
        state == LanguageRowState.Downloading ||
            (state is LanguageRowState.Selected && state.inner == LanguageRowState.Downloading)
    if (downloading && text != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(top = spacing.xs4),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.weight(1f).testTag("tt_lang_progress"),
            )
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            VoiceMark(visible = voiceMark, tint = color)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs4),
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            VoiceMark(visible = voiceMark, tint = color)
        }
    }
}

/**
 * The offline-voice speaker, drawn where the export draws it: on the supporting
 * line, just after the state words, in the same ink as them — not in the
 * trailing cluster, which belongs to the row's one state control.
 *
 * Silent to TalkBack on purpose. It is decoration over a fact already spoken by
 * the row's own description ([rowContentDescription]); announced separately it
 * would read as a second, tappable thing, and since rev 5 cut 19j there is
 * nothing to tap — the mark is only ever drawn where the voice already exists.
 */
@Composable
private fun VoiceMark(
    visible: Boolean,
    tint: Color,
) {
    if (!visible) return
    Icon(
        Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Dimensions.iconXs).testTag("tt_lang_voice_mark"),
    )
}

/** The single trailing control — one per state, decided in one place. */
@Composable
private fun RowTrailing(
    row: LanguagePickerRow,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    when (row.state) {
        is LanguageRowState.Selected -> {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(Dimensions.iconMd),
            )
        }

        // Static, not a button: deleting a pack lives in the offline manager
        // (D-E2) — the picker only reports what is on the device.
        is LanguageRowState.Downloaded -> {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.pickerStateIcon),
            )
        }

        LanguageRowState.Downloading -> {
            IconButton(
                onClick = { onStop(row.id) },
                modifier = Modifier.testTag("tt_lang_stop_${row.id}"),
            ) {
                Icon(
                    Icons.Filled.Close,
                    // Never "Cancel": ML Kit has no cancel, so stopping IS removing (R2).
                    contentDescription = stringResource(R.string.cd_text_lang_stop, row.displayName),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.iconSm),
                )
            }
        }

        LanguageRowState.Downloadable -> {
            IconButton(
                onClick = { onDownload(row.id) },
                modifier = Modifier.testTag("tt_lang_download_${row.id}"),
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.cd_text_lang_download, row.displayName),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.pickerStateIcon),
                )
            }
        }

        LanguageRowState.OnlineOnly -> {
            OnlineOnlyChip()
        }

        is LanguageRowState.Failed -> {
            RetryPill(row = row, onRetry = onDownload)
        }
    }
}

/**
 * The failed row's action — a labelled **Retry** pill, which is the 15a
 * deviation the rev3 ruling asks PR-18 to close (*"icon → spec filled pill"*).
 *
 * What was here was `Icons.Filled.Refresh` in an `IconButton`: the same glyph
 * this app uses for "reload" everywhere else, sitting where every other row in
 * the list draws a state — a cloud, a download arrow, a chip. A circular arrow
 * beside a red sentence asks the user to infer that the arrow undoes the
 * sentence. The export draws the word instead, and the word is the whole
 * argument: it is the ONE row state whose control performs an action rather than
 * reporting a fact, and a label is what says so.
 *
 * **The description now CONTAINS the visible word, and that is a fix the label
 * forced.** A control's accessible name must contain the text drawn on it (WCAG
 * 2.5.3, *Label in Name*) — otherwise a voice-control user says "tap Retry" and
 * nothing happens. While the control was an icon there was no visible text and
 * `cd_text_lang_retry` could read "Try downloading Hindi again"; the moment the
 * word appears on the button, that description stops containing its own label.
 * The key's VALUE changed with the control ("Retry download for Hindi"), in all
 * three locales, rather than the key being retired: a screen-reader user still
 * needs the language name, which the sighted user reads a few dp to the left.
 *
 * **Height, where the export and C-14 disagree.** The frame draws a 40dp pill.
 * C-14 makes [Dimensions.touchTargetMin] the authoritative floor, and PR-14
 * settled the same collision the same way for the landscape search field, so the
 * pill's own box is 48dp. It sits inside a 60dp row, so nothing grows.
 *
 * The colours ARE the export's: an error-filled container, which is the spec §5
 * reservation applied correctly — this is the one control on the screen that
 * belongs to a failure, and the row's other ink already carries it.
 */
@Composable
private fun RetryPill(
    row: LanguagePickerRow,
    onRetry: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val spoken = stringResource(R.string.cd_text_lang_retry, row.displayName)
    Button(
        onClick = { onRetry(row.id) },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        contentPadding = PaddingValues(horizontal = spacing.md16),
        modifier =
            Modifier
                .heightIn(min = Dimensions.touchTargetMin)
                .testTag("tt_lang_retry_${row.id}")
                .semantics { contentDescription = spoken },
    ) {
        // Left in the semantics tree on purpose. TalkBack prefers the button's
        // `contentDescription` over its text, so the word is not announced twice
        // — and leaving it there is what lets a test read the LABEL rather than
        // only the tag, which is the difference between catching the icon coming
        // back and not.
        Text(
            text = stringResource(R.string.lang_sheet_failed_retry),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * The 16a voice legend: what the speaker on a row means, said ONCE at the top
 * instead of on every row that carries one.
 *
 * It is drawn as a row-shaped block — same 40dp circle, same leading inset,
 * same full-pill shape as a language row — because it sits in the list and
 * anything else there would read as a card that had wandered in. The circle
 * takes the tertiary pair, which is the same treatment the "Detect language"
 * avatar already gets for the same reason: this is not a language, and the
 * palette should say so before the words do.
 *
 * Not a control, and not focusable as one: it explains a mark that has nothing
 * to tap (rev 5 cut 19j — see [showsVoiceMark]). TalkBack reads it as the
 * sentence it is.
 *
 * It is drawn ABOVE the list rather than as an item of it. The reason is not
 * layout taste — it is that the device's voice answer arrives after the list has
 * been laid out, and an item added to an anchored `LazyColumn` at index 0 lands
 * above the viewport. `pickerListPlan` carries the measurement.
 */
@Composable
private fun VoiceLegend(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.pickerRowHeight)
                .clip(TranzlateShapeFull)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(
                    start = Dimensions.pickerLeadingInset,
                    end = spacing.md16,
                    top = spacing.sm8,
                    bottom = spacing.sm8,
                ).testTag("tt_lang_voice_legend"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(Dimensions.iconChip)
                    .clip(TranzlateShapeFull)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(Dimensions.iconSm),
            )
        }
        Spacer(Modifier.width(spacing.md16))
        Text(
            text = stringResource(R.string.text_lang_voice_legend),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The design's own chip, reused for the 135 catalog languages that ML Kit
 * cannot hold offline at all (plan R4) — same visual language, nothing invented.
 */
@Composable
private fun OnlineOnlyChip() {
    val spacing = LocalSpacing.current
    val locale = LocalLocale.current.platformLocale
    Text(
        text = stringResource(R.string.text_lang_online_only).uppercase(locale),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .heightIn(min = Dimensions.pickerChipHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = spacing.sm8, vertical = spacing.xs4)
                .clearAndSetSemantics { },
    )
}

/**
 * The name AND its state — the old app announced `content-desc=""` on every node.
 *
 * The speaker mark is folded in HERE rather than labelled on the icon: a
 * screen reader should hear one sentence about the row ("Spanish, on device,
 * can be spoken offline"), not a row followed by a loose decorative node.
 */
@Composable
private fun rowContentDescription(
    row: LanguagePickerRow,
    voiceMark: Boolean,
): String {
    val base = stateContentDescription(row.state, row.displayName)
    return if (voiceMark) stringResource(R.string.cd_text_lang_row_voice, base) else base
}

@Composable
private fun stateContentDescription(
    state: LanguageRowState,
    name: String,
): String =
    when (state) {
        // `selected` semantics already announce the choice; the line adds the fact.
        is LanguageRowState.Selected -> {
            stateContentDescription(state.inner, name)
        }

        is LanguageRowState.Downloaded -> {
            stringResource(R.string.cd_text_lang_row_on_device, name)
        }

        LanguageRowState.Downloading -> {
            stringResource(R.string.cd_text_lang_row_downloading, name)
        }

        LanguageRowState.Downloadable -> {
            stringResource(R.string.cd_text_lang_row_downloadable, name)
        }

        LanguageRowState.OnlineOnly -> {
            stringResource(R.string.cd_text_lang_row_online_only, name)
        }

        is LanguageRowState.Failed -> {
            stringResource(R.string.cd_text_lang_row_failed, name)
        }
    }

private fun rowContainerColorFor(
    state: LanguageRowState,
    primaryContainer: Color,
    surfaceContainerLow: Color,
): Color =
    when (state) {
        is LanguageRowState.Selected -> primaryContainer
        LanguageRowState.Downloading -> surfaceContainerLow
        else -> Color.Transparent
    }

@Composable
private fun rowContainerColor(state: LanguageRowState): Color =
    rowContainerColorFor(
        state = state,
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow,
    )

/**
 * A–Z index down the right edge: [Dimensions.pickerRailWidth] of ink inside a
 * [Dimensions.touchTargetMin] target, active letter in a `primaryContainer` pill.
 *
 * Two deliberate properties:
 * - **It does not recompose the world.** The active letter is a
 *   [derivedStateOf] read INSIDE this composable, so a scroll that does not
 *   change the letter recomposes nothing, and one that does recomposes the rail
 *   alone — never the 194 rows.
 * - **TalkBack never sees it.** `clearAndSetSemantics` drops the rail and all 26
 *   of its targets out of traversal; the list itself is the accessible surface
 *   (plan §5).
 *
 * Only letters that actually exist are drawn — a rail letter that scrolls
 * nowhere is a dead control.
 */
@Composable
private fun AlphabetRail(
    letters: List<Pair<Char, Int>>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.width(Dimensions.touchTargetMin).fillMaxHeight()) {
        // Only as many letters as actually fit. `SpaceEvenly` has no answer for
        // negative free space: past that point the glyphs overlap and the ones
        // at the end clamp to nothing, while `railSlot` keeps mapping the
        // finger across the ORIGINAL count — so the letter you touch and the
        // place it scrolls to stop agreeing. Reachable two ways: landscape,
        // where the list viewport is a fraction of the 26 × 18dp an English
        // alphabet needs, and a CJK system locale, where the index letter is
        // the first character of a localized name and the count runs past a
        // hundred. Sampling keeps the rail honest at any height; below three
        // survivors it is not an index any more and does not draw.
        val fits = (maxHeight / Dimensions.pickerRailLetter).toInt().coerceAtLeast(1)
        val shown = remember(letters, fits) { letters.sampledTo(fits) }
        if (shown.size >= MIN_RAIL_LETTERS) {
            RailColumn(letters = shown, gridState = gridState)
        }
    }
}

@Composable
private fun RailColumn(
    letters: List<Pair<Char, Int>>,
    gridState: LazyGridState,
) {
    val scope = rememberCoroutineScope()
    val active: State<Char?> =
        remember(letters, gridState) {
            derivedStateOf {
                val first = gridState.firstVisibleItemIndex
                letters.lastOrNull { it.second <= first }?.first
            }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("tt_lang_rail")
                .clearAndSetSemantics { }
                .pointerInput(letters) {
                    // One gesture handler for tap AND scrub: 26 separate
                    // clickables would be 26 more nodes to lay out on a surface
                    // whose whole job is to stay cheap while the list flings.
                    // The scroll only fires when the LETTER under the finger
                    // changes, so a slow drag does not enqueue a scroll per pixel.
                    var lastSlot = NO_SLOT
                    awaitPointerEventScope {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                lastSlot = NO_SLOT
                            } else {
                                change.consume()
                                val slot = railSlot(change.position.y, size.height, letters.size)
                                if (slot != lastSlot) {
                                    lastSlot = slot
                                    scope.launch { gridState.scrollToItem(letters[slot].second) }
                                }
                            }
                        }
                    }
                },
    ) {
        letters.forEach { (letter, _) ->
            RailLetter(letter = letter, active = active.value == letter)
        }
    }
}

@Composable
private fun RailLetter(
    letter: Char,
    active: Boolean,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(if (active) Dimensions.pickerRailPill else Dimensions.pickerRailWidth)
                .then(
                    if (active) {
                        Modifier
                            .clip(TranzlateShapeFull)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
        )
    }
}

/**
 * EDGE_CASES no-dead-end: a fruitless search says WHAT was searched, why it may
 * have missed, and offers the one tap back to the whole catalog.
 */
@Composable
private fun NoSearchResults(
    query: String,
    onShowAll: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg24, vertical = spacing.xl32)
                .testTag("tt_lang_no_results"),
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.text_lang_no_results, query),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.text_lang_no_results_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onShowAll, modifier = Modifier.testTag("tt_lang_show_all")) {
            Text(stringResource(R.string.text_lang_show_all))
        }
    }
}

/** First frame only: the catalog flow has not emitted yet (no header over nothing). */
@Composable
private fun CatalogLoading() {
    val spacing = LocalSpacing.current
    Text(
        text = stringResource(R.string.text_lang_loading),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md16, vertical = spacing.lg24)
                .testTag("tt_lang_loading"),
    )
}

// ---- previews (CLAUDE.md rule 7 — one per meaningful STATE) ------------------

/**
 * The 16a cast, as the export draws it: Spanish selected, on device, with a
 * voice; English on device with a voice; Afrikaans on device with NO voice —
 * the row that proves a pack does not imply a voice. Arabic carries a voice
 * while its pack is still downloading, which is the other half of that
 * independence and the case 17a's landscape "to" frame draws.
 *
 * `internal` rather than file-private since #130 PR-16: the dialog host's
 * previews are in their own file and draw the same picker, and two casts would
 * mean two sets of rows that could quietly stop agreeing about which language
 * has a voice.
 */
internal val previewLanguages =
    listOf(
        Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = true),
        Language("sq", "Albanian", offlineAvailable = true, offlineDownloaded = false),
        Language("am", "Amharic", offlineAvailable = true, offlineDownloaded = false),
        Language(
            "ar",
            "Arabic",
            offlineAvailable = true,
            offlineDownloaded = false,
            hasOfflineVoice = true,
        ),
        Language("hy", "Armenian", offlineAvailable = false, offlineDownloaded = false),
        Language(
            "en",
            "English",
            offlineAvailable = true,
            offlineDownloaded = true,
            hasOfflineVoice = true,
        ),
        Language(
            "es",
            "Spanish",
            offlineAvailable = true,
            offlineDownloaded = true,
            hasOfflineVoice = true,
        ),
        Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
    )

/** Newest first: Spanish, English, Afrikaans — the export's recents order. */
internal val previewRecents = mapOf("es" to 3L, "en" to 2L, "af" to 1L)

internal val previewStates =
    mapOf(
        "ar" to OfflineModelState.Downloading,
        "am" to OfflineModelState.Failed(OfflineModelFailure.NETWORK),
    )

@PreviewLightDark
@Composable
private fun LanguagePickerSourcePreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            arrangementOverride = PickerArrangement.SinglePane,
            recents = previewRecents,
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerTargetPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            arrangementOverride = PickerArrangement.SinglePane,
            recents = previewRecents,
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerSearchingPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "a",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            arrangementOverride = PickerArrangement.SinglePane,
            recents = previewRecents,
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerNoResultsPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "klingon",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            arrangementOverride = PickerArrangement.SinglePane,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerLoadingPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = emptyList(),
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            arrangementOverride = PickerArrangement.SinglePane,
        )
    }
}

// The consent preview that stood here is GONE, not moved by accident. It drew
// the picker with `pendingConsent = "sq"` and an `AlertDialog` over it; 19a is a
// `ModalBottomSheet`, which opens a window the tooling does not render, so the
// same preview would now show the resting screen while claiming to show the
// consent state — a preview that lies is worse than none. The sheet's two
// meaningful states are previewed where the sheet lives, in `MobileDataSheet.kt`.
//
// `LanguagePickerContent(packFailure = …)` is absent for the SAME reason and had
// no comment at all, which made two identical omissions look like different ones
// in the source (#243). 19b and 19d are `ModalBottomSheet`s raised through
// `PackFailureSheetHost`, so a preview passing a non-null `packFailure` would
// draw the plain picker and claim to show a failure sheet. Both sheets and all
// three of their cause variants are previewed in `PackFailureSheets.kt`; what a
// failure looks like ON the picker is the row, which is
// `LanguageRowFailedPreview`, `LanguageRowFailedGenericPreview` and
// `LanguageRowSelectedFailedPreview` below.

// ---- 17a landscape (issue #130 PR-14) ---------------------------------------
// A preview cannot resize the window it is rendered in, so each frame below
// borrows the export's own landscape geometry and states which arrangement it
// is showing. The 892×412 pair is `from · landscape` / `to · landscape`; the
// narrow frame is the same arrangement on a window with room for one column.

private val previewLandscapeWidth: Dp = 892.dp
private val previewLandscapeHeight: Dp = 412.dp
private val previewNarrowLandscapeWidth: Dp = 640.dp
private val previewNarrowLandscapeHeight: Dp = 360.dp

@Composable
private fun LandscapePreviewFrame(
    width: Dp = previewLandscapeWidth,
    height: Dp = previewLandscapeHeight,
    content: @Composable () -> Unit,
) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.size(width = width, height = height)) { content() }
        }
    }
}

/**
 * `from · landscape`: the side pane carries Recent and the "Detect language"
 * row, the catalog takes two columns, and no row anywhere shows a speaker —
 * the mark is target-only, which that frame confirms by drawing none.
 */
@PreviewLightDark
@Composable
private fun PickerLandscapeFromPreview() {
    LandscapePreviewFrame {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewTwoPaneWide,
        )
    }
}

/**
 * `to · landscape`: the side pane carries "Recently used as target" and the
 * voice legend where the source side carries Detect, and the counter has moved
 * into the bar.
 */
@PreviewLightDark
@Composable
private fun PickerLandscapeToPreview() {
    LandscapePreviewFrame {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewTwoPaneWide,
        )
    }
}

/**
 * The state the export does not draw: a search on the source side clears the
 * recents and filters out the Detect row, so the side pane would have nothing
 * in it — and 272dp of empty surface beside a "no results" message is furniture
 * reporting a failure of its own. The pane goes and the catalog takes the width.
 */
@PreviewLightDark
@Composable
private fun PickerLandscapeNoSidePanePreview() {
    LandscapePreviewFrame {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "klingon",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewTwoPaneWide,
        )
    }
}

/**
 * Two panes, one column: a landscape window wide enough for the side pane but
 * not for a second column of languages. This is the arrangement the owner's
 * OnePlus 7 Pro class of device gets, and the reason the gate is a dp sum rather
 * than a size class.
 */
@PreviewLightDark
@Composable
private fun PickerLandscapeSingleColumnPreview() {
    LandscapePreviewFrame(width = previewNarrowLandscapeWidth, height = previewNarrowLandscapeHeight) {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewTwoPaneNarrow,
        )
    }
}

/** What [pickerArrangement] returns for the two frames above — stated, not assumed. */
private val previewTwoPaneWide = PickerArrangement(twoPane = true, columns = 2)
private val previewTwoPaneNarrow = PickerArrangement(twoPane = true, columns = 1)

// ---- 17b foldable two-leaf (issue #130 PR-15) -------------------------------
// The export's foldable frames are 760×812 — a window that is BOTH wider than
// 17a's gate needs and three hundred dp taller than it allows, which is exactly
// why 17b is a separate branch and not a wider 17a.

private val previewFoldableWidth: Dp = 760.dp
private val previewFoldableHeight: Dp = 812.dp

/** What [pickerArrangement] returns at 760×812 in [FoldPosture.BOOK] — one column. */
private val previewTwoLeaf =
    PickerArrangement(
        twoPane = true,
        columns = 1,
        gutter = Dimensions.pickerCreaseGutter,
        sidePaneWidth = Dimensions.pickerLeafPaneWidth,
        twoLeaf = true,
    )

@Composable
private fun FoldablePreviewFrame(content: @Composable () -> Unit) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier.size(width = previewFoldableWidth, height = previewFoldableHeight),
            ) { content() }
        }
    }
}

/**
 * `from · foldable`: the shortcut leaf on the left with Recent, the "Detect
 * language" row and the offline-library meter at its foot; 24dp of crease; the
 * catalog on the right.
 *
 * The gutter is the whole reason this preview is not just a taller
 * `PickerLandscapeFromPreview` — it is the strip the fold runs down, and the
 * owner reviews it from here.
 */
@PreviewLightDark
@Composable
private fun PickerFoldablePreview() {
    FoldablePreviewFrame {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            library = previewMeterPacks,
            arrangementOverride = previewTwoLeaf,
        )
    }
}

/**
 * `to · foldable`: the same leaf carrying "Recently used as target" and the
 * voice legend instead of the Detect row, with speaker marks in the catalog.
 */
@PreviewLightDark
@Composable
private fun PickerFoldableTargetPreview() {
    FoldablePreviewFrame {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            recents = previewRecents,
            offlineStates = previewStates,
            library = previewMeterPacks,
            arrangementOverride = previewTwoLeaf,
        )
    }
}

/**
 * The meter card, one preview per state it can be in — which is the whole of
 * what [offlineLibraryMeter] can return, so a fourth sentence cannot appear on
 * this card without a fourth preview here.
 *
 * They are shown together rather than one per function because the point of
 * three states is the CONTRAST: the degraded card must not be mistakable for the
 * sized one at a glance, and that is only reviewable side by side.
 */
@PreviewLightDark
@Composable
private fun OfflineLibraryMeterZeroPreview() {
    MeterPreviewSurface { OfflineLibraryMeterCard(meter = OfflineLibraryMeter.Empty(capable = 59)) }
}

@PreviewLightDark
@Composable
private fun OfflineLibraryMeterPacksPreview() {
    MeterPreviewSurface { OfflineLibraryMeterCard(meter = previewMeterPacks) }
}

/**
 * The R8 case: packs are installed, the model store is not where issue #90's
 * research measured it, so the size is unknown and free space is reported
 * instead. The bar is an empty track — a fraction of the disk is precisely what
 * is not known here.
 */
@PreviewLightDark
@Composable
private fun OfflineLibraryMeterDegradedPreview() {
    MeterPreviewSurface {
        OfflineLibraryMeterCard(
            meter = OfflineLibraryMeter.Unsized(downloaded = 5, capable = 59, freeBytes = 8_651_702_272L),
        )
    }
}

/** A library that fills the volume — the top end of the bar, drawn once. */
@PreviewLightDark
@Composable
private fun OfflineLibraryMeterFullPreview() {
    MeterPreviewSurface {
        OfflineLibraryMeterCard(
            meter =
                OfflineLibraryMeter.Sized(
                    downloaded = 59,
                    capable = 59,
                    usedBytes = 9_100_000_000L,
                    volumeBytes = 10_411_143_168L,
                ),
        )
    }
}

@Composable
private fun MeterPreviewSurface(content: @Composable () -> Unit) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(modifier = Modifier.width(Dimensions.pickerLeafPaneWidth).padding(LocalSpacing.current.sm8)) {
                content()
            }
        }
    }
}

/**
 * The E-S1 numbers, so the card is reviewed against a real device rather than a
 * round one: one af↔en pack measured 44,169,505 bytes on `emulator-5554`, on a
 * volume `df` reported as 10,411,143,168 bytes.
 */
private val previewMeterPacks =
    OfflineLibraryMeter.Sized(
        downloaded = 5,
        capable = 59,
        usedBytes = 220_847_525L,
        volumeBytes = 10_411_143_168L,
    )

/** The landscape bar on its own: with the counter, and before the catalog arrives. */
@PreviewLightDark
@Composable
private fun PickerCompactBarPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.width(previewLandscapeWidth)) {
                PickerCompactBar(
                    title = "Translate to",
                    onBack = {},
                    query = "",
                    onQueryChange = {},
                    catalogSize = 194,
                    counts = OnDeviceCount(downloaded = 5, capable = 59),
                )
                PickerCompactBar(
                    title = "Translate from",
                    onBack = {},
                    query = "span",
                    onQueryChange = {},
                    catalogSize = 0,
                    counts = null,
                )
            }
        }
    }
}

@Composable
private fun RowPreviewSurface(
    state: LanguageRowState,
    hasOfflineVoice: Boolean = false,
    target: LanguageRole = LanguageRole.SOURCE,
) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LanguageRow(
                row =
                    LanguagePickerRow(
                        id = "es",
                        displayName = "Spanish",
                        avatar = LanguageAvatar.Code("ES"),
                        state = state,
                        hasOfflineVoice = hasOfflineVoice,
                    ),
                target = target,
                onSelect = {},
                onDownload = {},
                onStop = {},
            )
        }
    }
}

// The six states of the plan's row matrix, one preview each.

@PreviewLightDark
@Composable
private fun LanguageRowSelectedPreview() {
    RowPreviewSurface(LanguageRowState.Selected(LanguageRowState.Downloaded()))
}

@PreviewLightDark
@Composable
private fun LanguageRowDownloadedPreview() {
    RowPreviewSurface(LanguageRowState.Downloaded())
}

@PreviewLightDark
@Composable
private fun LanguageRowDownloadingPreview() {
    RowPreviewSurface(LanguageRowState.Downloading)
}

@PreviewLightDark
@Composable
private fun LanguageRowDownloadablePreview() {
    RowPreviewSurface(LanguageRowState.Downloadable)
}

@PreviewLightDark
@Composable
private fun LanguageRowOnlineOnlyPreview() {
    RowPreviewSurface(LanguageRowState.OnlineOnly)
}

@PreviewLightDark
@Composable
private fun LanguageRowFailedPreview() {
    RowPreviewSurface(LanguageRowState.Failed(OfflineModelFailure.STORAGE))
}

/**
 * The THIRD failure sentence (#243). `downloadFailureCopy` produces three
 * distinct lines and this row previewed two of them — `STORAGE` above and
 * `NETWORK` on the selected-failed row below — so `lang_pack_error_generic`
 * ("Download didn't finish. Try again.") was drawn in no preview on either
 * screen. Because PR-18 made both screens read ONE map, a single omission hid
 * the sentence twice, and rule 7 exists because the owner reviews UI from
 * previews.
 *
 * `WIFI_REQUIRED` deliberately gets none: it folds onto the network line by
 * design and `DownloadFailureTest` pins the fold in both directions, so a
 * preview of it would draw a duplicate of `LanguageRowSelectedFailedPreview`
 * and imply a fourth sentence exists.
 */
@PreviewLightDark
@Composable
private fun LanguageRowFailedGenericPreview() {
    RowPreviewSurface(LanguageRowState.Failed(OfflineModelFailure.UNKNOWN))
}

/**
 * The one row whose size IS measured. Kept separate so the reviewer can see the
 * design's "On device · N MB" line render the moment a truthful source exists —
 * production supplies none today (plan R3).
 */
@PreviewLightDark
@Composable
private fun LanguageRowDownloadedWithSizePreview() {
    RowPreviewSurface(LanguageRowState.Downloaded(sizeBytes = 45_700_000L))
}

/**
 * The wrapper's point, drawn: selection no longer erases what the row was, so a
 * chosen language that is still downloading keeps its progress line and a
 * chosen language that failed still says why.
 */
@PreviewLightDark
@Composable
private fun LanguageRowSelectedDownloadingPreview() {
    RowPreviewSurface(LanguageRowState.Selected(LanguageRowState.Downloading))
}

@PreviewLightDark
@Composable
private fun LanguageRowSelectedFailedPreview() {
    RowPreviewSurface(LanguageRowState.Selected(LanguageRowState.Failed(OfflineModelFailure.NETWORK)))
}

// ---- 16a: the target row's one extra property (issue #130 PR-12) ------------
// The mark is TARGET-only, so every preview below passes LanguageRole.TARGET;
// the matching source-side rows are the six previews above, which pass SOURCE
// and must show no speaker at all.

/** The export's English row: on device, and this device can say it out loud. */
@PreviewLightDark
@Composable
private fun TargetPickerRowVoicePreview() {
    RowPreviewSurface(
        state = LanguageRowState.Downloaded(),
        hasOfflineVoice = true,
        target = LanguageRole.TARGET,
    )
}

/** The export's Afrikaans row: same pack, no voice, therefore no mark. */
@PreviewLightDark
@Composable
private fun TargetPickerRowNoVoicePreview() {
    RowPreviewSurface(
        state = LanguageRowState.Downloaded(),
        hasOfflineVoice = false,
        target = LanguageRole.TARGET,
    )
}

/** 16a's selected Spanish row: "On device" AND the speaker AND the tick, together. */
@PreviewLightDark
@Composable
private fun TargetPickerRowSelectedPreview() {
    RowPreviewSurface(
        state = LanguageRowState.Selected(LanguageRowState.Downloaded()),
        hasOfflineVoice = true,
        target = LanguageRole.TARGET,
    )
}

/**
 * Voice present, pack absent — the combination 16a does not draw and 17a's
 * landscape "to" frame does (Arabic, mid-download, marked). A pack and a voice
 * are separate installs; this preview is where a reviewer can see that the row
 * still finds room for the mark when there are no supporting words to sit
 * beside, and where the coupling would show up if it ever crept back in.
 */
@PreviewLightDark
@Composable
private fun TargetPickerRowVoiceNoPackPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                LanguageRow(
                    row =
                        LanguagePickerRow(
                            id = "ar",
                            displayName = "Arabic",
                            avatar = LanguageAvatar.Code("AR"),
                            state = LanguageRowState.Downloading,
                            hasOfflineVoice = true,
                        ),
                    target = LanguageRole.TARGET,
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
                LanguageRow(
                    row =
                        LanguagePickerRow(
                            id = "sq",
                            displayName = "Albanian",
                            avatar = LanguageAvatar.Code("SQ"),
                            state = LanguageRowState.Downloadable,
                            hasOfflineVoice = true,
                        ),
                    target = LanguageRole.TARGET,
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
                LanguageRow(
                    row =
                        LanguagePickerRow(
                            id = "hy",
                            displayName = "Armenian",
                            avatar = LanguageAvatar.Code("HY"),
                            state = LanguageRowState.OnlineOnly,
                            hasOfflineVoice = true,
                        ),
                    target = LanguageRole.TARGET,
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun VoiceLegendPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            VoiceLegend()
        }
    }
}

@PreviewLightDark
@Composable
private fun LanguageRowDetectPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                LanguageRow(
                    row = detectRow("Detect language", selected = false),
                    target = LanguageRole.SOURCE,
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
                LanguageRow(
                    row = detectRow("Detect language", selected = true),
                    target = LanguageRole.SOURCE,
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PickerSearchFieldPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(LocalSpacing.current.md16)) {
                PickerSearchField(query = "", onQueryChange = {}, catalogSize = 194)
                Spacer(Modifier.size(LocalSpacing.current.sm8))
                PickerSearchField(query = "span", onQueryChange = {}, catalogSize = 194)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SectionHeaderPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                SectionHeader(R.string.text_lang_recent_header)
                SectionHeader(R.string.text_lang_target_recent_header)
                SectionHeader(R.string.text_lang_all_header, OnDeviceCount(downloaded = 3, capable = 59))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AlphabetRailPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.size(width = Dimensions.touchTargetMin, height = previewRailHeight)) {
                AlphabetRail(
                    letters = ('A'..'J').mapIndexed { index, letter -> letter to index },
                    // `remember`, not `rememberLazyGridState()`: this file may not
                    // IMPORT the saveable versions at all, because an import is the
                    // one place an alias cannot hide what was imported and that is
                    // what `PickerHostAgnosticTest` reads. A preview has no state to
                    // restore anyway.
                    gridState = remember { LazyGridState() },
                )
            }
        }
    }
}

/**
 * The rail fills its parent's height, which a wrap-content preview cannot give
 * it — so the preview lends it a tall box. Any existing length token would do;
 * this one is simply a realistic list height.
 */
private val previewRailHeight: Dp = Dimensions.paneListMin

@PreviewLightDark
@Composable
private fun NoSearchResultsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            NoSearchResults(query = "klingon", onShowAll = {})
        }
    }
}

/**
 * The Retry pill on its own (#243).
 *
 * Rule 7 names row action buttons explicitly, and this control is the 15a
 * deviation the rev3 ruling asked PR-18 to close — icon → labelled pill. It
 * rendered only inside two row previews, where the row's ink, its avatar and its
 * red sentence are all competing for the same glance, and the two things a
 * reviewer has to judge about the pill are its own: that the word reads as an
 * action rather than a state, and that an error-filled container still passes
 * contrast on both themes. Drawn against the row's own surface so the pill's
 * container is the only colour in the frame.
 */
@PreviewLightDark
@Composable
private fun RetryPillPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(LocalSpacing.current.md16)) {
                RetryPill(
                    row =
                        LanguagePickerRow(
                            id = "hi",
                            displayName = "Hindi",
                            avatar = LanguageAvatar.Code("HI"),
                            state = LanguageRowState.Failed(OfflineModelFailure.NETWORK),
                        ),
                    onRetry = {},
                )
            }
        }
    }
}

/**
 * Evenly spaced subset of at most [limit] entries, first and last always kept.
 *
 * Dropping the tail instead would make the rail lie by omission — an index that
 * stops at M in a list that runs to Z.
 */
internal fun List<Pair<Char, Int>>.sampledTo(limit: Int): List<Pair<Char, Int>> {
    if (size <= limit) return this
    if (limit <= 1) return listOf(first())
    val step = (size - 1).toFloat() / (limit - 1)
    return (0 until limit).map { this[(it * step).roundToInt()] }
}

/** Below this a rail is decoration, not navigation. */
private const val MIN_RAIL_LETTERS = 3
