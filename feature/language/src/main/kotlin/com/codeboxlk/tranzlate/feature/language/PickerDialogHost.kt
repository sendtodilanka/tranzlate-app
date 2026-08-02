package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.LanguageRole

/**
 * The name the picker's dialog-scoped ViewModel store lives under.
 *
 * A constant rather than the call site's composite key, which is what
 * `rememberViewModelStoreOwner()` would use on its own. The composite key is
 * derived from where this composable sits in the composition tree, and the whole
 * point of the scope is that it must resolve to the SAME store after a rotation
 * — which is a different composition. A name cannot drift; a structural hash
 * can, silently, and the symptom would be the one PR-13 warned about: the card
 * comes back with an empty search field.
 */
private const val PICKER_DIALOG_SCOPE = "picker.dialog"

/**
 * 17c/17d — the picker raised as a card over the screen that asked for it.
 *
 * ## Why this is not a destination
 *
 * On a phone the picker takes the window, because there is no room to show
 * anything else. On a tablet the composer the user is translating in is 800dp
 * wide and right there; replacing it with a list of languages throws away the
 * context the choice is being made in. So the card sits over it and the screen
 * stays visible around the edges — which only works if the screen underneath is
 * still COMPOSED, and that is the constraint that decides everything below.
 * `NavDisplay` composes the top entry of the back stack and nothing else, so a
 * dialog raised from inside a picker entry would have an empty window behind its
 * scrim. The picker therefore does not go on the back stack in this host at all;
 * the shell holds a saved flag instead (ruling §2, and R13: this composition is
 * Nav-EXTERNAL, never a Nav3 overlay entry).
 *
 * ## The ViewModel, and the scope that would have been wrong
 *
 * That decision is exactly what makes the ViewModel the hard part.
 * `hiltViewModel()` resolves against `LocalViewModelStoreOwner`, which inside
 * `NavDisplay` is the nav entry — cleared when the entry is popped — and outside
 * it is the **Activity**. Called plainly here it would give the picker a
 * ViewModel that outlives the card, every screen and every trip to another
 * destination: a third screen-outliving scope, which the ruling's §2 inventory
 * bounces at review, and whose visible symptom is that reopening the card
 * restores the search someone typed ten minutes ago.
 *
 * The seam is that [LanguagePickerScreen] takes its ViewModel as a parameter, so
 * this host can decide the scope instead of inheriting it.
 * [rememberViewModelStoreProvider] builds a child scope INSIDE the Activity's
 * store, and its own `DisposableEffect` clears it when this composable leaves
 * the composition **unless the parent lifecycle is already destroyed** — which
 * is to say: cleared when the card closes, kept across a rotation. That is
 * precisely the lifetime a screen has, without the card being one, and it is
 * androidx's own code rather than a lifecycle hand-rolled here: it is the same
 * mechanism `rememberViewModelStoreNavEntryDecorator` gives every nav entry.
 *
 * [rememberViewModelStoreOwner] then wraps that store with the Activity's
 * `SavedStateRegistryOwner`, so `SavedStateHandle` — where PR-13 put the search
 * query and the list position — survives process death here exactly as it does
 * in a nav entry.
 *
 * The two hosts never coexist: the card is opened from a language chip, and that
 * chip is not on screen while the picker destination is. So one
 * `LanguagePickerViewModel` exists at a time and the two scopes cannot argue
 * about the same saved-state slot.
 *
 * ## What the card draws
 *
 * The picker itself, unchanged, plus the one thing the export adds and no other
 * host has: a docked action bar. See [PickerDialogActions].
 *
 * @param role which side of the pair is being chosen — the card is one picker in
 *   one role, exactly as 17a is.
 * @param onDismiss close the card and change nothing. The Close cross, the
 *   Cancel action, the system back gesture and a tap on the scrim all arrive
 *   here, and so does a completed selection: the picker writes the choice
 *   through its own ViewModel and then asks its host to go away.
 * @param onManagePacks the docked "Manage packs" action. The shell owns what it
 *   does, because it involves the back stack and features never navigate
 *   themselves — see `TranzlateApp.manageLanguagePacks` for the ORDER, which is
 *   load-bearing.
 */
@Composable
fun PickerDialogHost(
    role: LanguageRole,
    onDismiss: () -> Unit,
    onManagePacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ONE line this host exists for. Everything else about the card is in
    // [PickerDialogWindow], which knows nothing about ViewModels and can
    // therefore be rendered by a test; this call is held to its shape by
    // `PickerHostAgnosticTest`, honest about being a source rule.
    val viewModel: LanguagePickerViewModel = hiltViewModel(rememberPickerDialogScope())

    PickerDialogWindow(onDismiss = onDismiss, modifier = modifier) {
        LanguagePickerScreen(
            target = role,
            onDone = onDismiss,
            modifier = Modifier.weight(1f),
            host = PickerHost.DIALOG,
            viewModel = viewModel,
        )
        PickerDialogActions(onManagePacks = onManagePacks, onCancel = onDismiss)
    }
}

