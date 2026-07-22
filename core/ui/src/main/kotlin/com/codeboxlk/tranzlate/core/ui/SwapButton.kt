package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.2 swap (⇄) between the two [LanguageChip]s. 48dp target;
 * mirrored in RTL layouts (a11y contract §2.6 — swap/reverse icons must
 * mirror; SwapHoriz has no AutoMirrored variant, so we flip explicitly).
 *
 * Disable (with [Alpha.DISABLED][com.codeboxlk.tranzlate.core.designsystem.Alpha.DISABLED]
 * handled by the button colors) when the source is Auto-detect (DESIGN_SYSTEM
 * alpha token note).
 */
@Composable
fun SwapButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tt_core_swap",
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier =
            modifier
                .size(Dimensions.touchTargetMin)
                .testTag(testTag),
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f },
        )
    }
}

@PreviewLightDark
@Composable
private fun SwapButtonPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            SwapButton(
                onClick = {},
                contentDescription = "Swap source and target languages",
            )
        }
    }
}
