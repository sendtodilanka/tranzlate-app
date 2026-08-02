package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * How big the card is (17c/17d) — the two measurements the export gives, and the
 * clamp it cannot.
 *
 * The export draws two windows and both leave room to spare, so every number in
 * [pickerDialogSize] that the export can check is checked by the first two tests
 * here. Everything after them is the part a drawing cannot decide: what the card
 * does in a window the designer did not draw.
 */
class PickerDialogSizeTest {
    /** `from|to · tablet portrait`: an 800×1280 window, a 560dp card, 78% tall. */
    @Test
    fun `the portrait card is the width the export draws`() {
        val size = pickerDialogSize(windowWidth = 800.dp, windowHeight = 1280.dp)

        assertThat(size.width).isEqualTo(560.dp)
        assertThat(size.maxHeight).isEqualTo(1280.dp * Dimensions.PICKER_DIALOG_HEIGHT_FRACTION)
    }

    /** `from|to · tablet landscape`: a 1280×800 window, a 720dp card, 78% tall. */
    @Test
    fun `the landscape card is the width the export draws`() {
        val size = pickerDialogSize(windowWidth = 1280.dp, windowHeight = 800.dp)

        assertThat(size.width).isEqualTo(720.dp)
        assertThat(size.maxHeight).isEqualTo(800.dp * Dimensions.PICKER_DIALOG_HEIGHT_FRACTION)
    }

    /**
     * A square window is not landscape. Pinned because the branch is a strict
     * `>` and an `>=` would read the same to a reviewer: the two widths differ
     * by 160dp, so which side the boundary falls on is 160dp of card.
     */
    @Test
    fun `a square window takes the portrait width`() {
        assertThat(pickerDialogSize(900.dp, 900.dp).width).isEqualTo(560.dp)
    }

    /**
     * **The card may never be wider than the window it is centred in.**
     *
     * A tablet in a freeform or split-screen window is still MEDIUM and still
     * flat, so it still gets a card — and at 700dp the landscape preference of
     * 720dp would hang 10dp off each side. What falls off is the leading Close
     * cross and the trailing Cancel: both of the card's explicit ways out.
     */
    @Test
    fun `a window narrower than the preferred width clamps the card`() {
        val size = pickerDialogSize(windowWidth = 700.dp, windowHeight = 600.dp)

        assertWithMessage("a 720dp card in a 700dp window loses both of its dismiss controls")
            .that(size.width)
            .isEqualTo(700.dp - Dimensions.pickerDialogMargin * 2)
    }

    /** The same clamp on the portrait side, where the preference is 560dp. */
    @Test
    fun `a narrow tall window clamps the portrait card too`() {
        assertThat(pickerDialogSize(windowWidth = 600.dp, windowHeight = 1000.dp).width)
            .isEqualTo(600.dp - Dimensions.pickerDialogMargin * 2)
    }

    /**
     * …and the clamp never makes the card negative. A window narrower than the
     * two margins is not a window this host is reachable in — [pickerHost]
     * refuses COMPACT — but a size function that can return a negative width is
     * a crash waiting for whichever caller stops asking first.
     */
    @Test
    fun `an absurdly narrow window still yields a drawable card`() {
        assertThat(pickerDialogSize(windowWidth = 8.dp, windowHeight = 1000.dp).width)
            .isAtLeast(0.dp)
    }
}
