package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * DESIGN_SYSTEM §2 brand gradient token — logo, hero accents, splash,
 * premium/paywall highlights and decorative strokes ONLY; never a background
 * behind body text (contrast rule §2: white body text over the raw gradient is
 * NOT allowed — use a solid surface/scrim panel, or ≥24sp display headings only).
 *
 * Angle 135° = Compose `linearGradient` default offsets (topLeft → bottomRight).
 */
@Immutable
data class GradientColors(
    /** Teal stop: #2B8FB0 (light) / #3FB6D4 (dark). */
    val start: Color,
    /** Optional mid-stop #D67A6E at 55% for softer blends on large surfaces. */
    val mid: Color,
    /** Coral stop: #F0725A (both themes). */
    val end: Color,
) {
    /** Standard two-stop brand gradient (135° via default offsets). */
    val brush: Brush
        get() = Brush.linearGradient(colors = listOf(start, end))

    /** Softer three-stop variant for large surfaces (mid at 55%). */
    val largeSurfaceBrush: Brush
        get() = Brush.linearGradient(0f to start, MID_STOP_FRACTION to mid, 1f to end)

    private companion object {
        const val MID_STOP_FRACTION = 0.55f
    }
}

val LightGradientColors =
    GradientColors(
        start = Color(0xFF2B8FB0),
        mid = Color(0xFFD67A6E),
        end = Color(0xFFF0725A),
    )

val DarkGradientColors =
    GradientColors(
        start = Color(0xFF3FB6D4),
        mid = Color(0xFFD67A6E),
        end = Color(0xFFF0725A),
    )

/** Provided by [TranzlateTheme] (§10 — non-M3 tokens ride CompositionLocals). */
val LocalGradientColors =
    staticCompositionLocalOf<GradientColors> {
        error("No GradientColors provided — wrap content in TranzlateTheme")
    }
