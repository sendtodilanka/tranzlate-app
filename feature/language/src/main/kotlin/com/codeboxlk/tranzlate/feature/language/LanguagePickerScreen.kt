package com.codeboxlk.tranzlate.feature.language

import android.text.format.Formatter
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * stamp and the row-level download controls. Search state is held here so the
 * content stays stateless and previewable.
 */
@Composable
fun LanguagePickerScreen(
    target: LanguageRole,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguagePickerViewModel = hiltViewModel(),
) {
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsent.collectAsStateWithLifecycle()
    val selectedId by viewModel.selection(target).collectAsStateWithLifecycle()
    val recents by viewModel.recents(target).collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LanguagePickerContent(
        target = target,
        languages = languages,
        selectedId = selectedId,
        recents = recents,
        query = query,
        onQueryChange = { query = it },
        onSelect = { id ->
            viewModel.select(id, target)
            onDone()
        },
        onBack = onDone,
        modifier = modifier,
        offlineStates = offlineStates,
        onDownload = viewModel::download,
        onStop = viewModel::stopAndRemove,
        pendingConsent = pendingConsent,
        onDownloadAnyway = viewModel::downloadAnyway,
        onDismissConsent = viewModel::dismissConsent,
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
 * @param sizes measured on-disk bytes per tag; empty in production today, see
 *   [LanguageRowState.Downloaded].
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
    recents: Map<String, Long> = emptyMap(),
    offlineStates: Map<String, OfflineModelState> = emptyMap(),
    sizes: Map<String, Long> = emptyMap(),
    onDownload: (String) -> Unit = {},
    onStop: (String) -> Unit = {},
    pendingConsent: String? = null,
    onDownloadAnyway: () -> Unit = {},
    onDismissConsent: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
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
    MeteredConsentDialog(
        pendingId = pendingConsent,
        rows = sections.all,
        onConfirm = onDownloadAnyway,
        onDismiss = onDismissConsent,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { PickerTopBar(title = title, onBack = onBack) },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = adaptiveMarginShim()),
        ) {
            PickerSearchField(
                query = query,
                onQueryChange = onQueryChange,
                catalogSize = languages.size,
                modifier =
                    Modifier.padding(
                        start = spacing.md16,
                        end = spacing.md16,
                        bottom = spacing.sm8,
                    ),
            )
            PickerList(
                target = target,
                sections = sections,
                query = query,
                onSelect = onSelect,
                onDownload = onDownload,
                onStop = onStop,
                onClearQuery = { onQueryChange("") },
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
 * The list plus its A–Z rail. The rail overlays the list rather than taking a
 * column of its own, exactly as the design draws it; it is hidden while a
 * search is running, because an index over five results indexes nothing.
 */
@Composable
private fun PickerList(
    target: LanguageRole,
    sections: PickerSections,
    query: String,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val visibleRows = if (sections.searching) sections.results else sections.all
    val railed = !sections.searching && !sections.catalogEmpty
    // Every "is this section here?" answer, and with them the index the A–Z
    // rail scrolls into, comes from ONE pure function — so the arithmetic that
    // has to agree with the emission order below is readable by a unit test
    // rather than only by a screenshot.
    val plan =
        remember(target, sections.detect, sections.recent, sections.anyVoiceMark, railed) {
            pickerListPlan(
                role = target,
                detectRowPresent = sections.detect != null,
                recentCount = sections.recent.size,
                anyVoiceMark = sections.anyVoiceMark,
                railed = railed,
            )
        }
    val letters =
        remember(visibleRows, plan.railOffset, railed) {
            if (railed) {
                visibleRows.letterIndex(plan.railOffset).toList().sortedBy { it.second }
            } else {
                emptyList()
            }
        }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
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
            // Emission order IS the order pickerListPlan counts in.
            sections.detect?.let { detect ->
                item(key = "detect_${detect.id}", contentType = CONTENT_TYPE_ROW) {
                    LanguageRow(detect, target, onSelect, onDownload, onStop)
                }
            }
            if (plan.showVoiceLegend) {
                item(key = "voice_legend") { VoiceLegend() }
            }
            if (sections.catalogEmpty) {
                item(key = "catalog_loading") { CatalogLoading() }
            }
            if (sections.nothingFound) {
                item(key = "empty_result") { NoSearchResults(query = query, onShowAll = onClearQuery) }
            }
            plan.recentHeader?.let { header ->
                item(key = "header_recent", contentType = CONTENT_TYPE_HEADER) {
                    SectionHeader(recentHeaderRes(header))
                }
                pickerRows(sections.recent, "rec", target, onSelect, onDownload, onStop)
            }
            // No "All languages" banner over a filtered list: the results ARE
            // the list, and a counter beside them would count the catalog, not
            // them.
            if (plan.showAllHeader) {
                item(key = "header_all", contentType = CONTENT_TYPE_HEADER) {
                    SectionHeader(R.string.text_lang_all_header, sections.counts)
                }
            }
            pickerRows(visibleRows, "all", target, onSelect, onDownload, onStop)
        }
        if (letters.isNotEmpty()) {
            AlphabetRail(
                letters = letters,
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = spacing.lg24),
            )
        }
    }
}

