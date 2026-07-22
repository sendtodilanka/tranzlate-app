package com.codeboxlk.tranzlate.feature.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.Motion
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.ui.AmbientBackground
import com.codeboxlk.tranzlate.core.ui.ComposerCard
import com.codeboxlk.tranzlate.core.ui.DottedRingIconButton
import com.codeboxlk.tranzlate.core.ui.ModeChip
import com.codeboxlk.tranzlate.core.ui.QuickActionTile
import com.codeboxlk.tranzlate.core.ui.TranzlateTopBar
import kotlinx.coroutines.launch

/** Composer growth cap — ~40% of the viewport (UI_SPEC §2.2). */
private const val COMPOSER_MAX_HEIGHT_FRACTION = 0.4f

/**
 * UI_SPEC §2.1 Home hub — DI shell over [HomeContent]. Screens only ASK
 * ([TextViewModel]); navigation stays with the caller (`:app` mediator).
 *
 * @param onTranslateRequested called AFTER a translation actually started
 *   (C-2 tap → Translating) — the caller opens the Result screen.
 * @param onOpenConversation Conversation tile destination; null (no Dialog
 *   vertical yet) falls back to a guided message — never a dead tile.
 * @param userName future account-system slot (UI_SPEC greeting "Afternoon,
 *   *Dilanka*") — always null today.
 */
@Composable
fun HomeScreen(
    viewModel: TextViewModel,
    onOpenDrawer: () -> Unit,
    onTranslateRequested: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenConversation: (() -> Unit)? = null,
    userName: String? = null,
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    HomeContent(
        input = input,
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        languages = languages,
        greeting = viewModel.greeting,
        userName = userName,
        onInputChange = viewModel::onInputChange,
        onTranslate = { if (viewModel.onTranslate()) onTranslateRequested() },
        onSwapLanguages = viewModel::onSwapLanguages,
        onSelectSourceLanguage = viewModel::onSelectSourceLanguage,
        onSelectTargetLanguage = viewModel::onSelectTargetLanguage,
        onClearAll = viewModel::onClearAll,
        onOpenDrawer = onOpenDrawer,
        onOpenCamera = onOpenCamera,
        onOpenConversation = onOpenConversation,
        modifier = modifier,
    )
}

/**
 * Stateless Home hub layout (previewable without DI): transparent top bar over
 * the ambient wash · vertically-centred canvas that hides on the first typed
 * character · [ComposerCard] pinned above the IME with a ~40%-viewport cap.
 */
