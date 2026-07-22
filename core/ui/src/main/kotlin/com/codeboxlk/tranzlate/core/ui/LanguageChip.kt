package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.2 composer language selector: tonal pill (`surfaceContainerHigh`,
 * UI_SPEC §3) with the language label + a dropdown affordance. Stretches inside
 * the composer control row — give it `Modifier.weight(1f)` from the caller.
 * Min height = 48dp touch target.
 *
 * @param contentDescription full a11y phrase incl. the current language
 *   (contract §2.1 rows 4–5, e.g. "Source language, English").
 */
@Composable
fun LanguageChip(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tt_core_lang_chip",
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
                .heightIn(min = Dimensions.touchTargetMin)
                .testTag(testTag)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md16),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
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
private fun LanguageChipPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs4),
                modifier = Modifier.padding(spacing.md16),
            ) {
                LanguageChip(
                    label = "English",
                    onClick = {},
                    contentDescription = "Source language, English",
                    modifier = Modifier.weight(1f),
                )
                SwapButton(
                    onClick = {},
                    contentDescription = "Swap source and target languages",
                )
                LanguageChip(
                    label = "සිංහල",
                    onClick = {},
                    contentDescription = "Target language, Sinhala",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
