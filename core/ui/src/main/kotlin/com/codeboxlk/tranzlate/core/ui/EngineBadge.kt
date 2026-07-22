package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.4 engine badge: small tonal pill next to the target-block label,
 * e.g. `⏷ Offline · instant`. Non-interactive; TalkBack reads the [text]
 * (the leading glyph is decorative).
 */
@Composable
fun EngineBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "tt_core_engine_badge",
) {
    val spacing = LocalSpacing.current
    Surface(
        shape = TranzlateShapeFull,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs4),
            modifier = Modifier.padding(horizontal = spacing.sm8, vertical = spacing.xxs2),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconXs),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@PreviewLightDark
@Composable
private fun EngineBadgePreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.padding(spacing.md16),
            ) {
                EngineBadge(text = "Offline · instant", icon = Icons.Filled.OfflineBolt)
                EngineBadge(text = "Online")
            }
        }
    }
}
