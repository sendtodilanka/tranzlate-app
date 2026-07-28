package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.Elevation
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
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
        cardModifier = cardModifier,
        modifier = modifier,
    )
}

/** Stateless 5a layout (previewable without DI): top row + the one card. */
@Composable
@Suppress("LongMethod") // one cohesive 5a surface; splitting hides the edit/result state machine
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
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    // Editing vs reading is a UI concern: the shared uiState keeps the last
    // result while the user re-edits, so "which face does the card show" cannot
    // be derived from uiState alone. Saveable → survives rotation and the
    // language-picker round trip (requirement E).
    var isEditing by rememberSaveable { mutableStateOf(uiState is TextUiState.Idle) }

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
        ComposerTopRow(
            sourceLabel = languageLabel(sourceLangId),
            targetLabel = languageLabel(targetLangId),
            onBack = onBack,
            onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
            onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
            onSwap = onSwapLanguages,
            swapEnabled = sourceLangId != DETECT_LANGUAGE_ID,
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = LocalFloatingSurface.current,
            shadowElevation = Elevation.level1,
            modifier =
                cardModifier
                    .padding(horizontal = LocalSpacing.current.md16)
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
                Text(
                    text = languageLabel(sourceLangId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                                isEditing = false
                                keyboard?.hide()
                            }
                        },
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
) {
    val spacing = LocalSpacing.current
    val hasText = input.isNotBlank()
    val overLimit = input.length > TEXT_CHAR_LIMIT
    val inputDescription = stringResource(R.string.cd_text_input)
    val counterDescription = stringResource(R.string.cd_text_counter, input.length, TEXT_CHAR_LIMIT)

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
                .weight(1f)
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

/** Read face: source text → divider → target label + result/shimmer/error → actions. */
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
                .clickable(onClick = onEditRequest)
                .padding(top = spacing.sm8)
                .testTag("tt_composer_source"),
    )
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = spacing.md16),
    )
    Text(
        text = targetLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    when (uiState) {
        is TextUiState.Translating -> {
            ShimmerResult(modifier = Modifier.padding(top = spacing.sm8))
        }

        is TextUiState.Result -> {
            Text(
                text = uiState.translatedText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm8).testTag("tt_text_result"),
            )
        }

        is TextUiState.Error -> {
            Text(
                text = stringResource(R.string.text_error_generic_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = spacing.sm8).testTag("tt_text_error"),
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
    if (uiState is TextUiState.Result) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs4)) {
            IconButton(
                onClick = { onCopy(uiState.translatedText) },
                modifier = Modifier.testTag("tt_text_copy"),
            ) {
                Icon(
                    painterResource(DsR.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.cd_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSpeak, modifier = Modifier.testTag("tt_text_speak")) {
                Icon(
                    painterResource(DsR.drawable.ic_volume_up),
                    contentDescription = stringResource(R.string.cd_speak),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStar, modifier = Modifier.testTag("tt_text_star")) {
                Icon(
                    painterResource(DsR.drawable.ic_star),
                    contentDescription = stringResource(R.string.cd_favourite),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
