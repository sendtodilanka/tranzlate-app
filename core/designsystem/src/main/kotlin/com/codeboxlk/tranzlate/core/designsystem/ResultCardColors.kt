package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The translation result reads as a tonal card sitting inside the composer
 * (Claude Design export "Offline Translator M3"): the container is
 * `primaryContainer` in both modes, but the two content tones are NOT a single
 * M3 role. The design deliberately splits them — a dimmer tone for the language
 * label and the actions, a stronger one for the translation itself — and which
 * of the two happens to equal `onPrimaryContainer` flips between light and dark:
 *
 * |        | container       | label / icons      | translation        |
 * |--------|-----------------|--------------------|--------------------|
 * | light  | `#D3E3FD` p90   | `#0842A0` p30      | `#041E49` p10      |
 * | dark   | `#0842A0` p30   | `#A8C7FA` p80      | `#D3E3FD` p90      |
 *
 * So it is expressed here rather than at the call site, for the same reason as
 * [LocalPrimaryActionColors]: resolved from the ACTIVE scheme inside
 * [TranzlateTheme], never from `isSystemInDarkTheme()` — the app carries its own
 * light/dark override and the two can disagree.
 */
@Immutable
data class ResultCardColors(
    val container: Color,
    val label: Color,
    val text: Color,
)

val LocalResultCardColors =
    staticCompositionLocalOf<ResultCardColors> {
        error("No result card colors provided — wrap content in TranzlateTheme")
    }