@Composable
@Suppress("LongMethod") // one cohesive UI_SPEC §2.1 screen; splitting hides the hub structure
fun HomeContent(
    input: String,
    sourceLangId: String,
    targetLangId: String,
    languages: List<Language>,
    greeting: GreetingPeriod,
    userName: String?,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwapLanguages: () -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onClearAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenConversation: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickerTarget by rememberSaveable { mutableStateOf<LanguagePickerTarget?>(null) }

    val guidedMode = stringResource(R.string.text_guided_mode)
    val guidedVoice = stringResource(R.string.text_guided_voice)
    val guidedConversation = stringResource(R.string.text_guided_conversation)

    fun showGuided(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    AmbientBackground(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val composerMaxHeight = maxHeight * COMPOSER_MAX_HEIGHT_FRACTION
            Column(modifier = Modifier.fillMaxSize()) {
                TranzlateTopBar(
                    navigationIcon = {
                        DottedRingIconButton(
                            onClick = onOpenDrawer,
                            contentDescription = stringResource(R.string.cd_text_menu),
                            testTag = "tt_text_menu",
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    },
                    centerContent = {
                        val modeLabel = stringResource(R.string.text_mode_automatic)
                        ModeChip(
                            label = modeLabel,
                            onClick = { showGuided(guidedMode) },
                            contentDescription = stringResource(R.string.cd_text_mode_chip, modeLabel),
                            stateDescription = modeLabel,
                            testTag = "tt_text_mode_chip",
                        )
                    },
                    actions = {
                        DottedRingIconButton(
                            onClick = onClearAll,
                            contentDescription = stringResource(R.string.cd_text_clear),
                            testTag = "tt_text_clear",
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    // UI_SPEC §2.2 typing behaviour: the canvas hides on the FIRST
                    // character and returns only when the field is completely empty.
                    CanvasVisibility(visible = input.isEmpty()) {
                        HomeCanvas(
                            greeting = greeting,
                            userName = userName,
                            onOpenCamera = onOpenCamera,
                            onOpenConversation = onOpenConversation ?: { showGuided(guidedConversation) },
                        )
                    }
                }
                ComposerCard(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = stringResource(R.string.text_input_placeholder),
                    sourceLabel = languageDisplayName(sourceLangId),
                    targetLabel = languageDisplayName(targetLangId),
                    sourceContentDescription =
                        stringResource(R.string.cd_text_source_lang, languageDisplayName(sourceLangId)),
                    targetContentDescription =
                        stringResource(R.string.cd_text_target_lang, languageDisplayName(targetLangId)),
                    swapContentDescription = stringResource(R.string.cd_swap_language),
                    micContentDescription = stringResource(R.string.cd_text_mic),
                    translateContentDescription = stringResource(R.string.cd_translate),
                    inputContentDescription = stringResource(R.string.cd_text_input),
                    onSourceClick = { pickerTarget = LanguagePickerTarget.SOURCE },
                    onTargetClick = { pickerTarget = LanguagePickerTarget.TARGET },
                    onSwap = onSwapLanguages,
                    onMic = { showGuided(guidedVoice) },
                    onTranslate = onTranslate,
                    translateEnabled = input.isNotBlank() && input.length <= TEXT_CHAR_LIMIT,
                    counterText =
                        when {
                            input.isEmpty() -> {
                                null
                            }

                            // Over-limit: never truncate — keep the text, block the
                            // action and say why (EDGE_CASES OVER_CHAR_LIMIT).
                            input.length > TEXT_CHAR_LIMIT -> {
                                stringResource(R.string.text_over_char_limit, TEXT_CHAR_LIMIT)
                            }

                            else -> {
                                stringResource(R.string.text_char_counter, input.length, TEXT_CHAR_LIMIT)
                            }
                        },
                    counterContentDescription =
                        stringResource(R.string.cd_text_counter, input.length, TEXT_CHAR_LIMIT),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) onTranslate() }),
                    inputTestTag = "tt_text_input",
                    counterTestTag = "tt_text_counter",
                    sourceTestTag = "tt_text_source_lang",
                    targetTestTag = "tt_text_target_lang",
                    swapTestTag = "tt_text_swap",
                    actionTestTag = "tt_text_translate_btn",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = composerMaxHeight)
                            .padding(horizontal = spacing.md16)
                            .padding(bottom = spacing.md16),
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    pickerTarget?.let { target ->
        LanguagePickerSheet(
            target = target,
            languages = languages,
            selectedId = if (target == LanguagePickerTarget.SOURCE) sourceLangId else targetLangId,
            onSelect = { id ->
                when (target) {
                    LanguagePickerTarget.SOURCE -> onSelectSourceLanguage(id)
                    LanguagePickerTarget.TARGET -> onSelectTargetLanguage(id)
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }
}

/**
 * Own function so the TOP-LEVEL [AnimatedVisibility] overload resolves (inside
 * the hub Column the ColumnScope extension shadows it).
 */
@Composable
private fun CanvasVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.MEDIUM_1)),
        exit = fadeOut(tween(Motion.SHORT_4)),
    ) {
        content()
    }
}

/** Canvas band (UI_SPEC §2.1): sparkle → greeting → subtitle → quick-action tiles. */
@Composable
private fun HomeCanvas(
    greeting: GreetingPeriod,
    userName: String?,
    onOpenCamera: () -> Unit,
    onOpenConversation: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val periodText =
        stringResource(
            when (greeting) {
                GreetingPeriod.MORNING -> R.string.home_greeting_morning
                GreetingPeriod.AFTERNOON -> R.string.home_greeting_afternoon
                GreetingPeriod.EVENING -> R.string.home_greeting_evening
            },
        )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md16),
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.iconLg),
        )
        Spacer(modifier = Modifier.height(spacing.sm8))
        Text(
            text =
                if (userName == null) {
                    buildAnnotatedString { append(periodText) }
                } else {
                    // home_greeting_named = "%1$s, %2$s" — name rendered in primary.
                    buildAnnotatedString {
                        append(periodText)
                        append(", ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(userName)
                        }
                    }
                },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg24))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm8)) {
            QuickActionTile(
                title = stringResource(R.string.home_tile_conversation),
                icon = Icons.Filled.Forum,
                onClick = onOpenConversation,
                subLabel = stringResource(R.string.home_tile_conversation_sub),
                testTag = "tt_text_tile_conversation",
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                title = stringResource(R.string.home_tile_camera),
                icon = Icons.Filled.PhotoCamera,
                onClick = onOpenCamera,
                subLabel = stringResource(R.string.home_tile_camera_sub),
                testTag = "tt_text_tile_camera",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeContentEmptyPreview() {
    TranzlateTheme {
        HomeContent(
            input = "",
            sourceLangId = "en",
            targetLangId = "fr",
            languages = emptyList(),
            greeting = GreetingPeriod.AFTERNOON,
            userName = null,
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onSelectSourceLanguage = {},
            onSelectTargetLanguage = {},
            onClearAll = {},
            onOpenDrawer = {},
            onOpenCamera = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeContentTypingPreview() {
    TranzlateTheme {
        HomeContent(
            input = "Good morning",
            sourceLangId = "en",
            targetLangId = "fr",
            languages = emptyList(),
            greeting = GreetingPeriod.MORNING,
            userName = null,
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onSelectSourceLanguage = {},
            onSelectTargetLanguage = {},
            onClearAll = {},
            onOpenDrawer = {},
            onOpenCamera = {},
        )
    }
}
