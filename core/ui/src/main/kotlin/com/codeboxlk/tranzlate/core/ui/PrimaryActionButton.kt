package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * The ONE saturated element on a screen (UI_SPEC §1 accent discipline): a stock
 * [FilledIconButton] carrying the screen's primary action — Translate, mic, send.
 *
 * The only thing this adds over calling [FilledIconButton] directly is the
 * **light/dark container rule**, which lives here so it is stated once:
 * light theme fills with `primary`, dark theme with `primaryContainer`. In dark
 * `primary` is a near-white blue that glares as a large disc on the near-black
 * page; Google Translate uses the deep container tone there instead.
 *
 * The [icon] slot is decorative — pass `contentDescription = null` on the inner
 * [Icon]; TalkBack reads this button's [contentDescription] (a11y contract §2.1).
 */
@Composable
fun PrimaryActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tt_core_primary_action",
    icon: @Composable () -> Unit,
) {
    // TODO(#7): when an in-app theme override ships, read the theme's own dark
    //  flag instead of the system one (the two can disagree only from then on).
    val dark = isSystemInDarkTheme()
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor =
                    if (dark) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                contentColor =
                    if (dark) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
            ),
        modifier =
            modifier
                .size(Dimensions.touchTargetMin)
                .testTag(testTag)
                .semantics { this.contentDescription = contentDescription },
        content = icon,
    )
}

@PreviewLightDark
@Composable
private fun PrimaryActionButtonPreview() {
    TranzlateTheme {
        val spacing = LocalSpacing.current
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.padding(spacing.md16),
            ) {
                PrimaryActionButton(onClick = {}, contentDescription = "Translate") {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
                PrimaryActionButton(onClick = {}, contentDescription = "Translate by voice") {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                }
                PrimaryActionButton(onClick = {}, contentDescription = "Translate", enabled = false) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
