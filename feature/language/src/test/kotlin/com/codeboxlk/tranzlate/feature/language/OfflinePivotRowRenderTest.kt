package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Issue #224 — the ML Kit pivot (English) row is INCLUDED but NON-ACTIONABLE, on
 * the rewritten Manage packs screen (#130 PR-23).
 *
 * English arrives `Downloaded` (ML Kit reports the pivot as on-device before any
 * pack exists) so it sits in the on-device section like any pack — but its delete
 * is a measured no-op (`docs/research/issue-224-en-row-delete.md`, Branch A). Owner
 * ruling (2026-08-05): keep the row (English is the 59th offline-capable language,
 * so hiding it would make the "59" counter lie) but give it no overflow, and say
 * why with the "included" line in place of a usage line.
 *
 * Rendered from the real content + the real classifier, so a mutation that puts
 * the overflow back on the pivot, or drops the "included" line, reddens here
 * rather than passing on a green ViewModel test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OfflinePivotRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(rows: List<OfflineLanguageRow>) {
        val sections =
            buildManagePacksSections(rows, usage = emptyMap(), targetId = "", nowMillis = 0L, locale = Locale.ENGLISH)
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
                    onStopDownload = {},
                    onRetry = {},
                    onRemove = {},
                    onDismissNudge = {},
                    onBrowseAll = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Mutation decided first: delete the `if (row.isPivot)` guard in
     * `PackRowControl` (so the pivot's `Downloaded` state falls through to the
     * overflow) — `tt_manage_options` then appears and this reddens; that is the
     * #224 defect coming back. Deleting the `isPivot` branch in `PackRowSupporting`
     * reddens the `tt_manage_included` assertion.
     */
    @Test
    fun `the English pivot row shows the included line and no overflow control`() {
        show(listOf(OfflineLanguageRow("en", "English", OfflineModelState.Downloaded)))

        compose.onNodeWithTag("tt_manage_included").assertIsDisplayed()
        compose.onNodeWithText("Included with every language").assertIsDisplayed()
        compose.onNodeWithTag("tt_manage_options").assertDoesNotExist()
    }

    /**
     * The control case that makes the test above non-vacuous: an ORDINARY
     * downloaded pack still carries its overflow, and does NOT get the pivot line.
     * Mutation: widen the guard so it strips the overflow from every row — Spanish
     * loses its overflow and this reddens.
     */
    @Test
    fun `a non-pivot downloaded row keeps its overflow and has no included line`() {
        show(listOf(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded)))

        compose.onNodeWithTag("tt_manage_options").assertIsDisplayed()
        compose.onNodeWithTag("tt_manage_included").assertDoesNotExist()
    }

    /**
     * The identity the guards share (`OfflineLanguagesViewModel.kt`). English is
     * the pivot; nothing else is. Mutation: `isPivotLanguage` answering true for
     * another id, or false for `en`, reddens one of these.
     */
    @Test
    fun `isPivotLanguage identifies only English`() {
        assertThat(isPivotLanguage("en")).isTrue()
        assertThat(isPivotLanguage("fr")).isFalse()
        assertThat(isPivotLanguage("de")).isFalse()
        assertThat(isPivotLanguage("")).isFalse()
    }
}
