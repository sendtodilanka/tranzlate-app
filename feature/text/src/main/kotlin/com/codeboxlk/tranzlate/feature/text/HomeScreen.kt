package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.Elevation
import com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateShapeFull
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import kotlinx.coroutines.launch
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

// ── Design: the owner's Claude Design export "Offline Translator M3" (issue #42)
// Measurements come from the design system, never from raw dp/sp. Where the
// approved design sits off our scale the token wins — the owner compared both
// renditions and chose the tokenised one, so the scale is the contract and the
// design is the reference for layout, colour and content.
private val ScreenMargin @Composable get() = LocalSpacing.current.md16 // 16 -> 16 (same)
private val SectionGap @Composable get() = LocalSpacing.current.sm8 // 10 -> 8
private val CardRadius @Composable get() = MaterialTheme.shapes.large // 20 -> 16
private val InputCardRadius @Composable get() = MaterialTheme.shapes.extraLarge // 28 -> 28 (same)
private val PillHeight = Dimensions.touchTargetMin // 48 -> 48 (same)
private val CircleIconSize = Dimensions.iconChip // 44 -> 40
private val ActionSize = Dimensions.touchTargetMin // 48 -> 48 (same)
private val CardShadow = Elevation.level1 // 1 -> 1 (same)

// Two-pane split (issue #56 frames 1/4): translate zone 2 : tools 3 (≈40/60).
// A separating hinge overrides both to 1 : 1 (frames 6/7).
private const val PANE_WEIGHT_PRIMARY = 2f
private const val PANE_WEIGHT_SECONDARY = 3f

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
    onOpenComposer: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
    previewCardModifier: Modifier = Modifier,
) {
    val sourceLang by viewModel.sourceLang.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLang.collectAsStateWithLifecycle()

    HomeContent(
        sourceLangId = sourceLang,
        targetLangId = targetLang,
        onOpenComposer = onOpenComposer,
        onSwapLanguages = viewModel::onSwapLanguages,
        onPickLanguage = onPickLanguage,
        onOpenSettings = onOpenSettings,
        onOpenPaywall = onOpenPaywall,
        onOpenCamera = onOpenCamera,
        onOpenLanguages = onOpenLanguages,
        onOpenConversation = onOpenConversation,
        previewCardModifier = previewCardModifier,
        modifier = modifier,
    )
}

/** Stateless card-stack Home (previewable without DI). */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("LongMethod") // one cohesive screen; splitting hides the card-stack order
fun HomeContent(
    sourceLangId: String,
    targetLangId: String,
    onOpenComposer: () -> Unit,
    onSwapLanguages: () -> Unit,
    onPickLanguage: (LanguagePickerTarget) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
    previewCardModifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val guidedVoice = stringResource(R.string.text_guided_voice)
    val guidedPhrasebook = stringResource(R.string.home_guided_phrasebook)
    val guidedQuotes = stringResource(R.string.home_guided_quotes)
    val guidedPhrasing = stringResource(R.string.home_guided_phrasing)

    fun guided(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val layout = rememberAdaptiveLayout()
    if (layout.expandedWidth) {
        // Two-pane Home (issue #56, owner-approved frames 1/4/6): left = the
        // translate zone, right = the tools stack. A separating hinge snaps the
        // split to 50/50 with a gutter so nothing sits under the fold.
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { contentPadding ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = LocalSpacing.current.lg24),
            ) {
                Column(modifier = Modifier.weight(if (layout.hinged) 1f else PANE_WEIGHT_PRIMARY)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(Dimensions.topBarHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.weight(1f))
                        TokenProChip(onClick = onOpenPaywall)
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
                    }
                    LanguageRow(
                        sourceLabel = languageLabel(sourceLangId),
                        targetLabel = languageLabel(targetLangId),
                        onSourceClick = { onPickLanguage(LanguagePickerTarget.SOURCE) },
                        onTargetClick = { onPickLanguage(LanguagePickerTarget.TARGET) },
                        onSwap = onSwapLanguages,
                        swapEnabled = sourceLangId != DETECT_LANGUAGE_ID,
                        modifier = Modifier.padding(vertical = spacing.sm8),
                    )
                    InputPreviewCard(
                        onOpen = onOpenComposer,
                        onMic = onOpenComposer,
                        modifier = previewCardModifier.weight(1f).padding(bottom = ScreenMargin),
                    )
                }
                Spacer(Modifier.width(LocalSpacing.current.lg24))
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    Column(
                        modifier =
                            Modifier
                                .weight(if (layout.hinged) 1f else PANE_WEIGHT_SECONDARY)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(SectionGap),
                    ) {
                        ToolsStack(
                            guided = ::guided,
                            guidedVoice = guidedVoice,
                            guidedPhrasebook = guidedPhrasebook,
                            guidedQuotes = guidedQuotes,
                            guidedPhrasing = guidedPhrasing,
                            onOpenCamera = onOpenCamera,
                            onOpenLanguages = onOpenLanguages,
                            onOpenConversation = onOpenConversation,
                        )
                        Spacer(Modifier.height(ScreenMargin))
                    }
                }
            }
        }
        return
    }

    // Tablet portrait (medium width): the stack itself, centred at a readable width
    // (C-13 single-column rule) rather than stretched edge to edge.
    val contentMaxWidth =
        if (layout.mediumWidth) Modifier.widthIn(max = Dimensions.contentMaxWidthMedium) else Modifier

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
                        TokenProChip(onClick = onOpenPaywall)
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
                        contentMaxWidth
                            .align(Alignment.CenterHorizontally)
                            .padding(
                                start = ScreenMargin,
                                end = ScreenMargin,
                                top = spacing.sm8,
                                bottom = spacing.md16,
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
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            ) {
                Column(
                    modifier =
                        contentMaxWidth
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ScreenMargin),
                    verticalArrangement = Arrangement.spacedBy(SectionGap),
                ) {
                    InputPreviewCard(
                        onOpen = onOpenComposer,
                        onMic = onOpenComposer,
                        modifier = previewCardModifier,
                    )
                    ToolsStack(
                        guided = ::guided,
                        guidedVoice = guidedVoice,
                        guidedPhrasebook = guidedPhrasebook,
                        guidedQuotes = guidedQuotes,
                        guidedPhrasing = guidedPhrasing,
                        onOpenCamera = onOpenCamera,
                        onOpenLanguages = onOpenLanguages,
                        onOpenConversation = onOpenConversation,
                    )
                    Spacer(Modifier.height(ScreenMargin))
                }
            }
        }
    }
}

