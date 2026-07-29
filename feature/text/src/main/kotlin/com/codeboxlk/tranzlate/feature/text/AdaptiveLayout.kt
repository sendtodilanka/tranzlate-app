package com.codeboxlk.tranzlate.feature.text

import androidx.compose.runtime.Composable
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

    /** Phone landscape: only 5a's read face splits (the IME owns the height while editing). */
    val splitResultOnly: Boolean get() = expandedWidth && compactHeight
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
