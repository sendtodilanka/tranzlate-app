package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing

/**
 * Shared in-progress surface (EDGE_CASES §7 lifecycle: InProgress = VISIBLE
 * feedback). Polite live region (a11y contract §2.3 — e.g. "Translating…").
 *
 * Features pass their contract testTag (e.g. `tt_text_loading`).
 */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.core_ui_loading),
    testTag: String = "tt_core_loading",
) {
    val spacing = LocalSpacing.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(spacing.md16)
                .testTag(testTag)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm8),
    ) {
        CircularProgressIndicator()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
