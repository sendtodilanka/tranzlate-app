package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.codeboxlk.tranzlate.core.ui.WindowWidthClass
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Which host a window gets (17c/17d, #130 PR-16) — the four conditions, one case
 * each, plus the windows the export actually draws.
 *
 * Every case here is the disconfirming experiment for one clause of
 * [pickerHost]: delete that clause and exactly one test in this file goes red.
 * That is the property the file is for. A matrix that only checked the two
 * drawn windows would pass with three of the four clauses deleted.
 */
class PickerHostTest {
    // ---- the windows the export draws ---------------------------------------

    /** 17c: `from|to · tablet portrait`, 800×1280 — MEDIUM width, tall, flat. */
    @Test
    fun `a tablet in portrait gets the dialog`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.MEDIUM,
                heightCompact = false,
                posture = FoldPosture.FLAT,
            ),
        ).isEqualTo(PickerHost.DIALOG)
    }

    /** 17d: `from|to · tablet landscape`, 1280×800 — EXPANDED width, still not short. */
    @Test
    fun `a tablet in landscape gets the dialog`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.EXPANDED,
                heightCompact = false,
                posture = FoldPosture.FLAT,
            ),
        ).isEqualTo(PickerHost.DIALOG)
    }

    // ---- one case per condition, each a layout the dialog must not steal -----

    /**
     * A phone is the picker's own window. A dialog inside a compact window is the
     * same list at the same width with a border round it, and 15a/16a own that.
     */
    @Test
    fun `a compact window keeps the full screen`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.COMPACT,
                heightCompact = false,
                posture = FoldPosture.FLAT,
            ),
        ).isEqualTo(PickerHost.NAV_ENTRY)
    }

    /**
     * A landscape phone is 892×412: wide enough to be MEDIUM, and 17a's window.
     * At 78% of 412dp the card would be 321dp tall — five rows of languages.
     *
     * This is the case width alone gets wrong, which is why the height term is
     * not redundant with the width one.
     */
    @Test
    fun `a short window keeps the two-pane layout`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.MEDIUM,
                heightCompact = true,
                posture = FoldPosture.FLAT,
            ),
        ).isEqualTo(PickerHost.NAV_ENTRY)
    }

    /**
     * 17b's window is 760×812 and a tablet's is 800×1280 — both MEDIUM, both
     * tall. Posture is the ONLY thing that separates them, which is the reason
     * `WindowInfo.posture` was added in PR-13, and a card centred in a half-open
     * foldable lands on the crease.
     */
    @Test
    fun `a book-folded window keeps its two leaves`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.MEDIUM,
                heightCompact = false,
                posture = FoldPosture.BOOK,
            ),
        ).isEqualTo(PickerHost.NAV_ENTRY)
    }

    /** A tabletop fold puts a dead strip across the middle — where a card goes. */
    @Test
    fun `a tabletop window keeps the full screen`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.EXPANDED,
                heightCompact = false,
                posture = FoldPosture.TABLETOP,
            ),
        ).isEqualTo(PickerHost.NAV_ENTRY)
    }

    /**
     * The case posture alone gets wrong, and this PR's own addition to the
     * ruling's three conditions.
     *
     * A dual-screen device held fully OPEN reports [FoldPosture.FLAT] and still
     * has a physical seam down the middle of the window — that is exactly the
     * distinction PR-13 built `hinged` for and PR-15 first used. 17a's layout
     * routes content around the seam by widening the gutter; a centred card
     * cannot, because the middle is where it goes.
     */
    @Test
    fun `a separating hinge keeps the full screen even when flat`() {
        assertWithMessage(
            "A fully-open dual screen is FLAT and still split down the middle — a centred " +
                "card lands on the seam.",
        ).that(
            pickerHost(
                widthClass = WindowWidthClass.EXPANDED,
                heightCompact = false,
                posture = FoldPosture.FLAT,
                hinged = true,
            ),
        ).isEqualTo(PickerHost.NAV_ENTRY)
    }

    /** …and the same window without the seam is the dialog, so the term is the cause. */
    @Test
    fun `the same window without a hinge gets the dialog`() {
        assertThat(
            pickerHost(
                widthClass = WindowWidthClass.EXPANDED,
                heightCompact = false,
                posture = FoldPosture.FLAT,
                hinged = false,
            ),
        ).isEqualTo(PickerHost.DIALOG)
    }
}
