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

    // ---- Language picker, landscape two-pane (17a, issue #130 PR-14) --------
    // Measured off the export's `from · landscape` / `to · landscape` frames
    // (892×412), except where a measurement would break an a11y floor — noted
    // on the token that changed.

    /**
     * The 17a side pane: recents, plus the role's own extra (the "Detect
     * language" row on the source side, the offline-voice legend on the target
     * side). Fixed, as the export draws it — a proportional pane would grow the
     * shortcut list at the expense of the catalog, which is the wrong way round.
     */
    val pickerSidePaneWidth: Dp = 272.dp

    /**
     * The narrowest a language column may be before it stops being one.
     *
     * Token arithmetic, not taste: [pickerLeadingInset] 12 + [iconChip] 40 +
     * 16 gap + 96 for a name + 8 gap + [touchTargetMin] 48 for the trailing
     * control + 16 end margin = 236, rounded up. The 96dp name allowance is the
     * one judgement in the sum, and it is roughly six characters of `bodyLarge`
     * — below that every row in the catalog ellipsises.
     */
    val pickerColumnMin: Dp = 240.dp

    /**
     * The 17a top bar, which carries the title, the search field and the
     * on-device counter in ONE row.
     *
     * The export draws 52dp around a 40dp search field. The field here is
     * [touchTargetMin] instead — C-14 makes 48dp the authoritative a11y floor
     * and a 40dp target is below it — so the bar is 56dp, which is that field
     * plus its own 4dp of breathing room. Still 8dp shorter than
     * [topBarHeight], which is the point of the landscape treatment: at 412dp
     * of window height the standard bar costs a whole extra row of languages.
     */
    val pickerCompactBarHeight: Dp = 56.dp

    /** 17a's inline search field stops here rather than stretching to the counter. */
    val pickerSearchMaxWidth: Dp = 420.dp

    /** ListDetailPaneScaffold: list/input pane minimum (C-13). */
    val paneListMin: Dp = 360.dp

    /** ListDetailPaneScaffold: detail/result pane minimum (C-13). */
    val paneDetailMin: Dp = 400.dp

    /** Default split 40 / 60 (list : detail) — below combined min → single pane. */
    const val PANE_SPLIT_LIST = 0.40f
    const val PANE_SPLIT_DETAIL = 0.60f
}