/**
 * The same language legitimately appears under Recent AND under All languages
 * (GT does the same), so [prefix] is what keeps LazyColumn's keys unique —
 * duplicate keys are a hard crash, not a warning.
 */
private fun LazyListScope.pickerRows(
    rows: List<LanguagePickerRow>,
    prefix: String,
    target: LanguageRole,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) = items(rows, key = { "${prefix}_${it.id}" }, contentType = { CONTENT_TYPE_ROW }) { row ->
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

/** 64dp bar, left-aligned 22sp title, 48dp back target, no trailing action (design §2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("tt_lang_back")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_lang_back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
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
                .heightIn(
                    // The mark lives on the supporting line, so a row that has
                    // only the mark is still the TALL row: the "voice, no pack"
                    // case (17a's downloading Arabic) has no supporting words
                    // and must not lose its mark to a 56dp single-line box.
                    min =
                        if (supporting == null && !voiceMark) {
                            Dimensions.pickerRowHeight
                        } else {
                            Dimensions.pickerRowHeightTall
                        },
                ).clip(TranzlateShapeFull)
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
        is LanguageRowState.Failed -> stringResource(failureCauseRes(state.cause))
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

@StringRes
private fun failureCauseRes(cause: OfflineModelFailure): Int =
    when (cause) {
        OfflineModelFailure.STORAGE -> R.string.text_lang_error_storage
        OfflineModelFailure.NETWORK, OfflineModelFailure.WIFI_REQUIRED -> R.string.text_lang_error_network
        OfflineModelFailure.UNKNOWN -> R.string.text_lang_error_generic
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
            IconButton(
                onClick = { onDownload(row.id) },
                modifier = Modifier.testTag("tt_lang_retry_${row.id}"),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_text_lang_retry, row.displayName),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.pickerStateIcon),
                )
            }
        }
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
 */
@Composable
private fun VoiceLegend() {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
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
    listState: LazyListState,
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
            RailColumn(letters = shown, listState = listState)
        }
    }
}

@Composable
private fun RailColumn(
    letters: List<Pair<Char, Int>>,
    listState: LazyListState,
) {
    val scope = rememberCoroutineScope()
    val active: State<Char?> =
        remember(letters, listState) {
            derivedStateOf {
                val first = listState.firstVisibleItemIndex
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
                                    scope.launch { listState.scrollToItem(letters[slot].second) }
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

/**
 * Issue #90's consent ruling, re-honoured: the picker grew download buttons in
 * this redesign, so it asks the same question the offline manager asks before
 * spending someone's data plan.
 */
@Composable
private fun MeteredConsentDialog(
    pendingId: String?,
    rows: List<LanguagePickerRow>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (pendingId == null) return
    val name = rows.firstOrNull { it.id == pendingId }?.displayName ?: pendingId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_lang_data_dialog_title, name)) },
        text = { Text(stringResource(R.string.text_lang_data_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("tt_lang_data_once")) {
                Text(stringResource(R.string.text_lang_data_dialog_once))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("tt_lang_data_wait")) {
                Text(stringResource(R.string.text_lang_data_dialog_wait))
            }
        },
        modifier = Modifier.testTag("tt_lang_data_dialog"),
    )
}

// ---- previews (CLAUDE.md rule 7 — one per meaningful STATE) ------------------

/**
 * The 16a cast, as the export draws it: Spanish selected, on device, with a
 * voice; English on device with a voice; Afrikaans on device with NO voice —
 * the row that proves a pack does not imply a voice. Arabic carries a voice
 * while its pack is still downloading, which is the other half of that
 * independence and the case 17a's landscape "to" frame draws.
 */
private val previewLanguages =
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
private val previewRecents = mapOf("es" to 3L, "en" to 2L, "af" to 1L)

private val previewStates =
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
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerConsentDialogPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            pendingConsent = "sq",
        )
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
                    listState = rememberLazyListState(),
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
