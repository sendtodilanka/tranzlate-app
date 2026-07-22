package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.4 result-screen block (used for BOTH source and target): small
 * caps label ("ENGLISH" / "සිංහල" — pass pre-capitalised where the script has
 * caps) + optional trailing [badge] slot ([EngineBadge]) · main [text] (large,
 * selectable; pass `textColor = colorScheme.primary` for the TARGET block —
 * UI_SPEC §3 result-text rule) · optional [secondaryText] (phonetic /
 * transliteration) · optional [actions] row slot (caller supplies
 * [DottedRingIconButton]s: speaker / copy / 👍 / 👎).
 *
 * The main text carries a polite live region so "result ready" is announced
 * (a11y contract §2.3).
 */
@Composable
fun ResultBlock(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    secondaryText: String? = null,
    textTestTag: String = "tt_core_result_text",
    /** C-4 live-region announcement (e.g. "Translation ready: …"); null = read the text itself. */
    announcement: String? = null,
    badge: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            badge?.invoke()
        }
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = textColor.takeOrElse { MaterialTheme.colorScheme.onSurface },
                modifier =
                    Modifier
                        .testTag(textTestTag)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            if (announcement != null) contentDescription = announcement
                        },
            )
        }
        if (secondaryText != null) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                content = actions,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ResultBlockPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            ResultBlock(
                label = "සිංහල",
                text = "ආයුබෝවන්",
                textColor = MaterialTheme.colorScheme.primary,
                secondaryText = "āyubōvan",
                badge = { EngineBadge(text = "Offline · instant", icon = Icons.Filled.OfflineBolt) },
                actions = {
                    DottedRingIconButton(onClick = {}, contentDescription = "Copy translation") {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    }
                },
                modifier = Modifier.padding(spacing.md16),
            )
        }
    }
}
