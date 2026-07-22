package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UI_SPEC §1/§3 "floating surface" step: composer, panels, drawer sheet and cards
 * sit as a LIGHTER STEP over the FLAT page `surface` — light
 * `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer`. Separation by
 * lightness, not outlines and not a gradient (issue #15: no wash anywhere).
 *
 * Resolved from the ACTIVE color scheme inside [TranzlateTheme] (so it follows
 * dynamic color on API 31+ too) — never branch on `isSystemInDarkTheme()` at a
 * call site to pick a card color; read this instead.
 */
val LocalFloatingSurface =
    staticCompositionLocalOf<Color> {
        error("No floating surface provided — wrap content in TranzlateTheme")
    }
