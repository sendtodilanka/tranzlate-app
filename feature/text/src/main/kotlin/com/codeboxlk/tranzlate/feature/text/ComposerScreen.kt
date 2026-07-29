package com.codeboxlk.tranzlate.feature.text

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.ui.ShimmerResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

// Split panes (issue #56 frames 3/5): source/input 2 : result 3; a hinge → 1 : 1.
private const val PaneWeightInput = 2f
private const val PaneWeightResult = 3f

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
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComposerPaneContent(
        input = input,
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onTranslate = viewModel::onTranslate,
        onRetry = viewModel::onRetry,
        onSwapLanguages = viewModel::onSwapLanguages,
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
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ComposerPaneContent(
    input: String,
    sourceLangId: String,
    targetLangId: String,
    uiState: TextUiState,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Boolean,
    onRetry: () -> Unit,
    onSwapLanguages: () -> Unit,
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

    // Editing vs reading is a UI concern: the shared uiState keeps the last
    // result while the user re-edits, so "which face does the card show" cannot
    // be derived from uiState alone. Saveable → survives rotation and the
    // language-picker round trip (requirement E). On the permanent two-pane
    // shape (issue #56) there is no face to switch — both panes are always
    // live, so this stays true and the read face never mounts.
    var isEditing by rememberSaveable { mutableStateOf(uiState is TextUiState.Idle) }
    if (layout.permanentTwoPane && !isEditing) isEditing = true

    // Owner requirement: entering 5a lands ready to type. Choreography matters:
    // the IME inset resizes this pane, so showing the keyboard DURING the
    // container transform retargets the card mid-flight and the anchor dips.
    // Focus is immediate (cursor visible while the card grows); the keyboard
    // waits one motion beat on FIRST entry so the morph lands on a stable
    // target. Re-entering edit from the result face has no morph — no delay.
    var entryChoreoDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            if (!entryChoreoDone) {
                entryChoreoDone = true
                delay(Motion.MEDIUM_4.toLong())
            }
            keyboard?.show()
        }
    }

    val guidedVoice = stringResource(R.string.text_guided_voice)
    val guidedTts = stringResource(R.string.text_guided_tts)
    val guidedBookmark = stringResource(R.string.text_guided_bookmark)
    val copiedMessage = stringResource(R.string.text_copied)

    Column(modifier = modifier) {
        // Height-compact landscape + IME: the keyboard owns most of the 412dp —
        // hide the top row while typing (GT's own landscape behaviour) so the
        // field keeps a readable height. The system back gesture still works,
        // and the row returns the moment the keyboard drops. Verified on
        // device: without this the field measures near zero (issue #56).
        // No isImeVisible read: in the 412dp-tall landscape the row must go whenever
        // the user is composing anyway, and the per-frame inset invalidation it
        // caused is the prime suspect for the field's draw failure (issue #56).
        val hideTopRow = layout.splitResultOnly && isEditing
        Box(
            modifier =
                Modifier
                    .clipToBounds()
                    .then(if (hideTopRow) Modifier.height(0.dp) else Modifier),
        ) {
            ComposerTopRow(
                sourceLabel = languageLabel(sourceLangId),
                targetLabel = languageLabel(targetLangId),
                onBack = onBack,
                onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
                onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
                onSwap = onSwapLanguages,
                swapEnabled = sourceLangId != DETECT_LANGUAGE_ID,
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
                        .padding(horizontal = LocalSpacing.current.md16)
                        .weight(1f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = LocalFloatingSurface.current,
                    shadowElevation = Elevation.level1,
                    modifier =
                        cardModifier
                            .weight(if (layout.hinged) 1f else PaneWeightInput)
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
                            onPaste = {
                                clipboard
                                    .getText()
                                    ?.text
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(onInputChange)
                            },
                            onMic = { onNotify(guidedVoice) },
                            onTranslate = {
                                if (onTranslate()) keyboard?.hide()
                            },
                        )
                    }
                }
                Spacer(Modifier.width(spacing.lg24))
                ResultPane(
                    targetLabel = languageLabel(targetLangId),
                    uiState = uiState,
                    onRetry = onRetry,
                    onCopy = { text ->
                        clipboard.setText(AnnotatedString(text))
                        onNotify(copiedMessage)
                    },
                    onSpeak = { onNotify(guidedTts) },
                    onStar = { onNotify(guidedBookmark) },
                    modifier = Modifier.weight(if (layout.hinged) 1f else PaneWeightResult).fillMaxHeight(),
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
                        .padding(horizontal = LocalSpacing.current.md16)
                        .weight(1f),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = LocalFloatingSurface.current,
                    shadowElevation = Elevation.level1,
                    modifier = cardModifier.weight(PaneWeightInput).fillMaxHeight().testTag("tt_composer_card"),
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
                Spacer(Modifier.width(spacing.md16))
                ResultPane(
                    targetLabel = languageLabel(targetLangId),
                    uiState = uiState,
                    onRetry = onRetry,
                    onCopy = { text ->
                        clipboard.setText(AnnotatedString(text))
                        onNotify(copiedMessage)
                    },
                    onSpeak = { onNotify(guidedTts) },
                    onStar = { onNotify(guidedBookmark) },
                    modifier = Modifier.weight(PaneWeightResult).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(spacing.md16))
            return
        }

        val cardWidth =
            if (layout.mediumWidth && !layout.expandedWidth) {
                Modifier.widthIn(max = Dimensions.contentMaxWidthMedium)
            } else {
                Modifier
            }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalFloatingSurface.current,
            shadowElevation = Elevation.level1,
            modifier =
                cardModifier
                    .padding(horizontal = LocalSpacing.current.md16)
                    .then(cardWidth)
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .weight(1f)
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
                        isEditing = true
                    },
                )
                if (isEditing) {
                    ComposerEditBody(
                        input = input,
                        focusRequester = focusRequester,
                        onInputChange = onInputChange,
                        onPaste = {
                            clipboard
                                .getText()
                                ?.text
                                ?.takeIf { it.isNotEmpty() }
                                ?.let(onInputChange)
                        },
                        onMic = { onNotify(guidedVoice) },
                        onTranslate = {
                            if (onTranslate()) {
                                // Two-pane keeps both panes live (issue #56); the
                                // keyboard still drops so the result pane is unobscured.
                                if (!layout.permanentTwoPane) isEditing = false
                                keyboard?.hide()
                            }
                        },
                        fieldFillsHeight = !layout.splitResultOnly,
                    )
                } else {
                    ComposerReadBody(
                        sourceText = uiState.requestText ?: input,
                        targetLabel = languageLabel(targetLangId),
                        uiState = uiState,
                        onEditRequest = { isEditing = true },
                        onRetry = onRetry,
                        onCopy = { text ->
                            clipboard.setText(AnnotatedString(text))
                            onNotify(copiedMessage)
                        },
                        onSpeak = { onNotify(guidedTts) },
                        onStar = { onNotify(guidedBookmark) },
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
        LanguageRow(
            sourceLabel = sourceLabel,
            targetLabel = targetLabel,
            onSourceClick = onSourceClick,
            onTargetClick = onTargetClick,
            onSwap = onSwap,
            swapEnabled = swapEnabled,
            modifier = Modifier.padding(start = spacing.xs4),
        )
    }
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
    // Height-compact landscape (issue #56): a weighted field inside the
    // IME-animated card measures its inner text viewport down to ~0 and the
    // text stops drawing (device-verified). Wrap-content + a min height avoids
    // the pathological chain; a Spacer then pins the action row to the bottom.
    fieldFillsHeight: Boolean = true,
) {
    val spacing = LocalSpacing.current
    val hasText = input.isNotBlank()
    val overLimit = input.length > TEXT_CHAR_LIMIT
    val inputDescription = stringResource(R.string.cd_text_input)
    val counterDescription = stringResource(R.string.cd_text_counter, input.length, TEXT_CHAR_LIMIT)

    val fieldHeight =
        if (fieldFillsHeight) {
            Modifier.weight(1f)
        } else {
            Modifier.heightIn(min = Dimensions.composerInputMinHeight)
        }
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
            Modifier
                .fillMaxWidth()
                .then(fieldHeight)
                .padding(top = spacing.sm8)
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
    if (input.isEmpty()) {
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
    if (!fieldFillsHeight) Spacer(Modifier.weight(1f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = spacing.sm8),
    ) {
        Text(
            text =
                if (overLimit) {
                    stringResource(R.string.text_over_char_limit, TEXT_CHAR_LIMIT)
                } else {
                    stringResource(R.string.text_char_counter, input.length, TEXT_CHAR_LIMIT)
                },
            style = MaterialTheme.typography.labelMedium,
            color =
                if (overLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = counterDescription }
                    .testTag("tt_text_counter"),
        )
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
                    ResultAction(DsR.drawable.ic_volume_up, R.string.cd_speak, "tt_text_speak", onSpeak)
                    ResultAction(DsR.drawable.ic_content_copy, R.string.cd_copy, "tt_text_copy") {
                        onCopy(uiState.translatedText)
                    }
                    // Bookmark sits at the far edge in the design, away from the
                    // pair that acts on the text itself.
                    Spacer(Modifier.weight(1f))
                    ResultAction(DsR.drawable.ic_bookmark, R.string.cd_favourite, "tt_text_star", onStar)
                },
            ) {
                Text(
                    text = uiState.translatedText,
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalResultCardColors.current.text,
                    modifier = Modifier.fillMaxWidth().testTag("tt_text_result"),
                )
            }
        }

        is TextUiState.Error -> {
            Text(
                text = stringResource(R.string.text_error_generic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = spacing.md16).testTag("tt_text_error"),
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
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colors = LocalResultCardColors.current
    if (uiState is TextUiState.Error) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalFloatingSurface.current,
            shadowElevation = Elevation.level1,
            modifier = modifier,
        ) {
            Column(modifier = Modifier.padding(spacing.md16)) {
                Text(
                    text = stringResource(R.string.text_error_generic_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("tt_text_error"),
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
                        BasicText(
                            text = uiState.translatedText,
                            style =
                                MaterialTheme.typography.displayMedium.copy(color = colors.text),
                            autoSize =
                                TextAutoSize.StepBased(
                                    minFontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                                ),
                            modifier = Modifier.fillMaxSize().testTag("tt_text_result"),
                        )
                    }

                    else -> {
                        Unit
                    } // Idle: the label alone — nothing to read yet
                }
            }
            if (uiState is TextUiState.Result) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ResultAction(DsR.drawable.ic_volume_up, R.string.cd_speak, "tt_text_speak", onSpeak)
                    ResultAction(DsR.drawable.ic_content_copy, R.string.cd_copy, "tt_text_copy") {
                        onCopy(uiState.translatedText)
                    }
                    Spacer(Modifier.weight(1f))
                    ResultAction(DsR.drawable.ic_bookmark, R.string.cd_favourite, "tt_text_star", onStar)
                }
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

/** The request text of any non-idle state (source line while reading). */
private val TextUiState.requestText: String?
    get() =
        when (this) {
            is TextUiState.Translating -> request.text
            is TextUiState.Result -> request.text
            is TextUiState.Error -> request.text
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
                onSwapLanguages = {},
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
                onSwapLanguages = {},
                onPickLanguage = {},
                onBack = {},
                onNotify = {},
                onClearAll = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