/** The card stack below the input preview — one home so every window shape reuses it (issue #56). */
@Composable
@Suppress("LongParameterList", "LongMethod") // a straight column of the design's cards
private fun ColumnScope.ToolsStack(
    guided: (String) -> Unit,
    guidedVoice: String,
    guidedPhrasebook: String,
    guidedQuotes: String,
    guidedPhrasing: String,
    onOpenCamera: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenConversation: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Text(
        text = stringResource(R.string.home_tools),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = spacing.xs4, top = spacing.md16),
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
}

/** Pro upsell — a soft 36dp suffix chip, never a blocker (design brief). */
@Composable
private fun TokenProChip(onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.height(spacing.lg24).testTag("tt_home_pro"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(start = spacing.sm8, end = spacing.md16),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_workspace_premium),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSm),
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
internal fun LanguageRow(
    sourceLabel: String,
    targetLabel: String,
    onSourceClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwap: () -> Unit,
    swapEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        LanguagePill(
            label = sourceLabel,
            contentDescription = stringResource(R.string.cd_text_source_lang, sourceLabel),
            onClick = onSourceClick,
            testTag = "tt_text_source_lang",
            modifier = Modifier.weight(1f),
        )
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
                    modifier = Modifier.size(Dimensions.iconMd),
                )
            }
        }
        LanguagePill(
            label = targetLabel,
            contentDescription = stringResource(R.string.cd_text_target_lang, targetLabel),
            onClick = onTargetClick,
            testTag = "tt_text_target_lang",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun LanguagePill(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .height(PillHeight)
                // The visible label is just the language name; TalkBack needs to
                // hear WHICH side it sets, so the pill carries the description.
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(DsR.drawable.ic_arrow_drop_down),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSm),
            )
        }
    }
}

/**
 * Requirement A of the 5a brief: Home keeps ONLY the placeholder and the voice
 * button — no counter, nothing editable. Both taps open the 5a composer (voice
 * input happens THERE); the card is the shared morph anchor, so it carries the
 * A/B variants' sharedBounds modifier.
 */
@Composable
private fun InputPreviewCard(
    onOpen: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onOpen,
        shape = InputCardRadius,
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = modifier.fillMaxWidth().testTag("tt_text_card"),
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
                text = stringResource(R.string.text_input_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimensions.composerInputMinHeight)
                        .testTag("tt_home_input_preview"),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm8),
            ) {
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onMic,
                    shape = TranzlateShapeFull,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(ActionSize).testTag("tt_home_mic"),
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
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = CardRadius,
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = modifier.testTag(testTag),
    ) {
        Column(modifier = Modifier.padding(spacing.md16)) {
            Surface(shape = CircleShape, color = container, modifier = Modifier.size(CircleIconSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(Dimensions.iconMd),
                    )
                }
            }
            Spacer(Modifier.height(spacing.md16))
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
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = CardRadius,
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.md16),
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
                        modifier = Modifier.size(Dimensions.iconMd),
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
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = CardRadius,
        color = LocalFloatingSurface.current,
        shadowElevation = CardShadow,
        modifier = modifier.testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            modifier = Modifier.padding(spacing.md16),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconMd),
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
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = CardRadius,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("tt_home_phrasing"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md16),
            modifier = Modifier.padding(spacing.md16),
        ) {
            Icon(
                painterResource(DsR.drawable.ic_auto_awesome),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_phrasing_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(spacing.sm8))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            text = stringResource(R.string.home_badge_new),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = spacing.sm8),
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
private fun HomeContentPreview() {
    TranzlateTheme {
        HomeContent(
            sourceLangId = "en",
            targetLangId = "es",
            onOpenComposer = {},
            onSwapLanguages = {},
            onPickLanguage = {},
            onOpenSettings = {},
            onOpenPaywall = {},
            onOpenCamera = {},
            onOpenLanguages = {},
            onOpenConversation = {},
        )
    }
}
