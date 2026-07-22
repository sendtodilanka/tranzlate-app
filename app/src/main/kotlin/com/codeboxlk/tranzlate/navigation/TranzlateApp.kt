package com.codeboxlk.tranzlate.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo
import com.codeboxlk.tranzlate.feature.camera.CameraScreen
import com.codeboxlk.tranzlate.feature.history.HistoryScreen
import com.codeboxlk.tranzlate.feature.languagepicker.LanguagePickerScreen
import com.codeboxlk.tranzlate.feature.settings.SettingsScreen
import com.codeboxlk.tranzlate.feature.text.HomeScreen
import com.codeboxlk.tranzlate.feature.text.ResultScreen
import com.codeboxlk.tranzlate.feature.text.TextViewModel
import kotlinx.coroutines.launch

// UI_SPEC §2.3 drawer motion: the main screen is pushed right, scaled down,
// corner-rounded and dimmed while the sheet slides in — one continuous,
// gesture-driven progress (derived from DrawerState's live offset).
private const val CONTENT_PUSH_FRACTION = 0.6f
private const val CONTENT_SCALE_DELTA = 0.08f
private val CONTENT_CORNER_RADIUS = 28.dp

/**
 * The nav mediator (plan §3/§7 + D-5): **Compact = hub model — NO bottom bar**;
 * peers live ON the Home hub (composer / mic / tiles) and secondary
 * destinations in the ☰ modal drawer. Medium keeps the C-13 rail, Expanded the
 * permanent drawer (NavigationSuiteScaffold). All nav logic stays here —
 * features never navigate themselves.
 */
@Composable
@Suppress("LongMethod") // one cohesive nav mediator; splitting hides the shell structure
fun TranzlateApp(appConfig: AppConfig) {
    val destinations =
        remember(appConfig) {
            TopLevelDestination.entries.filter { it.toggle in appConfig.featureToggles }
        }
    val backStack = rememberNavBackStack(TextNavKey)
    val windowInfo = rememberWindowInfo()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val guidedSearch = stringResource(R.string.app_guided_search)

    val textViewModel: TextViewModel = hiltViewModel()
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val recents by drawerViewModel.recents.collectAsStateWithLifecycle()

    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    val drawerWidthPx = with(LocalDensity.current) { DrawerSheetWidth.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    recents = recents,
                    onDestinationClick = { destination ->
                        scope.launch {
                            drawerState.close()
                            openDrawerDestination(destination, ::navigateTo) {
                                snackbarHostState.showSnackbar(guidedSearch)
                            }
                        }
                    },
                )
            },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Gesture-driven fraction from the sheet's live offset
                            // (closed = -width … open = 0); read HERE so drags
                            // animate the layer without recomposition.
                            val offset = drawerState.currentOffset
                            val fraction =
                                if (offset.isNaN()) 0f else (1f + offset / drawerWidthPx).coerceIn(0f, 1f)
                            translationX = fraction * drawerWidthPx * CONTENT_PUSH_FRACTION
                            scaleX = 1f - fraction * CONTENT_SCALE_DELTA
                            scaleY = 1f - fraction * CONTENT_SCALE_DELTA
                            if (fraction > 0f) {
                                shape = RoundedCornerShape(CONTENT_CORNER_RADIUS * fraction)
                                clip = true
                            }
                        },
            ) {
                if (windowInfo.isCompact) {
                    // D-5 hub model: no bottom nav bar on Compact.
                    AppNavDisplay(
                        backStack = backStack,
                        textViewModel = textViewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigate = ::navigateTo,
                    )
                } else {
                    NavigationSuiteScaffold(
                        layoutType =
                            if (windowInfo.isExpanded) {
                                NavigationSuiteType.NavigationDrawer
                            } else {
                                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                                    currentWindowAdaptiveInfo(),
                                )
                            },
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
                        AppNavDisplay(
                            backStack = backStack,
                            textViewModel = textViewModel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onNavigate = ::navigateTo,
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Drawer section → destination mapping (UI_SPEC §2.3): Search has no surface
 * yet → guided message, never a dead row (EDGE_CASES no-dead-end); Saved lives
 * on the History surface (GT-style tabs later).
 */
private suspend fun openDrawerDestination(
    destination: DrawerDestination,
    navigateTo: (NavKey) -> Unit,
    showSearchGuided: suspend () -> Unit,
) {
    when (destination) {
        DrawerDestination.SEARCH -> showSearchGuided()
        DrawerDestination.HISTORY -> navigateTo(HistoryNavKey)
        DrawerDestination.SAVED -> navigateTo(HistoryNavKey)
        DrawerDestination.OFFLINE_LANGUAGES -> navigateTo(LanguagesNavKey)
        DrawerDestination.SETTINGS -> navigateTo(SettingsNavKey)
    }
}

/** The one NavDisplay both size classes share (Nav3 — plan §7 risk isolation). */
@Composable
private fun AppNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    textViewModel: TextViewModel,
    onOpenDrawer: () -> Unit,
    onNavigate: (NavKey) -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<TextNavKey> {
                    HomeScreen(
                        viewModel = textViewModel,
                        onOpenDrawer = onOpenDrawer,
                        onTranslateRequested = { onNavigate(ResultNavKey) },
                        onOpenCamera = { onNavigate(CameraNavKey) },
                    )
                }
                entry<ResultNavKey> {
                    ResultScreen(
                        viewModel = textViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<CameraNavKey> { CameraScreen() }
                entry<HistoryNavKey> { HistoryScreen() }
                entry<LanguagesNavKey> { LanguagePickerScreen() }
                entry<SettingsNavKey> { SettingsScreen() }
            },
    )
}
