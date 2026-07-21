package com.codeboxlk.tranzlate.core.designsystem

/**
 * DESIGN_SYSTEM "Alpha tokens (state opacity)".
 * Never alpha-only dimming to signal "over limit" — lock icon + readable-contrast
 * text (badge/counter style rule).
 */
object Alpha {
    /** Disabled controls (e.g. Swap on Auto source). */
    const val DISABLED = 0.38f

    /** Bottom-sheet grabber. */
    const val DRAG_HANDLE = 0.40f

    /** Modal scrim. */
    const val SCRIM = 0.32f

    // Interaction states (M3 defaults)
    const val HOVER = 0.08f
    const val FOCUS = 0.12f
    const val PRESSED = 0.12f
}
