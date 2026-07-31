package com.codeboxlk.tranzlate.feature.text

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import kotlinx.coroutines.launch

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
 * DI shell over [LanguagePickerContent]. Two state holders on purpose:
 * [TextViewModel] owns the CHOICE this screen was opened to change, while
 * [LanguagePickerViewModel] owns what the picker needs to present that choice
 * honestly — catalog, live offline-model state, the last-used stamp and the
 * row-level download controls. Search state is held here so the content stays
 * stateless and previewable.
 */
@Composable
fun LanguagePickerScreen(
    viewModel: TextViewModel,
    target: LanguagePickerTarget,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    pickerViewModel: LanguagePickerViewModel = hiltViewModel(),
) {
    val languages by pickerViewModel.languages.collectAsStateWithLifecycle()
    val offlineStates by pickerViewModel.offlineStates.collectAsStateWithLifecycle()
    val pendingConsent by pickerViewModel.pendingConsent.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LanguagePickerContent(
        target = target,
        languages = languages,
        selectedId = if (target == LanguagePickerTarget.SOURCE) sourceLang else targetLang,
        query = query,
        onQueryChange = { query = it },
        onSelect = { id ->
            pickerViewModel.onLanguagePicked(id)
            when (target) {
                LanguagePickerTarget.SOURCE -> viewModel.onSelectSourceLanguage(id)
                LanguagePickerTarget.TARGET -> viewModel.onSelectTargetLanguage(id)
            }
            onDone()
        },
        onBack = onDone,
        modifier = modifier,
        offlineStates = offlineStates,
        onDownload = pickerViewModel::download,
        onStop = pickerViewModel::stopAndRemove,
        pendingConsent = pendingConsent,
        onDownloadAnyway = pickerViewModel::downloadAnyway,
        onDismissConsent = pickerViewModel::dismissConsent,
    )
}

/**
 * Stateless picker layout: back + title · permanent search field · "Detect
 * language" (source side only) · Recent · All languages + on-device counter,
 * with an A–Z rail down the right edge.
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
    target: LanguagePickerTarget,
    languages: List<Language>,
    selectedId: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            LanguagePickerTarget.SOURCE -> stringResource(R.string.text_lang_sheet_source_title)
            LanguagePickerTarget.TARGET -> stringResource(R.string.text_lang_sheet_target_title)
        }
    val sections =
        rememberPickerSections(
            target = target,
            languages = languages,
            offlineStates = offlineStates,
            selectedId = selectedId,
            query = query,
            sizes = sizes,
            detectLabel = stringResource(R.string.text_lang_detect),
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
    target: LanguagePickerTarget,
    languages: List<Language>,
    offlineStates: Map<String, OfflineModelState>,
    selectedId: String,
    query: String,
    sizes: Map<String, Long>,
    detectLabel: String,
): PickerSections {
    val locale = LocalLocale.current.platformLocale
    val rows =
        remember(languages, offlineStates, selectedId, sizes, locale) {
            buildPickerRows(languages, offlineStates, selectedId, locale, sizes)
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
                .takeIf { target == LanguagePickerTarget.SOURCE }
                ?.takeIf { normalizedQuery.isEmpty() || it.searchKey.contains(normalizedQuery) }
        }
    val counts = remember(languages) { onDeviceCount(languages) }
    return PickerSections(
        all = rows,
        results = results,
        recent = recent,
        detect = detect,
        counts = counts,
        searching = normalizedQuery.isNotEmpty(),
        catalogEmpty = languages.isEmpty(),
    )
}

/**
 * The list plus its A–Z rail. The rail overlays the list rather than taking a
 * column of its own, exactly as the design draws it; it is hidden while a
 * search is running, because an index over five results indexes nothing.
 */
