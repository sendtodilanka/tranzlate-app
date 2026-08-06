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
import java.util.Locale

/**
 * Manage packs' remove flow as the SCREEN wires it (#130 PR-19, carried into the
 * PR-23 rewrite).
 *
 * `OfflineLanguagesViewModelTest` proves the rule (which sheet, what is counted,
 * what is deleted) and `RemovePackSheetsTest` proves the sheets. What neither can
 * see is the join: that the overflow is wired to the request rather than straight
 * to the delete, that the ⏹ is wired to the immediate stop, and that the question
 * picks the right one of two sheets with the right data. That join is exactly
 * where the shipped defect lived before PR-19 — the bin called `onDelete`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OfflineRemoveFlowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val removeRequests = mutableListOf<String>()
    private val stops = mutableListOf<String>()
    private var confirms = 0

    // es → on-device (an overflow); de → downloading (a stop). Built through the
    // real classifier so the sections are exactly what the screen would show.
    private val sections =
        buildManagePacksSections(
            rows =
                listOf(
                    OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded),
                    OfflineLanguageRow("de", "German", OfflineModelState.Downloading),
                ),
            usage = emptyMap(),
            targetId = "",
            locale = Locale.ENGLISH,
        )

    private fun showScreen(pendingRemoval: PendingPackRemoval? = null) {
        compose.setContent {
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = sections,
                    storage = null,
                    nudge = null,
                    suggestions = emptyList(),
                    capable = 59,
                    total = 194,
                    onBack = {},
                    onGet = {},
                    onStopDownload = { stops += it },
                    onRetry = {},
                    onRemove = { removeRequests += it },
                    onDismissNudge = {},
                    onBrowseAll = {},
                    pendingRemoval = pendingRemoval,
                    onConfirmRemove = { confirms++ },
                    onDismissRemove = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * The route the overflow takes since #130 PR-24: it opens the 20c pack-actions
     * sheet, and REMOVE INSIDE that sheet raises the question. Before PR-24 the
     * overflow called `onRemove` straight off (this test asserted exactly that); now
     * the overflow ASKS the sheet, so `removeRequests` is still empty right after it,
     * and only the sheet's Remove raises the question. Wiring the overflow back to
     * `onRemove` directly, or wiring the sheet's Remove to nothing, reddens here.
     */
    @Test
    fun `the overflow opens the actions sheet, and Remove there raises the question`() {
        showScreen()

        compose.onNodeWithTag("tt_manage_options").performClick()
        compose.waitForIdle()
        assertThat(removeRequests).isEmpty()

        compose.onNodeWithTag(TT_SHEET_PACK_REMOVE).performClick()
        compose.waitForIdle()

        assertThat(removeRequests).containsExactly("es")
        assertThat(stops).isEmpty()
        assertThat(confirms).isEqualTo(0)
    }

    /** And the stop on a downloading row stays immediate — it is not a pack removal. */
    @Test
    fun `the stop control does not raise the question`() {
        showScreen()

        compose.onNodeWithTag("tt_manage_stop").performClick()

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
     * count. Mutation decided first (rule 11): hand `RemovePackSheet` a literal
     * `savedCount = 0` instead of the question's count — the sheet draws no line
     * and `TT_SHEET_REMOVE_SAVED` reddens.
     */
    @Test
    fun `19f draws its saved line when the pack has saved phrases`() {
        showScreen(PendingPackRemoval(id = "es", inUseAsTarget = false, savedCount = 2))

        compose.onNodeWithTag(TT_SHEET_REMOVE).assertExists()
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
     * the rows use, not carried from the ViewModel: "es" must read "Spanish".
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
