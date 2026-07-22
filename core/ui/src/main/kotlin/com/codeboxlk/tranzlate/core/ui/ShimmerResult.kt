package com.codeboxlk.tranzlate.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.Motion
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/** Line width fractions — reads like two lines of text + a shorter caption. */
private const val LINE_FULL = 1f
private const val LINE_MID = 0.72f
private const val LINE_SHORT = 0.45f

/**
 * UI_SPEC §2.5 translating state: shimmer placeholder shaped like a result
 * block (two text lines + a caption line), swept by an animated highlight
 * ([Motion.SHIMMER_CYCLE], linear — a continuous loop, not a one-shot).
 *
 * Kept custom on purpose: Material 3 ships no skeleton/placeholder component.
 *
 * Purely decorative: announcements come from the feature's own loading live
 * region (`tt_text_loading`, a11y contract §2.3) — this draws no semantics of
 * its own beyond the [testTag].
 */
@Composable
fun ShimmerResult(
    modifier: Modifier = Modifier,
    testTag: String = "tt_core_shimmer",
) {
    val spacing = LocalSpacing.current
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.SHIMMER_BASE)
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.SHIMMER_HIGHLIGHT)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = Motion.SHIMMER_CYCLE, easing = LinearEasing),
            ),
        label = "shimmerProgress",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier = modifier.testTag(testTag),
    ) {
        ShimmerLine(widthFraction = LINE_FULL, height = spacing.lg24, base = base, highlight = highlight) { progress }
        ShimmerLine(widthFraction = LINE_MID, height = spacing.lg24, base = base, highlight = highlight) { progress }
        ShimmerLine(widthFraction = LINE_SHORT, height = spacing.md16, base = base, highlight = highlight) { progress }
    }
}

@Composable
private fun ShimmerLine(
    widthFraction: Float,
    height: Dp,
    base: Color,
    highlight: Color,
    progress: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(MaterialTheme.shapes.small)
                .drawBehind {
                    // Highlight band sweeps once per cycle, fully off-screen at
                    // both ends so the loop has no visible jump.
                    val band = size.width
                    val start = progress() * (size.width + band) - band
                    drawRect(color = base)
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(base, highlight, base),
                                startX = start,
                                endX = start + band,
                            ),
                    )
                },
    )
}

@PreviewLightDark
@Composable
private fun ShimmerResultPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ShimmerResult(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(LocalSpacing.current.md16),
            )
        }
    }
}
