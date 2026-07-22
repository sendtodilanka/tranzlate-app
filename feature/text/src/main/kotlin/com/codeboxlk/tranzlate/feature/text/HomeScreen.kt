package com.codeboxlk.tranzlate.feature.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.codeboxlk.tranzlate.core.ui.ComposerCard
import com.codeboxlk.tranzlate.core.ui.QuickActionButton
import kotlinx.coroutines.launch

/** Composer growth cap — ~40% of the viewport (UI_SPEC §2.2). */
private const val COMPOSER_MAX_HEIGHT_FRACTION = 0.4f

/**
 * UI_SPEC §2.1 Home hub — DI shell over [HomeContent]. Screens only ASK
 * ([TextViewModel]); navigation stays with the caller (`:app` mediator).
 *
 * @param onTranslateRequested called AFTER a translation actually started
 *   (C-2 tap → Translating) — the caller opens the Result screen.
 * @param onPickLanguage the caller opens the full-screen language picker for
 *   the tapped side (issue #15 — the picker is a destination, not a sheet).
 * @param onOpenConversation Conversation action destination; null (no Dialog
 *   vertical yet) falls back to a guided message — never a dead control.
 * @param userName future account-system slot (UI_SPEC greeting "Afternoon,
 *   *Dilanka*") — always null today.
 */
@Composable
fun HomeScreen(
    viewModel: TextViewModel,
    onOpenDrawer: () -> Unit,
    onTranslateRequested: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    modifier: Modifier = Modifier,
    onOpenConversation: (() -> Unit)? = null,
    userName: String? = null,
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    HomeContent(
        input = input,
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        greeting = viewModel.greeting,
        userName = userName,
        onInputChange = viewModel::onInputChange,
        onTranslate = { if (viewModel.onTranslate()) onTranslateRequested() },
        onSwapLanguages = viewModel::onSwapLanguages,
        onPickLanguage = onPickLanguage,
        onClearAll = viewModel::onClearAll,
        onOpenDrawer = onOpenDrawer,
        onOpenCamera = onOpenCamera,
        onOpenConversation = onOpenConversation,
        modifier = modifier,
    )
}

/**
 * Stateless Home hub layout (previewable without DI): flat `surface` page ·
 * transparent [CenterAlignedTopAppBar] · vertically-centred canvas that hides on
 * the first typed character · [ComposerCard] pinned above the IME with a
 * ~40%-viewport cap.
 */
@Composable
@Suppress("LongMethod") // one cohesive UI_SPEC §2.1 screen; splitting hides the hub structure
fun HomeContent(
    input: String,
    sourceLangId: String,
    targetLangId: String,
    greeting: GreetingPeriod,
    userName: String?,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwapLanguages: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onClearAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenConversation: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val guidedMode = stringResource(R.string.text_guided_mode)
    val guidedVoice = stringResource(R.string.text_guided_voice)
    val guidedConversation = stringResource(R.string.text_guided_conversation)

    fun showGuided(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val composerMaxHeight = maxHeight * COMPOSER_MAX_HEIGHT_FRACTION
            Column(modifier = Modifier.fillMaxSize()) {
                HomeTopBar(
                    onOpenDrawer = onOpenDrawer,
                    onClearAll = onClearAll,
                    onModeClick = { showGuided(guidedMode) },
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
                    sourceLabel = languageLabel(sourceLangId),
                    targetLabel = languageLabel(targetLangId),
                    sourceContentDescription =
                        stringResource(R.string.cd_text_source_lang, languageLabel(sourceLangId)),
                    targetContentDescription =
                        stringResource(R.string.cd_text_target_lang, languageLabel(targetLangId)),
                    swapContentDescription = stringResource(R.string.cd_swap_language),
                    micContentDescription = stringResource(R.string.cd_text_mic),
                    translateContentDescription = stringResource(R.string.cd_translate),
                    inputContentDescription = stringResource(R.string.cd_text_input),
                    onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
                    onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
                    onSwap = onSwapLanguages,
                    onMic = { showGuided(guidedVoice) },
                    onTranslate = onTranslate,
                    translateEnabled = input.isNotBlank() && input.length <= TEXT_CHAR_LIMIT,
                    // "Detect language" has nothing to swap INTO the target slot,
                    // so the control is disabled rather than silently wrong.
                    swapEnabled = sourceLangId != DETECT_LANGUAGE_ID,
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
}

/**
 * UI_SPEC §2.1 hub top bar: ☰ · centred mode chip · new/clear. Stock
 * [CenterAlignedTopAppBar], transparent so the flat page `surface` shows through;
 * insets are already handled by the screen's `safeDrawing` padding.
 */
@OptIn(ExperimentalMaterial3Api::class) // CenterAlignedTopAppBar's insets/colors overload
@Composable
private fun HomeTopBar(
    onOpenDrawer: () -> Unit,
    onClearAll: () -> Unit,
    onModeClick: () -> Unit,
) {
    val modeLabel = stringResource(R.string.text_mode_automatic)
    val modeDescription = stringResource(R.string.cd_text_mode_chip, modeLabel)
    CenterAlignedTopAppBar(
        title = {
            AssistChip(
                onClick = onModeClick,
                label = { Text(modeLabel) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSm),
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.iconSm),
                    )
                },
                modifier =
                    Modifier
                        .testTag("tt_text_mode_chip")
                        .semantics {
                            contentDescription = modeDescription
                            stateDescription = modeLabel
                        },
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("tt_text_menu")) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_text_menu))
            }
        },
        actions = {
            IconButton(onClick = onClearAll, modifier = Modifier.testTag("tt_text_clear")) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_text_clear))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
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

/**
 * Canvas band (UI_SPEC §2.1): sparkle → greeting → subtitle → quick actions.
 * The actions are GT-shaped tonal circles with their label beneath, and the row
 * is built to keep looking intentional as more of them arrive.
 */
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
        Spacer(modifier = Modifier.height(spacing.xl32))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl32)) {
            QuickActionButton(
                label = stringResource(R.string.home_tile_conversation),
                icon = Icons.Filled.Forum,
                onClick = onOpenConversation,
                testTag = "tt_text_tile_conversation",
            )
            QuickActionButton(
                label = stringResource(R.string.home_tile_camera),
                icon = Icons.Filled.PhotoCamera,
                onClick = onOpenCamera,
                testTag = "tt_text_tile_camera",
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
            greeting = GreetingPeriod.AFTERNOON,
            userName = null,
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onPickLanguage = {},
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
            greeting = GreetingPeriod.MORNING,
            userName = null,
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onPickLanguage = {},
            onClearAll = {},
            onOpenDrawer = {},
            onOpenCamera = {},
        )
    }
}
