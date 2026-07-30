package com.codeboxlk.tranzlate.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings

/**
 * Whether Material You can actually apply — dynamic colour schemes exist only on
 * API 31+. A plain top-level val so previews and tests read the same thing the
 * screen does; the theme itself already guards on this, so a stored `true` on an
 * older device is simply ignored.
 */
private val dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** UI_SPEC §2.6 Settings — DI shell over [SettingsContent]. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        modifier = modifier,
    )
}

/**
 * Stateless Appearance screen (previewable without DI). `settings == null` is the
 * not-read-yet state — the section renders once the stored value arrives, so a
 * selected row never flips a frame after appearing.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsContent(
    settings: ThemeSettings?,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tt_settings_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_settings_back),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        if (settings == null) return@Scaffold
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        // Issue #86: the same centred cap every screen shares on
                        // medium-width portrait (tablets/foldables).
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = spacing.md16),
            ) {
                SectionHeader(stringResource(R.string.settings_appearance_header))

                SubGroupLabel(stringResource(R.string.settings_theme_label))
                ThemeModeGroup(selected = settings.mode, onSelect = onThemeModeSelected)

                DynamicColorRow(enabled = settings.dynamicColor, onToggle = onDynamicColorChanged)

                // Issue #80 (owner): History lives HERE — the design has no drawer,
                // and Offline languages already has its Home entries.
                SectionHeader(stringResource(R.string.settings_data_header))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimensions.touchTargetMin)
                            .clickable(
                                role = Role.Button,
                                onClick = onOpenHistory,
                            ).padding(horizontal = spacing.lg24)
                            .testTag("tt_settings_history"),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_history_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.settings_history_supporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8),
    )
}

@Composable
private fun SubGroupLabel(text: String) {
    val spacing = LocalSpacing.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.xs4),
    )
}

/** UI_SPEC §2.6 theme picker — stock radio rows in a single-choice group. */
@Composable
private fun ThemeModeGroup(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    // The three options with their label + testTag, in one place so the row
    // builder stays a plain loop and nothing drifts per option.
    val options =
        listOf(
            Triple(ThemeMode.SYSTEM, R.string.settings_theme_system, "tt_settings_theme_system"),
            Triple(ThemeMode.LIGHT, R.string.settings_theme_light, "tt_settings_theme_light"),
            Triple(ThemeMode.DARK, R.string.settings_theme_dark, "tt_settings_theme_dark"),
        )
    Column(Modifier.selectableGroup()) {
        options.forEach { (mode, labelRes, tag) ->
            ThemeModeRow(
                label = stringResource(labelRes),
                selected = mode == selected,
                onClick = { onSelect(mode) },
                testTag = tag,
            )
        }
    }
}

@Composable
private fun ThemeModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                // selectable owns the click + role + selected state atomically; the
                // RadioButton below is visual only (onClick = null).
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .heightIn(min = Dimensions.touchTargetMin + spacing.sm8)
                .testTag(testTag)
                .padding(horizontal = spacing.md16, vertical = spacing.sm8),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = spacing.md16),
        )
    }
}

/** UI_SPEC §2.6 dynamic-colour toggle. Disabled with a reason on API < 31 (no dead end). */
@Composable
private fun DynamicColorRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    val supporting =
        if (dynamicColorAvailable) {
            stringResource(R.string.settings_dynamic_color_supporting)
        } else {
            stringResource(R.string.settings_dynamic_color_unavailable)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                // The row owns the toggle: one toggleable node with the state, so
                // TalkBack announces the row once. On API < 31 it is disabled and
                // the supporting line says why (no dead control).
                .toggleable(
                    value = enabled && dynamicColorAvailable,
                    enabled = dynamicColorAvailable,
                    role = Role.Switch,
                    onValueChange = onToggle,
                ).heightIn(min = Dimensions.touchTargetMin + spacing.md16)
                .testTag("tt_settings_dynamic_color")
                .padding(horizontal = spacing.md16, vertical = spacing.sm8),
    ) {
        Column(Modifier.padding(end = spacing.md16)) {
            Text(
                text = stringResource(R.string.settings_dynamic_color_label),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (dynamicColorAvailable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled && dynamicColorAvailable,
            enabled = dynamicColorAvailable,
            onCheckedChange = null,
            // Visual only — the row carries the semantics, so clear the Switch's.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsSystemPreview() {
    TranzlateTheme {
        SettingsContent(
            settings = ThemeSettings(mode = ThemeMode.SYSTEM, dynamicColor = false),
            onBack = {},
            onThemeModeSelected = {},
            onDynamicColorChanged = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsDarkChosenPreview() {
    TranzlateTheme(darkTheme = true) {
        SettingsContent(
            settings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = true),
            onBack = {},
            onThemeModeSelected = {},
            onDynamicColorChanged = {},
        )
    }
}
