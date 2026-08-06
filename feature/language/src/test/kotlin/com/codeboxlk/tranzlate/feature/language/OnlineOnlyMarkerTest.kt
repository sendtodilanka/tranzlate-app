package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Div 2: an online-only row states "online only" as a text chip in a full-width
 * column and as a `cloud_off` glyph where the catalog is two-up or beside a pane.
 * [onlineOnlyMarkerFor] is the whole of that decision, and this pins it two ways —
 * the two BOUNDARIES a single-term rule would get wrong, and all eight picker frames
 * driven through the REAL gate ([pickerArrangement]) so a change to what a window
 * produces reddens here instead of hiding behind a hand-built input (the same tie
 * `PickerArrangementTest` makes).
 *
 * ## Mutations decided before the assertions
 *
 * - **Drop the `columns >= 2` term** (glyph iff `twoPane`): the tablet-landscape
 *   dialog is `twoPane = false, columns = 2` and would wrongly keep the chip —
 *   caught by [`two columns without a pane draw the glyph`].
 * - **Drop the `twoPane` term** (glyph iff `columns >= 2`): the foldable leaf is
 *   `twoPane = true, columns = 1` and would wrongly keep the chip — caught by
 *   [`a one-column two-pane leaf draws the glyph`].
 * - **Always the chip** (the pre-fix behaviour): every glyph case reddens.
 */
class OnlineOnlyMarkerTest {
    // ---- the two boundaries a single-term rule gets wrong -------------------

    /**
     * `twoPane = false, columns = 2` — the tablet-landscape dialog (17d) narrows its
     * rows with a second column and draws NO side pane, so a `twoPane`-only rule
     * would leave it on the chip. The export draws the glyph.
     */
    @Test
    fun `two columns without a pane draw the glyph`() {
        val arrangement = PickerArrangement(twoPane = false, columns = 2, rail = false)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Glyph)
    }

    /**
     * `twoPane = true, columns = 1` — the foldable leaf (17b / 18b) is one column but
     * only half the window, beside a shortcut leaf, so a `columns >= 2`-only rule
     * would leave it on the chip. The export draws the glyph.
     */
    @Test
    fun `a one-column two-pane leaf draws the glyph`() {
        val arrangement = PickerArrangement(twoPane = true, columns = 1)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Glyph)
    }

    /** The full-width single column keeps the chip — the other side of both boundaries. */
    @Test
    fun `a full-width single column draws the chip`() {
        assertThat(onlineOnlyMarkerFor(PickerArrangement.SinglePane)).isEqualTo(OnlineOnlyMarker.Chip)
    }

    // ---- the eight frames, each through the real gate -----------------------

    /** 15a / 16a / 18a — phone portrait, one full-width scroller. */
    @Test
    fun `phone portrait draws the chip`() {
        val arrangement = pickerArrangement(411.dp, 891.dp, posture = FoldPosture.FLAT)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Chip)
    }

    /** 17a — landscape phone, the two-up catalog beside the shortcut pane. */
    @Test
    fun `landscape phone draws the glyph`() {
        val arrangement = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Glyph)
    }

    /** 17b / 18b — foldable book posture, the single-column leaf beside the shortcut leaf. */
    @Test
    fun `foldable book posture draws the glyph`() {
        val arrangement = pickerArrangement(760.dp, 812.dp, posture = FoldPosture.BOOK)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Glyph)
    }

    /** 17c — the tablet-portrait dialog card, one column (its box is taller than wide). */
    @Test
    fun `tablet portrait dialog draws the chip`() {
        val arrangement =
            pickerArrangement(560.dp, 998.dp, posture = FoldPosture.FLAT, host = PickerHost.DIALOG)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Chip)
    }

    /** 17d — the tablet-landscape dialog card, two columns (wider than tall, no pane). */
    @Test
    fun `tablet landscape dialog draws the glyph`() {
        val arrangement =
            pickerArrangement(720.dp, 624.dp, posture = FoldPosture.FLAT, host = PickerHost.DIALOG)

        assertThat(onlineOnlyMarkerFor(arrangement)).isEqualTo(OnlineOnlyMarker.Glyph)
    }
}
