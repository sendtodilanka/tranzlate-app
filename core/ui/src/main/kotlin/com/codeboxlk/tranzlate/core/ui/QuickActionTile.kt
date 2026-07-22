package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.Elevation
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.1 compact quick-action tile (fixed [Dimensions.quickTileHeight]):
 * leading rounded icon chip (soft tonal `primaryContainer` tint — accent
 * discipline: not the saturated element) + title + optional sub-label, on a
 * floating-surface card. Sized for a wrapping FlowRow/grid that must look
 * intentional from 2 up to 6 tiles — give each tile a width `weight` from the
 * caller.
 *
 * @param contentDescription optional override; when null TalkBack reads the
 *   merged title + sub-label text.
 */
@Composable
fun QuickActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    testTag: String = "tt_core_quick_tile",
) {
    val spacing = LocalSpacing.current
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = LocalFloatingSurface.current,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = Alpha.DISABLED),
        shadowElevation = Elevation.level1,
        modifier =
            modifier
                .height(Dimensions.quickTileHeight)
                .testTag(testTag)
                .semantics {
                    role = Role.Button
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(horizontal = spacing.md16),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(Dimensions.iconChip)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimensions.iconMd),
                )
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun QuickActionTilePreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(spacing.md16),
            ) {
                QuickActionTile(
                    title = "Conversation",
                    icon = Icons.Filled.Forum,
                    onClick = {},
                    subLabel = "Two-way talk",
                    modifier = Modifier.weight(1f),
                )
                QuickActionTile(
                    title = "Camera",
                    icon = Icons.Filled.PhotoCamera,
                    onClick = {},
                    subLabel = "Point and translate",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
