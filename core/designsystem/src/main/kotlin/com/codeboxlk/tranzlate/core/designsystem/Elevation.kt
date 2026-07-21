package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * DESIGN_SYSTEM §6 elevation / tonal levels.
 * M3 uses TONAL elevation (surface color shift via `surfaceTint`) in addition to
 * shadow — use tonal for containers, reserve shadow for floating/scrolled states;
 * in dark theme prefer tonal steps over heavy shadows (§6 note).
 *
 * Shadow-dp ↔ tonal-role mapping:
 *  - level0 → `surface` (page background, flat cards)
 *  - level1 → `surfaceContainerLow` (resting Card, NavigationBar)
 *  - level2 → `surfaceContainer` (elevated Card, scrolled top app bar)
 *  - level3 → `surfaceContainerHigh` (menus, elevated AssistChip, FAB)
 *  - level4 → `surfaceContainerHigh` (navigation drawer)
 *  - level5 → `surfaceContainerHighest` (modal bottom sheet, dialog)
 */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}
