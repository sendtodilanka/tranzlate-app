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
 * Div 2 WIRING: [onlineOnlyMarkerFor] decides the chip-vs-glyph split (pinned pure
 * in [OnlineOnlyMarkerTest]); this proves [RowTrailing] actually consults it —
 * splitting the decision from its wiring exactly as `PickerRowRenderTest` splits
 * `pickerRowMinHeight` from the one call site that reads it.
 *
 * **Arrangement is forced with `arrangementOverride`, not a window qualifier.** This
 * module already distrusts the runner's window for arrangement and overrides it
 * (`FirstRunRenderTest`: "Forces the counter … whatever the runner's window is"), so
 * a render driven by a qualifier could fail for a second, unrelated reason — what
 * the gate makes of that window. The gate is pinned separately; here the arrangement
 * is a given and the only variable is what the row draws for it.
 *
 * Both marker forms carry the same per-row `tt_lang_online_only_<id>` tag — the row
 * is online-only either way — so the discriminator is the glyph's own
 * `tt_lang_online_only_glyph_<id>`, present only in the glyph form. `useUnmergedTree`
 * for the same reason `LanguagePickerDetectRowRenderTest` needs it: the marker is a
 * decorative node the row's `selectable` merge absorbs.
 */
@RunWith(RobolectricTestRunner::class)
// A window wide enough to lay out BOTH forced arrangements with room; the override,
// not this qualifier, is what fixes which arrangement the row is drawn in.
@Config(qualifiers = "w1280dp-h800dp")
class OnlineOnlyMarkerRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /** A real online-only language (`ja`) plus a downloadable one, so the list is not degenerate. */
    private val catalogue =
        listOf(
            Language("ja", "Japanese", offlineAvailable = false, offlineDownloaded = false),
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
        )

    private fun show(arrangement: PickerArrangement) {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    target = LanguageRole.SOURCE,
                    languages = catalogue,
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    // Empty: keep the first-run block to its explainer, no suggestions,
                    // so nothing competes with the catalog row under test.
                    suggestionsOverride = emptyList(),
                    arrangementOverride = arrangement,
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Mutation decided first: make [RowTrailing] draw `OnlineOnlyChip` for every
     * `OnlineOnly` row, ignoring the marker (the pre-fix behaviour). The glyph is
     * then never emitted and its tag never exists, so the first assertion reddens.
     * The second keeps it non-vacuous: the row's marker must be on screen for the
     * missing glyph to mean the form is wrong rather than the row being absent.
     */
    @Test
    fun `a two-up arrangement draws the cloud-off glyph, not the chip`() {
        show(PickerArrangement(twoPane = true, columns = 2))

        compose.onNodeWithTag("tt_lang_online_only_glyph_ja", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("tt_lang_online_only_ja", useUnmergedTree = true).assertIsDisplayed()
        // The glyph is decorative; "online only" still reaches TalkBack through the
        // row's own content description, exactly as it does for the chip. So the
        // silent glyph is no a11y regression — the spoken line is unchanged.
        compose
            .onNode(
                hasTestTag("tt_lang_row_ja") and
                    hasContentDescription("online only", substring = true, ignoreCase = true),
            ).assertExists()
    }

    /**
     * The other side of the split, and the guard on "always draw the glyph": a
     * single full-width column keeps the text chip, so the glyph tag must be ABSENT
     * while the marker itself is still present. Mutation: make [RowTrailing] draw the
     * glyph unconditionally — the `assertDoesNotExist` then reddens.
     */
    @Test
    fun `a single-column arrangement keeps the text chip`() {
        show(PickerArrangement.SinglePane)

        compose.onNodeWithTag("tt_lang_online_only_glyph_ja", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("tt_lang_online_only_ja", useUnmergedTree = true).assertIsDisplayed()
    }
}
