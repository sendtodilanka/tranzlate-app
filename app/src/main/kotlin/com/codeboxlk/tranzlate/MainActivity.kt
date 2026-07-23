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
 * `internal` (`EdgeToEdge.kt:47,52`). Copied verbatim so the bars keep looking
 * exactly like the `enableEdgeToEdge()` default — the only thing this activity
 * changes about that default is *which theme* decides dark.
 */
private val NavigationBarLightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appConfig: AppConfig

    private val viewModel: MainActivityViewModel by viewModels()

    /**
     * The single source the bar detectors read.
     *
     * androidx pins a configuration-change listener to the FIRST enableEdgeToEdge
     * call and replays that call's styles forever (EdgeToEdge.kt:110-125). A field
     * is what makes that replay correct: the lambda never captures a Boolean, it
     * reads this, and the composition updates it before each apply. `@Volatile`
     * because the listener outlives any single composition and may read it from a
     * different context — which is also why this is a field rather than the
     * `rememberUpdatedState` the design debate sketched: the holder must belong to
     * the Activity, like the listener, not to a composition the listener outlives.
     */
    @Volatile
    private var currentDark: Boolean = false

    private fun applyEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { currentDark },
            navigationBarStyle =
                SystemBarStyle.auto(NavigationBarLightScrim, NavigationBarDarkScrim) { currentDark },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run before super.onCreate(). enableEdgeToEdge()
        // deliberately does NOT run here at all — see applyEdgeToEdge's call site.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until the stored appearance is known, so the first drawn
        // frame is already the theme the user chose. Safe to wait on: the data
        // source falls back to defaults rather than failing (issue #17 A2), so the
        // flow always emits.
        //
        // What this does NOT cover: the splash's own background comes from
        // Theme.Tranzlate.Splash -> @color/window_background, which the framework
        // resolves against the SYSTEM night qualifier before any of our code runs.
        // A user whose phone is light but who chose Dark still sees a light splash.
        // Closing that needs a synchronous read in attachBaseContext, i.e. a second
        // source of truth — tracked separately. Now in Android has the same gap.
        splashScreen.setKeepOnScreenCondition { viewModel.themeSettings.value == null }

        setContent {
            val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
            val settings = themeSettings ?: ThemeSettings.Default

            // Derived ONCE. The window and the theme must never disagree about this.
            val darkTheme = settings.mode.isDark(isSystemInDarkTheme())

            // The ONE and ONLY enableEdgeToEdge in this app, reached through
            // applyEdgeToEdge(). Updating the field first is what keeps the pinned
            // replay listener correct; the call then applies the change now, because
            // setting the field alone repaints nothing.
            //
            // An earlier pass in onCreate was tried and measured: it changes nothing
            // (1 transitional frame either way, twice, at normal animation speed),
            // because the stored preference cannot be read that early — the guess it
            // would have to make is exactly the one that is wrong in the case this
            // whole design exists for.
            //
            // ⚠️ Every enableEdgeToEdge in this app must go through applyEdgeToEdge().
            // A plain no-arg call added anywhere — it is the androidx KDoc sample, so
            // it is the natural thing to reach for — pins the bars to the system theme
            // and nothing fails at build or test time.
            //
            // It must also be the whole call, not a flip of isAppearanceLightStatusBars:
            // below API 29 the bar scrim itself is theme-dependent (getScrim(isDark),
            // EdgeToEdge.kt:217,262,283), so on Android 7-9 patching only the appearance
            // flag would leave the wrong scrim behind.
            DisposableEffect(darkTheme) {
                currentDark = darkTheme
                applyEdgeToEdge()
                onDispose {}
            }

            TranzlateTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                TranzlateApp(appConfig = appConfig)
            }
        }
    }
}
