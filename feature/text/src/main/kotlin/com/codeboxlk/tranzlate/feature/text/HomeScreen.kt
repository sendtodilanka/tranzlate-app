package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

// ── Design spec (docs/design/OFFLINE_TRANSLATOR_M3.md) ────────────────────────
// Measured from the approved Claude Design export, 412dp frame. These are the
// design's own numbers, so they are literals here rather than scale tokens.
private val ScreenMargin = 16.dp
private val SectionGap = 10.dp
private val CardRadius = 20.dp
private val InputCardRadius = 28.dp
private val PillHeight = 48.dp
private val CircleIconSize = 44.dp
private val ActionSize = 48.dp
private val CardShadow = 1.dp

/**
 * Home — the approved "card stack" (Claude Design · Offline Translator M3):
 * top app bar with a Pro chip + settings, source⇄target pills, one input card
 * whose mic becomes Translate once there is text, a 2×2 tonal tool grid, then
 * list rows. **No bottom bar and no FAB** — every other screen is reached from
 * the tool cards and rows (design brief). Screens only ASK; the caller navigates.
 */
@Composable
fun HomeScreen(
    viewModel: TextViewModel,
    onTranslateRequested: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    HomeContent(
        input = input,
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        onInputChange = viewModel::onInputChange,
        onTranslate = { if (viewModel.onTranslate()) onTranslateRequested() },
        onSwapLanguages = viewModel::onSwapLanguages,
        onPickLanguage = onPickLanguage,
        onOpenSettings = onOpenSettings,
        onOpenCamera = onOpenCamera,
        onOpenLanguages = onOpenLanguages,
        onOpenConversation = onOpenConversation,
        modifier = modifier,
    )
}

