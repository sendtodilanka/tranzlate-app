package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeboxlk.tranzlate.core.ui.FoldPosture
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

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
     * The posture cases, which are the ones a reader is most likely to get wrong,
     * because `WindowInfo` carries `hinged` as well and the two answer different
     * questions. A half-open BOOK foldable is 17b: two leaves with a 24dp crease
     * gutter between them (PR-15), not a side pane beside a catalog. Reading
     * `hinged` here instead would let it through — a fully-open dual screen is
     * `hinged` and FLAT — and reading neither would let it through as well.
     */
    @Test
    fun `a half-open book foldable is not 17a`() {
        assertThat(pickerArrangement(892.dp, 412.dp, posture = FoldPosture.BOOK))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /** Tabletop puts a dead strip across the middle; a side pane would straddle it. */
    @Test
    fun `a tabletop fold is not 17a`() {
        assertThat(pickerArrangement(892.dp, 412.dp, posture = FoldPosture.TABLETOP))
            .isEqualTo(PickerArrangement.SinglePane)
    }

    /**
     * …and the same window at FLAT posture DOES split, so the three cases above
     * are failing on posture rather than on something they happen to share.
     */
    @Test
    fun `posture is the only thing separating those three from 17a`() {
        val flat = pickerArrangement(892.dp, 412.dp, posture = FoldPosture.FLAT)

        assertThat(flat.twoPane).isTrue()
        assertThat(FoldPosture.entries.filter { pickerArrangement(892.dp, 412.dp, it).twoPane })
            .containsExactly(FoldPosture.FLAT)
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
