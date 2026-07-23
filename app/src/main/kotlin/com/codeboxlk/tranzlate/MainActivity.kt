package com.codeboxlk.tranzlate

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
 * `internal` (`EdgeToEdge.kt:47,52`). Copying the values keeps the navigation bar
 * looking exactly like the `enableEdgeToEdge()` default — the only thing this
 * activity changes about that default is *which theme* decides dark.
 */
private val NavigationBarLightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appConfig: AppConfig

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Both of these belong before super.onCreate(): the splash has to be
        // installed before the activity's content is set up, and androidx's own
        // sample for enableEdgeToEdge places it there too (EdgeToEdge.kt KDoc).
        // The no-arg call sets the window up for the very first frame; the
        // DisposableEffect below re-applies it once the app's own dark decision
        // is known, and again whenever it changes.
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash until the stored appearance is known. Without this the
        // first frame paints the defaults and someone who chose Dark sees a light
        // flash on every cold start. It is safe to wait on: the read is local, and
        // the data source falls back to defaults rather than failing (issue #17 A2),
        // so the flow always emits.
        splashScreen.setKeepOnScreenCondition { viewModel.themeSettings.value == null }

        setContent {
            val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
            val settings = themeSettings ?: ThemeSettings.Default
            val darkTheme = settings.mode.isDark(isSystemInDarkTheme())

            // The onCreate call above uses the DEFAULT detector, which reads
            // resources.configuration — i.e. the SYSTEM theme (EdgeToEdge.kt:167).
            // That is the wrong input once the app can override it: someone on a
            // light phone who picks Dark would get dark status-bar icons on our dark
            // surface, i.e. invisible. detectDarkMode is the seam androidx provides,
            // and the app's decision only exists once composition can read it —
            // hence re-applying here, keyed on darkTheme so it follows every change.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle =
                        SystemBarStyle.auto(NavigationBarLightScrim, NavigationBarDarkScrim) { darkTheme },
                )
                onDispose {}
            }

            TranzlateTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                TranzlateApp(appConfig = appConfig)
            }
        }
    }
}
