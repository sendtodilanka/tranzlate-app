package com.codeboxlk.tranzlate.feature.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * Scaffold placeholder (plan §2 Ring 4) — the real feature lands in its own
 * vertical per docs/specs + the TEST_A11Y contract tags. Screens only ASK the
 * brains; they never do the work (APP_STRUCTURE rule).
 */
@Composable
fun TextScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tt_text_placeholder"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.feature_text_placeholder),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
