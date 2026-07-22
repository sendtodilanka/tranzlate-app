package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §1 signature icon button: circular tonal fill with a subtle
 * dotted-ring border detail. Full 48dp touch target ([Dimensions.touchTargetMin])
 * with a 40dp tonal circle and a 24dp glyph slot.
 *
 * The [icon] slot is decorative — pass `contentDescription = null` on the inner
 * [Icon]; TalkBack reads this button's [contentDescription] (a11y contract §2.1
 * — non-empty, from strings.xml at the call site).
 *
 * @param selected non-null makes this a TOGGLE: the state is exposed via
 *   semantics (a11y contract §2.1 — icon change alone is not enough) and the
 *   fill switches to `primaryContainer` when `true`.
 */
@Composable
fun DottedRingIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    testTag: String = "tt_core_ring_btn",
    icon: @Composable () -> Unit,
) {
    val isSelected = selected == true
    val container =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(Dimensions.touchTargetMin)
                .testTag(testTag)
                .semantics {
                    this.contentDescription = contentDescription
                    if (selected != null) this.selected = selected
                }.clickable(
                    interactionSource = null,
                    indication = ripple(bounded = false),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).drawBehind {
                    val strokePx = Dimensions.borderThin.toPx()
                    drawCircle(
                        color = ringColor,
                        radius = size.minDimension / 2f - strokePx,
                        style =
                            Stroke(
                                width = strokePx,
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(
                                            Dimensions.ringDash.toPx(),
                                            Dimensions.ringGap.toPx(),
                                        ),
                                    ),
                            ),
                    )
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(Dimensions.iconChip)
                    .clip(CircleShape)
                    .background(if (enabled) container else container.copy(alpha = Alpha.DISABLED)),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides
                    if (enabled) contentColor else contentColor.copy(alpha = Alpha.DISABLED),
                content = icon,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DottedRingIconButtonPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(spacing.md16),
        ) {
            DottedRingIconButton(onClick = {}, contentDescription = "Open navigation") {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
            DottedRingIconButton(onClick = {}, contentDescription = "Copy translation") {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
            }
            DottedRingIconButton(
                onClick = {},
                contentDescription = "Good translation",
                selected = true,
            ) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null)
            }
            DottedRingIconButton(
                onClick = {},
                contentDescription = "Speak translation",
                enabled = false,
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            }
        }
    }
}
