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

    /**
     * M3 breakpoint margin, compact windows (<600dp) — 16dp
     * (m3.material.io/foundations/layout/breakpoints, issue #88).
     */
    val screenMarginCompact: Dp = 16.dp

    /**
     * M3 breakpoint margin, medium+ windows (600dp+) — 24dp. Single-pane
     * screens FILL the window at this margin (owner + M3, issue #88);
     * never re-introduce a centred max-width cap for pane content.
     */
    val screenMarginMedium: Dp = 24.dp

    /**
     * 5a landscape pills-cluster max width — a CONTROL cluster, not pane
     * content (owner, issue #56: the pills must not stretch edge to edge).
     * The one legitimate width cap left after issue #88.
     */
    val contentMaxWidthMedium: Dp = 600.dp

    /** Standard top bar height (M3 small top app bar). */
    val topBarHeight: Dp = 64.dp

    val sheetPeek: Dp = 56.dp
    val fabSize: Dp = 56.dp

    /** Home-canvas quick-action circle (UI_SPEC §2.1 — GT-sized tonal button). */
    val quickActionSize: Dp = 56.dp

    /** Feature-glyph / avatar chip container (§7 sizes: 40dp). */
    val iconChip: Dp = 40.dp

    /** Dense badge glyph (chip leading icons). */
    val iconXs: Dp = 16.dp

    /** Composer text area minimum height (~2 lines of bodyLarge). */
    val composerInputMinHeight: Dp = 56.dp

    /** Composer language pill — taller than the 32dp M3 chip default (GT parity). */
    val languageChipHeight: Dp = 40.dp

    // ---- Language picker (Claude Design "Language Picker 15a", issue #117) ----
    // Measured from the export, not eyeballed. The pill radii are deliberately
    // ABSENT: every picker pill is exactly half its own height, which is the
    // `full` shape token ([TranzlateShapeFull]) — a 28dp/30dp literal would be
    // the same shape with a worse name.

    /** Picker row without a supporting line. */
    val pickerRowHeight: Dp = 56.dp

    /** Picker row carrying a supporting line (on-device / progress / failure). */
    val pickerRowHeightTall: Dp = 60.dp

    /**
     * Leading inset inside a picker pill — the row's start padding and the
     * search field's icon→text gap. Smaller than [screenMarginCompact] because
     * the pill itself is already inset from the screen edge.
     */
    val pickerLeadingInset: Dp = 12.dp

    /** Trailing state glyph (cloud_done / download / refresh) — between [iconSm] and [iconMd]. */
    val pickerStateIcon: Dp = 22.dp

    /** "ONLINE ONLY" chip height. */
    val pickerChipHeight: Dp = 24.dp

    /** A–Z rail VISUAL width; its touch target is [touchTargetMin]. */
    val pickerRailWidth: Dp = 18.dp

    /**
     * Vertical slot one A–Z rail letter occupies. Not decoration: the rail
     * divides its height by this to decide how many letters can be drawn
     * without them overlapping, so it is the number that keeps the letter you
     * touch and the row it scrolls to in agreement.
     */
    val pickerRailLetter: Dp = 18.dp

    /** A–Z rail active-letter pill. */
    val pickerRailPill: Dp = 16.dp

    /** ListDetailPaneScaffold: list/input pane minimum (C-13). */
    val paneListMin: Dp = 360.dp

    /** ListDetailPaneScaffold: detail/result pane minimum (C-13). */
    val paneDetailMin: Dp = 400.dp

    /** Default split 40 / 60 (list : detail) — below combined min → single pane. */
    const val PANE_SPLIT_LIST = 0.40f
    const val PANE_SPLIT_DETAIL = 0.60f
}
