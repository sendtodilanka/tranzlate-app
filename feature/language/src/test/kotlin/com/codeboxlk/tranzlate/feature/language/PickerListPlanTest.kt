package com.codeboxlk.tranzlate.feature.language

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
 * they can be asserted here: this module has no Robolectric and no Compose test
 * rule, and the central claim ("empty recents → the section is ABSENT") is a
 * claim about something that is NOT on screen. A test that only counted rows
 * could not tell an absent section from an empty one.
 */
class PickerListPlanTest {
    private fun plan(
        role: LanguageRole = LanguageRole.TARGET,
        detect: Boolean = false,
        recentCount: Int = 3,
        anyVoiceMark: Boolean = true,
        railed: Boolean = true,
    ) = pickerListPlan(
        role = role,
        detectRowPresent = detect,
        recentCount = recentCount,
        anyVoiceMark = anyVoiceMark,
        railed = railed,
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
        assertThat(empty.railOffset).isEqualTo(1)
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
        assertThat(beforeTheDeviceAnswers.railOffset).isEqualTo(5)
        assertThat(afterTheDeviceAnswers.railOffset).isEqualTo(beforeTheDeviceAnswers.railOffset)
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
        assertThat(source.railOffset).isEqualTo(5)
    }

    /** A filtered list has no rail, so nothing above it needs counting. */
    @Test
    fun `an unrailed list needs no All-languages header`() {
        val searching = plan(railed = false, recentCount = 0)

        assertThat(searching.showAllHeader).isFalse()
        // Nothing above the results at all. The legend still STANDS while a
        // search runs — it just stands above the list rather than in it.
        assertThat(searching.railOffset).isEqualTo(0)
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
        assertThat(built.railOffset).isEqualTo(5)
        // Afrikaans is on device and has NO voice — the row that proves the two
        // are independent, and it is the first row the rail's 'A' points at.
        assertThat(rows.first().displayName).isEqualTo("Afrikaans")
        assertThat(rows.first().showsVoiceMark(LanguageRole.TARGET)).isFalse()
    }
}
