package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/**
 * UI_SPEC §2.1 top bar: ☰ nav slot at the start, [ModeChip] centred, action
 * slot at the end. TRANSPARENT — it rides directly on the page `surface`
 * (no container color, no elevation).
 *
 * The centre slot stays optically centred regardless of how wide the side
 * slots are (Box alignment, not a Row split).
 */
@Composable
fun TranzlateTopBar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    centerContent: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.topBarHeight)
                .padding(horizontal = spacing.xs4),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            navigationIcon()
        }
        Box(modifier = Modifier.align(Alignment.Center)) {
            centerContent()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd),
            content = actions,
        )
    }
}

@PreviewLightDark
@Composable
private fun TranzlateTopBarPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            TranzlateTopBar(
                navigationIcon = {
                    DottedRingIconButton(onClick = {}, contentDescription = "Open navigation") {
                        Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                },
                centerContent = {
                    ModeChip(
                        label = "Automatic",
                        onClick = {},
                        contentDescription = "Translation model, Automatic",
                    )
                },
                actions = {
                    DottedRingIconButton(onClick = {}, contentDescription = "New translation") {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                },
            )
        }
    }
}
