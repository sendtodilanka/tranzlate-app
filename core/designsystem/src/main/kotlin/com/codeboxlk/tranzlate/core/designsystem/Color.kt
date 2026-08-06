package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// DESIGN_SYSTEM §1 color role tokens — "GT Blue (Google 1P)", owner-final
// 2026-07-22 (issue #15: UI reset — the app now matches Google Translate).
// ALL 48 ColorScheme roles are set explicitly (36 base + the 12 `*Fixed`);
// nothing is left to a Compose default. Provenance + WCAG math:
// docs/design/PALETTES.md "GT Blue".
//
// Accent tones are Google's own reference values (chromium
// `ui/color/ref_color_mixer.cc`), surfaces/neutrals/error the 1P baseline greys.
// The teal set (P8) is RETIRED. This file is the ONLY home for raw hex (§10),
// with one unavoidable exception: `app/src/main/res/values*/colors.xml` repeats
// LightBackground/DarkBackground, because `android:windowBackground` must be a
// compiled Android resource the framework can paint before any Compose frame
// exists. Change one, change the other.
//
// 2026-08-05 (#347): reconciled to the rev5 colour SSOT (owner ruling — where a token
// differs from the rev5 drawings, the token is corrected to rev5, light AND dark). Two
// role values moved — `LightOnErrorContainer` #8C1D18→#410E0B and
// `DarkSurfaceContainerHigh` #282A2C→#2D2F31 — and one extension tone (`LightMeterFill`
// #C4D7F5) was added for a colour the 48-role scheme cannot hold. Diff + WCAG:
// docs/plan/issue-347-color-token-reconciliation.md; DESIGN_SYSTEM §0/§1.

// ---- Light scheme (§1.1) ------------------------------------------------------------------------

internal val LightPrimary = Color(0xFF0B57D0) // ref primary40
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD3E3FD) // ref primary90
internal val LightOnPrimaryContainer = Color(0xFF0842A0) // ref primary30
internal val LightSecondary = Color(0xFF00639B) // ref secondary40
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFC2E7FF) // ref secondary90
internal val LightOnSecondaryContainer = Color(0xFF004A77) // ref secondary30
internal val LightTertiary = Color(0xFF146C2E) // ref tertiary40
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFC4EED0) // ref tertiary90
internal val LightOnTertiaryContainer = Color(0xFF0F5223) // ref tertiary30
internal val LightBackground = Color(0xFFF8FAFD) // = surface
internal val LightOnBackground = Color(0xFF1F1F1F)
internal val LightSurface = Color(0xFFF8FAFD)
internal val LightOnSurface = Color(0xFF1F1F1F)
internal val LightSurfaceVariant = Color(0xFFE1E3E1)
internal val LightOnSurfaceVariant = Color(0xFF444746)
internal val LightSurfaceDim = Color(0xFFDADADA)
internal val LightSurfaceBright = Color(0xFFF8FAFD)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF0F4F9)
internal val LightSurfaceContainer = Color(0xFFEFEDED)
internal val LightSurfaceContainerHigh = Color(0xFFE9EEF6)
internal val LightSurfaceContainerHighest = Color(0xFFE3E3E3)
internal val LightSurfaceTint = LightPrimary // = primary (tonal elevation tint)
internal val LightInverseSurface = Color(0xFF303030)
internal val LightInverseOnSurface = Color(0xFFF2F2F2)
internal val LightInversePrimary = Color(0xFFA8C7FA) // = dark primary (ref primary80)
internal val LightOutline = Color(0xFF747775)
internal val LightOutlineVariant = Color(0xFFC4C7C5)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)

// ref error10 — rev5 SSOT failure text/icon tone on `errorContainer` (was error30
// #8C1D18, which the rev5 drawings never use in light; #347). Restores the M3
// baseline error10-on-error90 pairing; dark stays #F9DEDC (already rev5-correct).
internal val LightOnErrorContainer = Color(0xFF410E0B)
internal val LightScrim = Color(0xFF000000)

