package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * DESIGN_SYSTEM §5 shape/corner tokens mapped onto `MaterialTheme.shapes`.
 *  - extraSmall 4dp: snackbar, small chips
 *  - small 8dp: text fields, small cards
 *  - medium 12dp: DEFAULT cards, list items
 *  - large 16dp: bottom sheets (top corners), dialogs, hero cards
 *  - extraLarge 28dp: large modal sheets, FAB-adjacent surfaces
 */
val TranzlateShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** §5 `full` (50%) token — pills, AssistChip, avatar, NavigationBar indicator. */
val TranzlateShapeFull: Shape = CircleShape

/** §5 `none` token — full-bleed images. */
val TranzlateShapeNone: Shape = RoundedCornerShape(0.dp)
