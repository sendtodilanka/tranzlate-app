package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

// Whole-look sanity previews (plan §2): the Home hub and the Result screen
// assembled purely from :core:ui components + inline sample data — no DI, no
// feature module. The real screens are built in PR-C (:feature:text).

@PreviewLightDark
@Composable
@Suppress("LongMethod") // preview assembly — mirrors the full UI_SPEC §2.1 hub in one place
private fun HomePreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TranzlateTopBar(
                    navigationIcon = {
                        DottedRingIconButton(onClick = {}, contentDescription = "Open navigation") {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    },
                    centerContent = {
                        ModeChip(
                            label = "Automatic",
                            onClick = {},
                            contentDescription = "Translation model, Automatic",
                        )
                    },
                    actions = {
                        DottedRingIconButton(onClick = {}, contentDescription = "New translation") {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .weight(1f)
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
                            buildAnnotatedString {
                                append("Afternoon, ")
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("Dilanka")
                                }
                            },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "What would you like to translate?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacing.lg24))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm8)) {
                        QuickActionTile(
                            title = "Conversation",
                            icon = Icons.Filled.Forum,
                            onClick = {},
                            subLabel = "Two-way talk",
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionTile(
                            title = "Camera",
                            icon = Icons.Filled.PhotoCamera,
                            onClick = {},
                            subLabel = "Point and translate",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ComposerCard(
                    value = "hello",
                    onValueChange = {},
                    placeholder = "Enter text",
                    sourceLabel = "English",
                    targetLabel = "සිංහල",
                    sourceContentDescription = "Source language, English",
                    targetContentDescription = "Target language, Sinhala",
                    swapContentDescription = "Swap source and target languages",
                    micContentDescription = "Translate by voice",
                    inputContentDescription = "Text to translate",
                    translateContentDescription = "Translate",
                    onSourceClick = {},
                    onTargetClick = {},
                    onSwap = {},
                    onMic = {},
                    onTranslate = {},
                    counterText = "5/500",
                    counterContentDescription = "5 of 500 characters used",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md16)
                            .padding(bottom = spacing.md16),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
@Suppress("LongMethod") // preview assembly — mirrors the full UI_SPEC §2.4 result screen
private fun ResultPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.lg24),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(spacing.md16),
            ) {
                ResultBlock(
                    label = "ENGLISH",
                    text = "hello",
                    secondaryText = "/ həˈloʊ /",
                    actions = {
                        DottedRingIconButton(onClick = {}, contentDescription = "Speak source text") {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        }
                        DottedRingIconButton(onClick = {}, contentDescription = "Copy source text") {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ResultBlock(
                    label = "සිංහල",
                    text = "ආයුබෝවන්",
                    textColor = MaterialTheme.colorScheme.primary,
                    secondaryText = "āyubōvan",
                    badge = {
                        EngineBadge(text = "Offline · instant", icon = Icons.Filled.OfflineBolt)
                    },
                    actions = {
                        DottedRingIconButton(onClick = {}, contentDescription = "Speak translation") {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        }
                        DottedRingIconButton(onClick = {}, contentDescription = "Copy translation") {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        }
                        DottedRingIconButton(
                            onClick = {},
                            contentDescription = "Good translation",
                            selected = false,
                        ) {
                            Icon(Icons.Filled.ThumbUp, contentDescription = null)
                        }
                        DottedRingIconButton(
                            onClick = {},
                            contentDescription = "Bad translation",
                            selected = false,
                        ) {
                            Icon(Icons.Filled.ThumbDown, contentDescription = null)
                        }
                    },
                )
                FollowUpChipRow(
                    chips =
                        listOf(
                            FollowUpChip(label = "Formal", icon = Icons.Filled.AutoAwesome),
                            FollowUpChip(label = "Explain"),
                            FollowUpChip(label = "Examples"),
                        ),
                    onChipClick = {},
                )
            }
        }
    }
}
