package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #293 — the ML Kit pivot (English) row on the language PICKER is INCLUDED
 * but NON-ACTIONABLE, the sibling of #224 on the offline manager
 * (`OfflinePivotRowRenderTest`).
 *
 * The harm this pins is the shipped one: the picker's model-state overlay starts
 * with an empty map (`LanguageRepositoryImpl.kt:75`, `onStart { emit(emptyMap()) }`),
 * so on the first frame English — offline-capable, `offlineDownloaded == false` —
 * resolves to `Downloadable` and `RowTrailing` drew a real Download `IconButton`
 * (`tt_lang_download_en`) for a pack that does not exist (there is no standalone
 * `en` model; every ML Kit pack is an `X↔en` pair — `docs/research/issue-293…`).
 *
 * These render the real picker content, so a mutation that puts a control back on
 * the pivot row reddens here rather than passing on a green `rowStateOf` unit test:
 * the decision lives in the screen's `isPivotLanguage` guard, and only a render can
 * see what the guard actually draws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LanguagePickerPivotRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        languages: List<Language>,
        offlineStates: Map<String, OfflineModelState> = emptyMap(),
    ) {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    target = LanguageRole.SOURCE,
                    languages = languages,
                    // Nothing in the list is the selection, so every row renders at
                    // its resting state (English at Downloadable, not Selected).
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    offlineStates = offlineStates,
                    // Deterministic single column; production would measure the window.
                    arrangementOverride = PickerArrangement.SinglePane,
                )
            }
        }
        compose.waitForIdle()
    }

    /** English with no downloaded state overlaid => `Downloadable` — the exact bug path. */
    private fun english() = Language("en", "English", offlineAvailable = true, offlineDownloaded = false)

    private fun spanish() = Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false)

    /**
     * Mutation decided first: delete the
     * `if (isPivotLanguage(row.id) && row.state !is Selected) return` guard in
     * [RowTrailing]. English's `Downloadable` state then falls through to the
     * trailing `when` and renders `tt_lang_download_en` — the exact #293 defect
     * returning — so the download assertion reddens. Deleting the `pivot`
     * supporting-line override in [LanguageRow] reddens the `tt_lang_included`
     * assertion instead (the pivot's `Downloadable` state carries no line of its own).
     */
    @Test
    fun `the English pivot row shows the included line and no download control`() {
        show(listOf(english()))

        // The row is `selectable`, so it merges its descendants — the included
        // line is read on the UNMERGED tree (unlike #224's non-selectable offline
        // row). The Download button is its own clickable node and is found on the
        // merged tree, the same way the non-pivot control is in the test below.
        compose.onNodeWithTag("tt_lang_included", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Included with every language", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("tt_lang_download_en").assertDoesNotExist()
    }

    /**
     * The control case that makes the test above non-vacuous: an ORDINARY
     * offline-capable row that is not on device still carries its Download button,
     * and does NOT get the pivot line. Mutation: widen the guard so it strips the
     * control from every row (drop the `isPivotLanguage` conjunct, or make
     * `isPivotLanguage` answer true for all) — Spanish loses its Download and this
     * reddens. A test that only checked the pivot could pass by stripping the
     * control from everything.
     */
    @Test
    fun `a non-pivot downloadable row keeps its download control and has no included line`() {
        show(listOf(spanish()))

        compose.onNodeWithTag("tt_lang_download_es").assertIsDisplayed()
        // Unmerged, so widening the pivot line to every row would actually be seen
        // here (on the merged tree a selectable row hides its child's tag, which
        // would make this pass vacuously).
        compose.onNodeWithTag("tt_lang_included", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The guard is by id, not by state. English is `Downloadable` in the wild only
     * on the first frame, but if ML Kit ever reported the pivot mid-`Downloading`
     * the row must still never offer a control that acts on a pack that does not
     * exist. Mutation: guard only the `Downloadable` branch (by state) instead of
     * the whole trailing control (by id). A `Downloading` pivot then renders
     * `tt_lang_stop_en` and this reddens.
     */
    @Test
    fun `the pivot row offers no control even when reported downloading`() {
        show(listOf(english()), offlineStates = mapOf("en" to OfflineModelState.Downloading))

        compose.onNodeWithTag("tt_lang_download_en").assertDoesNotExist()
        compose.onNodeWithTag("tt_lang_stop_en").assertDoesNotExist()
        compose.onNodeWithTag("tt_lang_included", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * The identity the guard shares with #224 (`OfflineLanguagesViewModel.kt`).
     * English is the pivot; nothing else is. Mutation: `isPivotLanguage` answering
     * true for another id, or false for `en`, reddens one of these. (Also pinned by
     * `OfflinePivotRowRenderTest`; re-stated here so this file is self-contained
     * about the contract its render tests stand on.)
     */
    @Test
    fun `isPivotLanguage identifies only English`() {
        assertThat(isPivotLanguage("en")).isTrue()
        assertThat(isPivotLanguage("fr")).isFalse()
        assertThat(isPivotLanguage("de")).isFalse()
        assertThat(isPivotLanguage("")).isFalse()
    }
}
