package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Blue wash end-stop `#00639B` — identical in BOTH themes (PALETTES.md P8). */
private val AmbientWashBlue = LightSecondary

/**
 * DESIGN_SYSTEM §2 ambient wash token (P8, 2026-07-22 — replaces the retired
 * teal→coral brand gradient) — per UI_SPEC.md §1: a soft, LOW-OPACITY teal→blue
 * tint laid over `surface`, strengthening toward the bottom of the page.
 *
 * Usage rules (gradient discipline):
 * - Page background + at most ONE signature element per screen.
 * - NEVER behind body text; never on dividers, borders, icons, or text.
 * - Floating surfaces sit as a lighter step OVER the wash (light
 *   `surfaceContainerLowest`, dark `surfaceContainer`/`High`) — separation by
 *   lightness, not outlines.
 */
@Immutable
data class AmbientGradient(
    /** Teal stop (= `primary`): #1C7A97 (light) / #3FB6D4 (dark). */
    val start: Color,
    /** Blue stop (= `secondary` family): #00639B (both themes). */
    val end: Color,
)

val LightAmbientGradient =
    AmbientGradient(
        start = LightPrimary,
        end = AmbientWashBlue,
    )

val DarkAmbientGradient =
    AmbientGradient(
        start = DarkPrimary,
        end = AmbientWashBlue,
    )

/** Provided by [TranzlateTheme] (§10 — non-M3 tokens ride CompositionLocals). */
val LocalAmbientGradient =
    staticCompositionLocalOf<AmbientGradient> {
        error("No AmbientGradient provided — wrap content in TranzlateTheme")
    }
