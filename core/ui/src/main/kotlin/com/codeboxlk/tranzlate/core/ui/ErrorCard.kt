package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.designsystem.R as DsR

/**
 * The one failure surface (EDGE_CASES no-dead-end rule): a reason plus a way
 * forward, inline — never a dialog, never a blank screen.
 *
 * Issue #103 (owner): the previous card was a bare message + text button and
 * read as unfinished. This is the shape the old app got right — an alert icon,
 * a short bold TITLE naming what failed, the message under it, and a REAL
 * button — rebuilt on M3 colour roles instead of the old app's manual
 * light/dark swapping (`errorContainer`/`onErrorContainer` already invert; a
 * filled button ON that container takes `error`/`onError` so it keeps contrast
 * in both themes).
 *
 * [secondaryLabel] is the optional second exit (e.g. "Edit text") so a failure
 * that Retry cannot fix still has a way out.
 *
 * The container announces ASSERTIVELY (a11y contract §2.3 — Assertive is
 * reserved for error/limit). Features pass their contract testTags.
 */
@Composable
fun ErrorCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** Short bold line above the message; null keeps the message alone. */
    title: String? = null,
    /** Optional second exit; label AND handler must be present to render. */
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    /** C-4 assertive announcement (e.g. "Translation failed. …"); null = read the message. */
    announcement: String? = null,
    containerTestTag: String = "tt_core_error_card",
    actionTestTag: String = "tt_core_error_action",
    secondaryTestTag: String = "tt_core_error_secondary",
) {
    val spacing = LocalSpacing.current
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = modifier.testTag(containerTestTag),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm8),
            modifier = Modifier.fillMaxWidth().padding(spacing.md16),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(DsR.drawable.ic_error),
                    // The title (or message) already carries the meaning — a
                    // second "error" reading is noise for TalkBack.
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconMd),
                )
                if (title != null) {
                    // Same label treatment as the result card's language line
                    // (issue #103: one type scale across the cards).
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Lens catch (#104): the assertive region lives on this LEAF. On the
            // Card container it wrapped children that repeat the same copy, so
            // TalkBack could read the failure twice.
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                        if (announcement != null) contentDescription = announcement
                    },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(
                        onClick = onSecondary,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        modifier = Modifier.testTag(secondaryTestTag),
                    ) {
                        Text(text = secondaryLabel)
                    }
                }
                // M3 puts a card's primary action at the end of its action row.
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onAction,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier = Modifier.testTag(actionTestTag),
                ) {
                    Text(text = actionLabel)
                }
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
                title = "Couldn't translate",
                message =
                    "You're offline and this language pair isn't downloaded yet. " +
                        "Connect to the internet, or download it for offline use.",
                actionLabel = "Try again",
                onAction = {},
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.md16),
            )
        }
    }
}

/** With the second exit — a failure Retry alone cannot fix. */
@PreviewLightDark
@Composable
private fun ErrorCardWithSecondaryPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ErrorCard(
                title = "Couldn't translate",
                message = "This language pair isn't supported yet.",
                actionLabel = "Try again",
                onAction = {},
                secondaryLabel = "Edit text",
                onSecondary = {},
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.md16),
            )
        }
    }
}

/** Message-only (no title) — the short-cause shape. */
@PreviewLightDark
@Composable
private fun ErrorCardMessageOnlyPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ErrorCard(
                message = "Something went wrong. Please try again.",
                actionLabel = "Retry",
                onAction = {},
                modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.md16),
            )
        }
    }
}
