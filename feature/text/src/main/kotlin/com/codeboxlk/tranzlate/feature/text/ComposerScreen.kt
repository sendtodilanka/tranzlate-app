package com.codeboxlk.tranzlate.feature.text

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.Elevation
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalResultCardColors
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.Motion
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.ui.ShimmerResult
import com.codeboxlk.tranzlate.core.ui.adaptiveMarginShim
import com.codeboxlk.tranzlate.core.ui.adaptiveScreenMargin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

// Split panes (issue #56 frames 3/5): source/input 2 : result 3; a hinge → 1 : 1.
private const val PANE_WEIGHT_INPUT = 2f
private const val PANE_WEIGHT_RESULT = 3f

/** SharedTransition key for the ONE morph anchor — the input/composer card. */
const val COMPOSER_CARD_SHARED_KEY = "composer_card"

/**
 * Screen **5a** (Claude Design export "Offline Translator M3") — the single
 * working surface for text translation: the user types here, the result lands
 * here, and re-editing happens here. There is **no** engine badge (the engine
 * waterfall is invisible to the user — an owner decision, engine selection is
 * deferred) and the mic/Translate action exists **only while editing**: empty →
 * mic, text → Translate, result showing → neither.
 *
 * Hosted as its own destination (`ComposerNavKey`): the shell pushes it and the
 * input card is a shared element, so the push reads as Home's card growing in
 * place rather than a page swap. [cardModifier] carries that shared-bounds
 * modifier down from the shell — the feature never navigates itself.
 */
@Composable
fun ComposerScreen(
    viewModel: TextViewModel,
    onBack: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val onNotify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        ComposerPane(
            viewModel = viewModel,
            onBack = onBack,
            onPickLanguage = onPickLanguage,
            onOpenPaywall = onOpenPaywall,
            onNotify = onNotify,
            cardModifier = cardModifier,
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        )
    }
}

/** Stateful pane: collects the shared [TextViewModel] and hosts the 5a layout. */
@Composable
internal fun ComposerPane(
    viewModel: TextViewModel,
    onBack: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiRemaining by viewModel.aiRemaining.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val resultFavourite by viewModel.resultFavourite.collectAsStateWithLifecycle()
    val swapAvailable by viewModel.swapAvailable.collectAsStateWithLifecycle()
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()
    ComposerPaneContent(
        resultFavourite = resultFavourite,
        onToggleFavourite = viewModel::onToggleFavourite,
        speaking = speaking,
        onSpeakToggle = viewModel::onSpeak,
        onReverseRequest = viewModel::onReverse,
        input = input,
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        uiState = uiState,
        aiMeter = if (isPro) null else aiRemaining to viewModel.aiCap,
        onOpenPaywall = onOpenPaywall,
        onInputChange = viewModel::onInputChange,
        onTranslate = viewModel::onTranslate,
        onRetry = viewModel::onRetry,
        onSwapLanguages = viewModel::onSwapLanguages,
        swapAvailable = swapAvailable,
        onPickLanguage = onPickLanguage,
        onBack = onBack,
        onNotify = onNotify,
        onClearAll = viewModel::onClearAll,
        cardModifier = cardModifier,
        modifier = modifier,
    )
}

