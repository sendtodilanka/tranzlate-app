package com.codeboxlk.tranzlate.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * DESIGN_SYSTEM §8 motion tokens (M3 motion system).
 * Guidance: ENTER with decelerate (`medium2`), EXIT with accelerate (`short4`),
 * STATE toggles at `short3`–`short4` with `standard`.
 */
object Motion {
    // Durations (ms)

    /** Icon state flip, ripple start. */
    const val SHORT_1 = 50

    /** Small selection, checkbox. */
    const val SHORT_2 = 100

    /** Chip/toggle state. */
    const val SHORT_3 = 150

    /** Standard state change, nav item. */
    const val SHORT_4 = 200

    /** Card expand, small enter. */
    const val MEDIUM_1 = 250

    /** DEFAULT screen content transition. */
    const val MEDIUM_2 = 300

    /** Bottom sheet enter. */
    const val MEDIUM_3 = 350

    /** Large container transform. */
    const val MEDIUM_4 = 400

    /** Full-screen shared-element/hero. */
    const val LONG_1 = 450

    /** Splash → home hand-off. */
    const val LONG_2 = 500

    // Easing (cubic-bezier)

    /** Most on-screen state changes. */
    val standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** ENTER (elements arriving). */
    val standardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    /** EXIT (elements leaving). */
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    /** Hero / expressive transitions (emphasized spec). */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Sheet/dialog enter. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Sheet/dialog dismiss. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}
