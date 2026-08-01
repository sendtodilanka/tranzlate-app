package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The 17a window gate (#130 PR-14) — the ruling's "window-gate unit".
 *
 * Every case here is a layout PR-14 must either claim or leave alone, and the
 * ones it must leave alone are the interesting half: 17b's foldable two-leaf
 * (PR-15) and 17c/17d's tablet dialog (PR-16) both arrive at this function
 * looking wide enough, and both must come back single-pane.
 *
 * The window shapes are real ones, not round numbers. 892×412 is the export's
 * own landscape frame; 1280×800 and 800×1280 are the tablet the dialog host is
 * for; 832×384 is the owner's OnePlus 7 Pro in landscape, which issue #99
 * recorded as sitting 8dp under the EXPANDED width breakpoint — it is the whole
 * reason this gate is a dp sum and not a size class.
 */
class PickerArrangementTest {
    // ---- the layout this PR is for ------------------------------------------

    /** `from · landscape` / `to · landscape`, exactly as the export draws them. */
    @Test
    fun `the export's own 892x412 landscape frame is two panes and two columns`() {
        val arrangement = pickerArrangement(892.dp, heightCompact = true, posture = FoldPosture.FLAT)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.columns).isEqualTo(2)
    }

    /**
     * The device issue #99 was filed about. A size-class gate would have left it
     * in the portrait layout — 832dp is 8dp short of EXPANDED — while it has room
     * for the side pane and a column with 16dp to spare.
     */
    @Test
    fun `the OnePlus 7 Pro in landscape gets the two-pane layout`() {
        val arrangement = pickerArrangement(832.dp, heightCompact = true, posture = FoldPosture.FLAT)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.columns).isEqualTo(2)
    }

    // ---- the width threshold -------------------------------------------------

    /**
     * 568dp = side pane 272 + gutter 8 + one 240dp column + the 48dp rail. The
     * pair below is the whole point of the threshold being arithmetic: a drift of
     * a single dp moves a real class of window across it, and nothing else in the
     * app would notice.
     */
    @Test
    fun `exactly the two-pane floor already splits`() {
        assertThat(pickerArrangement(568.dp, heightCompact = true, posture = FoldPosture.FLAT).twoPane)
            .isTrue()
    }

    @Test
    fun `just under the two-pane floor stays single pane`() {
        val arrangement = pickerArrangement(567.9.dp, heightCompact = true, posture = FoldPosture.FLAT)

        assertThat(arrangement).isEqualTo(PickerArrangement.SinglePane)
    }

    /** A phone in portrait is nowhere near it, and must not be, whatever else it reports. */
    @Test
    fun `phone portrait is single pane`() {
        assertThat(pickerArrangement(412.dp, heightCompact = false, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    // ---- the column count ----------------------------------------------------

    /**
     * 808dp = 272 + 8 + 48 + two 240dp columns. Below it the catalog gets one
     * column rather than two cramped ones — a second column that ellipsises every
     * name is worse than a wider single one.
     */
    @Test
    fun `exactly the second-column floor gives two columns`() {
        assertThat(pickerArrangement(808.dp, heightCompact = true, posture = FoldPosture.FLAT).columns)
            .isEqualTo(2)
    }

    @Test
    fun `just under the second-column floor keeps two panes and one column`() {
        val arrangement = pickerArrangement(807.9.dp, heightCompact = true, posture = FoldPosture.FLAT)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.columns).isEqualTo(1)
    }

    /** A single-pane answer is always one column — there is no other list to widen. */
    @Test
    fun `single pane is always one column`() {
        assertThat(PickerArrangement.SinglePane.columns).isEqualTo(1)
        assertThat(pickerArrangement(1280.dp, heightCompact = false, posture = FoldPosture.FLAT).columns)
            .isEqualTo(1)
    }

    // ---- the layouts this PR must NOT steal ---------------------------------

    /**
     * A tablet in landscape is wide, flat, and 17c/17d's business (PR-16). Height
     * is the only axis that separates it from 892×412 — both are at least medium
     * width — so a gate that dropped the height condition would silently claim
     * every tablet.
     */
    @Test
    fun `a tablet in landscape is not 17a`() {
        assertThat(pickerArrangement(1280.dp, heightCompact = false, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    @Test
    fun `a tablet in portrait is not 17a either`() {
        assertThat(pickerArrangement(800.dp, heightCompact = false, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * The posture cases, which are the ones a reader is most likely to get wrong,
     * because `WindowInfo` carries `hinged` as well and the two answer different
     * questions. A half-open BOOK foldable is 17b: two leaves with a 24dp crease
     * gutter between them (PR-15), not a side pane beside a catalog. Reading
     * `hinged` here instead would let it through — a fully-open dual screen is
     * `hinged` and FLAT — and reading neither would let it through as well.
     */
    @Test
    fun `a half-open book foldable is not 17a`() {
        assertThat(pickerArrangement(892.dp, heightCompact = true, posture = FoldPosture.BOOK))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /** Tabletop puts a dead strip across the middle; a side pane would straddle it. */
    @Test
    fun `a tabletop fold is not 17a`() {
        assertThat(pickerArrangement(892.dp, heightCompact = true, posture = FoldPosture.TABLETOP))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * …and the same window at FLAT posture DOES split, so the three cases above
     * are failing on posture rather than on something they happen to share.
     */
    @Test
    fun `posture is the only thing separating those three from 17a`() {
        val flat = pickerArrangement(892.dp, heightCompact = true, posture = FoldPosture.FLAT)

        assertThat(flat.twoPane).isTrue()
        assertThat(FoldPosture.entries.filter { pickerArrangement(892.dp, true, it).twoPane })
            .containsExactly(FoldPosture.FLAT)
    }
}
