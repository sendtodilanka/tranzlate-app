package com.codeboxlk.tranzlate.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.core.model.isDark

/**
 * DESIGN_SYSTEM §10 theme wiring.
 * Dynamic color (Material You) applies only when SDK ≥ 31 AND [dynamicColor] is
 * enabled; the static §1 palette is the GUARANTEED fallback (API 24–30 renders the
 * brand identity, not gray defaults).
 *
 * [dynamicColor] defaults to false so the brand palette is the baseline; a Settings
 * preference wires user opt-in later. Palette = "GT Blue (Google 1P)" (owner-final
 * 2026-07-22, issue #15) — a preset swap (issue #7) touches Color.kt only.
 *
 * Backgrounds are FLAT (issue #15): pages paint `surface`; there is no ambient
 * gradient token any more. Elevated panels separate by a lighter surface step
 * ([LocalFloatingSurface]), never by a wash.
 */
@Composable
fun TranzlateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
            useDynamic -> dynamicLightColorScheme(LocalContext.current)
            darkTheme -> TranzlateDarkColors
            else -> TranzlateLightColors
        }
    val floatingSurface =
        if (darkTheme) colorScheme.surfaceContainer else colorScheme.surfaceContainerLowest
    val primaryActionColors =
        if (darkTheme) {
            PrimaryActionColors(colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        } else {
            PrimaryActionColors(colorScheme.primary, colorScheme.onPrimary)
        }
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalFloatingSurface provides floatingSurface,
        LocalPrimaryActionColors provides primaryActionColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TranzlateTypography,
            shapes = TranzlateShapes,
            content = content,
        )
    }
}

/**
 * The overload the app shell uses: the stored [ThemeSettings] decide, not the
 * system. `isSystemInDarkTheme()` is still read here — it is what
 * [com.codeboxlk.tranzlate.core.model.ThemeMode.SYSTEM] means — but it is one
 * input to the decision rather than the decision itself, and it is the only
 * place that reads it.
 */
@Composable
fun TranzlateTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    TranzlateTheme(
        darkTheme = settings.mode.isDark(isSystemInDarkTheme()),
        dynamicColor = settings.dynamicColor,
        content = content,
    )
}
