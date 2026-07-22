package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * Home-canvas quick action, Google-Translate shaped: a tonal circle with its
 * label BENEATH (GT's Camera / Live-translate pair), not a card tile.
 *
 * Thin wrapper, not a new component: it is a stock [FilledTonalIconButton] plus
 * a caption. It exists because the pair has to be assembled identically at every
 * call site (circle size, label style, and the a11y merge below) and the canvas
 * has to scale from two actions to six.
 *
 * **Accessibility:** the button carries the whole accessible name and the caption
 * is cleared — otherwise TalkBack reads the same word twice, once as a button and
 * once as loose text.
 */
@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tt_core_quick_action",
) {
    val spacing = LocalSpacing.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .size(Dimensions.quickActionSize)
                    .testTag(testTag),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(Dimensions.iconMd),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@PreviewLightDark
@Composable
private fun QuickActionButtonPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.lg24),
                modifier = Modifier.padding(spacing.md16),
            ) {
                QuickActionButton(label = "Conversation", icon = Icons.Filled.Forum, onClick = {})
                QuickActionButton(label = "Camera", icon = Icons.Filled.PhotoCamera, onClick = {})
                QuickActionButton(
                    label = "Camera",
                    icon = Icons.Filled.PhotoCamera,
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}
