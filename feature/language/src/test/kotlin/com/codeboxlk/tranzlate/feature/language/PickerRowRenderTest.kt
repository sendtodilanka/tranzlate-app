package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.codeboxlk.tranzlate.core.designsystem.Dimensions
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The picker's row rules, checked by LAYING THE ROW OUT (#186).
 *
 * `PickerRowHeightTest` in this same package pins [pickerRowMinHeight] as a function and
 * STAYS — it is the faster and more precise statement of the arithmetic, and nothing here
 * replaces it. What it cannot see is the one place that calls the function. #186's
 * finding 1 is that blind spot: a co-verify lens dropped the `!voiceMark` half of the
 * condition and `:feature:language:testDebugUnitTest` came back BUILD SUCCESSFUL with zero
 * failures. Extracting the decision into a pure function made the arithmetic testable and
 * left the WIRING exactly as untestable as before.
 *
 * ## What rendering it revealed, which no source-shape or pure test could
 *
 * Measured here at `w411dp-h891dp`, density 1: the voice-but-no-pack row lays out at
 * **67dp** and the plain row at **56dp**. `pickerRowMinHeight` returns 60dp for the first
 * — so on this row the minimum **never binds**: the name plus the supporting line that
 * carries the speaker already demand 67dp. The tall branch is a floor under a box that is
 * taller than the floor.
 *
 * That matters for how these tests are written. "The voice row is at least 60dp" is TRUE
 * and would stay true with the `voiceMark` argument deleted from the call, so asserting it
 * would be a gate that cannot fail for the reason its name gives — the vacuous shape this
 * repo has already been burned by (#110). The assertions below are therefore about
 * differences that are actually observable: the compact row sits exactly on its floor
 * (where the minimum DOES bind), the voice row is taller than the compact one, and the
 * mark the whole rule exists to protect is on screen.
 */
@RunWith(RobolectricTestRunner::class)
// A fixed window, not Robolectric's default. Every assertion here is a measurement, and a
// measurement taken on whatever screen the runner happened to pick is not reproducible
// between machines. w411dp-h891dp is an ordinary modern phone in portrait, and it is also
// what makes the short catalogue below fit without scrolling.
@Config(qualifiers = "w411dp-h891dp")
class PickerRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Two rows, one difference. Both are `Downloadable` — offline-capable, nothing on disk
     * — so [rowSupportingText] gives both of them NO supporting words, and the only thing
     * that can move the height is the voice mark.
     */
    private val catalogue =
        listOf(
            // A voice, no pack: 17a's Arabic, the row the rule exists for.
            Language(
                id = "ar",
                name = "Arabic",
                offlineAvailable = true,
                offlineDownloaded = false,
                hasOfflineVoice = true,
            ),
            // Same state, no voice: the control. Without it a mutation that shortened
            // EVERY row would still be caught, but the failure would not name a cause.
            Language(
                id = "sq",
                name = "Albanian",
                offlineAvailable = true,
                offlineDownloaded = false,
                hasOfflineVoice = false,
            ),
        )

    private fun showTargetPicker() {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    // TARGET, because `showsVoiceMark` is target-only — a source picker
                    // draws no mark anywhere and every assertion here would be vacuous.
                    target = LanguageRole.TARGET,
                    languages = catalogue,
                    // Nothing in the catalogue is selected, so no row is wrapped in
                    // `Selected` and no row gains a tick that could change its height.
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                )
            }
        }
    }

    private fun rowHeightOf(id: String) =
        compose
            .onNodeWithTag("tt_lang_row_$id")
            .getUnclippedBoundsInRoot()
            .height

    /**
     * The compact branch of [pickerRowMinHeight], measured where it is actually consumed.
     *
     * This is the one row where the minimum binds: strip the name row of its supporting
     * line and the content wants ~51dp, so the box is the 56dp token and nothing else.
     * Raising that floor, lowering it, or dropping the `heightIn` at the call site all land
     * here.
     */
    @Test
    fun `a row with neither words nor a mark sits on the compact floor`() {
        showTargetPicker()

        compose
            .onNodeWithTag("tt_lang_row_sq")
            .assertHeightIsEqualTo(Dimensions.pickerRowHeight)
    }

    /**
     * The rule as a user meets it: a row carrying the speaker gets more vertical room than
     * one that does not.
     *
     * Stated as a comparison rather than as an absolute, because the absolute is not
     * falsifiable here — see the class KDoc. This one IS: stop drawing the mark, or
     * collapse the supporting line that hosts it, and the two rows become the same height.
     */
    @Test
    fun `the voice row is taller than the plain row`() {
        showTargetPicker()

        val voice = rowHeightOf("ar")
        val plain = rowHeightOf("sq")

        assertThat(voice.value).isGreaterThan(plain.value)
    }

    /**
     * The user-visible half of finding 1, stated as the harm it named: "losing the mark".
     *
     * `useUnmergedTree` is required and is not incidental — the row merges its descendants
     * so that TalkBack reads it as one thing, and the speaker is deliberately silent
     * (`contentDescription = null`, since the row's own description already says the
     * language can be spoken). In the merged tree it therefore has no node of its own. A
     * test that forgot this would report "not displayed" for a mark that is on screen,
     * which is a false failure rather than a caught defect.
     */
    @Test
    fun `the offline-voice speaker is drawn on the voice row`() {
        showTargetPicker()

        compose
            .onNodeWithTag("tt_lang_voice_mark", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
