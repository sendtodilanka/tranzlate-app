package com.codeboxlk.tranzlate.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.codeboxlk.tranzlate.MainActivityViewModel
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.ui.languageLabel
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo
import com.codeboxlk.tranzlate.feature.history.HistoryScreen
import com.codeboxlk.tranzlate.feature.language.AlreadySourceSheet
import com.codeboxlk.tranzlate.feature.language.LanguagePickerScreen
import com.codeboxlk.tranzlate.feature.language.OfflineLanguagesScreen
import com.codeboxlk.tranzlate.feature.language.OfflinePackMissingSheet
import com.codeboxlk.tranzlate.feature.language.PickerDialogHost
import com.codeboxlk.tranzlate.feature.language.PickerHost
import com.codeboxlk.tranzlate.feature.language.pickerHost
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
    // The app-shell state VM (theme + the two composer-raised sheets, 19h/19m).
    // hiltViewModel() at the composition root resolves to the Activity's
    // ViewModelStore — the SAME instance MainActivity reads for the splash gate.
    val mainViewModel: MainActivityViewModel = hiltViewModel()

    // 17c/17d: on a tablet the picker is a card over whatever is on screen, so it
    // is NOT a back-stack entry — the screen behind it has to stay composed, and
    // NavDisplay composes the top entry and nothing else. Its presence is this
    // flag, and the role it was opened for is the flag's value.
    //
    // `rememberSaveable` so the card survives process death, which is where the
    // ruling asks the state to live for this host (§2). It is deliberately NOT
    // derived from the window: re-asking `pickerHost` every composition would
    // close the card mid-use the moment the user resized or unfolded, which is
    // what "keep-host-until-closed" refuses. The host is decided once, when the
    // chip is tapped, and the card outlives any answer that changes underneath
    // it.
    var pickerDialogForSource: Boolean? by rememberSaveable { mutableStateOf(null) }
    val window = rememberWindowInfo()
    val hostForNextPicker =
        pickerHost(window.widthClass, window.heightCompact, window.posture, window.hinged)

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

    // #158 — the push mirror of the guarded pop. `composedTop` is the top AS
    // COMPOSED: a snapshot read, so each recomposition makes a new value and a new
    // `navigateTo` closing over it. Two Home cards tapped in the SAME frame hold the
    // same stale `navigateTo` (no recomposition between them), so both carry
    // `from = this top`; the first push moves the top and [pushEntry] declines the
    // second. Every push — the screen callbacks AND the shell's picker/manage-packs
    // pushes below — funnels through here, so this is the one push chokepoint, as
    // [popEntry] is the one pop chokepoint.
    val composedTop = backStack.lastOrNull()

    fun navigateTo(key: NavKey) {
        pushEntry(backStack, from = composedTop, key = key)
    }

    // The ONE place a NEW picker is opened, so the window's host answer is read
    // exactly ONCE here (`PickerHostRoutingTest` pins the count — a second read
    // anywhere would gate the OPEN card on the window and close it under a user
    // who unfolded mid-search). Used by the language chips AND by 19m's "Pick
    // another", which both open a picker for a role and neither of which decides
    // an already-open card.
    fun openPicker(role: LanguageRole) {
        openLanguagePicker(
            role = role,
            host = hostForNextPicker,
            openDialog = { pickerDialogForSource = it == LanguageRole.SOURCE },
            navigate = ::navigateTo,
        )
    }

    SharedTransitionLayout {
        AppNavDisplay(
            backStack = backStack,
            textViewModel = textViewModel,
            onNavigate = ::navigateTo,
            onPickLanguage = ::openPicker,
            onOfflinePackMissing = mainViewModel::onOfflinePackMissing,
            sharedScope = this,
        )
    }

    // Composed AFTER the NavDisplay and outside it — both halves matter. Outside,
    // because a picker inside the display would replace the screen the card is
    // supposed to be sitting over; after, because that is what puts the card on
    // top of it. The ViewModel scoping this host needs is `PickerDialogHost`'s
    // own problem and is solved there; nothing about the picker is hoisted to
    // this shell, which is the point.
    pickerDialogForSource?.let { forSource ->
        PickerDialogHost(
            role = if (forSource) LanguageRole.SOURCE else LanguageRole.TARGET,
            onDismiss = { pickerDialogForSource = null },
            onManagePacks = {
                manageLanguagePacks(
                    dismissDialog = { pickerDialogForSource = null },
                    navigate = ::navigateTo,
                )
            },
        )
    }

    // Sheet 19h (#130 PR-20) — composed AFTER the NavDisplay and OUTSIDE it, the
    // reasons PickerDialogHost is: a sheet inside the display would replace the
    // screen it is meant to sit over. It is raised by the Text composer (which
    // HOISTS onOfflinePackMissing into `mainViewModel`) but hosted here, because
    // the composer cannot host a `:feature:language` sheet without `:feature:text`
    // depending on that module — so the composition root, which already depends on
    // every feature, does (ruling §0 P3, :26).
    val offlinePackMissing by mainViewModel.offlinePackMissing.collectAsStateWithLifecycle()
    offlinePackMissing?.let { request ->
        OfflinePackMissingSheet(
            missingLangId = request.missingLangId,
            onDeviceLangIds = request.onDeviceLangIds,
            onUse = mainViewModel::useLanguage,
            onClose = mainViewModel::dismissOfflinePackMissing,
        )
    }

    // Sheet 19m (#130 PR-20) — the duplicate-selection guard, hosted the same way.
    // It reads `textViewModel.duplicateSelection` directly (the VM is already at
    // this shell), so nothing is hoisted for it. Suppressed while a picker is open
    // so that "Pick another" — which reopens the target picker — does not leave 19m
    // floating over it. A degenerate pair has no valid no-op to return to, so every
    // way out RESOLVES it: Swap and a scrim/back dismiss both restore the swapped
    // pair (the likely intent, ruling §2 "dismiss is always a state-machine
    // action — no dead end"), and Pick another reopens the picker. The guard clears
    // itself the instant source ≠ target again.
    val duplicateSelection by textViewModel.duplicateSelection.collectAsStateWithLifecycle()
    val pickerOpen = pickerDialogForSource != null || backStack.lastOrNull() is LanguagePickerNavKey
    duplicateSelection?.takeUnless { pickerOpen }?.let { duplicateId ->
        AlreadySourceSheet(
            languageName = languageLabel(duplicateId),
            onSwap = { textViewModel.onSwapLanguages() },
            onPickAnother = { openPicker(LanguageRole.TARGET) },
            onDismiss = { textViewModel.onSwapLanguages() },
        )
    }
}