/** Stateless 5a layout (previewable without DI): top row + the one card. */
@Composable
// One cohesive 5a surface across every window shape (issue #56);
// splitting further would hide the edit/result state machine.
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ComposerPaneContent(
    input: String,
    sourceLangId: String,
    targetLangId: String,
    uiState: TextUiState,
    onInputChange: (String) -> Unit,
    aiMeter: Pair<Int, Int>? = null,
    onOpenPaywall: () -> Unit = {},
    resultFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    speaking: Boolean = false,
    onSpeakToggle: () -> Boolean = { false },
    onReverseRequest: () -> Boolean = { false },
    onTranslate: () -> Boolean,
    onRetry: () -> Unit,
    onSwapLanguages: () -> Boolean,
    swapAvailable: Boolean = true,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    val layout = rememberAdaptiveLayout()

    // Trigger #1 / not-entitled CTA (BUSINESS_MODEL §5 · C-11): the sheet rides
    // OVER the composer — free engines keep working underneath, never a block.
    (uiState as? TextUiState.Limit)?.let { LimitSheet(it, onOpenPaywall) }

    // Editing vs reading is a UI concern: the shared uiState keeps the last
    // result while the user re-edits, so "which face does the card show" cannot
    // be derived from uiState alone. Saveable → survives rotation and the
    // language-picker round trip (requirement E). The permanent two-pane shape
    // (issue #56) renders BOTH panes and never consults this — deliberately no
    // forced write here: a restored Result must not pop the keyboard over what
    // the user is reading, and rotating out of two-pane must land on the READ
    // face (co-verify finding 1).
    var isEditing by rememberSaveable { mutableStateOf(uiState is TextUiState.Idle) }

    // Owner requirement: entering 5a lands ready to type. Choreography matters:
    // the IME inset resizes this pane, so showing the keyboard DURING the
    // container transform retargets the card mid-flight and the anchor dips.
    // Focus is immediate (cursor visible while the card grows); the keyboard
    // waits one motion beat on FIRST entry so the morph lands on a stable
    // target. Re-entering edit from the result face has no morph — no delay.
    var entryChoreoDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isEditing) {
        // Permanent two-pane never flips isEditing on Translate, so a process-
        // death restore arrives with isEditing=true AND a result on screen —
        // popping the keyboard over what the user is reading (co-verify
        // finding 1, second half). Focus/keyboard belong to entries with
        // nothing to read.
        val readingOnTwoPane = layout.permanentTwoPane && uiState !is TextUiState.Idle
        if (isEditing && !readingOnTwoPane) {
            focusRequester.requestFocus()
            if (!entryChoreoDone) {
                entryChoreoDone = true
                delay(Motion.MEDIUM_4.toLong())
            }
            keyboard?.show()
        }
    }

    val guidedVoice = stringResource(R.string.text_guided_voice)
    val pasteEmpty = stringResource(R.string.text_paste_empty)
    val pasteAction: () -> Unit = {
        val clip = clipboard.getText()?.text
        // EDGE_CASES no-dead-end (issue #70): an empty clipboard says so.
        if (clip.isNullOrEmpty()) onNotify(pasteEmpty) else onInputChange(clip)
    }
    val swapNeedsDetect = stringResource(R.string.text_swap_needs_detect)
    val swapAction: () -> Unit = {
        if (!onSwapLanguages()) onNotify(swapNeedsDetect)
    }
    val ttsUnavailable = stringResource(R.string.text_tts_unavailable)
    val speakAction: () -> Unit = {
        if (!onSpeakToggle()) onNotify(ttsUnavailable)
    }
    val reverseAction: () -> Unit = {
        if (!onReverseRequest()) onNotify(swapNeedsDetect) // same truth: needs a detected language
    }
    val starUnavailable = stringResource(R.string.text_star_unavailable)
    val starAction: () -> Unit = {
        val starResult = uiState as? TextUiState.Result
        if (starResult?.resolvedSourceLang != null) onToggleFavourite() else onNotify(starUnavailable)
    }
    val copiedMessage = stringResource(R.string.text_copied)

    Column(modifier = modifier) {
        // Height-compact landscape + IME: the keyboard owns most of the 412dp —
        // hide the top row while typing (GT's own landscape behaviour) so the
        // field keeps a readable height. The system back gesture still works,
        // and the row returns the moment the keyboard drops. Verified on
        // device: without this the field measures near zero (issue #56).
        // The isImeVisible read is short-circuit-gated to the short-landscape
        // shape: issue-56 flagged its per-frame inset invalidation as the draw-
        // failure suspect, so every other shape stays unsubscribed from IME
        // toggles. Here it is required (issue #86, owner): the row hides ONLY
        // while the keyboard owns the height — dismissing the IME must bring
        // back the ONLY back affordance.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val splitImeVisible = layout.splitResultOnly && WindowInsets.isImeVisible
        val hideTopRow = isEditing && splitImeVisible
        // Issue #92 (debate-ruled): the 16dp-based top row shifts by the margin
        // shim in EVERY non-compact shape (shim = margin − 16, so 0dp on
        // phones) — back + pills land exactly on the pane/card edge because
        // the shim and the pane margins read the same token.
        Box(
            modifier =
                Modifier
                    .padding(horizontal = adaptiveMarginShim())
                    .clipToBounds()
                    .then(
                        if (hideTopRow) {
                            // Collapsed visually AND for TalkBack: a 0dp node keeps
                            // its semantics, so without this the back/pill/swap
                            // controls stay swipe-reachable while invisible
                            // (TEST_A11Y contract; co-verify finding 4).
                            Modifier.height(0.dp).clearAndSetSemantics {}
                        } else {
                            Modifier
                        },
                    ),
        ) {
            ComposerTopRow(
                sourceLabel = languageLabel(sourceLangId),
                targetLabel = languageLabel(targetLangId),
                onBack = onBack,
                onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
                onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
                onSwap = swapAction,
                swapEnabled = swapAvailable,
                constrainPills = layout.expandedWidth,
            )
        }
        if (hideTopRow) Spacer(Modifier.height(spacing.sm8))
        if (layout.permanentTwoPane) {
            // Tablet / unfolded foldable (issue #56, frames 5/7): both panes live
            // at once — the editable card left, the result pane right. A hinge
            // snaps the split to 50/50 so nothing renders under the fold.
            Row(
                modifier =
                    Modifier
                        // Issue #92: M3 expanded margin (24dp) via the canonical
                        // reader; the top row's shim keeps back/pills on this edge.
                        .padding(horizontal = adaptiveScreenMargin())
                        .weight(1f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = LocalFloatingSurface.current,
                    shadowElevation = Elevation.level1,
                    modifier =
                        cardModifier
                            .weight(if (layout.hinged) 1f else PANE_WEIGHT_INPUT)
                            .fillMaxHeight()
                            .testTag("tt_composer_card"),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                start = spacing.md16,
                                end = spacing.md16,
                                top = spacing.lg24,
                                bottom = spacing.md16,
                            ),
                    ) {
                        SourceLabelRow(
                            label = languageLabel(sourceLangId),
                            showClear = input.isNotEmpty(),
                            onClear = {
                                onClearAll()
                                focusRequester.requestFocus()
                            },
                        )
                        ComposerEditBody(
                            input = input,
                            focusRequester = focusRequester,
                            onInputChange = onInputChange,
                            onPaste = pasteAction,
                            onMic = { onNotify(guidedVoice) },
                            onTranslate = {
                                if (onTranslate()) keyboard?.hide()
                            },
                        )
                    }
                }
                Spacer(Modifier.width(spacing.lg24))
                ResultPane(
                    aiMeter = aiMeter,
                    starFilled = resultFavourite,
                    speaking = speaking,
                    onReverse = reverseAction,
                    targetLabel = languageLabel(targetLangId),
                    uiState = uiState,
                    onRetry = onRetry,
                    onCopy = { text ->
                        clipboard.setText(AnnotatedString(text))
                        onNotify(copiedMessage)
                    },
                    onSpeak = speakAction,
                    onStar = starAction,
                    modifier = Modifier.weight(if (layout.hinged) 1f else PANE_WEIGHT_RESULT).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(spacing.md16))
            return
        }

        val showsResult = uiState is TextUiState.Result || uiState is TextUiState.Translating
        if (layout.splitResultOnly && !isEditing && showsResult) {
            // Phone landscape read face (issue #56, frame 3): source | result side
            // by side — no vertical scroll in a 412dp-tall window.
            Row(
                modifier =
                    Modifier
                        // Issue #92: M3 expanded margin (24dp) via the canonical
                        // reader; the top row's shim keeps back/pills on this edge.
                        .padding(horizontal = adaptiveScreenMargin())
                        .weight(1f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = LocalFloatingSurface.current,
                    shadowElevation = Elevation.level1,
                    modifier = cardModifier.weight(PANE_WEIGHT_INPUT).fillMaxHeight().testTag("tt_composer_card"),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                start = spacing.md16,
                                end = spacing.md16,
                                top = spacing.lg24,
                                bottom = spacing.md16,
                            ),
                    ) {
                        SourceLabelRow(
                            label = languageLabel(sourceLangId),
                            showClear = input.isNotEmpty(),
                            onClear = {
                                onClearAll()
                                isEditing = true
                            },
                        )
                        Text(
                            text = uiState.requestText ?: input,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClickLabel = stringResource(R.string.cd_text_edit),
                                        onClick = { isEditing = true },
                                    ).padding(top = spacing.sm8)
                                    .testTag("tt_composer_source"),
                        )
                    }
                }
                Spacer(Modifier.width(spacing.lg24)) // Issue #92: M3 pane spacer = 24dp
                ResultPane(
                    aiMeter = aiMeter,
                    starFilled = resultFavourite,
                    speaking = speaking,
                    onReverse = reverseAction,
                    targetLabel = languageLabel(targetLangId),
                    uiState = uiState,
                    onRetry = onRetry,
                    onCopy = { text ->
                        clipboard.setText(AnnotatedString(text))
                        onNotify(copiedMessage)
                    },
                    onSpeak = speakAction,
                    onStar = starAction,
                    modifier = Modifier.weight(PANE_WEIGHT_RESULT).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(spacing.md16))
            return
        }

        // Issue #92 (ruling principle: margin + shim read the SAME token at
        // every width): 16dp compact, 24dp otherwise — the edit-face card edge
        // stays on the shifted top row's back/pills line in landscape too.
        val cardMargin = adaptiveScreenMargin()
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalFloatingSurface.current,
            shadowElevation = Elevation.level1,
            modifier =
                cardModifier
                    .padding(horizontal = cardMargin)
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("tt_composer_card"),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = spacing.md16,
                            end = spacing.md16,
                            // Issue #86: with the IME up in the short landscape
                            // shape every dp belongs to the field.
                            top =
                                if (isEditing && splitImeVisible) {
                                    spacing.sm8
                                } else {
                                    spacing.lg24
                                },
                            bottom = spacing.md16,
                        ),
            ) {
                if (!(layout.splitResultOnly && isEditing)) {
                    SourceLabelRow(
                        label = languageLabel(sourceLangId),
                        showClear = input.isNotEmpty(),
                        onClear = {
                            onClearAll()
                            isEditing = true
                        },
                    )
                }
                if (isEditing) {
                    ComposerEditBody(
                        input = input,
                        focusRequester = focusRequester,
                        onInputChange = onInputChange,
                        onPaste = pasteAction,
                        onMic = { onNotify(guidedVoice) },
                        onTranslate = {
                            if (onTranslate()) {
                                // Two-pane keeps both panes live (issue #56); the
                                // keyboard still drops so the result pane is unobscured.
                                if (!layout.permanentTwoPane) isEditing = false
                                keyboard?.hide()
                            }
                        },
                        compactLandscape = layout.splitResultOnly,
                        minimalIme = splitImeVisible,
                        label = languageLabel(sourceLangId),
                        onClear = {
                            onClearAll()
                            isEditing = true
                        },
                    )
                } else {
                    ComposerReadBody(
                        aiMeter = aiMeter,
                        starFilled = resultFavourite,
                        speaking = speaking,
                        onReverse = reverseAction,
                        sourceText = uiState.requestText ?: input,
                        targetLabel = languageLabel(targetLangId),
                        uiState = uiState,
                        onEditRequest = { isEditing = true },
                        onRetry = onRetry,
                        onCopy = { text ->
                            clipboard.setText(AnnotatedString(text))
                            onNotify(copiedMessage)
                        },
                        onSpeak = speakAction,
                        onStar = starAction,
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.md16))
    }
}

