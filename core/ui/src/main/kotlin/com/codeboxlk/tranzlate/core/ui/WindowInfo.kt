package com.codeboxlk.tranzlate.core.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
) {
    val isCompact: Boolean get() = widthClass == WindowWidthClass.COMPACT
    val isMedium: Boolean get() = widthClass == WindowWidthClass.MEDIUM
    val isExpanded: Boolean get() = widthClass == WindowWidthClass.EXPANDED
}

/** The one place layouts read adaptive breakpoints from (C-13). */
@Composable
fun rememberWindowInfo(): WindowInfo {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return remember(windowSizeClass) {
        WindowInfo(widthClass = windowSizeClass.toWidthClass())
    }
}

private fun WindowSizeClass.toWidthClass(): WindowWidthClass = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthClass.EXPANDED
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.COMPACT
}
