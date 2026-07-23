package com.codeboxlk.tranzlate.feature.text

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.ui.ErrorCard
import com.codeboxlk.tranzlate.core.ui.ResultBlock
import com.codeboxlk.tranzlate.core.ui.ShimmerResult
import kotlinx.coroutines.launch

/**
 * UI_SPEC §2.4 Result screen — DI shell over [ResultContent]. A separate
 * READING surface: no composer here. Opens straight into the Translating
 * shimmer (the tap navigates immediately), then Result or the inline
 * error card + Retry (UI_SPEC §2.5, no dead ends).
 */
@Composable
fun ResultScreen(
    viewModel: TextViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Process-death recovery: the restored back stack shows this entry while the
    // ViewModel restarted at Idle — replay the persisted request (no dead end).
    LaunchedEffect(Unit) { viewModel.restoreResultIfNeeded() }
    ResultContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onReverse = viewModel::onReverse,
        modifier = modifier,
    )
}

/**
 * Stateless Result layout (previewable without DI): flat `surface` page, plain
 * icon actions, source block · divider · target block. Follow-up chips
 * (UI_SPEC §2.4 `✦ Formal · Explain · Examples`) are the issue-#8 AI slot and
 * are not composed here — no dead chip row renders.
 */
@Composable
fun ResultContent(
    uiState: TextUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReverse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Scaffold, not a bare Box: it is the Surface that provides LocalContentColor
    // (material3 defaults it to Color.Black when nothing does — which painted every
    // icon here black in dark mode), and it is what offsets the snackbar above the
    // navigation bar. Both were audit P0s.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ResultTopBar(onBack = onBack, onGuided = ::showMessage) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.lg24),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md16),
        ) {
            when (uiState) {
                // transient frame during restore only — nothing to draw
                is TextUiState.Idle -> {}

                is TextUiState.Translating -> {
                    SourceBlock(uiState.request, onGuided = ::showMessage, onCopied = ::showMessage)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TranslatingShimmer()
                }

                is TextUiState.Result -> {
                    SourceBlock(uiState.request, onGuided = ::showMessage, onCopied = ::showMessage)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TargetBlock(uiState, onReverse = onReverse, onGuided = ::showMessage, onCopied = ::showMessage)
                }

                is TextUiState.Error -> {
                    SourceBlock(uiState.request, onGuided = ::showMessage, onCopied = ::showMessage)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ErrorCard(
                        message = errorMessage(uiState.reason),
                        actionLabel = stringResource(R.string.button_retry),
                        onAction = onRetry,
                        announcement = stringResource(R.string.a11y_error, errorMessage(uiState.reason)),
                        containerTestTag = "tt_text_error_view",
                        actionTestTag = "tt_text_retry",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Back · bookmark · ⋯ — stock [CenterAlignedTopAppBar], transparent over `surface`. */
@OptIn(ExperimentalMaterial3Api::class) // CenterAlignedTopAppBar's insets/colors overload
@Composable
private fun ResultTopBar(
    onBack: () -> Unit,
    onGuided: (String) -> Unit,
) {
    val guidedBookmark = stringResource(R.string.text_guided_bookmark)
    val guidedMore = stringResource(R.string.text_guided_more)
    CenterAlignedTopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("tt_text_back")) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_text_back),
                )
            }
        },
        actions = {
            IconButton(
                onClick = { onGuided(guidedBookmark) },
                modifier = Modifier.testTag("tt_text_star"),
            ) {
                Icon(
                    Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(R.string.cd_favourite),
                )
            }
            IconButton(
                onClick = { onGuided(guidedMore) },
                modifier = Modifier.testTag("tt_text_more_menu"),
            ) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.cd_text_more))
            }
        },
        // Default windowInsets, not zero: inside a Scaffold the bar owns the status-bar
        // inset, and the content padding it hands down is measured from the bar's height.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** Source block: language label · source text · speaker (guided) + working copy. */
@Composable
private fun SourceBlock(
    request: TranslateRequest,
    onGuided: (String) -> Unit,
    onCopied: (String) -> Unit,
) {
    val guidedTts = stringResource(R.string.text_guided_tts)
    ResultBlock(
        label = languageBlockLabel(request.sourceLang),
        text = request.text,
        textTestTag = "tt_text_source_text",
        actions = {
            ActionIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.cd_text_speak_source),
                testTag = "tt_text_speak_source",
                onClick = { onGuided(guidedTts) },
            )
            CopyIconButton(
                text = request.text,
                contentDescription = stringResource(R.string.cd_text_copy_source),
                testTag = "tt_text_copy_source",
                onCopied = onCopied,
            )
        },
    )
}