/** 5a top row (export: no app bar) — 48dp back + the same pills as Home. */
@Composable
internal fun ComposerTopRow(
    sourceLabel: String,
    targetLabel: String,
    onBack: () -> Unit,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit,
    swapEnabled: Boolean,
    modifier: Modifier = Modifier,
    // Wide windows (issue #56 frames 2/3/5/7): the pill group stays a compact,
    // LEFT-ALIGNED cluster instead of stretching edge to edge — pills wider
    // than the approved frames was the owner-visible diff in the first
    // identity pass. Compact portrait keeps the shipped full-width pills.
    constrainPills: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = spacing.xs4, end = spacing.md16, top = spacing.sm8, bottom = spacing.md16),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("tt_composer_back")) {
            Icon(
                painterResource(DsR.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.cd_text_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val pillsWidth =
            if (constrainPills) {
                Modifier.weight(1f, fill = false).widthIn(max = Dimensions.contentMaxWidthMedium)
            } else {
                Modifier
            }
        LanguageRow(
            sourceLabel = sourceLabel,
            targetLabel = targetLabel,
            onSourceClick = onSourceClick,
            onTargetClick = onTargetClick,
            onSwap = onSwap,
            swapEnabled = swapEnabled,
            modifier = pillsWidth.padding(start = spacing.xs4),
        )
    }
}

/** THE input field — one definition for every edit-face arrangement (issue #86). */
@Composable
private fun ComposerField(
    input: String,
    hasText: Boolean,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    inputDescription: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = input,
        onValueChange = onInputChange,
        textStyle =
            MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (hasText) onTranslate() }),
        modifier =
            modifier
                .focusRequester(focusRequester)
                .semantics { contentDescription = inputDescription }
                .testTag("tt_text_input"),
        decorationBox = { inner ->
            if (input.isEmpty()) {
                Text(
                    text = stringResource(R.string.text_input_placeholder),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inner()
        },
    )
}

