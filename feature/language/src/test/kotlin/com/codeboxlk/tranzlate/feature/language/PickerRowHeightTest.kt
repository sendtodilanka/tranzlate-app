package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * How short a picker row may be — the decision that used to live inside
 * `LanguageRow` and that nothing in this module could reach.
 *
 * A co-verify lens proved the gap rather than argued it: it deleted the
 * `!voiceMark` half of the condition, which collapses the voice-but-no-pack row
 * to the 56dp single-line box and clips away the very mark 16a exists to draw,
 * and `:feature:language:testDebugUnitTest` finished BUILD SUCCESSFUL with zero
 * failures. The module has no Robolectric and no Compose test rule — the only
 * `createComposeRule` in the repo is under `app/src/androidTestProd` — so the
 * fix is the same one `pickerListPlan` already applies one level up: move the
 * decision out of the composable, and test it there.
 */
class PickerRowHeightTest {
    /**
     * The case the lens broke. `Downloadable` and `OnlineOnly` rows have no
     * supporting words at all, so on a device that CAN speak the language the
     * mark is the only thing on that line — and the mark lives on the supporting
     * line. A 56dp box has no room for it.
     */
    @Test
    fun `voiceOnlyRowStaysTheTallRow`() {
        assertThat(pickerRowMinHeight(hasSupportingText = false, voiceMark = true))
            .isEqualTo(Dimensions.pickerRowHeightTall)
    }

    /** Nothing on the second line at all: the row is allowed to be short. */
    @Test
    fun `plainRowIsTheShortRow`() {
        assertThat(pickerRowMinHeight(hasSupportingText = false, voiceMark = false))
            .isEqualTo(Dimensions.pickerRowHeight)
        // The two heights are genuinely different, so the assertions above and
        // below cannot both be satisfied by one constant.
        assertThat(Dimensions.pickerRowHeight).isLessThan(Dimensions.pickerRowHeightTall)
    }

    /** Supporting words alone were always enough — that half predates the mark. */
    @Test
    fun `supportingTextAloneStillMakesTheTallRow`() {
        assertThat(pickerRowMinHeight(hasSupportingText = true, voiceMark = false))
            .isEqualTo(Dimensions.pickerRowHeightTall)
        assertThat(pickerRowMinHeight(hasSupportingText = true, voiceMark = true))
            .isEqualTo(Dimensions.pickerRowHeightTall)
    }

    /**
     * The real row this protects, built the way the screen builds it: an
     * offline-capable language with nothing downloaded (so no supporting words)
     * that this device happens to have a voice for. In a TARGET picker it draws
     * the mark, so it must be the tall row; in a SOURCE picker no mark is drawn
     * ([showsVoiceMark]) and the same language is the short one.
     */
    @Test
    fun `theVoiceButNoPackRowIsTallOnlyWhereTheMarkIsDrawn`() {
        val row =
            buildPickerRows(
                languages =
                    listOf(
                        Language(
                            "ar",
                            "Arabic",
                            offlineAvailable = true,
                            offlineDownloaded = false,
                            hasOfflineVoice = true,
                        ),
                    ),
                modelStates = emptyMap(),
                selectedId = "en",
                locale = Locale.ENGLISH,
            ).single()

        assertThat(row.state).isEqualTo(LanguageRowState.Downloadable)
        assertThat(
            pickerRowMinHeight(
                hasSupportingText = false,
                voiceMark = row.showsVoiceMark(LanguageRole.TARGET),
            ),
        ).isEqualTo(Dimensions.pickerRowHeightTall)
        assertThat(
            pickerRowMinHeight(
                hasSupportingText = false,
                voiceMark = row.showsVoiceMark(LanguageRole.SOURCE),
            ),
        ).isEqualTo(Dimensions.pickerRowHeight)
    }
}
