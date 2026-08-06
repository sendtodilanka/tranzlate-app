package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DESIGN_SYSTEM §1.4 — the rev5 storage-meter "used"/consumed-segment tint (#347).
 *
 * rev5 paints the used portion of a storage bar, and the matching "other apps and
 * system" legend dot (Manage packs 20b/20d/20f, the no-space sheet 19b), in a muted
 * primary that sits between `primaryContainer` (#D3E3FD) and `primary` (#0B57D0) —
 * light `#C4D7F5`, a tone the fixed 48-role M3 [androidx.compose.material3.ColorScheme]
 * has no slot for. Its dark counterpart is `primaryContainer` (#0842A0), so the value
 * is resolved from the ACTIVE scheme inside [TranzlateTheme] — never by branching on
 * `isSystemInDarkTheme()` at a call site (the same rule as [LocalFloatingSurface] /
 * [LocalPrimaryActionColors] / [LocalResultCardColors]: the app carries its own
 * light/dark override, so the system and the app can disagree).
 *
 * A meter fill carries no text, so there is no on-colour to pair it with; the numeric
 * figure and the legend labels beside the bar carry the information (WCAG 1.4.1 / the
 * used-vs-track luminance ratio is intentionally low per the rev5 SSOT — see
 * DESIGN_SYSTEM §0 / the #347 plan doc).
 *
 * NOTE the downloading-row progress TRACK also draws light `#C4D7F5`, but takes
 * `surfaceContainerHigh` (#2D2F31) in dark — so that track is composed per-screen, not
 * read from this token.
 */
val LocalMeterFillColor =
    staticCompositionLocalOf<Color> {
        error("No meter fill colour provided — wrap content in TranzlateTheme")
    }