/** Edit face: field → Paste chip (empty only) → counter + mic/Translate. */
@Composable
private fun ColumnScope.ComposerEditBody(
    input: String,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onMic: () -> Unit,
    onTranslate: () -> Unit,
    // Height-compact landscape (issue #56): the card has ~140dp of interior —
    // label row + a min-height field + the action row overflow it and the
    // actions clip out of reach (device-verified). Compact mode folds label,
    // counter, clear and the action into ONE top row so the field keeps the
    // rest.
    compactLandscape: Boolean = false,
    // Issue #86 (owner): with the IME up in the short landscape shape there was
    // ~0dp to type in — this mode is field + action ONLY; the chrome returns
    // the moment the keyboard drops.
    minimalIme: Boolean = false,
    label: String = "",
    onClear: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val hasText = input.isNotBlank()
    val overLimit = input.length > TEXT_CHAR_LIMIT
    val inputDescription = stringResource(R.string.cd_text_input)
    val counterDescription =
        if (input.length >= TEXT_CHAR_LIMIT) {
            stringResource(R.string.cd_text_counter_limit, TEXT_CHAR_LIMIT)
        } else {
            stringResource(R.string.cd_text_counter, input.length, TEXT_CHAR_LIMIT)
        }

    // Issue #97 (owner video, debate-ruled): the FIELD renders at exactly ONE
    // source position inside a stable parent Row. Arrangement changes must
    // never dispose the focused node — a different call site made Compose
    // clear focus mid-IME-slide, the InputConnection dropped, and the
    // keyboard dismissed itself in a loop (landscape show-then-hide).
    // minimalIme / compactLandscape only toggle SIBLING chrome + modifiers.
    if (compactLandscape && !minimalIme) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            CharCounter(
                length = input.length,
                overLimit = overLimit,
                counterDescription = counterDescription,
            )
            if (input.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.testTag("tt_composer_clear")) {
                    Icon(
                        painterResource(DsR.drawable.ic_close),
                        contentDescription = stringResource(R.string.cd_text_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(spacing.sm8))
            EditAction(hasText = hasText, overLimit = overLimit, onMic = onMic, onTranslate = onTranslate)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = if (minimalIme) 0.dp else spacing.sm8),
    ) {
        ComposerField(
            input = input,
            hasText = hasText,
            focusRequester = focusRequester,
            onInputChange = onInputChange,
            onTranslate = onTranslate,
            inputDescription = inputDescription,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        if (minimalIme) {
            Spacer(Modifier.width(spacing.sm8))
            // Counter stays even here: the only over-limit signal + the P0-3
            // announce live on this node (lens catch — a bare disabled action
            // with no reason is an EDGE_CASES dead end).
            CharCounter(length = input.length, overLimit = overLimit, counterDescription = counterDescription)
            Spacer(Modifier.width(spacing.sm8))
            EditAction(hasText = hasText, overLimit = overLimit, onMic = onMic, onTranslate = onTranslate)
        }
    }
    if (!minimalIme && input.isEmpty()) {
        TextButton(onClick = onPaste, modifier = Modifier.testTag("tt_composer_paste")) {
            Icon(
                painterResource(DsR.drawable.ic_content_paste),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Spacer(Modifier.size(spacing.sm8))
            Text(stringResource(R.string.composer_paste))
        }
    }
    if (compactLandscape || minimalIme) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = spacing.sm8),
    ) {
        CharCounter(
            length = input.length,
            overLimit = overLimit,
            counterDescription = counterDescription,
            modifier = Modifier.weight(1f),
        )
        EditAction(hasText = hasText, overLimit = overLimit, onMic = onMic, onTranslate = onTranslate)
    }
}

