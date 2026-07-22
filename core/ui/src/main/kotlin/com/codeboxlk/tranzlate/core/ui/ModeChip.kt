package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.1 centred mode chip: `✦ Automatic ▾`. Tonal pill
 * (`surfaceContainerHigh`, UI_SPEC §3), sparkle glyph in `primary`
 * (icon/label accent use — §0), trailing dropdown affordance.
 *
 * @param label mode title, e.g. "Automatic" — a parameter, never hardcoded.
 * @param contentDescription full a11y phrase (contract §2.1 row 6, e.g.
 *   "Translation model, Automatic").
 * @param stateDescription current model title for TalkBack state (contract row 6).
 * @param trailingCounter optional slot for the metered counter ("15/20 today" —
 *   Advanced-AI variant, UI_SPEC §4).
 */
@Composable
fun ModeChip(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
    testTag: String = "tt_core_mode_chip",
    trailingCounter: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TranzlateShapeFull,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = Alpha.DISABLED),
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                    if (stateDescription != null) this.stateDescription = stateDescription
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs4),
            modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            trailingCounter?.invoke()
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.iconSm),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ModeChipPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.padding(spacing.md16),
            ) {
                ModeChip(
                    label = "Automatic",
                    onClick = {},
                    contentDescription = "Translation model, Automatic",
                    stateDescription = "Automatic",
                )
                ModeChip(
                    label = "Advanced AI",
                    onClick = {},
                    contentDescription = "Translation model, Advanced AI",
                    stateDescription = "Advanced AI",
                    trailingCounter = {
                        Text(
                            text = "15/20 today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
