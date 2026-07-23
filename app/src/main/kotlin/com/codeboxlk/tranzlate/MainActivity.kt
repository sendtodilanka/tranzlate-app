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
            // For someone who chose Dark that is a real, brief light frame — which is
            // exactly what issue #17 A6 removes by holding the splash while
            // `themeSettings` is still null. The nullable state exists for that gate.
            TranzlateTheme(settings = themeSettings ?: ThemeSettings.Default) {
                TranzlateApp(appConfig = appConfig)
            }
        }
    }
}
