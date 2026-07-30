package com.codeboxlk.tranzlate.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions

/**
 * The C-13 canonical M3 breakpoint margin (issue #88): compact windows use
 * 16dp, medium and wider use 24dp — single-pane content FILLS the window at
 * this margin (m3.material.io/foundations/layout/breakpoints). Screens read
 * this instead of picking numbers, and never re-introduce width caps.
 */
@Composable
fun adaptiveScreenMargin(): Dp =
    if (rememberWindowInfo().isCompact) {
        Dimensions.screenMarginCompact
    } else {
        Dimensions.screenMarginMedium
    }

/**
 * The margin OVER the compact base ([adaptiveScreenMargin] − 16dp → 0dp/8dp):
 * for screens whose rows already carry the compact 16dp-based insets, padding
 * the sheet by this shim lands every row on the M3 margin without rewriting
 * the rows (their relative alignment is the design).
 */
@Composable
fun adaptiveMarginShim(): Dp = adaptiveScreenMargin() - Dimensions.screenMarginCompact
