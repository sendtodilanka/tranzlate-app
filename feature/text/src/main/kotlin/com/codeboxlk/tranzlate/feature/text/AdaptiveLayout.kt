package com.codeboxlk.tranzlate.feature.text

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * The one window-shape read both text screens branch on (issue #56 — the
 * owner-approved adaptive frames). Derived per composition from
 * [currentWindowAdaptiveInfo]; no state is held.
 *
 * | shape | Home | 5a |
 * |---|---|---|
 * | compact width (phone portrait) | shipped card stack | face-switching composer |
 * | medium width (tablet portrait) | same, content max-width centred | same, card max-width centred |
 * | expanded width + compact height (phone landscape) | two-pane | edit full-width; RESULT face splits |
 * | expanded width + taller (tablet / unfolded) | two-pane | permanent input↔result panes |
 * | separating vertical hinge (book posture) | 50/50, gutter at the hinge | 50/50, gutter at the hinge |
 */
internal data class AdaptiveLayout(
    val expandedWidth: Boolean,
    val mediumWidth: Boolean,
    val compactHeight: Boolean,
    val hinged: Boolean,
) {
    /** Tablet / unfolded foldable: both 5a panes live at once. */
    val permanentTwoPane: Boolean get() = expandedWidth && !compactHeight

    /** Phone landscape: only 5a's read face splits (the IME owns the height while editing). */
    val splitResultOnly: Boolean get() = expandedWidth && compactHeight
}

@Composable
internal fun rememberAdaptiveLayout(): AdaptiveLayout {
    val info = currentWindowAdaptiveInfo()
    val sizeClass = info.windowSizeClass
    return AdaptiveLayout(
        expandedWidth = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND),
        mediumWidth = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
        compactHeight = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        hinged = info.windowPosture.separatingVerticalHingeBounds.isNotEmpty(),
    )
}
