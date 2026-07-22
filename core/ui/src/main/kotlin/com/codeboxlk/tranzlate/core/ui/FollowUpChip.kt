package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * One follow-up suggestion. [icon] (e.g. the ✦ sparkle for AI actions) renders
 * in `primary` tint — an icon/label accent use, not the saturated element.
 */
@Immutable
data class FollowUpChip(
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * UI_SPEC §2.4 follow-up chips under the result: `✦ Formal · Explain ·
 * Examples` (the AI-enhancement slot, issue #8). Assist-chip styling
 * (DESIGN_SYSTEM §9: flat, `outlineVariant` border, pill), horizontally
 * scrollable when they overflow.
 */
@Composable
fun FollowUpChipRow(
    chips: List<FollowUpChip>,
    onChipClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tt_core_followup_row",
) {
    val spacing = LocalSpacing.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .testTag(testTag),
    ) {
        chips.forEachIndexed { index, chip ->
            AssistChip(
                onClick = { onChipClick(index) },
                enabled = enabled,
                shape = TranzlateShapeFull,
                label = { Text(text = chip.label) },
                leadingIcon =
                    chip.icon?.let { chipIcon ->
                        {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.iconSm),
                            )
                        }
                    },
                border =
                    AssistChipDefaults.assistChipBorder(
                        enabled = enabled,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FollowUpChipRowPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FollowUpChipRow(
                chips =
                    listOf(
                        FollowUpChip(label = "Formal", icon = Icons.Filled.AutoAwesome),
                        FollowUpChip(label = "Explain"),
                        FollowUpChip(label = "Examples"),
                    ),
                onChipClick = {},
                modifier = Modifier.padding(LocalSpacing.current.md16),
            )
        }
    }
}
