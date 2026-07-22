package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.5 inline error: a reason + an inline Retry — no dialog, no dead
 * end (EDGE_CASES no-dead-end rule). `errorContainer`-tinted row; assertive
 * live region (a11y contract §2.3 — Assertive is reserved for error/limit).
 */
@Composable
fun InlineErrorRetry(
    message: String,
    /** C-4 assertive announcement (e.g. "Translation failed. …"); null = read the message. */
    announcement: String? = null,
    onRetry: () -> Unit,
    retryLabel: String,
    modifier: Modifier = Modifier,
    containerTestTag: String = "tt_core_inline_error",
    retryTestTag: String = "tt_core_inline_retry",
) {
    val spacing = LocalSpacing.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            modifier
                .testTag(containerTestTag)
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    if (announcement != null) contentDescription = announcement
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.padding(horizontal = spacing.md16, vertical = spacing.xs4),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRetry,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(retryTestTag),
            ) {
                Text(text = retryLabel)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun InlineErrorRetryPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            InlineErrorRetry(
                message = "No connection — check your network",
                onRetry = {},
                retryLabel = "Retry",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(LocalSpacing.current.md16),
            )
        }
    }
}
