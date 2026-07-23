package com.codeboxlk.tranzlate

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.core.model.isDark
import com.codeboxlk.tranzlate.navigation.TranzlateApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The platform's own scrim colours, re-declared because androidx keeps them
 * `internal` (`EdgeToEdge.kt:47,52`). Copied verbatim so the bars keep looking
 * exactly like the `enableEdgeToEdge()` default — the only thing this activity
 * changes about that default is *which theme* decides dark.
 */
private val NavigationBarLightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/** What androidx's own default detector does (`EdgeToEdge.kt:167`). */
private fun Resources.isSystemInDarkMode(): Boolean =
    (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appConfig: AppConfig

    private val viewModel: MainActivityViewModel by viewModels()

    /**
     * One definition of what edge-to-edge means for this app; only the dark
     * detector varies between call sites.
     *
     * Re-applying the whole thing (rather than just flipping
     * `isAppearanceLightStatusBars`) is not belt-and-braces: below API 29 the bar
     * scrim itself is theme-dependent — `getScrim(isDark)`, `EdgeToEdge.kt:217,262,283`
     * — so on Android 7–9 a theme change has to go back through here or the bars
     * keep the wrong scrim. On API 29+ `getScrimWithEnforcedContrast` returns
     * transparent for `auto` styles and only the appearance flags move.
     */
    private fun applyEdgeToEdge(detectDarkMode: (Resources) -> Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDarkMode),
            navigationBarStyle =
                SystemBarStyle.auto(NavigationBarLightScrim, NavigationBarDarkScrim, detectDarkMode),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Both belong before super.onCreate(): the splash must be installed before
        // the activity's content is set up, and androidx's own KDoc sample for
        // enableEdgeToEdge places it there too.
        //
        // The stored preference cannot be read this early — it arrives asynchronously
        // through a ViewModel — so this first pass uses the system setting, exactly
        // as the no-arg default would. The composition below re-applies it with the
        // app's own answer as soon as there is one.
        val splashScreen = installSplashScreen()
        applyEdgeToEdge { resources -> resources.isSystemInDarkMode() }
        super.onCreate(savedInstanceState)

        // Hold the splash until the stored appearance is known. Without this the
        // first frame paints the defaults and someone who chose Dark sees a light
        // flash on every cold start. Safe to wait on: the read is local, and the
        // data source falls back to defaults rather than failing (issue #17 A2),
        // so the flow always emits.
        splashScreen.setKeepOnScreenCondition { viewModel.themeSettings.value == null }

        setContent {
            val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
            val settings = themeSettings ?: ThemeSettings.Default
            val darkTheme = settings.mode.isDark(isSystemInDarkTheme())

            // The app's answer, which can disagree with the system's. Without this a
            // user on a light phone who picks Dark gets dark status-bar icons drawn
            // on our near-black surface — i.e. no icons at all. Keyed on darkTheme so
            // it follows every change, including the Settings toggle (A4).
            DisposableEffect(darkTheme) {
                applyEdgeToEdge { darkTheme }
                onDispose {}
            }

            TranzlateTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                TranzlateApp(appConfig = appConfig)
            }
        }
    }
}
