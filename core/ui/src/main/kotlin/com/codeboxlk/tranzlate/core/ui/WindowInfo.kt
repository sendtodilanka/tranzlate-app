package com.codeboxlk.tranzlate.core.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass

/**
 * C-13 wrap: window size classes consumed ONLY through this — never hardcode dp
 * breakpoints in layout code (DESIGN_SYSTEM adaptive section).
 *
 * Compact < 600dp → bottom NavigationBar · Medium 600–840 → NavigationRail ·
 * Expanded > 840 → permanent NavigationDrawer.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class WindowInfo(
    val widthClass: WindowWidthClass,
    /** Height below the medium bound (480dp) — phone landscape (issue #56). */
    val heightCompact: Boolean = false,
    /** A separating vertical hinge (book-posture foldable) splits the window. */
    val hinged: Boolean = false,
) {
    val isCompact: Boolean get() = widthClass == WindowWidthClass.COMPACT
    val isMedium: Boolean get() = widthClass == WindowWidthClass.MEDIUM
    val isExpanded: Boolean get() = widthClass == WindowWidthClass.EXPANDED
}

/** The one place layouts read adaptive breakpoints from (C-13). */
@Composable
fun rememberWindowInfo(): WindowInfo {
    val info = currentWindowAdaptiveInfo()
    val windowSizeClass = info.windowSizeClass
    val posture = info.windowPosture
    return remember(windowSizeClass, posture) {
        WindowInfo(
            widthClass = windowSizeClass.toWidthClass(),
            heightCompact =
                !windowSizeClass.isHeightAtLeastBreakpoint(
                    WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
                ),
            hinged = posture.separatingVerticalHingeBounds.isNotEmpty(),
        )
    }
}

private fun WindowSizeClass.toWidthClass(): WindowWidthClass =
    when {
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthClass.EXPANDED
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.COMPACT
    }
