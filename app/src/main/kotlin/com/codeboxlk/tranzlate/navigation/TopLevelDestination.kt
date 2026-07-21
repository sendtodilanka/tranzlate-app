package com.codeboxlk.tranzlate.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.FeatureToggle
import kotlinx.serialization.Serializable

// Navigation 3 keys — @Serializable so rememberNavBackStack can save/restore.
@Serializable
data object TextNavKey : NavKey

@Serializable
data object CameraNavKey : NavKey

@Serializable
data object HistoryNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/**
 * The 4 MVP top-level destinations (plan §3). Toggle-aware from day 1
 * (plan §4 R2): the shell filters this registry by `AppConfig.featureToggles`,
 * so e.g. a brand without CAMERA simply never registers the entry — no code edits.
 */
enum class TopLevelDestination(
    val navKey: NavKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val toggle: FeatureToggle,
) {
    TEXT(TextNavKey, R.string.nav_text, Icons.AutoMirrored.Filled.Chat, FeatureToggle.TEXT),
    CAMERA(CameraNavKey, R.string.nav_camera, Icons.Filled.PhotoCamera, FeatureToggle.CAMERA),
    HISTORY(HistoryNavKey, R.string.nav_history, Icons.Filled.History, FeatureToggle.HISTORY),
    SETTINGS(SettingsNavKey, R.string.nav_settings, Icons.Filled.Settings, FeatureToggle.SETTINGS),
}
