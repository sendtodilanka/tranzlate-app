package com.codeboxlk.tranzlate.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.R
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * The one destination for a Home entry whose feature has not shipped yet
 * (Camera — issue #78 still open; Conversation/Dialog — deferred to v2).
 *
 * Two rules it exists to satisfy:
 *  - **EDGE_CASES no-dead-end** — the screen says plainly what is not here and
 *    what to do instead, instead of a blank canvas the user has to guess at.
 *  - **A back affordance is not optional.** The gesture alone is not an
 *    affordance: it is invisible, and on a 3-button device the arrow is the only
 *    thing that reads as "you can leave". Both destinations that use this screen
 *    were reachable with no visible way back before this.
 *
 * Copy promises no date. "Coming soon" as a bare line is a claim we cannot keep;
 * [message] names the gap and points at the feature that does work today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(
    title: String,
    message: String,
    icon: ImageVector,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tt_coming_soon"),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("tt_coming_soon_back"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_coming_soon_back),
                        )
                    }
                },
            )
        },
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
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.md16),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.sm8).testTag("tt_coming_soon_message"),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ComingSoonPreview() {
    TranzlateTheme {
        ComingSoonScreen(
            title = "Conversation",
            message =
                "Two-way conversation isn't in this version yet. " +
                    "You can still translate anything you type on the home screen.",
            icon = Icons.AutoMirrored.Filled.Chat,
            onBack = {},
        )
    }
}
