package com.codeboxlk.tranzlate.feature.text

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Boundary pins for the measured-height gate (issue #99). The thresholds are
 * token arithmetic, so the arithmetic is what a regression would break — a
 * one-dp drift silently changes which shape a whole device class gets.
 */
class ComposerFitTest {
    @Test
    fun `just under the folded floor is MINIMAL`() {
        assertThat(composerFitFor(271.9.dp)).isEqualTo(ComposerFit.MINIMAL)
    }

    @Test
    fun `exactly the folded floor already folds chrome`() {
        assertThat(composerFitFor(272.dp)).isEqualTo(ComposerFit.FOLDED_CHROME)
    }

    @Test
    fun `just under the full floor still folds chrome`() {
        assertThat(composerFitFor(375.9.dp)).isEqualTo(ComposerFit.FOLDED_CHROME)
    }

    @Test
    fun `exactly the full floor is FULL`() {
        assertThat(composerFitFor(376.dp)).isEqualTo(ComposerFit.FULL)
    }

    /** The owner's OnePlus 7 Pro landscape, keyboard up — the bug that started #99. */
    @Test
    fun `the reported 832x384 window with the keyboard up is MINIMAL`() {
        assertThat(composerFitFor(107.7.dp)).isEqualTo(ComposerFit.MINIMAL)
    }

    /** Same window, keyboard down: chrome folds but the field still gets room. */
    @Test
    fun `the same window with the keyboard down folds chrome`() {
        assertThat(composerFitFor(332.dp)).isEqualTo(ComposerFit.FOLDED_CHROME)
    }

    /** Phone portrait with the keyboard up must stay untouched. */
    @Test
    fun `phone portrait with the keyboard up stays FULL`() {
        assertThat(composerFitFor(549.7.dp)).isEqualTo(ComposerFit.FULL)
    }
}
