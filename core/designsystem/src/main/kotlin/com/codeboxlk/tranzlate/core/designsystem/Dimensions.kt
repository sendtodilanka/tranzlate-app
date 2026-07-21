package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DESIGN_SYSTEM "Adaptive & dimensions" (C-13) fixed dimension tokens.
 * Breakpoints themselves come from the window-size-class API via
 * `rememberWindowInfo()` (:core:ui) — never hardcode dp breakpoints in layouts.
 */
object Dimensions {
    /** Minimum touch target (a11y contract §2.1). */
    val touchTargetMin: Dp = 48.dp

    val iconSm: Dp = 20.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 32.dp

    val borderThin: Dp = 1.dp
    val borderThick: Dp = 2.dp

    /** Compact single-pane content max width, centered. */
    val contentMaxWidth: Dp = 480.dp

    val sheetPeek: Dp = 56.dp
    val fabSize: Dp = 56.dp

    /** ListDetailPaneScaffold: list/input pane minimum (C-13). */
    val paneListMin: Dp = 360.dp

    /** ListDetailPaneScaffold: detail/result pane minimum (C-13). */
    val paneDetailMin: Dp = 400.dp

    /** Default split 40 / 60 (list : detail) — below combined min → single pane. */
    const val PANE_SPLIT_LIST = 0.40f
    const val PANE_SPLIT_DETAIL = 0.60f
}
