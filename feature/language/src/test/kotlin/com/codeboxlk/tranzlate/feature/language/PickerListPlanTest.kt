package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * What 16a's list emits above the alphabet — the voice legend, the recents
 * header, and the index the A–Z rail scrolls into.
 *
 * These live in a pure function rather than inside the composable precisely so
 * they can be asserted here. The central claim ("empty recents → the section is
 * ABSENT") is a claim about something that is NOT on screen, which a pure function
 * states directly; #186 has since added a Compose runtime to this module, and that
 * does not change the argument. A test that only counted rows could not tell an
 * absent section from an empty one.
 */
class PickerListPlanTest {
    /** 17a: a pane beside the catalog on one flat surface. */
    private val sidePane = PickerArrangement(twoPane = true, columns = 1)

    /** 17b: the same pane, on the far side of a fold. */
    private val twoLeaf =
        PickerArrangement(twoPane = true, columns = 1, twoLeaf = true, gutter = Dimensions.pickerCreaseGutter)

    private fun plan(
        role: LanguageRole = LanguageRole.TARGET,
        detect: Boolean = false,
        recentCount: Int = 3,
        anyVoiceMark: Boolean = true,
        railed: Boolean = true,
        arrangement: PickerArrangement = PickerArrangement.SinglePane,
        /** The storage snapshot has landed. Default: yes, the resting case. */
        libraryReady: Boolean = true,
    ) = pickerListPlan(
        role = role,
        detectRowPresent = detect,
        recentCount = recentCount,
        anyVoiceMark = anyVoiceMark,
        railed = railed,
        arrangement = arrangement,
        libraryReady = libraryReady,
    )

    // ---- recents: absent, not empty -----------------------------------------

    /**
     * The 18a pattern, applied to 16a: with nothing to list there is no header,
     * no placeholder and no "no recents yet" — the section simply is not built.
     * A header standing over nothing reports a failure the user did not have.
     */
    @Test
    fun `emptyTargetRecentsRemoveTheHeaderEntirely`() {
        val empty = plan(recentCount = 0)

        assertThat(empty.recentHeader).isNull()
        // …and the list closes over the gap: nothing above the alphabet except
        // the "All languages" header. The legend is not an item of this list —
        // see `theVoiceAnswerAddsNoItemToTheAnchoredList`.
        assertThat(empty.catalogOffset).isEqualTo(1)
    }

    @Test
    fun `one recent is enough to bring the section back`() {
        assertThat(plan(recentCount = 1).recentHeader).isEqualTo(RecentHeader.TARGET)
    }

    /** 16a says "Recently used as target"; 15a says "Recent". Two headers, two scopes. */
    @Test
    fun `targetRecentHeaderIsTheTargetOne`() {
        assertThat(plan(role = LanguageRole.TARGET).recentHeader).isEqualTo(RecentHeader.TARGET)
        assertThat(plan(role = LanguageRole.SOURCE).recentHeader).isEqualTo(RecentHeader.GENERIC)
    }

    @Test
    fun `an empty source picker loses its header too`() {
        assertThat(plan(role = LanguageRole.SOURCE, recentCount = 0).recentHeader).isNull()
    }

    // ---- the legend ---------------------------------------------------------

    @Test
    fun `sourcePickerHasNoVoiceLegend`() {
        assertThat(plan(role = LanguageRole.SOURCE, anyVoiceMark = true).showVoiceLegend).isFalse()
        assertThat(plan(role = LanguageRole.TARGET, anyVoiceMark = true).showVoiceLegend).isTrue()
    }

    /**
     * The same rule rev 5 applied to the mark, one level up. On a device with no
     * installed voices at all — E-V1's AOSP-with-no-Google-TTS case, which the
     * catalog resolves to the empty set — no row would carry a speaker, and an
     * explainer for a glyph that appears nowhere is the dead affordance the
     * ruling refused in §7.6.
     */
    @Test
    fun `legendIsAbsentWhenNoRowCarriesAMark`() {
        assertThat(plan(anyVoiceMark = false).showVoiceLegend).isFalse()
    }

    // ---- the rail's offset --------------------------------------------------

    /**
     * THE regression test for the co-verify blocking defect.
     *
     * The device's offline-voice answer arrives LATE — binding `TextToSpeech` is
     * documented at up to 5000ms, and the picker paints well before that — so
     * `anyVoiceMark` flips false → true while the list is already on screen. If
     * the legend is an item of that list, the flip inserts an item at index 0 of
     * a `LazyColumn` whose scroll position is anchored to the key it was already
     * showing: the anchor follows its key to index 1 and the new item is laid out
     * ABOVE the viewport, where it is never seen. Measured on the emulator —
     * `totalItemsCount` 197 → 198, `firstVisibleItemIndex` 0 → 1, same first
     * visible key, and `VoiceLegend` first composed 64.8 seconds later when the
     * list was dragged back to the top by hand.
     *
     * So the invariant is not "count the legend correctly". It is that the
     * answer arriving must not change the anchored item set AT ALL, in either
     * direction, which is only true while the legend is drawn outside the list.
     * Both answers must produce the same offset — asserting one of them alone
     * would pass with the legend put straight back.
     */
    @Test
    fun `theVoiceAnswerAddsNoItemToTheAnchoredList`() {
        val beforeTheDeviceAnswers = plan(anyVoiceMark = false, recentCount = 3)
        val afterTheDeviceAnswers = plan(anyVoiceMark = true, recentCount = 3)

        // (header + 3 rows) + "All languages" — on both sides of the answer.
        assertThat(beforeTheDeviceAnswers.catalogOffset).isEqualTo(5)
        assertThat(afterTheDeviceAnswers.catalogOffset).isEqualTo(beforeTheDeviceAnswers.catalogOffset)
        // …and the legend itself is still the thing that changed, so this is not
        // passing because nothing happened.
        assertThat(beforeTheDeviceAnswers.showVoiceLegend).isFalse()
        assertThat(afterTheDeviceAnswers.showVoiceLegend).isTrue()
    }

    @Test
    fun `the offset counts the detect row, the recents header and its rows`() {
        val source = plan(role = LanguageRole.SOURCE, detect = true, recentCount = 2)

        // detect(1) + header(1) + 2 rows + "All languages"(1); no legend on this side.
        assertThat(source.showVoiceLegend).isFalse()
        assertThat(source.catalogOffset).isEqualTo(5)
    }

    /** A filtered list has no rail, so nothing above it needs counting. */
    @Test
    fun `an unrailed list needs no All-languages header`() {
        val searching = plan(railed = false, recentCount = 0)

        assertThat(searching.showAllHeader).isFalse()
        // Nothing above the results at all. The legend still STANDS while a
        // search runs — it just stands above the list rather than in it.
        assertThat(searching.catalogOffset).isEqualTo(0)
        assertThat(searching.showVoiceLegend).isTrue()
    }

    /**
     * End-to-end against the drawn frame: the export's 16a has three recents and
     * the "All languages" header above Albanian inside the list, and the legend
     * above the list, so the first alphabetical row sits at index 5.
     */
    @Test
    fun `the export's own 16a arithmetic`() {
        val rows =
            buildPickerRows(
                languages =
                    listOf(
                        Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = true),
                        Language("sq", "Albanian", offlineAvailable = true, offlineDownloaded = false),
                        Language(
                            "en",
                            "English",
                            offlineAvailable = true,
                            offlineDownloaded = true,
                            hasOfflineVoice = true,
                        ),
                        Language(
                            "es",
                            "Spanish",
                            offlineAvailable = true,
                            offlineDownloaded = true,
                            hasOfflineVoice = true,
                        ),
                    ),
                modelStates = emptyMap(),
                selectedId = "es",
                locale = Locale.ENGLISH,
                recents = mapOf("es" to 3L, "en" to 2L, "af" to 1L),
            )
        val recent = rows.recentRows()
        val built =
            pickerListPlan(
                role = LanguageRole.TARGET,
                detectRowPresent = false,
                recentCount = recent.size,
                anyVoiceMark = rows.any { it.showsVoiceMark(LanguageRole.TARGET) },
                railed = true,
            )

        assertThat(built.showVoiceLegend).isTrue()
        assertThat(built.recentHeader).isEqualTo(RecentHeader.TARGET)
        assertThat(built.catalogOffset).isEqualTo(5)
        // Afrikaans is on device and has NO voice — the row that proves the two
        // are independent, and it is the first row the rail's 'A' points at.
        assertThat(rows.first().displayName).isEqualTo("Afrikaans")
        assertThat(rows.first().showsVoiceMark(LanguageRole.TARGET)).isFalse()
    }

    // ---- 17a: the same plan, with three of its items moved out of the list ---

    /**
     * THE arithmetic 17a can break silently.
     *
     * In two-pane the detect row, the recents header and the recents rows are
     * drawn in the SIDE pane, which is not the catalog's scroller. Counting them
     * anyway makes every A–Z letter land that many rows short — deterministic,
     * never an exception, and invisible to any test that only looks at rows. It
     * is the same defect the single-pane `catalogOffset` was written to prevent,
     * arriving from the opposite direction.
     *
     * Asserted as a PAIR: the identical inputs give 5 in one arrangement and 1 in
     * the other, so this cannot pass by the offset being right for the wrong
     * reason.
     */
    @Test
    fun `two-pane stops counting what the side pane holds`() {
        val stacked =
            plan(role = LanguageRole.SOURCE, detect = true, recentCount = 3, arrangement = PickerArrangement.SinglePane)
        val split = plan(role = LanguageRole.SOURCE, detect = true, recentCount = 3, arrangement = sidePane)

        // detect(1) + header(1) + 3 rows + "All languages"(1)
        assertThat(stacked.catalogOffset).isEqualTo(6)
        // …and in the catalog pane, only "All languages" stands above the alphabet.
        assertThat(split.catalogOffset).isEqualTo(1)
        // The sections themselves have NOT gone away — they moved.
        assertThat(split.recentHeader).isEqualTo(RecentHeader.GENERIC)
        assertThat(split.sidePane).isTrue()
    }

    /** No rail, nothing above the alphabet to count, in either arrangement. */
    @Test
    fun `an unrailed two-pane catalog has no offset at all`() {
        assertThat(plan(arrangement = sidePane, railed = false, recentCount = 0).catalogOffset).isEqualTo(0)
    }

    // ---- 17a: the side pane, and the role-specific slot at its foot ----------

    /**
     * The side pane's foot is the role's own affordance — "Detect language" on
     * the source side, the offline-voice legend on the target side, exactly as
     * the two landscape frames draw them. Giving both panes the same role's slot
     * is the mistake this pins: a source pane with a legend explains a mark the
     * source side never draws, and a target pane with a Detect row offers to
     * detect the language you are translating INTO.
     */
    @Test
    fun `each side of the picker gets its own slot at the foot of the pane`() {
        val source = plan(role = LanguageRole.SOURCE, detect = true, anyVoiceMark = true, arrangement = sidePane)
        val target = plan(role = LanguageRole.TARGET, detect = false, anyVoiceMark = true, arrangement = sidePane)

        assertThat(source.showVoiceLegend).isFalse()
        assertThat(target.showVoiceLegend).isTrue()
        assertThat(source.recentHeader).isEqualTo(RecentHeader.GENERIC)
        assertThat(target.recentHeader).isEqualTo(RecentHeader.TARGET)
    }

    /**
     * A pane with nothing in it is 272dp of empty surface next to the results,
     * and it is reachable: a search on the source side clears the recents and
     * filters the Detect row out. EDGE_CASES' no-dead-end rule cuts the same way
     * for furniture as it does for errors — the pane goes and the catalog takes
     * the width back.
     */
    @Test
    fun `an empty side pane is not drawn`() {
        val searching =
            plan(
                role = LanguageRole.SOURCE,
                detect = false,
                recentCount = 0,
                anyVoiceMark = true,
                railed = false,
                arrangement = sidePane,
            )

        assertThat(searching.sidePane).isFalse()
    }

    /** Any ONE of the three is enough to keep it. */
    @Test
    fun `one thing is enough to keep the side pane`() {
        val recentsOnly = plan(role = LanguageRole.SOURCE, detect = false, recentCount = 1, arrangement = sidePane)
        val detectOnly = plan(role = LanguageRole.SOURCE, detect = true, recentCount = 0, arrangement = sidePane)
        val legendOnly =
            plan(
                role = LanguageRole.TARGET,
                detect = false,
                recentCount = 0,
                anyVoiceMark = true,
                arrangement = sidePane,
            )

        assertThat(recentsOnly.sidePane).isTrue()
        assertThat(detectOnly.sidePane).isTrue()
        assertThat(legendOnly.sidePane).isTrue()
    }

    /** There is no side pane in the single-pane arrangement, whatever it would hold. */
    @Test
    fun `single pane never reports a side pane`() {
        assertThat(plan(role = LanguageRole.SOURCE, detect = true, recentCount = 3).sidePane).isFalse()
    }

    // ---- 17a: where the on-device counter goes ------------------------------

    /**
     * The counter states a fact about the whole catalog. In two-pane the "All
     * languages" header heads only the catalog PANE, so the counter moves to the
     * bar; in one pane the header IS the whole list and it stays there. Either
     * way it is drawn once — two copies would be two `tt_lang_counter` tags.
     */
    @Test
    fun `the counter moves to the bar only in two-pane`() {
        assertThat(plan(arrangement = sidePane).counterInTopBar).isTrue()
        assertThat(plan(arrangement = PickerArrangement.SinglePane).counterInTopBar).isFalse()
    }

    // ---- 17b: the offline-library meter (PR-15, U-5) ------------------------

    /**
     * **The meter belongs to the FOLDED window and to nothing else.**
     *
     * 17a's pane is 272dp wide inside a 412dp-tall window, and the export draws
     * no meter in either landscape frame — there is no room for the card without
     * pushing the recents section, which is the reason the pane exists, off the
     * bottom. So `twoPane` is the wrong question and `twoLeaf` is the right one;
     * they are the same value in every other test in this file, which is exactly
     * why this one has to be written down.
     */
    @Test
    fun `17a landscape draws no library meter`() {
        assertThat(plan(arrangement = sidePane).showMeter).isFalse()
        assertThat(plan(arrangement = twoLeaf).showMeter).isTrue()
        assertThat(plan().showMeter).isFalse()
    }

    /**
     * …and it is a permanent tenant of the leaf, so 17b's pane survives the one
     * thing that empties 17a's: a search that clears the recents and filters the
     * Detect row away. That pane still has the meter in it, so it stays.
     */
    @Test
    fun `a folded leaf keeps its pane even when the shortcuts are all gone`() {
        val searching =
            plan(
                role = LanguageRole.SOURCE,
                detect = false,
                recentCount = 0,
                anyVoiceMark = true,
                railed = false,
                arrangement = twoLeaf,
            )

        assertThat(searching.sidePane).isTrue()
        assertThat(searching.showMeter).isTrue()
    }

    /**
     * **While the disk is still being read there is no card, and therefore no
     * tenant** — so the one case that CAN empty a folded leaf is the same one
     * that empties a landscape pane, arriving a moment earlier.
     *
     * `packsBytes()` walks the model store on IO, and a search that has cleared
     * the recents and filtered the Detect row away can land inside that window.
     * Drawing 296dp of empty rounded surface beside a "no results" message for a
     * few hundred milliseconds is the same furniture EDGE_CASES' no-dead-end
     * rule refuses in the landscape case; it is just briefer.
     */
    @Test
    fun `a folded leaf with nothing in it yet is not drawn`() {
        val loading =
            plan(
                role = LanguageRole.SOURCE,
                detect = false,
                recentCount = 0,
                anyVoiceMark = true,
                railed = false,
                arrangement = twoLeaf,
                libraryReady = false,
            )

        assertThat(loading.showMeter).isFalse()
        assertThat(loading.sidePane).isFalse()
    }

    /** …and the shortcuts alone still keep it, snapshot or no snapshot. */
    @Test
    fun `a folded leaf with recents survives the wait for the disk`() {
        val loading = plan(role = LanguageRole.SOURCE, recentCount = 3, arrangement = twoLeaf, libraryReady = false)

        assertThat(loading.showMeter).isFalse()
        assertThat(loading.sidePane).isTrue()
    }

    /**
     * The offset arithmetic is IDENTICAL in the two arrangements, and that is a
     * claim worth pinning rather than an accident: both move the same items into
     * the same pane, so a fold and a rotation must number the catalog the same
     * way. If they ever diverged, a position captured in one and restored in the
     * other would land on a different language — the PR-14 defect, arriving from
     * a new direction.
     */
    @Test
    fun `folding does not renumber the catalog`() {
        val landscape = plan(role = LanguageRole.SOURCE, detect = true, recentCount = 3, arrangement = sidePane)
        val folded = plan(role = LanguageRole.SOURCE, detect = true, recentCount = 3, arrangement = twoLeaf)

        assertThat(folded.catalogOffset).isEqualTo(landscape.catalogOffset)
        assertThat(folded.copy(showMeter = false)).isEqualTo(landscape)
    }
}