/**
 * The ONE counter definition. Every edit arrangement must include it: it is
 * the only visible over-limit explanation (EDGE_CASES no-dead-end — the
 * Translate action just disables) AND the node carrying the recorded TalkBack
 * P0-3 cap-announce. Dropping it from a shape silently kills both (lens
 * catch, issue #86).
 */
@Composable
private fun CharCounter(
    length: Int,
    overLimit: Boolean,
    counterDescription: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            if (overLimit) {
                stringResource(R.string.text_over_char_limit, TEXT_CHAR_LIMIT)
            } else {
                stringResource(R.string.text_char_counter, length, TEXT_CHAR_LIMIT)
            },
        style = MaterialTheme.typography.labelMedium,
        color =
            if (overLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            modifier
                .semantics {
                    contentDescription = counterDescription
                    if (length >= TEXT_CHAR_LIMIT) liveRegion = LiveRegionMode.Polite
                }.testTag("tt_text_counter"),
    )
}

/** The one action slot: empty → mic, text → Translate (a morph, never a disable). */
@Composable
private fun EditAction(
    hasText: Boolean,
    overLimit: Boolean,
    onMic: () -> Unit,
    onTranslate: () -> Unit,
) {
    val spacing = LocalSpacing.current
    if (hasText) {
        Button(
            onClick = onTranslate,
            enabled = !overLimit,
            modifier = Modifier.height(Dimensions.touchTargetMin).testTag("tt_text_translate_btn"),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_translate),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSm),
            )
            Spacer(Modifier.size(spacing.sm8))
            Text(stringResource(R.string.home_translate))
        }
    } else {
        Surface(
            onClick = onMic,
            shape = TranzlateShapeFull,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimensions.touchTargetMin).testTag("tt_text_mic"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(DsR.drawable.ic_mic),
                    contentDescription = stringResource(R.string.cd_text_mic),
                    modifier = Modifier.size(Dimensions.iconMd),
                )
            }
        }
    }
}

/**
 * Read face (design: the result is a TONAL CARD nested inside the composer, not
 * a second half of it). The source text stays where it was and stays tappable —
 * tapping it returns to editing — and the translation lands underneath in its
 * own `primaryContainer` card carrying the target language in CAPITALS, then
 * speak · copy on the left with bookmark pushed to the right edge.
 *
 * An Error is deliberately NOT dressed as a result card: error colours on a
 * primary container fail contrast, and a failure is not a translation.
 */
