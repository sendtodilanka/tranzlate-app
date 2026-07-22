package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language

/**
 * MINIMAL language picker (issue #11 interim — the full searchable
 * Recent/All/Detected picker is its own feature vertical, plan §6 non-goals).
 * A plain M3 modal sheet listing the [LanguageRepository] catalog; a tap writes
 * the pref and dismisses. Search deliberately omitted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    target: LanguagePickerTarget,
    languages: List<Language>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("tt_text_lang_sheet"),
    ) {
        LanguagePickerList(
            title =
                when (target) {
                    LanguagePickerTarget.SOURCE -> stringResource(R.string.text_lang_sheet_source_title)
                    LanguagePickerTarget.TARGET -> stringResource(R.string.text_lang_sheet_target_title)
                },
            languages = languages,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

/** Sheet body — split out so the list previews without sheet chrome. */
@Composable
fun LanguagePickerList(
    title: String,
    languages: List<Language>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = spacing.lg24)) {
            items(languages, key = Language::id) { language ->
                val selected = language.id == selectedId
                Surface(
                    onClick = { onSelect(language.id) },
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("tt_text_lang_item_${language.id}")
                            .semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .heightIn(min = Dimensions.touchTargetMin)
                                .padding(horizontal = spacing.md16),
                    ) {
                        Text(
                            text = languageDisplayName(language.id),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null, // state carried by semantics.selected
                                modifier = Modifier.size(Dimensions.iconSm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LanguagePickerListPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LanguagePickerList(
                title = "Translate to",
                languages =
                    listOf(
                        Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                        Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                        Language("ta", "Tamil", offlineAvailable = true, offlineDownloaded = false),
                    ),
                selectedId = "fr",
                onSelect = {},
            )
        }
    }
}
