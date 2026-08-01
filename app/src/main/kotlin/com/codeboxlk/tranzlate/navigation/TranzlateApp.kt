package com.codeboxlk.tranzlate.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.feature.history.HistoryScreen
import com.codeboxlk.tranzlate.feature.language.LanguagePickerScreen
import com.codeboxlk.tranzlate.feature.language.OfflineLanguagesScreen
import com.codeboxlk.tranzlate.feature.paywall.PaywallScreen
import com.codeboxlk.tranzlate.feature.settings.SettingsScreen
import com.codeboxlk.tranzlate.feature.text.COMPOSER_CARD_SHARED_KEY
import com.codeboxlk.tranzlate.feature.text.ComposerScreen
import com.codeboxlk.tranzlate.feature.text.HomeScreen
import com.codeboxlk.tranzlate.feature.text.TextViewModel

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

    SharedTransitionLayout {
        AppNavDisplay(
            backStack = backStack,
            textViewModel = textViewModel,
            onNavigate = ::navigateTo,
            sharedScope = this,
        )
    }
}

/** The one NavDisplay both size classes share (Nav3 — plan §7 risk isolation). */
@Composable
private fun AppNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    textViewModel: TextViewModel,
    onNavigate: (NavKey) -> Unit,
    sharedScope: SharedTransitionScope,
) {
    NavDisplay(
        backStack = backStack,
        // Guard the pop: NavDisplay throws if the back stack is emptied. On a
        // non-Compact window a top-level switch trims the stack to a single entry
        // (see the rail's onClick), so a back from that lone destination — now
        // reachable as a tap via the Settings top-bar arrow — would otherwise leave
        // it empty and crash. Guarding here covers every destination, not just
        // Settings.
        onBack = {
            if (backStack.size > 1) {
                // Requirement D: leaving 5a discards the draft, whichever way
                // the user leaves — the button and the system gesture share this.
                if (backStack.lastOrNull() == ComposerNavKey) textViewModel.onComposerDismissed()
                backStack.removeLastOrNull()
            }
        },
        // NavDisplay's default entryDecorators is only the saveable-state one; a
        // hiltViewModel() called inside an entry<> would otherwise resolve to the
        // Activity's ViewModelStore and never be cleared. The ViewModelStore
        // decorator scopes each entry's ViewModels to the destination and clears
        // them when it is popped. SettingsScreen is the first entry to acquire its
        // own ViewModel; the hoisted TextViewModel is unaffected.
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
                        onOpenComposer = { onNavigate(ComposerNavKey) },
                        onPickLanguage = { target ->
                            onNavigate(
                                LanguagePickerNavKey(forSource = target == LanguageRole.SOURCE),
                            )
                        },
                        onOpenSettings = { onNavigate(SettingsNavKey) },
                        onOpenPaywall = { onNavigate(PaywallNavKey) },
                        onOpenCamera = { onNavigate(CameraNavKey) },
                        onOpenLanguages = { onNavigate(LanguagesNavKey) },
                        onOpenConversation = { onNavigate(ChatNavKey) },
                        previewCardModifier =
                            with(sharedScope) {
                                Modifier.sharedBounds(
                                    sharedContentState =
                                        rememberSharedContentState(COMPOSER_CARD_SHARED_KEY),
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    resizeMode = SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds,
                                )
                            },
                    )
                }
                entry<ComposerNavKey> {
                    ComposerScreen(
                        viewModel = textViewModel,
                        onBack = {
                            textViewModel.onComposerDismissed()
                            backStack.removeLastOrNull()
                        },
                        onOpenPaywall = { onNavigate(PaywallNavKey) },
                        onPickLanguage = { target ->
                            onNavigate(
                                LanguagePickerNavKey(forSource = target == LanguageRole.SOURCE),
                            )
                        },
                        cardModifier =
                            with(sharedScope) {
                                Modifier.sharedBounds(
                                    sharedContentState =
                                        rememberSharedContentState(COMPOSER_CARD_SHARED_KEY),
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    resizeMode = SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds,
                                )
                            },
                    )
                }
                // No TextViewModel handed in since the #130 rev.3 decouple:
                // the picker's own ViewModel reads/writes the selection through
                // TranslatePrefsRepository — the same DataStore keys the chips
                // read — so coherence needs no shared handle.
                entry<LanguagePickerNavKey> { key ->
                    LanguagePickerScreen(
                        target = if (key.forSource) LanguageRole.SOURCE else LanguageRole.TARGET,
                        onDone = { backStack.removeLastOrNull() },
                    )
                }
                // Camera + Conversation are BOTH doors to features that have not
                // shipped (issue #78 open · Dialog deferred to v2). Neither may be
                // a blank screen the user has to guess their way out of, so both
                // land on the one honest placeholder with a back arrow.
                entry<CameraNavKey> {
                    ComingSoonScreen(
                        title = stringResource(R.string.camera_coming_soon_title),
                        message = stringResource(R.string.camera_coming_soon_body),
                        icon = Icons.Filled.PhotoCamera,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<ChatNavKey> {
                    ComingSoonScreen(
                        title = stringResource(R.string.chat_coming_soon_title),
                        message = stringResource(R.string.chat_coming_soon_body),
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<HistoryNavKey> {
                    HistoryScreen(
                        viewModel = hiltViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                        onPick = { translation ->
                            textViewModel.onHistoryPick(translation)
                            onNavigate(ComposerNavKey)
                        },
                    )
                }
                // "Offline languages" (Home entries) = the :feature:language
                // placeholder (download/delete packs) — a different job from the
                // text vertical's source/target picker above, hence the alias.
                entry<LanguagesNavKey> {
                    OfflineLanguagesScreen(
                        viewModel = hiltViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<PaywallNavKey> {
                    PaywallScreen(
                        viewModel = hiltViewModel(),
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
                entry<SettingsNavKey> {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenHistory = { onNavigate(HistoryNavKey) },
                    )
                }
            },
    )
}
