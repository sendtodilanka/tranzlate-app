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

    // ---- Language picker, foldable two-leaf (17b, issue #130 PR-15) ---------
    // Measured off the export's `from · foldable` / `to · foldable` frames
    // (760×812).

    /**
     * The gap between the two leaves of a half-open foldable — the strip of
     * window the crease runs down.
     *
     * Three times its flat-window sibling (`PANE_GUTTER`, 8dp), and that is the
     * whole point of the token: 17a's gutter separates two
     * things on ONE surface, so it only has to be legible, while this one has
     * to keep content off a physical fold. Material's own foldable guidance
     * ("Support different screen sizes → fold-aware layouts") puts the hinge in
     * its own region rather than under content for exactly that reason, and the
     * export draws 24dp between the leaves in both foldable frames.
     */
    val pickerCreaseGutter: Dp = 24.dp

    /**
     * The 17b leaf that holds the shortcuts — the same role as
     * [pickerSidePaneWidth] in 17a, 24dp wider because the export draws it that
     * way: an unfolded inner display is a much taller window, so the leaf has
     * the recents section AND the offline-library meter card in it, and the
     * card's "of 59 packs · 110 MB used" line is what sets the floor.
     */
    val pickerLeafPaneWidth: Dp = 296.dp

    /** The offline-library meter's progress track (17b, export: 4px). */
    val pickerMeterBarHeight: Dp = 4.dp

    // ---- Language picker, tablet dialog (17c/17d, issue #130 PR-16) ---------
    // Measured off the export's four tablet frames: `from|to · tablet portrait`
    // (800×1280) and `from|to · tablet landscape` (1280×800). Both orientations
    // draw the same card at two widths and one height rule.

    /**
     * The dialog's width in a window that is taller than it is wide — the export
     * draws 560dp inside 800×1280.
     *
     * It is also Material 3's own maximum for a basic dialog
     * (`AlertDialogDefaults`, `DialogMaxWidth = 560.dp`), which is why the
     * portrait card needs `usePlatformDefaultWidth = false` only to stop the
     * platform SHRINKING it, not to exceed anything.
     */
    val pickerDialogWidthPortrait: Dp = 560.dp

    /**
     * The dialog's width in a window that is wider than it is tall — the export
     * draws 720dp inside 1280×800.
     *
     * This one is deliberately past M3's 560dp basic-dialog maximum, and the
     * reason is the content rather than the taste: at 720dp the catalog runs in
     * TWO columns, which is what buys back the rows a 624dp-tall card loses. A
     * 560dp card in a landscape window would be a tall list in a short box.
     */
    val pickerDialogWidthLandscape: Dp = 720.dp

    /**
     * How much of the window's height the card may take — the export's
     * `max-height:78%`, in both orientations.
     *
     * A fraction rather than a dp because it is the one number that has to mean
     * the same thing at 1280dp and at 800dp: enough of the window to be the
     * subject, with enough left over that the screen behind it is still visibly
     * there. That visible remainder is the whole difference between this and a
     * full-screen destination.
     */
    const val PICKER_DIALOG_HEIGHT_FRACTION = 0.78f

    /** The dialog's docked action bar (export: 68px, `Manage packs` + `Cancel`). */
    val pickerDialogActionBar: Dp = 68.dp

    /** The dialog card's own inset from the window edge when the width has to be clamped. */
    val pickerDialogMargin: Dp = 24.dp

    /** ListDetailPaneScaffold: list/input pane minimum (C-13). */
    val paneListMin: Dp = 360.dp

    /** ListDetailPaneScaffold: detail/result pane minimum (C-13). */
    val paneDetailMin: Dp = 400.dp

    /** Default split 40 / 60 (list : detail) — below combined min → single pane. */
    const val PANE_SPLIT_LIST = 0.40f
    const val PANE_SPLIT_DETAIL = 0.60f
}
