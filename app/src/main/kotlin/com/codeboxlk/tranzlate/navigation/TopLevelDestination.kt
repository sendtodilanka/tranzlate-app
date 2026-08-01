package com.codeboxlk.tranzlate.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.FeatureToggle
import kotlinx.serialization.Serializable

// Navigation 3 keys — @Serializable so rememberNavBackStack can save/restore.
@Serializable
data object TextNavKey : NavKey

/**
 * Full-screen language picker (issue #15 — replaces the composer's bottom sheet).
 * [forSource] rather than the feature's enum: a nav key must be `@Serializable`,
 * and a Boolean keeps the key free of a feature-module type.
 */
@Serializable
data class LanguagePickerNavKey(
    val forSource: Boolean,
) : NavKey

/**
 * Screen 5a — the text vertical's ONE working surface: typing, the result and
 * re-editing all happen here, so it replaces the former Result destination.
 * It is a real destination (not a Home state) so the shell's back stack owns
 * leaving it: system back, the gesture and the in-screen arrow all resolve
 * through the same guarded pop, and `NavDisplay` drives the predictive-back
 * preview and the shared-element morph off it. See `TranzlateApp`.
 */
@Serializable
data object ComposerNavKey : NavKey

@Serializable
data object PaywallNavKey : NavKey

@Serializable
data object CameraNavKey : NavKey

/** Conversation/Dialog tab — the feature ships in v2, so it shows a coming-soon placeholder. */
@Serializable
data object ChatNavKey : NavKey

@Serializable
data object HistoryNavKey : NavKey

/** Drawer "Offline languages" placeholder destination (:feature:language). */
@Serializable
data object LanguagesNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/**
 * The persistent bottom-nav destinations (D-5 rev.2): Home (text) · Chat
 * (conversation) · Camera. Toggle-aware (plan §4 R2): the shell filters this
 * registry by `AppConfig.featureToggles`, so a brand without a mode simply never
 * registers its tab — no code edits. History/Settings are secondary (drawer).
 */
enum class TopLevelDestination(
    val navKey: NavKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val toggle: FeatureToggle,
) {
    HOME(TextNavKey, R.string.nav_home, Icons.Filled.Home, FeatureToggle.TEXT),
    CHAT(ChatNavKey, R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, FeatureToggle.DIALOG),
    CAMERA(CameraNavKey, R.string.nav_camera, Icons.Filled.PhotoCamera, FeatureToggle.CAMERA),
}
