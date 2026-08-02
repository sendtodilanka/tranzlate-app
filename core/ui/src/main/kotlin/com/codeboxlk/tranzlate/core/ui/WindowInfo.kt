package com.codeboxlk.tranzlate.core.ui

import androidx.compose.material3.adaptive.Posture
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

/**
 * How the device is being HELD — the half of adaptive layout that window width
 * cannot answer.
 *
 * The #130 rev.3 ruling (§2, "Adaptive") needs this because the two layouts it
 * has to tell apart land on the same width: an unfolded inner display at 760dp
 * and a tablet at 800dp are both [WindowWidthClass.MEDIUM], so width alone
 * cannot choose between the foldable two-leaf layout (17b) and the dialog host
 * (17c/17d). Posture is what is left, and deriving it once here is what stops
 * every screen re-deriving it from raw hinge lists.
 *
 * Derived from Material's [Posture], which is itself derived from Jetpack
 * WindowManager's `FoldingFeature` — verified against
 * `androidx.compose.material3.adaptive:adaptive:1.2.0`, `AndroidPosture.android.kt:29-51`:
 * `isTabletop` is set for a HORIZONTAL hinge in the HALF_OPENED state, and each
 * `HingeInfo.isFlat` is `state == FLAT`.
 */
enum class FoldPosture {
    /**
     * One flat surface: no fold at all, or a fold opened out flat. A dual-screen
     * device held fully open is FLAT and still [WindowInfo.hinged] — see there.
     */
    FLAT,

    /**
     * Half-open around a VERTICAL crease: two leaves side by side, like a book.
     * `FoldingFeature.State` has exactly two values in `androidx.window` 1.5.0
     * (`FoldingFeature.kt:82,91` — FLAT and HALF_OPENED), so "not flat" is
     * "half-open" today; if a third state is ever added this reads as
     * "not flat", which is still the right side of the branch for a two-leaf
     * layout.
     */
    BOOK,

    /**
     * Half-open around a HORIZONTAL crease: one leaf above, one below (tabletop
     * / laptop posture). The area over the crease is hard to touch, which is why
     * Material tracks it separately.
     */
    TABLETOP,
}

@Immutable
data class WindowInfo(
    val widthClass: WindowWidthClass,
    /** Height below the medium bound (480dp) — phone landscape (issue #56). */
    val heightCompact: Boolean = false,
    /**
     * A separating vertical hinge splits the window — where a crease gutter goes.
     *
     * Deliberately NOT the same question as [posture]. A dual-screen device held
     * fully open reports `FLAT` with a hinge that still separates the window into
     * two logical areas, so content must still be routed around it; and a
     * half-open fold whose hinge does not separate (a small crease on one
     * continuous display) is `BOOK` with nothing to route around. Layout needs
     * both answers and neither implies the other.
     */
    val hinged: Boolean = false,
    /** How the device is being held (17b vs 17c discriminator — see [FoldPosture]). */
    val posture: FoldPosture = FoldPosture.FLAT,
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
            posture = foldPosture(posture),
        )
    }
}

/**
 * [Posture] → [FoldPosture], as a plain function so a JVM test can drive it.
 *
 * Left out of [rememberWindowInfo] on purpose: a decision made inside a
 * `@Composable` is a decision no test in THIS MODULE can reach: #186 added the
 * `tranzlate.compose-test` runtime, and `:core:ui` has not opted into it, while CI
 * compiles instrumented tests without running them (issue #40). The same reasoning that pulled
 * `pickerListPlan` out of the picker's list composable.
 *
 * TABLETOP is checked first because it is the more specific claim: Material sets
 * `isTabletop` only for a half-open HORIZONTAL hinge, and a device cannot be
 * half-open around a horizontal crease and a vertical one at the same time. If a
 * future multi-hinge device reported both, the horizontal one is the one that
 * puts a dead strip across the middle of the screen, so it should win.
 */
fun foldPosture(posture: Posture): FoldPosture =
    when {
        posture.isTabletop -> FoldPosture.TABLETOP
        posture.hingeList.any { !it.isFlat && it.isVertical } -> FoldPosture.BOOK
        else -> FoldPosture.FLAT
    }

private fun WindowSizeClass.toWidthClass(): WindowWidthClass =
    when {
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowWidthClass.EXPANDED
        isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.COMPACT
    }
