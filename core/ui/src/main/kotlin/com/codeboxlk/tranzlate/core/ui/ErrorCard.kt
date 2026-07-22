package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * The one failure surface (EDGE_CASES no-dead-end rule): a reason plus a way
 * forward, inline — never a dialog, never a blank screen. Google Translate does
 * the same: an `errorContainer` card with a text action, no modal.
 *
 * Stock [Card] + [TextButton]; the action sits bottom-end where M3 puts a card's
 * primary action. The container announces ASSERTIVELY (a11y contract §2.3 —
 * Assertive is reserved for error/limit).
 *
 * Features pass their contract testTags (e.g. `tt_text_error_view` /
 * `tt_text_retry`) so Compose + Maestro can target the instance.
 */
@Composable
fun ErrorCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** C-4 assertive announcement (e.g. "Translation failed. …"); null = read the message. */
    announcement: String? = null,
    containerTestTag: String = "tt_core_error_card",
    actionTestTag: String = "tt_core_error_action",
) {
    val spacing = LocalSpacing.current
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier =
            modifier
                .testTag(containerTestTag)
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    if (announcement != null) contentDescription = announcement
                },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.md16, top = spacing.md16, end = spacing.md16),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onAction,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .testTag(actionTestTag),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ErrorCardPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ErrorCard(
                message = "Something went wrong while translating. Please check your connection and try again.",
                actionLabel = "Retry",
                onAction = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(LocalSpacing.current.md16),
            )
        }
    }
}