@Composable
private fun PickerList(
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
    // Where the alphabetical block starts, so a rail letter scrolls to the row
    // and not to whatever happens to sit that far down. Counted from the items
    // actually emitted below — detect row, Recent header + its rows, and the
    // "All languages" header.
    val railed = !sections.searching && !sections.catalogEmpty
    val detectCount = if (sections.detect != null) 1 else 0
    val recentCount = if (sections.recent.isEmpty()) 0 else sections.recent.size + 1
    val allOffset = detectCount + recentCount + if (railed) 1 else 0
    val letters =
        remember(visibleRows, allOffset, railed) {
            if (railed) {
                visibleRows.letterIndex(allOffset).toList().sortedBy { it.second }
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
                    end = spacing.sm8,
                    bottom = spacing.sm8,
                ),
            modifier = Modifier.fillMaxSize().testTag("tt_lang_list"),
        ) {
            sections.detect?.let { detect ->
                item(key = "detect_${detect.id}", contentType = CONTENT_TYPE_ROW) {
                    LanguageRow(detect, onSelect, onDownload, onStop)
                }
            }
            if (sections.catalogEmpty) {
                item(key = "catalog_loading") { CatalogLoading() }
            }
            if (sections.nothingFound) {
                item(key = "empty_result") { NoSearchResults(query = query, onShowAll = onClearQuery) }
            }
            if (sections.recent.isNotEmpty()) {
                item(key = "header_recent", contentType = CONTENT_TYPE_HEADER) {
                    SectionHeader(R.string.text_lang_recent_header)
                }
                pickerRows(sections.recent, "rec", onSelect, onDownload, onStop)
            }
            // No "All languages" banner over a filtered list: the results ARE
            // the list, and a counter beside them would count the catalog, not
            // them.
            if (railed) {
                item(key = "header_all", contentType = CONTENT_TYPE_HEADER) {
                    SectionHeader(R.string.text_lang_all_header, sections.counts)
                }
            }
            pickerRows(visibleRows, "all", onSelect, onDownload, onStop)
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
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) = items(rows, key = { "${prefix}_${it.id}" }, contentType = { CONTENT_TYPE_ROW }) { row ->
    LanguageRow(row, onSelect, onDownload, onStop)
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
                    contentDescription = stringResource(R.string.cd_text_back),
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
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val selected = row.state is LanguageRowState.Selected
    val supporting = rowSupportingText(row.state)
    val description = rowContentDescription(row)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = if (supporting == null) Dimensions.pickerRowHeight else Dimensions.pickerRowHeightTall,
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
            RowSupportingLine(state = row.state, text = supporting)
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

/** The supporting line's WORDS, or null when the state has nothing to add. */
@Composable
private fun rowSupportingText(state: LanguageRowState): String? {
    val context = LocalContext.current
    return when (state) {
        is LanguageRowState.Selected -> if (state.onDevice) onDeviceLine(context, state.sizeBytes) else null
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
) {
    if (text == null) return
    val spacing = LocalSpacing.current
    val color =
        when (state) {
            is LanguageRowState.Selected -> MaterialTheme.colorScheme.onPrimaryContainer
            is LanguageRowState.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    if (state == LanguageRowState.Downloading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(top = spacing.xs4),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.weight(1f).testTag("tt_lang_progress"),
            )
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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

/** The name AND its state — the old app announced `content-desc=""` on every node. */
@Composable
private fun rowContentDescription(row: LanguagePickerRow): String {
    val name = row.displayName
    return when (row.state) {
        // `selected` semantics already announce the choice; the line adds the fact.
        is LanguageRowState.Selected -> {
            if (row.state.onDevice) stringResource(R.string.cd_text_lang_row_on_device, name) else name
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
            modifier
                .width(Dimensions.touchTargetMin)
                .fillMaxHeight()
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

private val previewLanguages =
    listOf(
        Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = true, lastUsedAt = 3L),
        Language("sq", "Albanian", offlineAvailable = true, offlineDownloaded = false),
        Language("am", "Amharic", offlineAvailable = true, offlineDownloaded = false),
        Language("ar", "Arabic", offlineAvailable = true, offlineDownloaded = false),
        Language("hy", "Armenian", offlineAvailable = false, offlineDownloaded = false),
        Language("en", "English", offlineAvailable = true, offlineDownloaded = true, lastUsedAt = 2L),
        Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = true, lastUsedAt = 1L),
        Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
    )

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
            target = LanguagePickerTarget.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerTargetPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguagePickerTarget.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerSearchingPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguagePickerTarget.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "a",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            offlineStates = previewStates,
        )
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerNoResultsPreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguagePickerTarget.TARGET,
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
            target = LanguagePickerTarget.TARGET,
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
            target = LanguagePickerTarget.TARGET,
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
private fun RowPreviewSurface(state: LanguageRowState) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LanguageRow(
                row =
                    LanguagePickerRow(
                        id = "es",
                        displayName = "Spanish",
                        avatar = LanguageAvatar.Code("ES"),
                        state = state,
                    ),
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
    RowPreviewSurface(LanguageRowState.Selected(onDevice = true))
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

@PreviewLightDark
@Composable
private fun LanguageRowDetectPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                LanguageRow(
                    row = detectRow("Detect language", selected = false),
                    onSelect = {},
                    onDownload = {},
                    onStop = {},
                )
                LanguageRow(
                    row = detectRow("Detect language", selected = true),
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
