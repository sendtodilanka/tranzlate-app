package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UI_SPEC §1 accent discipline: the one saturated element on a screen fills with
 * `primary` in light and `primaryContainer` in dark. In dark, `primary` is a
 * near-white blue that glares as a large disc on the near-black page; Google
 * Translate uses the deep container tone there instead.
 *
 * Resolved from the ACTIVE color scheme inside [TranzlateTheme], for the same
 * reason as [LocalFloatingSurface]: a call site that branches on
 * `isSystemInDarkTheme()` asks the *system* whether it is dark, and from the
 * moment an in-app light/dark override exists the system and the app can
 * disagree. Read this instead.
 */
@Immutable
data class PrimaryActionColors(
    val container: Color,
    val content: Color,
)

val LocalPrimaryActionColors =
    staticCompositionLocalOf<PrimaryActionColors> {
        error("No primary action colors provided — wrap content in TranzlateTheme")
    }
