package com.codeboxlk.tranzlate.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * Placeholder for a bottom-nav destination whose feature ships later (Chat/Dialog
 * is deferred to v2). The tab stays reachable and says plainly that it is coming
 * — EDGE_CASES no-dead-end — rather than hiding or dead-ending on a blank screen.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tt_coming_soon"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(spacing.md16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconLg),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = spacing.md16),
            )
            Text(
                text = stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs4),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ComingSoonPreview() {
    TranzlateTheme {
        ComingSoonScreen(title = "Conversation", icon = Icons.AutoMirrored.Filled.Chat)
    }
}
