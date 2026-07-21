package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * DESIGN_SYSTEM §1 color role tokens — EXACT hex values (WCAG-AA-checked, §0).
 * The ONLY home for raw hex in the codebase (§10 rule).
 *
 * D-P2 (owner resolution): this palette is the provisional implementation baseline;
 * final colours are decided in the UI-design phase — swapping hex = this file only.
 */

// §1.1 Light scheme
internal val LightPrimary = Color(0xFF1C7A97) // brand teal, deepened for AA (§0 design rule)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFB8E7F5)
internal val LightOnPrimaryContainer = Color(0xFF002F3C)
internal val LightSecondary = Color(0xFFC0563E) // coral swoosh, deepened for AA
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFFFDBD1)
internal val LightOnSecondaryContainer = Color(0xFF3B0A02)
internal val LightTertiary = Color(0xFF7A5A2E) // warm bronze/gold bridge accent
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFBDFA6)
internal val LightOnTertiaryContainer = Color(0xFF2A1C00)
internal val LightBackground = Color(0xFFFCFCFD)
internal val LightOnBackground = Color(0xFF1A1C1E)
internal val LightSurface = Color(0xFFFBFCFE)
internal val LightOnSurface = Color(0xFF1A1C1E)
internal val LightSurfaceVariant = Color(0xFFDCE3E8)
internal val LightOnSurfaceVariant = Color(0xFF40484D)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF4F5F7)
internal val LightSurfaceContainer = Color(0xFFEEF0F3)
internal val LightSurfaceContainerHigh = Color(0xFFE8EBED)
internal val LightSurfaceContainerHighest = Color(0xFFE2E5E8)
internal val LightSurfaceTint = Color(0xFF1C7A97) // = primary (tonal elevation tint)
internal val LightInverseSurface = Color(0xFF2E3134)
internal val LightInverseOnSurface = Color(0xFFF1F1F3)
internal val LightInversePrimary = Color(0xFF3FB6D4)
internal val LightOutline = Color(0xFF70787D)
internal val LightOutlineVariant = Color(0xFFC0C8CD)
internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)
internal val LightScrim = Color(0xFF000000)

// §1.2 Dark scheme
internal val DarkPrimary = Color(0xFF3FB6D4) // bright brand teal
internal val DarkOnPrimary = Color(0xFF00363F)
internal val DarkPrimaryContainer = Color(0xFF10586B)
internal val DarkOnPrimaryContainer = Color(0xFFB8E7F5)
internal val DarkSecondary = Color(0xFFFFB4A0) // bright coral
internal val DarkOnSecondary = Color(0xFF5C1900)
internal val DarkSecondaryContainer = Color(0xFF7A3421)
internal val DarkOnSecondaryContainer = Color(0xFFFFDBD1)
internal val DarkTertiary = Color(0xFFE0C08A) // soft gold
internal val DarkOnTertiary = Color(0xFF422C00)
internal val DarkTertiaryContainer = Color(0xFF5E421E)
internal val DarkOnTertiaryContainer = Color(0xFFFBDFA6)
internal val DarkBackground = Color(0xFF101416)
internal val DarkOnBackground = Color(0xFFE2E5E8)
internal val DarkSurface = Color(0xFF101416)
internal val DarkOnSurface = Color(0xFFE2E5E8)
internal val DarkSurfaceVariant = Color(0xFF40484D)
internal val DarkOnSurfaceVariant = Color(0xFFC0C8CD)
internal val DarkSurfaceContainerLowest = Color(0xFF0B0F11)
internal val DarkSurfaceContainerLow = Color(0xFF181C1F)
internal val DarkSurfaceContainer = Color(0xFF1C2023)
internal val DarkSurfaceContainerHigh = Color(0xFF262A2E)
internal val DarkSurfaceContainerHighest = Color(0xFF303539)
internal val DarkSurfaceTint = Color(0xFF3FB6D4)
internal val DarkInverseSurface = Color(0xFFE2E5E8)
internal val DarkInverseOnSurface = Color(0xFF2E3134)
internal val DarkInversePrimary = Color(0xFF1C7A97)
internal val DarkOutline = Color(0xFF8A9297)
internal val DarkOutlineVariant = Color(0xFF40484D)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkScrim = Color(0xFF000000)

/** Static light scheme — guaranteed fallback for API 24–30 / dynamic-color-off (§ header). */
val TranzlateLightColors = lightColorScheme(
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
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

/** Static dark scheme (§1.2). */
val TranzlateDarkColors = darkColorScheme(
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
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)
