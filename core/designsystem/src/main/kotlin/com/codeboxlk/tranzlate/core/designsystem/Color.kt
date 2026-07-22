package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// DESIGN_SYSTEM §1 color role tokens — P8 "Tranzlate Teal (cool-mono)", owner-final
// 2026-07-22 (issue #10). EXACT hex values, WCAG-checked (§0 / docs/design/PALETTES.md P8).
// Teal primary kept from the brand set; blue support + neutrals + error = Google 1P (P1)
// values verbatim. No coral, no gold, no violet, no warm hue anywhere (P8 rule).
// The ONLY home for raw hex in the codebase (§10 rule).

// §1.1 Light scheme

internal val LightPrimary = Color(0xFF1C7A97) // brand teal (kept), AA-checked
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFB8E7F5)
internal val LightOnPrimaryContainer = Color(0xFF002F3C)
internal val LightSecondary = Color(0xFF00639B) // support blue (P1 secondary)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFC2E7FF)
internal val LightOnSecondaryContainer = Color(0xFF004A77)
internal val LightTertiary = Color(0xFF10586B) // deep teal, in-family (P8-derived)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFCDE9F2)
internal val LightOnTertiaryContainer = Color(0xFF002F3C)
internal val LightBackground = Color(0xFFFAF9F8) // = surface (P1)
internal val LightOnBackground = Color(0xFF1F1F1F)
internal val LightSurface = Color(0xFFFAF9F8)
internal val LightOnSurface = Color(0xFF1F1F1F)
internal val LightSurfaceVariant = Color(0xFFE1E3E1)
internal val LightOnSurfaceVariant = Color(0xFF444746)
internal val LightSurfaceDim = Color(0xFFDADADA)
internal val LightSurfaceBright = Color(0xFFFAF9F8)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF4F3F2)
internal val LightSurfaceContainer = Color(0xFFEFEDED)
internal val LightSurfaceContainerHigh = Color(0xFFE9E8E8)
internal val LightSurfaceContainerHighest = Color(0xFFE3E3E3)
internal val LightSurfaceTint = LightPrimary // = primary (tonal elevation tint)
internal val LightInverseSurface = Color(0xFF303030)
internal val LightInverseOnSurface = Color(0xFFF2F2F2)
internal val LightInversePrimary = Color(0xFF3FB6D4) // = dark primary (P8 teal family)
internal val LightOutline = Color(0xFF747775)
internal val LightOutlineVariant = Color(0xFFC4C7C5)
internal val LightError = Color(0xFFB3261E) // P1 error set
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF8C1D18)
internal val LightScrim = Color(0xFF000000)

// §1.2 Dark scheme

internal val DarkPrimary = Color(0xFF3FB6D4) // bright brand teal (kept)
internal val DarkOnPrimary = Color(0xFF00363F)
internal val DarkPrimaryContainer = Color(0xFF10586B)
internal val DarkOnPrimaryContainer = Color(0xFFB8E7F5)
internal val DarkSecondary = Color(0xFF7FCFFF) // support blue (P1 secondary)
internal val DarkOnSecondary = Color(0xFF003355)
internal val DarkSecondaryContainer = Color(0xFF004A77)
internal val DarkOnSecondaryContainer = Color(0xFFC2E7FF)
internal val DarkTertiary = Color(0xFF8FD3E3) // soft teal, in-family (P8-derived)
internal val DarkOnTertiary = Color(0xFF00363F)
internal val DarkTertiaryContainer = Color(0xFF0B4A5A)
internal val DarkOnTertiaryContainer = Color(0xFFCDE9F2)
internal val DarkBackground = Color(0xFF131314) // = surface (P1)
internal val DarkOnBackground = Color(0xFFE3E3E3)
internal val DarkSurface = Color(0xFF131314)
internal val DarkOnSurface = Color(0xFFE3E3E3)
internal val DarkSurfaceVariant = Color(0xFF444746)
internal val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
internal val DarkSurfaceDim = Color(0xFF131314)
internal val DarkSurfaceBright = Color(0xFF393939)
internal val DarkSurfaceContainerLowest = Color(0xFF0E0E0F)
internal val DarkSurfaceContainerLow = Color(0xFF1F1F1F)
internal val DarkSurfaceContainer = Color(0xFF1F2020)
internal val DarkSurfaceContainerHigh = Color(0xFF2A2A2A)
internal val DarkSurfaceContainerHighest = Color(0xFF343535)
internal val DarkSurfaceTint = DarkPrimary // = primary (tonal elevation tint)
internal val DarkInverseSurface = Color(0xFFE3E3E3)
internal val DarkInverseOnSurface = Color(0xFF303030)
internal val DarkInversePrimary = Color(0xFF1C7A97) // = light primary (P8 teal family)
internal val DarkOutline = Color(0xFF8E918F)
internal val DarkOutlineVariant = Color(0xFF444746)
internal val DarkError = Color(0xFFF2B8B5) // P1 error set
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF8C1D18)
internal val DarkOnErrorContainer = Color(0xFFF9DEDC)
internal val DarkScrim = Color(0xFF000000)

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
    )

/** Static dark scheme (§1.2). */
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
    )
