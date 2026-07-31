package com.codeboxlk.tranzlate.feature.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo

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

    /**
     * Phone landscape: only 5a's READ face splits into source | result.
     *
     * Width is the honest axis here — two panes at 2:3 need the room. This is
     * the ONLY question this flag answers; issue #99 took its second job (the
     * short-window edit treatments) away, because width could never answer
     * "does the edit face fit?" — see [ComposerFit].
     */
    val splitResultOnly: Boolean get() = expandedWidth && compactHeight
}

/**
 * Which of 5a's three edit-face arrangements the window can actually hold —
 * decided by MEASURED height, never by a size class (issue #99).
 *
 * WindowSizeClass has height breakpoints at 480dp and 900dp only, so a phone in
 * landscape reads compact-height with OR without the keyboard: the class never
 * changes when the IME opens, and therefore cannot drive keyboard adaptation.
 * Width is worse — the owner's OnePlus 7 Pro is 832dp in landscape, 8dp short
 * of the expanded breakpoint, and the width-gated treatments left a 48dp field
 * inside a 40dp card interior. The truthful signal is the height the pane is
 * actually handed, `WindowInsets.safeDrawing` (IME included) already removed.
 *
 * Structure (navigation, panes, margins) still comes from [AdaptiveLayout];
 * only "does this fit?" comes from here.
 */
internal enum class ComposerFit {
    /** The portrait face: label row · field · Paste · counter+action row. */
    FULL,

    /** Label, counter, clear and the action fold into ONE row; the field keeps the rest. */
    FOLDED_CHROME,

    /** Field + counter + action in a single row — nothing else fits. */
    MINIMAL,
    ;

    /** Everything but [FULL] folds the chrome into the field's row. */
    val foldsChrome: Boolean get() = this != FULL

    val minimal: Boolean get() = this == MINIMAL
}

/*
 * The thresholds below are the ARITHMETIC of the arrangement each one guards —
 * summed from the very tokens that build it — so they stay true on a phone
 * whose insets differ from any device we measured on.
 *
 * Every arrangement pays the same chrome outside the card: the top row
 * (8dp padding + 48dp back button + 16dp padding = 72dp) plus the 16dp spacer
 * under the card = 88dp. The card's own interior padding is 24dp top + 16dp
 * bottom = 40dp. Both sums below add those 128dp to the stack they need.
 */

/**
 * Below this the FULL face cannot give the field a usable multi-line area:
 *
 * ```
 *  48  SourceLabelRow (the ✕ IconButton sets the row height once there is text)
 * + 8  field row top padding
 * +96  field — 3 lines of headlineSmall (lineHeight 32sp → 32dp per line)
 * +40  Paste chip (empty state; the M3 TextButton minimum height)
 * +56  action row (8dp padding + the 48dp touchTargetMin button)
 * =248 interior → +40 card padding +88 outside chrome
 * ```
 *
 * The 48dp terms are `Dimensions.touchTargetMin`, the a11y floor C-14 makes
 * authoritative. The 96dp field is the only judgement call in the sum, and it
 * is the one the owner's complaint is about: a field that holds a single
 * clipped line is the bug, not the fix.
 */
private val FULL_CHROME_MIN_HEIGHT: Dp = 376.dp

/**
 * Below this even the folded face stops fitting:
 *
 * ```
 *  48  chrome row (holds the 48dp action)
 * + 8  field row top padding
 * +48  field — one comfortable line, at the touch-target floor
 * +40  Paste chip (empty state)
 * =144 interior → +40 card padding +88 outside chrome
 * ```
 */
private val FOLDED_CHROME_MIN_HEIGHT: Dp = 272.dp

/**
 * Classify the height the composer pane was actually handed.
 *
 * [availableHeight] must be the pane's INCOMING constraint, not the card's
 * leftover: the card grows when the top row hides, so gating on the card's own
 * height would oscillate. The pane's constraint is branch-independent.
 */
internal fun composerFitFor(availableHeight: Dp): ComposerFit =
    when {
        availableHeight < FOLDED_CHROME_MIN_HEIGHT -> ComposerFit.MINIMAL
        availableHeight < FULL_CHROME_MIN_HEIGHT -> ComposerFit.FOLDED_CHROME
        else -> ComposerFit.FULL
    }

@Composable
internal fun rememberAdaptiveLayout(): AdaptiveLayout {
    // Derived from the C-13 canonical reader — this file never touches the
    // window-size-class API directly (co-verify finding 2).
    val window = rememberWindowInfo()
    return AdaptiveLayout(
        expandedWidth = window.isExpanded,
        mediumWidth = !window.isCompact,
        compactHeight = window.heightCompact,
        hinged = window.hinged,
    )
}