@Composable
private fun ColumnScope.ComposerReadBody(
    sourceText: String,
    targetLabel: String,
    uiState: TextUiState,
    onEditRequest: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    aiMeter: Pair<Int, Int>? = null,
    starFilled: Boolean = false,
    speaking: Boolean = false,
    onReverse: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Text(
        text = sourceText,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                // The source line resumes editing, so TalkBack must announce the
                // action, not just read the text back.
                .clickable(
                    onClickLabel = stringResource(R.string.cd_text_edit),
                    onClick = onEditRequest,
                ).padding(top = spacing.sm8)
                .testTag("tt_composer_source"),
    )
    when (uiState) {
        is TextUiState.Translating -> {
            ResultCard(targetLabel = targetLabel) {
                ShimmerResult()
            }
        }

        is TextUiState.Result -> {
            ResultCard(
                targetLabel = targetLabel,
                actions = {
                    ResultAction(
                        if (speaking) DsR.drawable.ic_stop else DsR.drawable.ic_volume_up,
                        if (speaking) R.string.cd_speak_stop else R.string.cd_speak,
                        "tt_text_speak",
                        onSpeak,
                    )
                    ResultAction(DsR.drawable.ic_content_copy, R.string.cd_copy, "tt_text_copy") {
                        onCopy(uiState.translatedText)
                    }
                    ResultAction(DsR.drawable.ic_swap_horiz, R.string.cd_text_reverse, "tt_text_reverse", onReverse)
                    // Bookmark sits at the far edge in the design, away from the
                    // pair that acts on the text itself.
                    Spacer(Modifier.weight(1f))
                    ResultAction(
                        if (starFilled) DsR.drawable.ic_bookmark_filled else DsR.drawable.ic_bookmark,
                        if (starFilled) R.string.cd_favourite_remove else R.string.cd_favourite,
                        "tt_text_star",
                        onStar,
                    )
                },
            ) {
                val resultAnnounce = stringResource(R.string.a11y_result_ready, uiState.translatedText)
                Text(
                    text = uiState.translatedText,
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalResultCardColors.current.text,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // C-4 canonical announce, FORMATTED (issue #76): TalkBack
                            // hears "Translation ready: …", sighted users see the text.
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = resultAnnounce
                            }.testTag("tt_text_result"),
                )
            }
            AiMeter(uiState.engine, aiMeter)
        }

        is TextUiState.Error -> {
            val readErrorAnnounce = stringResource(R.string.a11y_error, errorBodyFor(uiState.cause))
            Text(
                text = errorBodyFor(uiState.cause),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(top = spacing.md16)
                        // C-4: a failure interrupts — assertive, formatted.
                        .semantics {
                            liveRegion = LiveRegionMode.Assertive
                            contentDescription = readErrorAnnounce
                        }.testTag("tt_text_error"),
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("tt_text_retry")) {
                Text(stringResource(R.string.button_retry))
            }
        }

        is TextUiState.Limit -> {
            // Guidance, not an error: nothing failed — the gate answered no.
            Text(
                text = limitBodyFor(uiState.notEntitled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(top = spacing.md16)
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                        .testTag("tt_text_limit"),
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("tt_text_retry")) {
                Text(stringResource(R.string.button_retry))
            }
        }

        TextUiState.Idle -> {
            Unit
        } // unreachable: Idle always shows the edit face
    }
    Spacer(Modifier.weight(1f))
}

/** Length thresholds for the result's auto-size tiers (issue #56, owner v3). */
private const val RESULT_DISPLAY_MAX_CHARS = 24
private const val RESULT_HEADLINE_MAX_CHARS = 80

/**
 * Per-cause copy (issue #53 A3 · EDGE_CASES §4) for the causes that exist
 * today; the engines phase adds the rest (download CTA, timeout…). Null cause
 * (empty input) and everything unmapped fall back to the guided generic body.
 */
@Composable
private fun errorBodyFor(cause: AttemptCause?): String =
    stringResource(
        when (cause) {
            AttemptCause.OFFLINE -> R.string.text_error_offline
            AttemptCause.UNSUPPORTED_PAIR -> R.string.text_error_unsupported_pair
            else -> R.string.text_error_generic_body
        },
    )

/** Limit-face copy: quota vs access denial get DIFFERENT truths (A3). */
@Composable
private fun limitBodyFor(notEntitled: Boolean): String =
    stringResource(
        if (notEntitled) R.string.text_error_not_entitled else R.string.text_error_limit_reached,
    )

/** Short → display · medium → headline · long → the portrait titleLarge. */
@Composable
private fun resultTypeFor(text: String) =
    when {
        text.length <= RESULT_DISPLAY_MAX_CHARS -> MaterialTheme.typography.displayMedium
        text.length <= RESULT_HEADLINE_MAX_CHARS -> MaterialTheme.typography.headlineMedium
        else -> MaterialTheme.typography.titleLarge
    }

/**
 * BUSINESS_MODEL §5 goal-gradient: the AI-quality meter shows WHILE an
 * AI-quality result is on screen (FREE users only) — awareness of the meter is
 * what makes the limit sheet land as expected instead of as a surprise.
 */
