package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DESIGN_SYSTEM §4 spacing scale — named dp tokens via [LocalSpacing].
 * No raw dp padding in layout code (§10 rule).
 */
@Immutable
data class Spacing(
    /** Reset. */
    val none: Dp = 0.dp,
    /** Icon–label gap, hairline insets. */
    val xxs2: Dp = 2.dp,
    /** Chip internal, dense rows. */
    val xs4: Dp = 4.dp,
    /** Between related items. */
    val sm8: Dp = 8.dp,
    /** DEFAULT screen/content padding. */
    val md16: Dp = 16.dp,
    /** Section separation. */
    val lg24: Dp = 24.dp,
    /** Large block separation. */
    val xl32: Dp = 32.dp,
    /** Hero / empty-state vertical rhythm. */
    val xxl48: Dp = 48.dp,
)

/** Provided by [TranzlateTheme]. */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