// ---- Dark scheme (§1.2) -------------------------------------------------------------------------

internal val DarkPrimary = Color(0xFFA8C7FA) // ref primary80
internal val DarkOnPrimary = Color(0xFF062E6F) // ref primary20
internal val DarkPrimaryContainer = Color(0xFF0842A0) // ref primary30
internal val DarkOnPrimaryContainer = Color(0xFFD3E3FD) // ref primary90
internal val DarkSecondary = Color(0xFF7FCFFF) // ref secondary80
internal val DarkOnSecondary = Color(0xFF003355) // ref secondary20
internal val DarkSecondaryContainer = Color(0xFF004A77) // ref secondary30
internal val DarkOnSecondaryContainer = Color(0xFFC2E7FF) // ref secondary90
internal val DarkTertiary = Color(0xFF6DD58C) // ref tertiary80
internal val DarkOnTertiary = Color(0xFF0A3818) // ref tertiary20
internal val DarkTertiaryContainer = Color(0xFF0F5223) // ref tertiary30
internal val DarkOnTertiaryContainer = Color(0xFFC4EED0) // ref tertiary90
internal val DarkBackground = Color(0xFF131314) // = surface
internal val DarkOnBackground = Color(0xFFE3E3E3)
internal val DarkSurface = Color(0xFF131314)
internal val DarkOnSurface = Color(0xFFE3E3E3)
internal val DarkSurfaceVariant = Color(0xFF444746)
internal val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
internal val DarkSurfaceDim = Color(0xFF131314)
internal val DarkSurfaceBright = Color(0xFF393939)
internal val DarkSurfaceContainerLowest = Color(0xFF0E0E0F)
internal val DarkSurfaceContainerLow = Color(0xFF1F1F1F)
internal val DarkSurfaceContainer = Color(0xFF1E1F20)

// rev5 SSOT dark chip / ONLINE-ONLY fill / icon-button hover / meter track (was
// #282A2C, which the rev5 drawings never use; #2D2F31 appears 202× — README:54, #347).
internal val DarkSurfaceContainerHigh = Color(0xFF2D2F31)
internal val DarkSurfaceContainerHighest = Color(0xFF343535)
internal val DarkSurfaceTint = DarkPrimary // = primary (tonal elevation tint)
internal val DarkInverseSurface = Color(0xFFE3E3E3)
internal val DarkInverseOnSurface = Color(0xFF303030)
internal val DarkInversePrimary = Color(0xFF0B57D0) // = light primary (ref primary40)
internal val DarkOutline = Color(0xFF8E918F)
internal val DarkOutlineVariant = Color(0xFF444746)
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF8C1D18)
internal val DarkOnErrorContainer = Color(0xFFF9DEDC)
internal val DarkScrim = Color(0xFF000000)

// ---- The 12 `*Fixed` roles — IDENTICAL in both schemes ------------------------------------------
// That is the point of "fixed": a colour that survives a light↔dark switch
// unchanged (M3 role ladder, mirrored in Compose ColorLightTokens/ColorDarkTokens:
// Fixed = tone90 · FixedDim = tone80 · onFixed = tone10 · onFixedVariant = tone30).

internal val PrimaryFixed = Color(0xFFD3E3FD) // ref primary90
internal val PrimaryFixedDim = Color(0xFFA8C7FA) // ref primary80
internal val OnPrimaryFixed = Color(0xFF041E49) // ref primary10
internal val OnPrimaryFixedVariant = Color(0xFF0842A0) // ref primary30
internal val SecondaryFixed = Color(0xFFC2E7FF) // ref secondary90
internal val SecondaryFixedDim = Color(0xFF7FCFFF) // ref secondary80
internal val OnSecondaryFixed = Color(0xFF001D35) // ref secondary10
internal val OnSecondaryFixedVariant = Color(0xFF004A77) // ref secondary30
internal val TertiaryFixed = Color(0xFFC4EED0) // ref tertiary90
internal val TertiaryFixedDim = Color(0xFF6DD58C) // ref tertiary80
internal val OnTertiaryFixed = Color(0xFF072711) // ref tertiary10
internal val OnTertiaryFixedVariant = Color(0xFF0F5223) // ref tertiary30