/**
 * The card's own ViewModel scope: a child of the Activity's store that is
 * cleared when the card closes and kept across a rotation.
 *
 * Both halves are androidx's, in [rememberViewModelStoreProvider]'s own
 * `DisposableEffect` — it clears the child store when this composable leaves the
 * composition **unless the parent lifecycle is already destroyed**, which is the
 * one case that means "a configuration change is in flight" rather than "the
 * user closed it". The store itself lives inside the parent's `ViewModelStore`
 * (`parentStore.getOrPut(parentKey)`), which is what makes it survive the
 * rotation at all.
 *
 * [rememberViewModelStoreOwner] then attaches the Activity's
 * `SavedStateRegistryOwner`, so a `SavedStateHandle` in this scope is saved and
 * restored across process death exactly as a nav entry's is — which is where
 * PR-13 put the search query and the list position.
 *
 * Named and `internal` so the lifetime can be tested without Hilt:
 * `PickerDialogScopeTest` drives this function directly with a plain ViewModel.
 */
@Composable
internal fun rememberPickerDialogScope(): ViewModelStoreOwner {
    val parentOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "PickerDialogHost needs a ViewModelStoreOwner to hang the picker's scope off"
        }
    val scopeProvider =
        rememberViewModelStoreProvider(key = PICKER_DIALOG_SCOPE, parent = parentOwner)
    return rememberViewModelStoreOwner(key = PICKER_DIALOG_SCOPE, provider = scopeProvider)
}

/**
 * The dialog window, the scrim tap and the card — everything about this host
 * except the ViewModel.
 *
 * Split from [PickerDialogHost] so that a test can render it: `hiltViewModel`
 * needs a Hilt-instrumented application, and this module's Compose tests are
 * plain Robolectric (#186). What is left here is every behaviour the ruling asks
 * for a test of — back dismisses, the card is the size it should be — with
 * nothing injected.
 *
 * **One measurement, one source.** The card's width and height come from this
 * `BoxWithConstraints`, and the arrangement inside the card comes from the
 * picker's own, nested in it. Reading the window a second time (through
 * `LocalWindowInfo` or the configuration) is the exact shape of defect PR-14
 * measured: two sources for one size, disagreeing for a few frames after every
 * rotation.
 *
 * The scrim itself is drawn by the platform — a Compose `Dialog` dims everything
 * behind its window — so the full-window layer here is transparent and exists to
 * catch the tap. A tap on the card is swallowed by [PickerDialogCard] so it
 * cannot fall through to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerDialogWindow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // The card is sized from the window it is raised in, so the dialog window
        // has to BE the window rather than wrap the card. `BasicAlertDialog`'s own
        // 280–560dp clamp is inert against fixed constraints (`Constraints.constrain`
        // cannot widen a fixed range, nor narrow one), which is what lets the
        // landscape card be the 720dp the export draws — measured in
        // `PickerDialogRenderTest` rather than trusted from that reading.
        modifier = Modifier.fillMaxSize(),
        properties =
            DialogProperties(
                // Back closes the card. Stated rather than defaulted because it is
                // the one dismissal with no control on screen, and a card the back
                // gesture cannot close is a dead end (EDGE_CASES).
                dismissOnBackPress = true,
                // The platform cannot serve this one: with the dialog window filling
                // the display there is no "outside" left for it to detect, so the
                // scrim tap is handled below instead.
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    // The keyboard takes its space from the layer the card is
                    // MEASURED in, not from the card afterwards. E-D1 measured the
                    // difference on `emulator-5554` at 800×1280: without it the
                    // dialog window does not resize for the IME, so the keyboard
                    // covered the bottom of a card that still thought it was
                    // 998dp tall — search field fine, docked "Manage packs" and
                    // "Cancel" unreachable until the keyboard was dismissed.
                    .imePadding()
                    .testTag("tt_lang_dialog_scrim")
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            PickerDialogCard(size = pickerDialogSize(maxWidth, maxHeight), content = content)
        }
    }
}

/**
 * The card: an M3 dialog surface at the width and height cap [pickerDialogSize]
 * decided, holding whatever the host puts in it.
 *
 * Split out from [PickerDialogHost] so it can be seen in a preview at all — a
 * platform `Dialog` opens a second window and renders nothing in the tooling,
 * the same reason `:core:designsystem` previews its sheets through a plain
 * surface.
 *
 * The corner radius is `extraLarge` (28dp), which is both M3's dialog shape and
 * what the export draws.
 */
