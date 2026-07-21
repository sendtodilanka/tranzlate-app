package com.codeboxlk.tranzlate.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * DESIGN_SYSTEM §10 theme wiring.
 * Dynamic color (Material You) applies only when SDK ≥ 31 AND [dynamicColor] is
 * enabled; the static §1 palette is the GUARANTEED fallback (API 24–30 renders the
 * brand identity, not gray defaults).
 *
 * [dynamicColor] defaults to false so the brand palette is the baseline; a Settings
 * preference wires user opt-in later (D-P2: palette itself is provisional until the
 * UI-design phase — swap = Color.kt only).
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
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalGradientColors provides if (darkTheme) DarkGradientColors else LightGradientColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TranzlateTypography,
            shapes = TranzlateShapes,
            content = content,
        )
    }
}
