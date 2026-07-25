package com.codeboxlk.tranzlate.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo
import com.codeboxlk.tranzlate.feature.camera.CameraScreen
import com.codeboxlk.tranzlate.feature.history.HistoryScreen
import com.codeboxlk.tranzlate.feature.settings.SettingsScreen
import com.codeboxlk.tranzlate.feature.text.HomeScreen
import com.codeboxlk.tranzlate.feature.text.LanguagePickerScreen
import com.codeboxlk.tranzlate.feature.text.LanguagePickerTarget
import com.codeboxlk.tranzlate.feature.text.ResultScreen
import com.codeboxlk.tranzlate.feature.text.TextViewModel
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.feature.languagepicker.LanguagePickerScreen as OfflineLanguagesScreen

/**
 * The nav mediator (D-5 rev.3 — Claude Design "Offline Translator M3"): the
 * approved design has **no bottom bar and no FAB**; every destination is reached
 * from the Home card stack's tool cards and list rows, so the shell is just the
 * NavDisplay. All nav logic stays here — features never navigate themselves.
 */
@Composable
fun TranzlateApp(
    @Suppress("UNUSED_PARAMETER") appConfig: AppConfig,
) {
    val backStack = rememberNavBackStack(TextNavKey)
    val textViewModel: TextViewModel = hiltViewModel()

    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    AppNavDisplay(
        backStack = backStack,
        textViewModel = textViewModel,
        onNavigate = ::navigateTo,
    )
}

/** The one NavDisplay both size classes share (Nav3 — plan §7 risk isolation). */
@Composable
private fun AppNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    textViewModel: TextViewModel,
    onNavigate: (NavKey) -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        // Guard the pop: NavDisplay throws if the back stack is emptied. On a
        // non-Compact window a top-level switch trims the stack to a single entry
        // (see the rail's onClick), so a back from that lone destination — now
        // reachable as a tap via the Settings top-bar arrow — would otherwise leave
        // it empty and crash. Guarding here covers every destination, not just
        // Settings.
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        // NavDisplay's default entryDecorators is only the saveable-state one; a
        // hiltViewModel() called inside an entry<> would otherwise resolve to the
        // Activity's ViewModelStore and never be cleared. The ViewModelStore
        // decorator scopes each entry's ViewModels to the destination and clears
        // them when it is popped. SettingsScreen is the first entry to acquire its
        // own ViewModel; the hoisted TextViewModel/DrawerViewModel are unaffected.
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<TextNavKey> {
                    HomeScreen(
                        viewModel = textViewModel,
                        onTranslateRequested = { onNavigate(ResultNavKey) },
                        onPickLanguage = { target ->
                            onNavigate(
                                LanguagePickerNavKey(forSource = target == LanguagePickerTarget.SOURCE),
                            )
                        },
                        onOpenSettings = { onNavigate(SettingsNavKey) },
                        onOpenCamera = { onNavigate(CameraNavKey) },
                        onOpenLanguages = { onNavigate(LanguagesNavKey) },
                        onOpenConversation = { onNavigate(ChatNavKey) },
                    )
                }
                entry<ResultNavKey> {
                    ResultScreen(
                        viewModel = textViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<LanguagePickerNavKey> { key ->
                    LanguagePickerScreen(
                        viewModel = textViewModel,
                        target =
                            if (key.forSource) {
                                LanguagePickerTarget.SOURCE
                            } else {
                                LanguagePickerTarget.TARGET
                            },
                        onDone = { backStack.removeLastOrNull() },
                    )
                }
                entry<CameraNavKey> { CameraScreen() }
                entry<ChatNavKey> {
                    ComingSoonScreen(
                        title = stringResource(R.string.chat_coming_soon_title),
                        icon = Icons.AutoMirrored.Filled.Chat,
                    )
                }
                entry<HistoryNavKey> { HistoryScreen() }
                // Drawer "Offline languages" = the :feature:languagepicker
                // placeholder (download/delete packs) — a different job from the
                // text vertical's source/target picker above, hence the alias.
                entry<LanguagesNavKey> { OfflineLanguagesScreen() }
                entry<SettingsNavKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
            },
    )
}