/** Target block: label + engine chip · result in `primary` · actions row. */
@Composable
private fun TargetBlock(
    result: TextUiState.Result,
    onReverse: () -> Unit,
    onGuided: (String) -> Unit,
    onCopied: (String) -> Unit,
) {
    val guidedTts = stringResource(R.string.text_guided_tts)
    val guidedFeedback = stringResource(R.string.text_guided_feedback)
    val guidedMode = stringResource(R.string.text_guided_mode)
    ResultBlock(
        label = languageBlockLabel(result.request.targetLang),
        text = result.translatedText,
        textColor = MaterialTheme.colorScheme.primary,
        secondaryText = result.transliteration,
        textTestTag = "tt_text_result",
        announcement = stringResource(R.string.a11y_result_ready, result.translatedText),
        badge = {
            // Stock AssistChip rather than SuggestionChip(enabled = false): a
            // disabled chip dims its label with alpha and would drop the badge
            // text under the 4.5:1 floor (a11y contract §2.5). Tapping explains
            // which engine ran instead of doing nothing (no dead affordance).
            AssistChip(
                onClick = { onGuided(guidedMode) },
                label = { Text(engineBadgeText(result.engine)) },
                leadingIcon = {
                    Icon(
                        engineBadgeIcon(result.engine),
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.iconXs),
                    )
                },
                modifier = Modifier.testTag("tt_text_engine_badge"),
            )
        },
        actions = {
            // C-7 Reverse: result → input, languages swapped, re-translated.
            ActionIconButton(
                icon = Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.cd_text_reverse),
                testTag = "tt_text_reverse",
                onClick = onReverse,
            )
            ActionIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.cd_speak),
                testTag = "tt_text_speak",
                onClick = { onGuided(guidedTts) },
            )
            CopyIconButton(
                text = result.translatedText,
                contentDescription = stringResource(R.string.cd_copy),
                testTag = "tt_text_copy",
                onCopied = onCopied,
            )
            ActionIconButton(
                icon = Icons.Filled.ThumbUp,
                contentDescription = stringResource(R.string.cd_text_thumb_up),
                testTag = "tt_text_thumb_up",
                onClick = { onGuided(guidedFeedback) },
            )
            ActionIconButton(
                icon = Icons.Filled.ThumbDown,
                contentDescription = stringResource(R.string.cd_text_thumb_down),
                testTag = "tt_text_thumb_down",
                onClick = { onGuided(guidedFeedback) },
            )
        },
    )
}

/** Plain M3 [IconButton] — the result screen's actions carry no container (GT parity). */
@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** WORKING copy action (EDGE_CASES §7: success feedback — "Copied"). */
@Composable
private fun CopyIconButton(
    text: String,
    contentDescription: String,
    testTag: String,
    onCopied: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.text_copied)
    ActionIconButton(
        icon = Icons.Filled.ContentCopy,
        contentDescription = contentDescription,
        testTag = testTag,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text)))
                onCopied(copiedMessage)
            }
        },
    )
}

/** UI_SPEC §2.5 translating shimmer + the contract's polite loading live region. */
@Composable
private fun TranslatingShimmer() {
    val translating = stringResource(R.string.a11y_translating)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("tt_text_loading")
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = translating
                },
    ) {
        ShimmerResult(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun errorMessage(reason: FailureReason): String =
    stringResource(
        when (reason) {
            FailureReason.NETWORK, FailureReason.ENGINE -> R.string.text_error_generic_body
            FailureReason.UNSUPPORTED_PAIR -> R.string.text_error_unsupported_pair
            FailureReason.EMPTY_INPUT -> R.string.home_edit_no_text_to_translate_warning
        },
    )

private fun engineBadgeIcon(engine: Engine): ImageVector =
    when (engine) {
        Engine.OFFLINE_MLKIT -> Icons.Filled.OfflineBolt
        Engine.ONLINE_GOOGLE -> Icons.Filled.Cloud
        Engine.ONLINE_CLOUD_NLP -> Icons.Filled.AutoAwesome
    }

@Composable
private fun engineBadgeText(engine: Engine): String =
    stringResource(
        when (engine) {
            Engine.OFFLINE_MLKIT -> R.string.text_engine_badge_offline
            Engine.ONLINE_GOOGLE -> R.string.text_engine_badge_online
            Engine.ONLINE_CLOUD_NLP -> R.string.text_engine_badge_advanced
        },
    )

private val previewRequest =
    TranslateRequest(text = "Good morning", sourceLang = "en", targetLang = "fr", mode = ModeId.AUTO)

@PreviewLightDark
@Composable
private fun ResultContentSuccessPreview() {
    TranzlateTheme {
        ResultContent(
            uiState =
                TextUiState.Result(
                    request = previewRequest,
                    translatedText = "Bonjour (fake)",
                    transliteration = null,
                    engine = Engine.OFFLINE_MLKIT,
                ),
            onBack = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ResultContentTranslatingPreview() {
    TranzlateTheme {
        ResultContent(
            uiState = TextUiState.Translating(previewRequest),
            onBack = {},
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ResultContentErrorPreview() {
    TranzlateTheme {
        ResultContent(
            uiState = TextUiState.Error(previewRequest, FailureReason.NETWORK),
            onBack = {},
            onRetry = {},
        )
    }
}