@Composable
private fun AiMeter(
    engine: Engine,
    meter: Pair<Int, Int>?,
) {
    if (meter == null || engine != Engine.ONLINE_CLOUD_NLP) return
    val (left, cap) = meter
    Text(
        text = stringResource(R.string.text_ai_meter, left, cap),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = LocalSpacing.current.sm8).testTag("tt_text_ai_meter"),
    )
}

/**
 * Trigger #1 (C-11) + the not-entitled upgrade CTA (PR-60 lens N2): a
 * DISMISSIBLE sheet over the composer — never a navigated block, free engines
 * keep working underneath. Copy differs by kind: quota is "come back tomorrow
 * or go Pro", denial is "this quality needs Pro".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(
    limit: TextUiState.Limit,
    onOpenPaywall: () -> Unit,
) {
    var dismissed by rememberSaveable(limit) { mutableStateOf(false) }
    if (dismissed) return
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = { dismissed = true },
        modifier = Modifier.testTag("tt_text_limit_sheet"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg24)
                    .padding(bottom = spacing.lg24),
        ) {
            Text(
                text =
                    stringResource(
                        if (limit.notEntitled) R.string.limit_sheet_title_pro else R.string.limit_sheet_title_quota,
                    ),
                style = MaterialTheme.typography.titleLarge,
                // The gate's answer interrupts a task the user just asked for —
                // TalkBack hears it without hunting (contract tag-table promise).
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Spacer(Modifier.height(spacing.sm8))
            Text(
                text =
                    stringResource(
                        if (limit.notEntitled) R.string.limit_sheet_body_pro else R.string.limit_sheet_body_quota,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md16))
            Button(
                onClick = onOpenPaywall,
                modifier = Modifier.fillMaxWidth().testTag("tt_limit_sheet_cta"),
            ) {
                Text(stringResource(R.string.limit_sheet_cta))
            }
            TextButton(
                onClick = { dismissed = true },
                modifier = Modifier.fillMaxWidth().testTag("tt_limit_sheet_dismiss"),
            ) {
                Text(stringResource(R.string.limit_sheet_dismiss))
            }
        }
    }
}

/** Source-language label + the ✕ clear affordance (the Paste chip's mirror). */
@Composable
private fun SourceLabelRow(
    label: String,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (showClear) {
            IconButton(onClick = onClear, modifier = Modifier.testTag("tt_composer_clear")) {
                Icon(
                    painterResource(DsR.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_text_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The standalone result pane for the split shapes (issue #56, frames 3/5/7):
 * full-height tonal card; the translation AUTO-SIZES (owner decision — short
 * results render display-sized, long results body-sized, the card never
 * changes shape; GT behaviour per D-0). An error is not dressed as a result —
 * plain card, error colours, Retry.
 */
@Composable
private fun ResultPane(
    targetLabel: String,
    uiState: TextUiState,
    aiMeter: Pair<Int, Int>? = null,
    starFilled: Boolean = false,
    speaking: Boolean = false,
    onReverse: () -> Unit = {},
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colors = LocalResultCardColors.current
    // Errors AND limit answers are plain cards — neither is dressed as a result.
    val plainFace: Pair<String, Boolean>? =
        when (uiState) {
            is TextUiState.Error -> errorBodyFor(uiState.cause) to true
            is TextUiState.Limit -> limitBodyFor(uiState.notEntitled) to false
            else -> null
        }
    if (plainFace != null) {
        val (body, isError) = plainFace
        val paneErrorAnnounce = stringResource(R.string.a11y_error, body)
        val bodyColor =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalFloatingSurface.current,
            shadowElevation = Elevation.level1,
            modifier = modifier,
        ) {
            Column(modifier = Modifier.padding(spacing.md16)) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    modifier =
                        Modifier
                            .semantics {
                                liveRegion = LiveRegionMode.Assertive
                                if (isError) contentDescription = paneErrorAnnounce
                            }.testTag(if (isError) "tt_text_error" else "tt_text_limit"),
                )
                TextButton(onClick = onRetry, modifier = Modifier.testTag("tt_text_retry")) {
                    Text(stringResource(R.string.button_retry))
                }
            }
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.container,
        modifier = modifier.testTag("tt_text_result_card"),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.md16)) {
            Text(
                text = targetLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.label,
            )
            Spacer(Modifier.height(spacing.sm8))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    is TextUiState.Translating -> {
                        ShimmerResult()
                    }

                    is TextUiState.Result -> {
                        val paneResultAnnounce =
                            stringResource(R.string.a11y_result_ready, uiState.translatedText)
                        Text(
                            text = uiState.translatedText,
                            // GT-style auto-size (owner v3): short results render
                            // display-sized, long ones step down. Deterministic
                            // length tiers rather than TextAutoSize — measured on
                            // device, StepBased settled on its minimum for a short
                            // one-liner and never grew (issue #56 research note).
                            style = resultTypeFor(uiState.translatedText).copy(color = colors.text),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = paneResultAnnounce
                                    }.testTag("tt_text_result"),
                        )
                    }

                    else -> {
                        Unit
                    } // Idle: the label alone — nothing to read yet
                }
            }
            if (uiState is TextUiState.Result) {
                AiMeter(uiState.engine, aiMeter)
                PaneResultActions(
                    translatedText = uiState.translatedText,
                    speaking = speaking,
                    starFilled = starFilled,
                    onSpeak = onSpeak,
                    onCopy = onCopy,
                    onReverse = onReverse,
                    onStar = onStar,
                )
            }
        }
    }
}

