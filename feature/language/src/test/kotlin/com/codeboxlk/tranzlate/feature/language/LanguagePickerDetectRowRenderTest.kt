package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner ruling 2 (2026-08-01) / designer-brief §11-10: the "Detect language"
 * pseudo-row must carry NO `ONLINE ONLY` chip — detection runs on-device
 * (`MlKitLanguageIdentifier.kt:3,22`), so the chip states something false — while
 * the 135 real online-only *languages* KEEP theirs (the chip is correct for them,
 * designer-brief §1).
 *
 * The claim "online only" is made on the Detect row in TWO independent places, so
 * this file pins both — a fix to one is not a fix to the other:
 * - the **visible chip**, drawn in [RowTrailing] (`OnlineOnlyChip`);
 * - the **spoken line**, the row's content description
 *   (`cd_text_lang_row_online_only`). This is the ONLY place the claim reaches
 *   TalkBack, because `OnlineOnlyChip` clears its own semantics — so stripping
 *   just the pixels would leave a blind user hearing the exact falsehood.
 *
 * These render the real picker content (source side, so the Detect row is
 * present), so a mutation that puts the claim back on the Detect row reddens here
 * rather than passing on a green unit test — the decision lives in the screen's
 * `DETECT_LANGUAGE_ID` guards, and only a render can see what they actually draw.
 *
 * The chip is tagged per row (`tt_lang_online_only_<id>`) at its one call site, so
 * "present on Japanese, absent on Detect" is directly observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LanguagePickerDetectRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(languages: List<Language>) {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    // SOURCE, so `rememberPickerSections` adds the Detect pseudo-row.
                    target = LanguageRole.SOURCE,
                    languages = languages,
                    // Nothing in the list (nor the Detect sentinel "auto") is selected,
                    // so the Detect row renders at its resting state, not Selected.
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    // Deterministic single column; production would measure the window.
                    arrangementOverride = PickerArrangement.SinglePane,
                )
            }
        }
        compose.waitForIdle()
    }

    /** A real online-only language (`ja`) plus a downloadable one, so the list is not degenerate. */
    private fun catalog() =
        listOf(
            Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
        )

    /**
     * Mutation decided first: delete the
     * `if (row.id == DETECT_LANGUAGE_ID && row.state !is Selected) return` guard in
     * [RowTrailing]. The Detect row's borrowed `OnlineOnly` state then falls through
     * to `OnlineOnlyChip(... testTag("tt_lang_online_only_auto"))` and the chip tag
     * exists — so the `assertDoesNotExist` reddens. The first assertion keeps this
     * non-vacuous: the Detect row must actually be on screen for its missing chip to
     * mean anything.
     */
    @Test
    fun `the Detect row is present but draws no ONLINE ONLY chip`() {
        show(catalog())

        compose.onNodeWithTag("tt_lang_row_auto").assertIsDisplayed()
        // Unmerged tree: the chip is a non-clickable node with only a testTag, so
        // the row's `selectable` mergeDescendants absorbs it — on the MERGED tree a
        // re-added chip would be invisible here and this check would pass vacuously.
        compose.onNodeWithTag("tt_lang_online_only_auto", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Mutation decided first: delete the `row.id == DETECT_LANGUAGE_ID` branch in
     * [rowContentDescription]. The base then becomes
     * `stateContentDescription(OnlineOnly, "Detect language")` =
     * "Detect language, online only", so the second matcher finds a node and the
     * `assertDoesNotExist` reddens. The first assertion proves the row is present and
     * its description contains its name, so the second is not vacuously true because
     * the row is missing.
     */
    @Test
    fun `the Detect row does not announce online only`() {
        show(catalog())

        compose
            .onNode(hasTestTag("tt_lang_row_auto") and hasContentDescription("Detect language", substring = true))
            .assertExists()
        compose
            .onNode(
                hasTestTag("tt_lang_row_auto") and
                    hasContentDescription("online only", substring = true, ignoreCase = true),
            ).assertDoesNotExist()
    }

    /**
     * Non-vacuity — proves the strip did not go too far. A real online-only language
     * still draws its chip AND still announces "online only". Mutation: guard the
     * chip/description by STATE (strip from every `OnlineOnly` row) instead of by the
     * Detect id — Japanese then loses both, and both assertions redden. A test that
     * only checked the Detect row could pass by stripping the chip from everything.
     */
    @Test
    fun `a real online-only language keeps its chip and its spoken line`() {
        show(catalog())

        // Unmerged tree, for the same reason as the absence check above.
        compose.onNodeWithTag("tt_lang_online_only_ja", useUnmergedTree = true).assertIsDisplayed()
        compose
            .onNode(
                hasTestTag("tt_lang_row_ja") and
                    hasContentDescription("online only", substring = true, ignoreCase = true),
            ).assertExists()
    }
}
