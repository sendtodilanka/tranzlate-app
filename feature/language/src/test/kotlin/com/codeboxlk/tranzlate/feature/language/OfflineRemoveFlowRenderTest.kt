package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The offline manager's remove flow as the SCREEN wires it (#130 PR-19).
 *
 * `OfflineLanguagesViewModelTest` proves the rule (which sheet, what is counted,
 * what is deleted) and `RemovePackSheetsTest` proves the sheets. What neither
 * can see is the join: that the 🗑 is wired to the request rather than straight
 * back to the delete, that the ⏹ is wired to the immediate stop, and that the
 * question picks the right one of two sheets.
 *
 * That join is exactly where the shipped defect lived before this PR — the bin
 * called `onDelete` — and it is a one-line edit to put back with every unit test
 * still green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OfflineRemoveFlowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val removeRequests = mutableListOf<String>()
    private val stops = mutableListOf<String>()
    private var confirms = 0

    private val rows =
        listOf(
            OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded),
            OfflineLanguageRow("de", "German", OfflineModelState.Downloading),
        )

    private fun showScreen(pendingRemoval: PendingPackRemoval? = null) {
        compose.setContent {
            TranzlateTheme {
                OfflineLanguagesContent(
                    rows = rows,
                    onDownload = {},
                    onStopDownload = { stops += it },
                    onRequestRemove = { removeRequests += it },
                    onBack = {},
                    pendingRemoval = pendingRemoval,
                    onConfirmRemove = { confirms++ },
                    onDismissRemove = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** The whole point: the bin ASKS. Wiring it back to the delete reddens here. */
    @Test
    fun `the bin raises the question and nothing else`() {
        showScreen()

        compose.onNodeWithTag("tt_offline_delete").performClick()

        assertThat(removeRequests).containsExactly("es")
        assertThat(stops).isEmpty()
        assertThat(confirms).isEqualTo(0)
    }

    /** And the stop on a downloading row stays immediate — it is not a pack removal. */
    @Test
    fun `the stop control does not raise the question`() {
        showScreen()

        compose.onNodeWithTag("tt_offline_stop").performClick()

        assertThat(stops).containsExactly("de")
        assertThat(removeRequests).isEmpty()
    }

    @Test
    fun `an ordinary question draws 19f with the language named`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = false, savedCount = 0))

        compose.onNodeWithTag(TT_SHEET_REMOVE).assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE).assertDoesNotExist()
        compose.onNodeWithText("Remove Spanish?").assertExists()
    }

    /**
     * #230: the ordinary sheet carries the reassurance line too, gated on the
     * count. `es` is not the target here, so this is 19f, and its OWN saved-line
     * tag is present — the join the ViewModel and sheet tests cannot see.
     *
     * Mutation decided first (rule 11): in `OfflineLanguagesContent`, hand
     * `RemovePackSheet` a literal `savedCount = 0` instead of the question's
     * count. The `PendingPackRemoval` still carries 2, but the sheet receives 0
     * and draws no line, so `TT_SHEET_REMOVE_SAVED` reddens.
     */
    @Test
    fun `19f draws its saved line when the pack has saved phrases`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = false, savedCount = 2))

        compose.onNodeWithTag(TT_SHEET_REMOVE).assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_REMOVE_SAVED).assertExists()
    }

    /** #230: gated on the count — at zero the line is absent, never a "0 saved" row. */
    @Test
    fun `19f draws no saved line when nothing is saved`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = false, savedCount = 0))

        compose.onNodeWithTag(TT_SHEET_REMOVE).assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE_SAVED).assertDoesNotExist()
    }

    /** Mutation D5: drawing 19f for the in-use case would lose the whole warning. */
    @Test
    fun `an in-use question draws 19g with its saved line`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = true, savedCount = 2))

        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE).assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE).assertDoesNotExist()
        compose.onNodeWithText("Spanish is in use right now").assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_SAVED).assertExists()
    }

    /**
     * The name on the sheet is resolved from the id through the same CLDR lookup
     * the rows use, not carried from the ViewModel. `es` must read "Spanish",
     * not "es" — a sheet titled "Remove es?" is what a missing lookup produces
     * and what nothing else in this suite would notice.
     */
    @Test
    fun `the sheet names the language, not its code`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = false, savedCount = 0))

        compose.onNodeWithText("Remove es?").assertDoesNotExist()
        compose.onNodeWithText("Remove Spanish?").assertExists()
    }

    /** No question, no sheet — the resting state of the screen. */
    @Test
    fun `no question draws no sheet`() {
        showScreen()

        compose.onNodeWithTag(TT_SHEET_REMOVE).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE).assertDoesNotExist()
    }
}
