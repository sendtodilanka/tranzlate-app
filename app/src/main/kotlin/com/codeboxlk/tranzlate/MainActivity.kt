package com.codeboxlk.tranzlate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.navigation.TranzlateApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appConfig: AppConfig

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
            // Until the stored preference arrives we render the documented defaults.
            // Harmless today — nobody can store anything but SYSTEM, so the fallback and
            // the real value always agree.
            // TODO(#17 A6): the moment the Settings toggle (A4) ships this becomes a real
            //  light frame on every cold start for anyone who picked Dark. A6 removes it by
            //  holding the splash while `themeSettings` is still null — which is why this
            //  state is nullable. A4 must not land before A6; see the plan doc.
            TranzlateTheme(settings = themeSettings ?: ThemeSettings.Default) {
                TranzlateApp(appConfig = appConfig)
            }
        }
    }
}
