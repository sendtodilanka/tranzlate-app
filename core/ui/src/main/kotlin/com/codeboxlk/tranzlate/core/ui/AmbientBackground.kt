package com.codeboxlk.tranzlate.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.codeboxlk.tranzlate.core.designsystem.Alpha
import com.codeboxlk.tranzlate.core.designsystem.LocalAmbientGradient
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme

/** Bottom glow radius as a fraction of page height — wide enough for no hard edge. */
private const val GLOW_RADIUS_FRACTION = 0.85f

/** The vertical teal wash passes the midpoint of its strength just past mid-page. */
private const val WASH_MID_STOP = 0.55f

/**
 * UI_SPEC §1 page background: `surface` + the ambient wash — a soft
 * `primary`-tinted vertical gradient strengthening toward the bottom, plus a
 * blue radial glow anchored at the bottom edge. Low opacity, no hard edges
 * ([Alpha.WASH_FAINT]..[Alpha.WASH_STRONG]).
 *
 * Gradient discipline (DESIGN_SYSTEM §2): page background + at most ONE
 * signature element — never behind body text; floating surfaces sit over this
 * as a lighter step ([com.codeboxlk.tranzlate.core.designsystem.LocalFloatingSurface]).
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ambient = LocalAmbientGradient.current
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier =
            modifier
                .background(surface)
                .drawBehind {
                    // Vertical teal wash — strengthens toward the bottom.
                    drawRect(
                        Brush.verticalGradient(
                            0f to ambient.start.copy(alpha = Alpha.WASH_FAINT),
                            WASH_MID_STOP to
                                ambient.start.copy(alpha = (Alpha.WASH_FAINT + Alpha.WASH_STRONG) / 2f),
                            1f to ambient.start.copy(alpha = Alpha.WASH_STRONG),
                        ),
                    )
                    // Soft blue glow rising from the bottom-centre — no hard edges.
                    drawRect(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    ambient.end.copy(alpha = Alpha.WASH_STRONG),
                                    ambient.end.copy(alpha = 0f),
                                ),
                            center = Offset(x = size.width / 2f, y = size.height),
                            radius = (size.height * GLOW_RADIUS_FRACTION).coerceAtLeast(1f),
                        ),
                    )
                },
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun AmbientBackgroundPreview() {
    TranzlateTheme {
        AmbientBackground(modifier = Modifier.fillMaxSize()) {}
    }
}