/**
 * The tonal result card. Sits inside the composer card, so it carries no
 * elevation of its own — the design separates it by tone alone.
 */
@Composable
private fun ResultCard(
    targetLabel: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current
    val colors = LocalResultCardColors.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        modifier = modifier.fillMaxWidth().padding(top = spacing.md16).testTag("tt_text_result_card"),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8)) {
            Text(
                text = targetLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.label,
            )
            Spacer(Modifier.height(spacing.xs4))
            content()
            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs4),
                    content = actions,
                )
            }
        }
    }
}

/** One 48dp action inside the result card — glyph tinted to the card's label tone. */
@Composable
private fun ResultAction(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(description),
            tint = LocalResultCardColors.current.label,
        )
    }
}

/** The pane's result-action row (extracted — PR-85 CI complexity gate). */
@Suppress("LongParameterList") // one callback per action, by design
@Composable
private fun PaneResultActions(
    translatedText: String,
    speaking: Boolean,
    starFilled: Boolean,
    onSpeak: () -> Unit,
    onCopy: (String) -> Unit,
    onReverse: () -> Unit,
    onStar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ResultAction(
            if (speaking) DsR.drawable.ic_stop else DsR.drawable.ic_volume_up,
            if (speaking) R.string.cd_speak_stop else R.string.cd_speak,
            "tt_text_speak",
            onSpeak,
        )
        ResultAction(DsR.drawable.ic_content_copy, R.string.cd_copy, "tt_text_copy") {
            onCopy(translatedText)
        }
        ResultAction(DsR.drawable.ic_swap_horiz, R.string.cd_text_reverse, "tt_text_reverse", onReverse)
        Spacer(Modifier.weight(1f))
        ResultAction(
            if (starFilled) DsR.drawable.ic_bookmark_filled else DsR.drawable.ic_bookmark,
            if (starFilled) R.string.cd_favourite_remove else R.string.cd_favourite,
            "tt_text_star",
            onStar,
        )
    }
}

/** The request text of any non-idle state (source line while reading). */
private val TextUiState.requestText: String?
    get() =
        when (this) {
            is TextUiState.Translating -> request.text
            is TextUiState.Result -> request.text
            is TextUiState.Error -> request.text
            is TextUiState.Limit -> request.text
            TextUiState.Idle -> null
        }

@PreviewLightDark
@Composable
private fun ComposerEditPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ComposerPaneContent(
                input = "",
                sourceLangId = "en",
                targetLangId = "es",
                uiState = TextUiState.Idle,
                onInputChange = {},
                onTranslate = { true },
                onRetry = {},
                onSwapLanguages = { true },
                onPickLanguage = {},
                onBack = {},
                onNotify = {},
                onClearAll = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ComposerResultPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ComposerPaneContent(
                input = "Good morning, how are you today?",
                sourceLangId = "en",
                targetLangId = "es",
                uiState =
                    TextUiState.Result(
                        request =
                            TranslateRequest(
                                text = "Good morning, how are you today?",
                                sourceLang = "en",
                                targetLang = "es",
                                mode = ModeId.AUTO,
                            ),
                        translatedText = "Buenos días, ¿cómo estás hoy?",
                        transliteration = null,
                        engine = Engine.OFFLINE_MLKIT,
                    ),
                onInputChange = {},
                onTranslate = { true },
                onRetry = {},
                onSwapLanguages = { true },
                onPickLanguage = {},
                onBack = {},
                onNotify = {},
                onClearAll = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** THE ITEMS: the counter at three states + the one action slot (mic ⇄ Translate). */
@PreviewLightDark
@Composable
private fun ComposerItemsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm8),
                modifier = Modifier.padding(LocalSpacing.current.md16),
            ) {
                CharCounter(length = 0, overLimit = false, counterDescription = "")
                CharCounter(length = TEXT_CHAR_LIMIT, overLimit = false, counterDescription = "")
                CharCounter(length = TEXT_CHAR_LIMIT + 12, overLimit = true, counterDescription = "")
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm8)) {
                    EditAction(hasText = false, overLimit = false, onMic = {}, onTranslate = {})
                    EditAction(hasText = true, overLimit = false, onMic = {}, onTranslate = {})
                    EditAction(hasText = true, overLimit = true, onMic = {}, onTranslate = {})
                }
            }
        }
    }
}