// ---- rev5 extension colours — NOT M3 ColorScheme roles (#347) ------------------------------------
// rev5 draws a handful of tones the fixed 48-role scheme has no slot for. They are
// provided as CompositionLocals off the ACTIVE scheme by [TranzlateTheme] (the
// [LocalFloatingSurface] / [LocalPrimaryActionColors] / [LocalResultCardColors]
// pattern), so a screen reads them theme-aware without branching on
// isSystemInDarkTheme(). Only the LIGHT raw hex lives here; the dark counterpart is
// an existing scheme role resolved in Theme.kt. DESIGN_SYSTEM §1.4.

// Storage-meter "used"/consumed-segment tint (storage bar used segment, the "other
// apps and system" legend dot — 20b/20d/20f/19b). A muted primary between
// primaryContainer #D3E3FD and primary #0B57D0. Dark counterpart = primaryContainer
// #0842A0 (resolved in Theme.kt). The downloading-row progress track draws the SAME
// values (light #C4D7F5 / dark #0842A0); the storage bar's own track (the unfilled
// remainder) is the different element — dark surfaceContainerHigh #2D2F31 — composed
// per-screen, not from this token.
internal val LightMeterFill = Color(0xFFC4D7F5)

/** Static light scheme — guaranteed fallback for API 24–30 / dynamic-color-off (§ header). */
val TranzlateLightColors =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = LightInversePrimary,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = LightTertiary,
        onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = LightSurfaceTint,
        inverseSurface = LightInverseSurface,
        inverseOnSurface = LightInverseOnSurface,
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        scrim = LightScrim,
        surfaceBright = LightSurfaceBright,
        surfaceDim = LightSurfaceDim,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        primaryFixed = PrimaryFixed,
        primaryFixedDim = PrimaryFixedDim,
        onPrimaryFixed = OnPrimaryFixed,
        onPrimaryFixedVariant = OnPrimaryFixedVariant,
        secondaryFixed = SecondaryFixed,
        secondaryFixedDim = SecondaryFixedDim,
        onSecondaryFixed = OnSecondaryFixed,
        onSecondaryFixedVariant = OnSecondaryFixedVariant,
        tertiaryFixed = TertiaryFixed,
        tertiaryFixedDim = TertiaryFixedDim,
        onTertiaryFixed = OnTertiaryFixed,
        onTertiaryFixedVariant = OnTertiaryFixedVariant,
    )

/** Static dark scheme (§1.2) — the `*Fixed` roles repeat the light values by design. */
val TranzlateDarkColors =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = DarkInversePrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = DarkSurfaceTint,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = DarkScrim,
        surfaceBright = DarkSurfaceBright,
        surfaceDim = DarkSurfaceDim,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        primaryFixed = PrimaryFixed,
        primaryFixedDim = PrimaryFixedDim,
        onPrimaryFixed = OnPrimaryFixed,
        onPrimaryFixedVariant = OnPrimaryFixedVariant,
        secondaryFixed = SecondaryFixed,
        secondaryFixedDim = SecondaryFixedDim,
        onSecondaryFixed = OnSecondaryFixed,
        onSecondaryFixedVariant = OnSecondaryFixedVariant,
        tertiaryFixed = TertiaryFixed,
        tertiaryFixedDim = TertiaryFixedDim,
        onTertiaryFixed = OnTertiaryFixed,
        onTertiaryFixedVariant = OnTertiaryFixedVariant,
    )
