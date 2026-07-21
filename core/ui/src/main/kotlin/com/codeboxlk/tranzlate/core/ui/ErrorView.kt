package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing

/**
 * Shared no-dead-end error surface (EDGE_CASES §7): a reason + a way forward,
 * never a blank failure. Container announces assertively (a11y contract §2.3 —
 * Assertive is reserved for error/limit).
 *
 * Features pass their contract testTags (e.g. `tt_text_error_view` /
 * `tt_text_retry`) so Compose + Maestro can target the instance.
 */
@Composable
fun ErrorView(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.core_ui_retry),
    containerTestTag: String = "tt_core_error_view",
    retryTestTag: String = "tt_core_retry",
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.md16)
            .testTag(containerTestTag)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .heightIn(min = Dimensions.touchTargetMin)
                    .testTag(retryTestTag),
            ) {
                Text(text = retryLabel)
            }
        }
    }
}
