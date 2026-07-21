package com.codeboxlk.tranzlate.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo
import com.codeboxlk.tranzlate.feature.camera.CameraScreen
import com.codeboxlk.tranzlate.feature.history.HistoryScreen
import com.codeboxlk.tranzlate.feature.settings.SettingsScreen
import com.codeboxlk.tranzlate.feature.text.TextScreen

/**
 * Thin adaptive shell (plan §3/§7): NavigationSuiteScaffold bar (Compact) →
 * rail (Medium) → permanent drawer (Expanded, layoutType override — the default
 * calculation never returns drawer), with Navigation 3 NavDisplay mediating the
 * toggle-filtered top-level destinations (plan §4 R2). All nav logic stays in
 * this mediator — features never navigate themselves (Nav3-ecosystem risk
 * isolation, plan §7).
 */
@Composable
fun TranzlateApp(appConfig: AppConfig) {
    val destinations = remember(appConfig) {
        TopLevelDestination.entries.filter { it.toggle in appConfig.featureToggles }
    }
    val backStack = rememberNavBackStack(TextNavKey)
    val windowInfo = rememberWindowInfo()
    val layoutType = if (windowInfo.isExpanded) {
        NavigationSuiteType.NavigationDrawer
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            destinations.forEach { destination ->
                item(
                    selected = backStack.lastOrNull() == destination.navKey,
                    onClick = {
                        if (backStack.lastOrNull() != destination.navKey) {
                            backStack.add(destination.navKey)
                            // top-level switch = single-entry stack (GT-style)
                            while (backStack.size > 1) backStack.removeAt(0)
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                    modifier = Modifier.testTag("tt_app_nav_${destination.name.lowercase()}"),
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<TextNavKey> { TextScreen() }
                entry<CameraNavKey> { CameraScreen() }
                entry<HistoryNavKey> { HistoryScreen() }
                entry<SettingsNavKey> { SettingsScreen() }
            },
        )
    }
}