@Composable
internal fun PickerDialogCard(
    size: PickerDialogSize,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        // The app's own floating-surface step — light `surfaceContainerLowest`
        // (#FFFFFF), dark `surfaceContainer` — which is both the colour the export
        // draws the card in and the one every other raised thing in this app uses.
        //
        // NOT `surface` with a tonal elevation, which is what this shipped for one
        // device run: Compose tints `surface` towards the PRIMARY at elevation, so
        // the card came out Google-blue over a near-white page. Separation here is
        // by lightness and a shadow, never by a tint (UI_SPEC §1/§3, issue #15).
        color = LocalFloatingSurface.current,
        shape = RoundedCornerShape(PICKER_DIALOG_CORNER),
        shadowElevation = PICKER_DIALOG_ELEVATION,
        modifier =
            modifier
                .width(size.width)
                .heightIn(max = size.maxHeight)
                .testTag("tt_lang_dialog")
                // Taps that land on the card's own background stop here. Rows,
                // buttons and the list keep their own gestures: `detectTapGestures`
                // waits for a down event nothing else has consumed, and everything
                // interactive inside consumes first.
                .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * The docked bar at the foot of the card — the one piece of chrome that exists
 * in this host and in no other.
 *
 * **"Manage packs" leads and "Cancel" trails, which is the export's order and
 * not M3's.** M3 puts the confirming action last; this card has no confirming
 * action, because choosing a language IS the confirmation and it happens in the
 * list. What is left is one action that goes somewhere and one that goes away,
 * and the export draws them in that order in all four tablet frames. The
 * engineering brief already ruled these four dismisses read "Cancel" correctly
 * (§9: sheet dismisses reading "Cancel" are fine — the prohibition is about the
 * ✕ on a downloading row).
 *
 * Both are `TextButton`s: neither is an emphasised action, and a filled button
 * beside a list of tappable languages would read as the way to finish.
 */
@Composable
internal fun PickerDialogActions(
    onManagePacks: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.pickerDialogActionBar)
                    .padding(horizontal = spacing.md16),
        ) {
            TextButton(onClick = onManagePacks, modifier = Modifier.testTag("tt_lang_dialog_manage")) {
                Text(stringResource(R.string.lang_dialog_manage_packs))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("tt_lang_dialog_cancel")) {
                Text(stringResource(R.string.lang_dialog_cancel))
            }
        }
    }
}

/** M3's `extraLarge` corner, which is also the 28dp the export draws. */
private val PICKER_DIALOG_CORNER: Dp = 28.dp

/** M3 dialog elevation (level 3). */
private val PICKER_DIALOG_ELEVATION: Dp = 6.dp

// ---- previews ---------------------------------------------------------------
// The card at the two sizes the export measures, drawn WITHOUT the platform
// dialog: a `Dialog` opens its own window and the tooling renders an empty
// frame, so the previews show the card the owner is actually reviewing. The
// scrim is the platform's dim and is not part of the card.

/** 17c: `from|to · tablet portrait` — 560dp of an 800×1280 window, 78% tall. */
private val previewDialogPortrait = PickerDialogSize(width = 560.dp, maxHeight = 998.dp)

/** 17d: `from|to · tablet landscape` — 720dp of a 1280×800 window, 78% tall. */
private val previewDialogLandscape = PickerDialogSize(width = 720.dp, maxHeight = 624.dp)

/** What [pickerArrangement] returns for the two cards above — stated, not assumed. */
private val previewDialogOneColumn = PickerArrangement(twoPane = false, columns = 1, rail = false)
private val previewDialogTwoColumn = PickerArrangement(twoPane = false, columns = 2, rail = false)

@Composable
private fun DialogPreviewFrame(
    size: PickerDialogSize,
    content: @Composable ColumnScope.() -> Unit,
) {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                modifier = Modifier.padding(Dimensions.pickerDialogMargin),
                contentAlignment = Alignment.Center,
            ) {
                PickerDialogCard(size = size, content = content)
            }
        }
    }
}

/**
 * `from · tablet portrait`: one column, no A–Z rail, the Detect row and Recent
 * stacked above the catalog exactly as phone portrait stacks them — and the
 * docked bar the phone does not have.
 */
@PreviewLightDark
@Composable
private fun PickerDialogPortraitPreview() {
    DialogPreviewFrame(previewDialogPortrait) {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = previewLanguages,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            modifier = Modifier.weight(1f),
            host = PickerHost.DIALOG,
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewDialogOneColumn,
        )
        PickerDialogActions(onManagePacks = {}, onCancel = {})
    }
}

/**
 * `to · tablet landscape`: the card is wider than it is tall, so the catalog
 * runs in TWO columns — the rows the shorter card lost, bought back with the
 * width it gained. Drawn on the target side because that is where the speaker
 * marks and the voice legend are.
 */
@PreviewLightDark
@Composable
private fun PickerDialogLandscapeTwoUpPreview() {
    DialogPreviewFrame(previewDialogLandscape) {
        LanguagePickerContent(
            target = LanguageRole.TARGET,
            languages = previewLanguages,
            selectedId = "es",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {},
            modifier = Modifier.weight(1f),
            host = PickerHost.DIALOG,
            recents = previewRecents,
            offlineStates = previewStates,
            arrangementOverride = previewDialogTwoColumn,
        )
        PickerDialogActions(onManagePacks = {}, onCancel = {})
    }
}

/**
 * The bar on its own, because it is a custom item built from standard M3 parts
 * and the owner reviews items from previews (CLAUDE.md rule 7). Its one
 * interesting property is the order of the two actions — see
 * [PickerDialogActions].
 */
@PreviewLightDark
@Composable
private fun PickerDialogActionsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.width(previewDialogPortrait.width)) {
                PickerDialogActions(onManagePacks = {}, onCancel = {})
            }
        }
    }
}