/**
 * A language chip was tapped: open the picker in whichever host this window gets.
 *
 * A function rather than four lines inside the shell so the branch can be driven
 * from a JVM test — the same reason [popEntry] is one. What it must never do is
 * BOTH: pushing the destination as well as raising the card would leave a card
 * over a picker over the composer, and dismissing the card would reveal the
 * screen it was meant to replace.
 */
internal fun openLanguagePicker(
    role: LanguageRole,
    host: PickerHost,
    openDialog: (LanguageRole) -> Unit,
    navigate: (NavKey) -> Unit,
) {
    when (host) {
        PickerHost.DIALOG -> {
            openDialog(role)
        }

        PickerHost.NAV_ENTRY -> {
            navigate(LanguagePickerNavKey(forSource = role == LanguageRole.SOURCE))
        }
    }
}

/**
 * The card's docked "Manage packs" action.
 *
 * **The order is the whole function** (ruling §2: `dialogVisible = false` THEN
 * `push(LanguagesNavKey)`). The card is not on the back stack, so pushing does
 * not dismiss it: push first and the user arrives at Manage packs with the
 * language picker still floating over it, and with no way back to the screen the
 * card belonged to. Dismiss first and the card is gone before the destination
 * arrives.
 *
 * Written as a named function for exactly that reason — an order that only lives
 * as two adjacent lines inside a lambda is an order no test can name.
 */
internal fun manageLanguagePacks(
    dismissDialog: () -> Unit,
    navigate: (NavKey) -> Unit,
) {
    dismissDialog()
    navigate(LanguagesNavKey)
}

/** The one NavDisplay both size classes share (Nav3 — plan §7 risk isolation). */
@Composable
private fun AppNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    textViewModel: TextViewModel,
    onNavigate: (NavKey) -> Unit,
    onPickLanguage: (LanguageRole) -> Unit,
    onOfflinePackMissing: (String?) -> Unit,
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
                        onPickLanguage = onPickLanguage,
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
                        onPickLanguage = onPickLanguage,
                        onOfflinePackMissing = onOfflinePackMissing,
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
                        // Sheet 19b's one action (#130 PR-18). A PUSH, not the
                        // card's dismiss-then-push: this picker IS on the back
                        // stack, so Manage packs lands on top of it and Back
                        // returns to the language the user was trying to get.
                        onManagePacks = { onNavigate(LanguagesNavKey) },
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
 * The ONE push, guarded as its mirror [popEntry] is — the same stale-event race, the
 * other direction (issue #158).
 *
 * `navigateTo` used to refuse only a key equal to the current top. That stopped a
 * double-tap on ONE card but not two DIFFERENT cards tapped in the same frame: two
 * different keys both pass the equal-key test, and because a `SnapshotStateList`
 * mutation is applied synchronously while recomposition is only SCHEDULED, the second
 * callback reads the stack the first already grew and pushes again — `[Text, Camera,
 * Languages]` from one gesture, the wanted screen underneath. Measured reachable on a
 * real device (`NavDoublePushReachabilityTest`): a two-finger tap fires both cards'
 * `onClick` in one frame, before `NavDisplay` can recompose the leaving screen away.
 *
 * The answer is [popEntry]'s: [from] is the destination that rendered the tapped
 * affordance, captured at COMPOSITION (`composedTop` in the shell), and the push is
 * declined once that destination is no longer the top. Two same-frame taps hold the
 * same stale [from]; the first push moves the top, so the second — a card on a screen
 * already leaving — is refused.
 *
 * Unlike a pop, a push ALWAYS has a caller (a user tapped a visible affordance), so
 * [from] is never legitimately null here; it stays nullable only to mirror [popEntry]
 * and to no-op rather than crash if ever called without one.
 *
 * Keeping the self-dedup (top already equals [key]) is load-bearing, not incidental:
 * [popEntry]'s identity check is sound ONLY because no two adjacent entries are ever
 * equal, which this self-dedup is what guarantees.
 *
 * Plain `MutableList` and no Compose in the signature, so the invariant is reachable
 * from a JVM test (`BackStackPushTest`); the device only proves the same-frame race is
 * real, which CI cannot run (#40).
 *
 * @param from the destination that owns the tapped affordance, captured at composition.
 * @param key the destination to push.
 * @return the key actually pushed, or null when the request was declined.
 */
internal fun pushEntry(
    backStack: MutableList<NavKey>,
    from: NavKey?,
    key: NavKey,
): NavKey? {
    if (from != null && backStack.lastOrNull() != from) return null
    if (backStack.lastOrNull() == key) return null
    backStack.add(key)
    return key
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
 * The identity check is sound because the push side ([pushEntry]) refuses to stack a
 * key on top of itself, so no two adjacent entries are ever equal — "the top still
 * equals [from]" can only mean the caller's own entry.
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