/** Stateless card-stack Home (previewable without DI). */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("LongMethod") // one cohesive screen; splitting hides the card-stack order
fun HomeContent(
    input: String,
    sourceLangId: String,
    targetLangId: String,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwapLanguages: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val guidedVoice = stringResource(R.string.text_guided_voice)
    val guidedPhrasebook = stringResource(R.string.home_guided_phrasebook)
    val guidedQuotes = stringResource(R.string.home_guided_quotes)
    val guidedPhrasing = stringResource(R.string.home_guided_phrasing)
    val guidedPro = stringResource(R.string.home_guided_pro)

    fun guided(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Scaffold does not inset topBar content — it expects the bar to do
            // it. TopAppBar handles its own; the pinned language row below it
            // would otherwise sit under a landscape display cutout.
            Column(
                modifier =
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        ProChip(onClick = { guided(guidedPro) })
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("tt_home_settings"),
                        ) {
                            Icon(
                                painterResource(DsR.drawable.ic_settings),
                                contentDescription = stringResource(R.string.cd_home_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                LanguageRow(
                    sourceLabel = languageLabel(sourceLangId),
                    targetLabel = languageLabel(targetLangId),
                    onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
                    onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
                    onSwap = onSwapLanguages,
                    swapEnabled = sourceLangId != DETECT_LANGUAGE_ID,
                    modifier =
                        Modifier.padding(
                            start = ScreenMargin,
                            end = ScreenMargin,
                            top = 8.dp,
                            bottom = 12.dp,
                        ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        // No stretch/glow at the scroll ends: the approved design has no
        // overscroll effect, and on Android 12+ the platform default stretches
        // the whole card stack. `null` factory = no effect (the scroll itself,
        // including fling, is unchanged).
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenMargin),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                InputCard(
                    input = input,
                    onInputChange = onInputChange,
                    onTranslate = onTranslate,
                    onMic = { guided(guidedVoice) },
                )
                Text(
                    text = stringResource(R.string.home_tools),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                    ToolCard(
                        icon = painterResource(DsR.drawable.ic_cloud_done),
                        title = stringResource(R.string.home_tool_offline),
                        subtitle = stringResource(R.string.home_tool_offline_sub),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onOpenLanguages,
                        testTag = "tt_home_tool_offline",
                        modifier = Modifier.weight(1f),
                    )
                    ToolCard(
                        icon = painterResource(DsR.drawable.ic_record_voice_over),
                        title = stringResource(R.string.home_tool_voice),
                        subtitle = stringResource(R.string.home_tool_voice_sub),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { guided(guidedVoice) },
                        testTag = "tt_home_tool_voice",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                    ToolCard(
                        icon = painterResource(DsR.drawable.ic_photo_camera),
                        title = stringResource(R.string.home_tool_camera),
                        subtitle = stringResource(R.string.home_tool_camera_sub),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = onOpenCamera,
                        testTag = "tt_home_tool_camera",
                        modifier = Modifier.weight(1f),
                    )
                    ToolCard(
                        icon = painterResource(DsR.drawable.ic_forum),
                        title = stringResource(R.string.home_tool_conversation),
                        subtitle = stringResource(R.string.home_tool_conversation_sub),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onContainer = MaterialTheme.colorScheme.onSurface,
                        onClick = onOpenConversation,
                        testTag = "tt_home_tool_conversation",
                        modifier = Modifier.weight(1f),
                    )
                }
                ListRowCard(
                    icon = painterResource(DsR.drawable.ic_download_for_offline),
                    title = stringResource(R.string.home_row_download),
                    subtitle = stringResource(R.string.home_row_download_sub),
                    onClick = onOpenLanguages,
                    testTag = "tt_home_row_download",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
                    MiniCard(
                        icon = painterResource(DsR.drawable.ic_menu_book),
                        label = stringResource(R.string.home_mini_phrasebook),
                        onClick = { guided(guidedPhrasebook) },
                        testTag = "tt_home_phrasebook",
                        modifier = Modifier.weight(1f),
                    )
                    MiniCard(
                        icon = painterResource(DsR.drawable.ic_format_quote),
                        label = stringResource(R.string.home_mini_quotes),
                        onClick = { guided(guidedQuotes) },
                        testTag = "tt_home_quotes",
                        modifier = Modifier.weight(1f),
                    )
                }
                PhrasingBanner(onClick = { guided(guidedPhrasing) })
                Spacer(Modifier.height(ScreenMargin))
            }
        }
    }
}

/** Pro upsell — a soft 36dp suffix chip, never a blocker (design brief). */
@Composable
private fun ProChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.height(24.dp).testTag("tt_home_pro"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 14.dp),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_workspace_premium),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.home_pro),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Source ⇄ target: two tonal pills either side of a swap button. */
@Composable
private fun LanguageRow(
    sourceLabel: String,
    targetLabel: String,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit,
    swapEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        LanguagePill(sourceLabel, onSourceClick, "tt_text_source_lang", Modifier.weight(1f))
        Surface(
            onClick = onSwap,
            enabled = swapEnabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = CardShadow,
            modifier = Modifier.size(ActionSize).testTag("tt_text_swap"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(DsR.drawable.ic_swap_horiz),
                    contentDescription = stringResource(R.string.cd_swap_language),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        LanguagePill(targetLabel, onTargetClick, "tt_text_target_lang", Modifier.weight(1f))
    }
}

@Composable
private fun LanguagePill(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(PillHeight).testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(DsR.drawable.ic_arrow_drop_down),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The input card. Per the brief the mic lives in the action row and becomes the
 * filled Translate button as soon as there is text — so there is no FAB.
 */
@Composable
private fun InputCard(
    input: String,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onMic: () -> Unit,
) {
    val hasText = input.isNotBlank()
    val overLimit = input.length > TEXT_CHAR_LIMIT
    Surface(
        shape = RoundedCornerShape(InputCardRadius),
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = Modifier.fillMaxWidth().testTag("tt_text_card"),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
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
                        .heightIn(min = 96.dp)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                    modifier = Modifier.weight(1f).testTag("tt_text_counter"),
                )
                if (hasText) {
                    Button(
                        onClick = onTranslate,
                        enabled = !overLimit,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        modifier = Modifier.height(ActionSize).testTag("tt_text_translate_btn"),
                    ) {
                        Icon(
                            painterResource(DsR.drawable.ic_translate),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.home_translate))
                    }
                } else {
                    Surface(
                        onClick = onMic,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(ActionSize).testTag("tt_text_mic"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(DsR.drawable.ic_mic),
                                contentDescription = stringResource(R.string.cd_text_mic),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One tile of the 2×2 tonal tool grid. */
@Composable
private fun ToolCard(
    icon: Painter,
    title: String,
    subtitle: String,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = modifier.testTag(testTag),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(shape = CircleShape, color = container, modifier = Modifier.size(CircleIconSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Full-width list row (icon · headline + supporting · chevron). */
@Composable
private fun ListRowCard(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(CircleIconSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(DsR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Half-width shortcut card (icon + label). */
@Composable
private fun MiniCard(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = modifier.testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The AI teaser — a tonal banner, kept soft (design brief: never a blocker). */
@Composable
private fun PhrasingBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("tt_home_phrasing"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_auto_awesome),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_phrasing_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            text = stringResource(R.string.home_badge_new),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.home_phrasing_sub),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(painterResource(DsR.drawable.ic_chevron_right), contentDescription = null)
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
            targetLangId = "es",
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onPickLanguage = {},
            onOpenSettings = {},
            onOpenCamera = {},
            onOpenLanguages = {},
            onOpenConversation = {},
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
            targetLangId = "es",
            onInputChange = {},
            onTranslate = {},
            onSwapLanguages = {},
            onPickLanguage = {},
            onOpenSettings = {},
            onOpenCamera = {},
            onOpenLanguages = {},
            onOpenConversation = {},
        )
    }
}
