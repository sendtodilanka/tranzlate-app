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
 * `!voiceMark` half of the condition and
 * `:feature:language:testDebugUnitTest` finished BUILD SUCCESSFUL with zero
 * failures. The fix was the same one `pickerListPlan` already applies one level
 * up: move the decision out of the composable, and test it there.
 *
 * **Correction, measured under the runtime added by #186.** The lens's account of
 * the HARM does not survive rendering. `PickerRowRenderTest` lays the real row out
 * and the voice-but-no-pack row measures 67dp against this function's 60dp answer:
 * `heightIn` sets a MINIMUM, and the name plus the supporting line that hosts the
 * mark already demand more than the minimum, so the tall branch never binds and
 * nothing is clipped. Re-applying the deletion there confirmed it — the pure tests
 * below go red, the rendered row does not move at all.
 *
 * That does not retire this file. It pins the contract [pickerRowMinHeight]
 * states, which is what a caller reads and what a future row with shorter content
 * would depend on. It does mean the `voiceMark` term is, at today's row content,
 * a floor under a box that is already taller than the floor — worth knowing before
 * anyone cites it as protection.
 */
class PickerRowHeightTest {
    /**
     * The case the lens broke. `Downloadable` and `OnlineOnly` rows have no
     * supporting words at all, so on a device that CAN speak the language the
     * mark is the only thing on that line — and the mark lives on the supporting
     * line, which is why the contract asks for the two-line box.
     *
     * ("A 56dp box has no room for it" stood here and was wrong: `heightIn` is a
     * minimum, so a smaller one cannot shrink a row whose content is already
     * larger. Measured in `PickerRowRenderTest`. See this class's KDoc.)
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
