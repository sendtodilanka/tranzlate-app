package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import kotlinx.coroutines.launch

/**
 * Full-screen language picker (issue #15 — replaces the interim bottom sheet;
 * GT keeps its picker on its own screen, and a sheet cannot hold ~100 languages
 * plus search comfortably).
 *
 * DI shell over [LanguagePickerContent]: reads the catalog from the
 * [TextViewModel] (which asks `LanguageRepository`), writes the chosen id to the
 * side that opened it, then pops.
 */
@Composable
fun LanguagePickerScreen(
    viewModel: TextViewModel,
    target: LanguagePickerTarget,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    LanguagePickerContent(
        target = target,
        languages = languages,
        selectedId = if (target == LanguagePickerTarget.SOURCE) sourceLang else targetLang,
        onSelect = { id ->
            when (target) {
                LanguagePickerTarget.SOURCE -> viewModel.onSelectSourceLanguage(id)
                LanguagePickerTarget.TARGET -> viewModel.onSelectTargetLanguage(id)
            }
            onDone()
        },
        onBack = onDone,
        modifier = modifier,
    )
}

/**
 * Stateless picker layout: back + search top bar · "Detect language" row (source
 * side only — a target cannot be detected) · "All languages" section header in
 * `primary` · plain [ListItem] rows, no dividers.
 *
 * Search has no surface yet, so the action shows the guided message rather than
 * a dead affordance (EDGE_CASES no-dead-end); it becomes a real filter with the
 * search vertical.
 */
@OptIn(ExperimentalMaterial3Api::class) // CenterAlignedTopAppBar's colors overload
@Composable
fun LanguagePickerContent(
    target: LanguagePickerTarget,
    languages: List<Language>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val guidedSearch = stringResource(R.string.text_guided_lang_search)
    val title =
        when (target) {
            LanguagePickerTarget.SOURCE -> stringResource(R.string.text_lang_sheet_source_title)
            LanguagePickerTarget.TARGET -> stringResource(R.string.text_lang_sheet_target_title)
        }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tt_lang_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_text_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { snackbarHostState.showSnackbar(guidedSearch) } },
                        modifier = Modifier.testTag("tt_lang_search"),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.cd_text_lang_search),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = spacing.lg24),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("tt_lang_list"),
        ) {
            if (target == LanguagePickerTarget.SOURCE) {
                item(key = DETECT_LANGUAGE_ID) {
                    LanguageRow(
                        id = DETECT_LANGUAGE_ID,
                        label = stringResource(R.string.text_lang_detect),
                        selected = selectedId == DETECT_LANGUAGE_ID,
                        onSelect = onSelect,
                        leadingIcon = Icons.Filled.Translate,
                    )
                }
            }
            item(key = "header_all") {
                Text(
                    text = stringResource(R.string.text_lang_all_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8),
                )
            }
            items(languages.sortedBy { languageDisplayName(it.id) }, key = Language::id) { language ->
                LanguageRow(
                    id = language.id,
                    label = languageDisplayName(language.id),
                    selected = language.id == selectedId,
                    onSelect = onSelect,
                )
            }
        }
    }
}

/**
 * One picker row — stock [ListItem], transparent over the page, no divider.
 * The current choice is exposed as `selected` semantics AND a trailing check
 * (icon alone is not enough for TalkBack — a11y contract §2.1).
 */
@Composable
private fun LanguageRow(
    id: String,
    label: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
    leadingIcon: ImageVector? = null,
) {
    ListItem(
        headlineContent = { Text(text = label) },
        leadingContent =
            leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null) }
            },
        trailingContent =
            if (selected) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
            } else {
                null
            },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                trailingIconColor = MaterialTheme.colorScheme.primary,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton) { onSelect(id) }
                .testTag("tt_lang_row_$id")
                .semantics { this.selected = selected },
    )
}

private val previewLanguages =
    listOf(
        Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
        Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
        Language("si", "Sinhala", offlineAvailable = true, offlineDownloaded = false),
        Language("ta", "Tamil", offlineAvailable = true, offlineDownloaded = false),
    )

@PreviewLightDark
@Composable
private fun LanguagePickerSourcePreview() {
    TranzlateTheme {
        LanguagePickerContent(
            target = LanguagePickerTarget.SOURCE,
            languages = previewLanguages,
            selectedId = "en",
            onSelect = {},
            onBack = {},
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
            selectedId = "fr",
            onSelect = {},
            onBack = {},
        )
    }
}
