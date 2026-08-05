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

/**
 * Issue #224 — the ML Kit pivot (English) row is INCLUDED but NON-ACTIONABLE.
 *
 * The harm this pins is the shipped one: English arrives in the `Downloaded`
 * state (ML Kit reports the pivot as on-device before any pack exists) and drew
 * the same 🗑 every real pack does — a control whose delete is a measured no-op
 * (`docs/research/issue-224-en-row-delete.md`, Branch A). Owner ruling
 * (2026-08-05): keep the row (English is the 59th offline-capable language, so
 * hiding it would make the "59" counter lie) but give it no ⬇ and no 🗑.
 *
 * These render the real screen content, so a mutation that puts a control back on
 * the pivot row reddens here rather than passing on a green ViewModel test — the
 * decision lives in `OfflineRow`'s `isPivotLanguage` guard, and only a render can
 * see what the guard actually draws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OfflinePivotRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(rows: List<OfflineLanguageRow>) {
        compose.setContent {
            TranzlateTheme {
                OfflineLanguagesContent(
                    rows = rows,
                    onDownload = {},
                    onStopDownload = {},
                    onRequestRemove = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Mutation decided first: delete the `if (!isPivotLanguage(row.id))` guard in
     * [OfflineRow] (so the pivot's `Downloaded` state falls through to the trailing
     * control). The `tt_offline_delete` assertion then reddens — that is the exact
     * #224 defect coming back. Deleting the `isPivotLanguage` sub-line branch
     * reddens the `tt_offline_included` assertion.
     */
    @Test
    fun `the English pivot row shows the included line and no download or delete control`() {
        show(listOf(OfflineLanguageRow("en", "English", OfflineModelState.Downloaded)))

        compose.onNodeWithTag("tt_offline_included").assertIsDisplayed()
        compose.onNodeWithText("Included with every language").assertIsDisplayed()
        compose.onNodeWithTag("tt_offline_delete").assertDoesNotExist()
        compose.onNodeWithTag("tt_offline_download").assertDoesNotExist()
    }

    /**
     * The control case that makes the test above non-vacuous: an ORDINARY
     * downloaded pack still carries its 🗑, and does NOT get the pivot line.
     * Mutation: widen the guard so it strips controls from every row (or make
     * `isPivotLanguage` answer true for all) — Spanish loses its remove control and
     * this reddens. A test that only checked the pivot could pass by stripping the
     * control from everything.
     */
    @Test
    fun `a non-pivot downloaded row keeps its remove control and has no included line`() {
        show(listOf(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded)))

        compose.onNodeWithTag("tt_offline_delete").assertIsDisplayed()
        compose.onNodeWithTag("tt_offline_included").assertDoesNotExist()
    }

    /**
     * The guard is by id, not by state. English is always `Downloaded` in the wild
     * (Branch A), but if ML Kit ever reported the pivot as `NotDownloaded` the row
     * must still never offer a ⬇ for a standalone pack that does not exist.
     *
     * Mutation: guard only the `Downloaded` branch (by state) instead of the whole
     * trailing control (by id). A `NotDownloaded` pivot then renders
     * `tt_offline_download` and this reddens.
     */
    @Test
    fun `the pivot row offers no download even when reported not downloaded`() {
        show(listOf(OfflineLanguageRow("en", "English", OfflineModelState.NotDownloaded)))

        compose.onNodeWithTag("tt_offline_download").assertDoesNotExist()
        compose.onNodeWithTag("tt_offline_included").assertIsDisplayed()
    }

    /**
     * The identity the two guards share (`OfflineLanguagesViewModel.kt`). English
     * is the pivot; nothing else is. Mutation: `isPivotLanguage` answering true for
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
