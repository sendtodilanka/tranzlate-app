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
import androidx.lifecycle.compose.LifecycleStartEffect
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

    // Issue #149 / #159 co-verify: this ViewModel is hoisted OUTSIDE the
    // NavDisplay entries, so it resolves to the Activity's ViewModelStore and
    // onCleared() runs only when the Activity finishes. Backgrounding with a
    // result on screen therefore released nothing, and the speech engine — a
    // bound service connection the platform never takes back on its own — stayed
    // pinned at visible-app importance behind a backgrounded app, which is the
    // harm #149 reported.
    //
    // The lifetime belongs HERE and not in a screen: the engine has to go back
    // when the APP stops, whichever destination is on top, and the composer is
    // not composed while the picker or Settings is. This effect is scoped to the
    // same host the ViewModel is, so the two cannot drift.
    LifecycleStartEffect(textViewModel) {
        textViewModel.onHostStarted()
        onStopOrDispose { textViewModel.onHostStopped() }
    }

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
    // EVERY way out of a destination funnels through here — the system gesture,
    // every top-bar arrow, the picker's Done, the paywall's Close. [popEntry] owns
    // the two invariants; this owns the one side effect a pop can carry —
    // requirement D: leaving 5a discards the draft, whichever way the user leaves.
    // It fires off the key [popEntry] RETURNS, so it runs exactly when the composer
    // entry actually came off and never on a pop that was declined.
    // What the user is looking at, captured at composition rather than read at
    // event time — see `onBack` below for why the difference is the whole fix.
    val composedTop = backStack.lastOrNull()

    fun pop(from: NavKey? = null) {
        popEntry(backStack, from) { removed ->
            if (removed == ComposerNavKey) textViewModel.onComposerDismissed()
        }
    }

    NavDisplay(
        backStack = backStack,
        // System back gets the top AS COMPOSED, not as it is when the event
        // lands. A lens found the hole: NavDisplay counts entries it remembered
        // (`DecoratedNavEntries.kt:125` — `remember(backStack.toList())`), so a
        // Done tap and a back press in the same frame both see a stack the tap
        // has already shortened. Clamp-only let the second one take the
        // composer, draft and all — #150 again, through the door #150 exempted.
        //
        // Passing the composed top closes it. Safe today because this app uses
        // only `SinglePaneSceneStrategy`, where `previousEntries =
        // entries.dropLast(1)` (`SinglePaneScene.kt:65`), so NavDisplay's
        // `repeat(entries.size - previousEntries.size)` is always exactly one.
        // A multi-pane strategy would fire it more than once and the extra
        // calls would be declined — revisit here if one is ever added.
        onBack = { pop(from = composedTop) },
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
                        onBack = { pop(ComposerNavKey) },
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
                        // `key`, not a bare `LanguagePickerNavKey` — the picker is
                        // the one key carrying data, so identity means THIS picker.
                        onDone = { pop(key) },
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
                        onBack = { pop(CameraNavKey) },
                    )
                }
                entry<ChatNavKey> {
                    ComingSoonScreen(
                        title = stringResource(R.string.chat_coming_soon_title),
                        message = stringResource(R.string.chat_coming_soon_body),
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onBack = { pop(ChatNavKey) },
                    )
                }
                entry<HistoryNavKey> {
                    HistoryScreen(
                        viewModel = hiltViewModel(),
                        onBack = { pop(HistoryNavKey) },
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
                        onBack = { pop(LanguagesNavKey) },
                    )
                }
                entry<PaywallNavKey> {
                    PaywallScreen(
                        viewModel = hiltViewModel(),
                        onClose = { pop(PaywallNavKey) },
                    )
                }
                entry<SettingsNavKey> {
                    SettingsScreen(
                        onBack = { pop(SettingsNavKey) },
                        onOpenHistory = { onNavigate(HistoryNavKey) },
                    )
                }
            },
    )
}

/**
 * The ONE pop. Nine call sites used to remove an entry by hand and exactly one of
 * them was guarded (issue #150), so the rule had to be remembered eight more
 * times and never was. It lives here instead.
 *
 * Two different hazards, one home:
 *
 * **1 — never empty the stack.** `NavDisplay` hard-requires a non-empty back stack
 * (`NavDisplay.kt:361`, `require(backStack.isNotEmpty()) { "NavDisplay backstack
 * cannot be empty" }`), so a pop at the root is a crash, not a no-op. On a
 * non-Compact window a top-level switch trims the stack to one entry, from which
 * the Settings arrow is a reachable tap — that is the case the original guard was
 * written for.
 *
 * **2 — a screen may only pop ITSELF.** Two taps landing in the same frame — a
 * double-tap on the picker's Done, or a slow frame during a fling — re-enter the
 * same callback before recomposition, so the second one popped the screen
 * UNDERNEATH. Clamping alone hides that only at depth 2: from Home → 5a → picker
 * it still throws the user back to Home, and the composer leaves WITHOUT
 * `TextViewModel.onComposerDismissed`, which is the stale-draft bug #48 returning
 * by another door. So [from] names the caller and the pop is declined once that
 * destination is no longer on top: the second tap is a no-op because the screen is
 * already leaving, not merely clamped at the root.
 *
 * The identity check is sound because the push side refuses to stack a key on top
 * of itself (`navigateTo`), so no two adjacent entries are ever equal — "the top
 * still equals [from]" can only mean the caller's own entry.
 *
 * [from] is null for SYSTEM back, which has no caller and must NOT be
 * identity-checked: `NavDisplay` fires `onBack` once PER ENTRY it needs removed
 * (`NavDisplay.kt:564`, `repeat(entries.size - scene.previousEntries.size)`), so a
 * multi-entry predictive-back gesture legitimately calls it more than once and the
 * size clamp is the only limit that may apply to it.
 *
 * Plain `MutableList` (which `NavBackStack` is — it delegates to a
 * `SnapshotStateList`) and no Compose in the signature, so the invariant is
 * reachable from a JVM test; this repo has no instrumentation harness (#40, #111).
 *
 * @param from the destination asking to leave, or null for system back.
 * @return the key actually removed, or null when the request was declined.
 */
internal fun popEntry(
    backStack: MutableList<NavKey>,
    from: NavKey? = null,
    onRemoved: (NavKey) -> Unit = {},
): NavKey? {
    if (backStack.size <= 1) return null
    if (from != null && backStack.last() != from) return null
    return backStack.removeLastOrNull()?.also(onRemoved)
}
