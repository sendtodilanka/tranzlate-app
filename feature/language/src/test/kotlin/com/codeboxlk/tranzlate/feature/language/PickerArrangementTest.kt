package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 * The window gate for 17a AND 17b (#130 PR-14, extended by PR-15).
 *
 * Every case here is a layout the gate must either claim or leave alone, and
 * the ones it must leave alone are the interesting half: 17c/17d's tablet
 * dialog (PR-16) arrives at this function looking wide enough and must come
 * back single-pane, and a tabletop fold arrives looking exactly like a book.
 *
 * **PR-15 added a second arrangement behind the same `twoPane` flag**, so the
 * cases below distinguish three answers, not two: single-pane, 17a's side pane
 * (8dp gutter, 272dp pane) and 17b's two leaves (24dp crease, 296dp leaf).
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
        val arrangement = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT)

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
        val arrangement = pickerArrangement(832.dp, 384.dp, posture = FoldPosture.FLAT)

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
        assertThat(pickerArrangement(568.dp, 412.dp, posture = FoldPosture.FLAT).twoPane)
            .isTrue()
    }

    @Test
    fun `just under the two-pane floor stays single pane`() {
        val arrangement = pickerArrangement(567.9.dp, 412.dp, posture = FoldPosture.FLAT)

        assertThat(arrangement).isEqualTo(PickerArrangement.SinglePane)
    }

    /** A phone in portrait is nowhere near it, and must not be, whatever else it reports. */
    @Test
    fun `phone portrait is single pane`() {
        assertThat(pickerArrangement(412.dp, 892.dp, posture = FoldPosture.FLAT))
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
        assertThat(pickerArrangement(808.dp, 412.dp, posture = FoldPosture.FLAT).columns)
            .isEqualTo(2)
    }

    @Test
    fun `just under the second-column floor keeps two panes and one column`() {
        val arrangement = pickerArrangement(807.9.dp, 412.dp, posture = FoldPosture.FLAT)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.columns).isEqualTo(1)
    }

    /** A single-pane answer is always one column — there is no other list to widen. */
    @Test
    fun `single pane is always one column`() {
        assertThat(PickerArrangement.SinglePane.columns).isEqualTo(1)
        assertThat(pickerArrangement(1280.dp, 800.dp, posture = FoldPosture.FLAT).columns)
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
        assertThat(pickerArrangement(1280.dp, 800.dp, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    @Test
    fun `a tablet in portrait is not 17a either`() {
        assertThat(pickerArrangement(800.dp, 1280.dp, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * A half-open BOOK foldable gets 17b — two LEAVES — and not 17a's side pane,
     * which is a different arrangement wearing the same `twoPane` flag.
     *
     * PR-14 shipped this case as `SinglePane` with a KDoc saying PR-15 would
     * claim it; PR-15 claims it.
     */
    @Test
    fun `a half-open book foldable is 17b, not 17a`() {
        val arrangement = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.BOOK)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.twoLeaf).isTrue()
        assertThat(arrangement.sidePaneWidth).isEqualTo(Dimensions.pickerLeafPaneWidth)
    }

    /**
     * **The number the ruling names for this PR.** 24dp, and it must not be
     * confusable with 17a's 8dp: the first keeps content off a physical fold,
     * the second is a gap that only has to be legible.
     */
    @Test
    fun `a book posture gets the 24dp crease gutter`() {
        assertThat(pickerArrangement(760.dp, 812.dp, posture = FoldPosture.BOOK).gutter)
            .isEqualTo(24.dp)
        assertThat(pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT).gutter)
            .isEqualTo(8.dp)
    }

    /**
     * The export's own foldable geometry, end to end: 760×812 is BOTH wide
     * enough for two panes AND far taller than 17a's height gate allows, so a
     * 17b that had been bolted onto 17a's condition would return single-pane
     * here — on the exact window the design was drawn for.
     */
    @Test
    fun `the export's 760 by 812 foldable frame splits into two leaves`() {
        val arrangement = pickerArrangement(760.dp, 812.dp, posture = FoldPosture.BOOK)

        assertThat(arrangement.twoLeaf).isTrue()
        // 760 − 296 pane − 24 crease − 48 rail = 392dp of catalog: one column,
        // as the export draws it. Two would need 480.
        assertThat(arrangement.columns).isEqualTo(1)
    }

    /** Below 296 + 24 + 240 + 48 the leaf layout has nowhere to put its catalog. */
    @Test
    fun `a book window too narrow for a leaf and a column stays single-pane`() {
        assertThat(pickerArrangement(608.dp, 812.dp, posture = FoldPosture.BOOK).twoLeaf).isTrue()
        assertThat(pickerArrangement(607.9.dp, 812.dp, posture = FoldPosture.BOOK))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /** Tabletop puts a dead strip across the middle; two side-by-side panes would each straddle it. */
    @Test
    fun `a tabletop fold is neither 17a nor 17b`() {
        assertThat(pickerArrangement(892.dp, 412.dp, posture = FoldPosture.TABLETOP))
            .isEqualTo(PickerArrangement.SinglePane)
        assertThat(pickerArrangement(760.dp, 812.dp, posture = FoldPosture.TABLETOP))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * Each posture gets its OWN arrangement at one window size, so none of the
     * three cases is passing on something they happen to share.
     */
    @Test
    fun `posture is what separates the three arrangements`() {
        val at = { p: FoldPosture -> pickerArrangement(892.dp, 412.dp, posture = p) }

        assertThat(at(FoldPosture.FLAT).twoLeaf).isFalse()
        assertThat(at(FoldPosture.FLAT).twoPane).isTrue()
        assertThat(FoldPosture.entries.filter { at(it).twoLeaf }).containsExactly(FoldPosture.BOOK)
        assertThat(FoldPosture.entries.filter { at(it).twoPane })
            .containsExactly(FoldPosture.FLAT, FoldPosture.BOOK)
    }

    // ---- posture is not `hinged`, and the difference is load-bearing ---------

    /**
     * **A fully-open dual screen is not a book**, and this is the pair that says
     * so. `WindowInfo.hinged` is true for it — a separating vertical hinge — while
     * its posture is FLAT, because it is one flat plane in two pieces. It gets
     * 17a's arrangement, which is what it has room for, and NOT the two-leaf one.
     *
     * Reading `hinged` where [pickerArrangement] reads posture would flip this
     * to a leaf layout; reading posture where it reads `hinged` would lose the
     * wider gutter below. The two fail in opposite directions on purpose.
     */
    @Test
    fun `a fully open dual screen is not a two-leaf book`() {
        val arrangement = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT, hinged = true)

        assertThat(arrangement.twoPane).isTrue()
        assertThat(arrangement.twoLeaf).isFalse()
        assertThat(arrangement.sidePaneWidth).isEqualTo(Dimensions.pickerSidePaneWidth)
    }

    /**
     * …but it still has a seam running down the gap, so the gap is drawn at the
     * crease width. That is the whole of what `hinged` decides here: content is
     * routed around the hinge rather than the arrangement being refused (the
     * reading PR-13's `WindowInfo.hinged` KDoc asks for).
     */
    @Test
    fun `a separating hinge widens 17a's gutter without changing its layout`() {
        val seamed = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT, hinged = true)
        val plain = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT, hinged = false)

        assertThat(seamed.gutter).isEqualTo(Dimensions.pickerCreaseGutter)
        assertThat(plain.gutter).isEqualTo(8.dp)
        // Everything else about them is the same window and the same layout.
        assertThat(seamed.copy(gutter = plain.gutter)).isEqualTo(plain)
    }

    /**
     * The opposite direction: a half-open fold whose hinge does NOT separate —
     * a crease on one continuous inner display, which is what a Fold actually
     * reports — still gets two leaves. Posture is the question, and `hinged` has
     * no vote in it.
     */
    @Test
    fun `a half open book with no separating hinge still gets two leaves`() {
        val arrangement = pickerArrangement(760.dp, 812.dp, posture = FoldPosture.BOOK, hinged = false)

        assertThat(arrangement.twoLeaf).isTrue()
        assertThat(arrangement.gutter).isEqualTo(Dimensions.pickerCreaseGutter)
    }

    /**
     * A "leaf" with nothing beside it is the one shape [PickerArrangement] must
     * not take — it would draw a crease gutter down a single column and hang the
     * meter off a pane that is not there. Refused at construction, the way
     * `LanguageRowState.Selected` refuses a double wrap.
     */
    @Test
    fun `a two-leaf arrangement cannot be single-pane`() {
        val thrown =
            assertThrows(IllegalArgumentException::class.java) {
                PickerArrangement(twoPane = false, columns = 1, twoLeaf = true)
            }

        assertThat(thrown).hasMessageThat().contains("two-leaf")
    }

    // ---- the height threshold, now that it is a dp and not a boolean ---------

    /**
     * 480dp is `WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND` — the same number
     * `WindowInfo.heightCompact` is computed from, so moving the measurement to
     * the constraints did not move the line. The pair is what pins that: one dp
     * either side, same width, opposite answers.
     */
    @Test
    fun `just under the medium height bound is still a short window`() {
        assertThat(pickerArrangement(892.dp, 479.9.dp, posture = FoldPosture.FLAT).twoPane).isTrue()
    }

    @Test
    fun `exactly the medium height bound is no longer short`() {
        assertThat(pickerArrangement(892.dp, 480.dp, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * An unbounded height reaches `BoxWithConstraints.maxHeight` as
     * `Constraints.Infinity` converted to dp — a very large number. It must land
     * on the SAFE side: a window whose height cannot be measured never takes
     * 17a's layout away from the arrangement that works everywhere.
     */
    @Test
    fun `an unmeasurable height never claims the two-pane layout`() {
        assertThat(pickerArrangement(892.dp, Dp.Infinity, posture = FoldPosture.FLAT))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    // ---- 17c/17d: the card's own arrangement (PR-16) ------------------------
    // The sizes below are the CARD's, not the window's — `pickerDialogSize` has
    // already turned 800×1280 into 560×998 and 1280×800 into 720×624 by the time
    // the picker inside the card measures itself.

    /**
     * `from|to · tablet portrait`: one column and no rail.
     *
     * **The case a width-only rule gets wrong.** 560dp clears two
     * [Dimensions.pickerColumnMin] columns with 80dp to spare, so "wide enough
     * for two" would put this card in two columns; the export draws one. What
     * separates it from 17d is that this card is TALLER than it is wide and has
     * not lost any rows to buy back.
     */
    @Test
    fun `the portrait card is one column with no rail`() {
        val arrangement =
            pickerArrangement(560.dp, 998.dp, posture = FoldPosture.FLAT, host = PickerHost.DIALOG)

        assertThat(arrangement.twoPane).isFalse()
        assertThat(arrangement.columns).isEqualTo(1)
        assertThat(arrangement.rail).isFalse()
    }

    /** `from|to · tablet landscape`: the two-column catalog the export draws. */
    @Test
    fun `the landscape card is two columns with no rail`() {
        val arrangement =
            pickerArrangement(720.dp, 624.dp, posture = FoldPosture.FLAT, host = PickerHost.DIALOG)

        assertThat(arrangement.twoPane).isFalse()
        assertThat(arrangement.columns).isEqualTo(2)
        assertThat(arrangement.rail).isFalse()
    }

    /**
     * A card can be landscape and still narrow. At 520dp the second column would
     * be 260dp of a name that has to share it with an avatar and a trailing
     * control, which is the arithmetic [Dimensions.pickerColumnMin] is.
     */
    @Test
    fun `a landscape card too narrow for two columns takes one`() {
        val arrangement =
            pickerArrangement(460.dp, 400.dp, posture = FoldPosture.FLAT, host = PickerHost.DIALOG)

        assertThat(arrangement.columns).isEqualTo(1)
    }

    /**
     * **The card never gets a side pane, whatever it measures.** 720dp clears
     * 17a's 568dp two-pane floor, so without the host being asked first this
     * card would be handed a 272dp shortcut pane the export draws in none of the
     * four tablet frames — and the pane would eat 40% of a card that is already
     * narrower than the window.
     */
    @Test
    fun `a card wide enough for 17a still gets no side pane`() {
        assertWithMessage("720dp clears 17a's 568dp floor — the host is what refuses the pane")
            .that(
                pickerArrangement(
                    720.dp,
                    624.dp,
                    posture = FoldPosture.FLAT,
                    host = PickerHost.DIALOG,
                ).twoPane,
            ).isFalse()
    }

    /**
     * …and the host is asked BEFORE posture, so a fold cannot reach into the
     * card. It should never happen — [pickerHost] refuses a folded window — but
     * a gate whose two entrances disagree is the defect PR-14's F2 was.
     */
    @Test
    fun `the card is never given two leaves`() {
        val arrangement =
            pickerArrangement(720.dp, 624.dp, posture = FoldPosture.BOOK, host = PickerHost.DIALOG)

        assertThat(arrangement.twoLeaf).isFalse()
        assertThat(arrangement.twoPane).isFalse()
    }

    /** Every arrangement that is NOT the card keeps the rail it has always had. */
    @Test
    fun `every non-dialog arrangement keeps its rail`() {
        assertThat(pickerArrangement(412.dp, 892.dp, posture = FoldPosture.FLAT).rail).isTrue()
        assertThat(pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT).rail).isTrue()
        assertThat(pickerArrangement(760.dp, 812.dp, posture = FoldPosture.BOOK).rail).isTrue()
        assertThat(PickerArrangement.SinglePane.rail).isTrue()
    }

    // ---- both sizes must come from ONE measurement (the F2 fix) --------------

    /**
     * The rule this gate's two dp arguments exist for, held where a JVM test can
     * reach it: **the screen must measure the height the same way it measures the
     * width**, against the constraints of one layout pass.
     *
     * PR-14 passed `maxWidth` from `BoxWithConstraints` and `heightCompact` from
     * `rememberWindowInfo()`. Those two are refreshed by different things and they
     * disagree for a few frames after a rotation — measured on `emulator-5554`,
     * four times in a 2.5-minute rotation hammer:
     *
     * ```
     * box=914.29x411.43dp   ← the constraints: already landscape
     * container=1080x2400px ← Compose's window snapshot: still portrait
     * metrics=2400x1080     ← WindowMetricsCalculator, same frame: landscape
     * ```
     *
     * A wide window reporting itself tall fails the height condition, and the
     * picker drew the portrait layout at full landscape width.
     *
     * A source rule rather than a behaviour test for the reason this whole file
     * is a pure function. Written when this module had no Compose test runtime;
     * #186 has since added one, and CI still compiles instrumented tests without
     * running them (#40). The extraction is still right — reading the one call
     * site is cheaper to exhaust than mounting the screen, and it is the exact
     * line a later PR would "simplify" back.
     */
    @Test
    fun `the screen measures both sizes against the same constraints`() {
        val call = pickerArrangementCall()

        assertWithMessage(
            "LanguagePickerScreen.kt calls pickerArrangement($call). Both sizes must come from " +
                "the BoxWithConstraints measurement — a window snapshot and a layout pass are " +
                "half a rotation apart after a rotate (see this test's KDoc).",
        ).that(call)
            .startsWith("maxWidth, maxHeight")
        // …and the source it must NOT go back to is gone from the call.
        assertThat(call).doesNotContain("heightCompact")
    }

    /** The arguments of the one production `pickerArrangement(` call, as written. */
    private fun pickerArrangementCall(): String {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        val source = checkoutRoot.resolve(PICKER_SCREEN_SOURCE).readText()
        val calls = Regex("""pickerArrangement\(([^)]*)\)""").findAll(source).map { it.groupValues[1] }.toList()

        // Never vacuous: a moved file, or a screen that stopped asking at all,
        // fails here rather than passing by having nothing to read.
        assertWithMessage("LanguagePickerScreen.kt makes ${calls.size} pickerArrangement calls")
            .that(calls)
            .hasSize(1)
        return calls.single()
    }
}

private const val PICKER_SCREEN_SOURCE =
    "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerScreen.kt"
