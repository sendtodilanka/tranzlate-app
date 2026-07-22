package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.Elevation
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.2 — THE signature component. Always a card (owner decision A,
 * 2026-07-22): floating-surface rounded card with a multi-line text area on
 * top and the full-width control row beneath —
 * `[source ▾] ⇄ [target ▾] (action)`.
 *
 * - **Primary action** = the ONE saturated `primary` element on the screen:
 *   mic while [value] is empty, Translate (➜) as soon as there is text (C-2:
 *   translation fires only on this explicit action).
 * - **Growth**: the card grows with content; the CALLER caps it by passing
 *   `Modifier.heightIn(max = …)` (e.g. ~40% of the viewport) — beyond the cap
 *   the text area scrolls internally. Unconstrained (e.g. in previews) it
 *   simply wraps.
 * - **No IME logic inside** — the caller applies `imePadding()` and keeps the
 *   card directly above the keyboard.
 * - Char counter ([counterText], C-5 `"12/500"`) sits at the text-area corner
 *   with a polite live region (a11y contract §2.3).
 */
@Composable
@Suppress("LongMethod") // one cohesive UI_SPEC §2.2 layout; splitting hides the structure
fun ComposerCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    sourceLabel: String,
    targetLabel: String,
    sourceContentDescription: String,
    targetContentDescription: String,
    swapContentDescription: String,
    micContentDescription: String,
    translateContentDescription: String,
    inputContentDescription: String,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit,
    onMic: () -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    translateEnabled: Boolean = true,
    swapEnabled: Boolean = true,
    counterText: String? = null,
    counterContentDescription: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    inputTestTag: String = "tt_core_composer_input",
    counterTestTag: String = "tt_core_composer_counter",
    sourceTestTag: String = "tt_core_composer_source",
    targetTestTag: String = "tt_core_composer_target",
    swapTestTag: String = "tt_core_composer_swap",
    actionTestTag: String = "tt_core_composer_action",
) {
    val spacing = LocalSpacing.current
    val scrollState = rememberScrollState()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = LocalFloatingSurface.current,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = Elevation.level2,
        modifier = modifier,
    ) {
        BoxWithConstraints {
            // With a caller cap (Modifier.heightIn(max = …)) the text area takes
            // the leftover share and scrolls internally. Unbounded (previews,
            // measurement passes) it must NOT use weight/scroll: weighted
            // children collapse to zero and verticalScroll is disallowed under
            // infinite height constraints.
            val bounded = constraints.hasBoundedHeight
            Column(modifier = Modifier.padding(spacing.md16)) {
                val textAreaModifier =
                    if (bounded) {
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(textAreaModifier),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle =
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimensions.composerInputMinHeight)
                                .testTag(inputTestTag)
                                .semantics { contentDescription = inputContentDescription },
                    )
                }
                if (counterText != null) {
                    Text(
                        text = counterText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .align(Alignment.End)
                                .testTag(counterTestTag)
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    if (counterContentDescription != null) {
                                        contentDescription = counterContentDescription
                                    }
                                },
                    )
                }
                Spacer(modifier = Modifier.height(spacing.sm8))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs4),
                ) {
                    LanguageChip(
                        label = sourceLabel,
                        onClick = onSourceClick,
                        contentDescription = sourceContentDescription,
                        enabled = enabled,
                        testTag = sourceTestTag,
                        modifier = Modifier.weight(1f),
                    )
                    SwapButton(
                        onClick = onSwap,
                        contentDescription = swapContentDescription,
                        enabled = enabled && swapEnabled,
                        testTag = swapTestTag,
                    )
                    LanguageChip(
                        label = targetLabel,
                        onClick = onTargetClick,
                        contentDescription = targetContentDescription,
                        enabled = enabled,
                        testTag = targetTestTag,
                        modifier = Modifier.weight(1f),
                    )
                    val showMic = value.isEmpty()
                    FilledIconButton(
                        onClick = if (showMic) onMic else onTranslate,
                        enabled = enabled && (showMic || translateEnabled),
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier =
                            Modifier
                                .size(Dimensions.touchTargetMin)
                                .testTag(actionTestTag),
                    ) {
                        if (showMic) {
                            Icon(Icons.Filled.Mic, contentDescription = micContentDescription)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = translateContentDescription,
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
private fun ComposerCardEmptyPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        AmbientBackground {
            ComposerCard(
                value = "",
                onValueChange = {},
                placeholder = "Enter text",
                sourceLabel = "English",
                targetLabel = "සිංහල",
                sourceContentDescription = "Source language, English",
                targetContentDescription = "Target language, Sinhala",
                swapContentDescription = "Swap source and target languages",
                micContentDescription = "Translate by voice",
                inputContentDescription = "Text to translate",
                translateContentDescription = "Translate",
                onSourceClick = {},
                onTargetClick = {},
                onSwap = {},
                onMic = {},
                onTranslate = {},
                modifier = Modifier.padding(spacing.md16),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ComposerCardFilledPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        AmbientBackground {
            ComposerCard(
                value = "Good morning",
                onValueChange = {},
                placeholder = "Enter text",
                sourceLabel = "English",
                targetLabel = "French",
                sourceContentDescription = "Source language, English",
                targetContentDescription = "Target language, French",
                swapContentDescription = "Swap source and target languages",
                micContentDescription = "Translate by voice",
                inputContentDescription = "Text to translate",
                translateContentDescription = "Translate",
                onSourceClick = {},
                onTargetClick = {},
                onSwap = {},
                onMic = {},
                onTranslate = {},
                counterText = "12/500",
                counterContentDescription = "12 of 500 characters used",
                modifier = Modifier.padding(spacing.md16),
            )
        }
    }
}
