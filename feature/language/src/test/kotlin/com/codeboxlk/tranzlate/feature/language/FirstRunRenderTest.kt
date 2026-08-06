package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The 18a block and its counter variant, RENDERED (#186's Compose runtime).
 *
 * Two things here can only be seen on a laid-out screen: the "All languages"
 * counter reading "N can be offline" when nothing is downloaded (a source-shape
 * test would never notice the branch), and the Get → confirm chain drawing a
 * sheet titled with the language the CATALOGUE names — the resolution a unit test
 * of the ViewModel cannot exercise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FirstRunRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /** [n] offline-capable languages, the first [downloaded] of them on device. */
    private fun capable(
        n: Int,
        downloaded: Int,
    ): List<Language> =
        List(n) { i ->
            Language(
                id = "l$i",
                name = "Lang $i",
                offlineAvailable = true,
                offlineDownloaded = i < downloaded,
            )
        }

    private fun showTarget(languages: List<Language>) {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    // TARGET, so no first-run block competes for the screen — the
                    // counter is the only thing under test.
                    target = LanguageRole.TARGET,
                    languages = languages,
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    // Forces the counter into the "All languages" header rather than
                    // the two-pane top bar, whatever the runner's window is.
                    arrangementOverride = PickerArrangement.SinglePane,
                )
            }
        }
        compose.waitForIdle()
    }

    /** Zero downloaded → 18a's "N can be offline", not the false "0 of N on device". */
    @Test
    fun `nothing downloaded reads N can be offline`() {
        showTarget(capable(3, downloaded = 0))

        compose.onNodeWithTag("tt_lang_counter").assertTextEquals("3 can be offline")
    }

    /**
     * One or more downloaded → the ordinary on-device counter, which now says
     * "packs" (div 1): the frames draw "5 of 59 packs on device", the sibling
     * Manage-packs counter already says "packs", and the two were inconsistent.
     * This is the div-1 mutate-first proof: revert `text_lang_on_device_count` to
     * "%1$d of %2$d on device" and this exact-text assertion reddens.
     */
    @Test
    fun `some downloaded reads N of M packs on device`() {
        showTarget(capable(3, downloaded = 1))

        compose.onNodeWithTag("tt_lang_counter").assertTextEquals("1 of 3 packs on device")
    }

    /** The block is a source affordance: a target picker with empty recents draws none of it. */
    @Test
    fun `the target picker draws no first-run block`() {
        showTarget(capable(3, downloaded = 0))

        compose.onNodeWithTag("tt_lang_first_run").assertDoesNotExist()
    }

    /** The source picker with no recents draws the "No packs yet" explainer. */
    @Test
    fun `the source picker draws the first-run explainer when recents is empty`() {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    target = LanguageRole.SOURCE,
                    languages = capable(3, downloaded = 0),
                    selectedId = "l0",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    // Empty suggestions: the explainer must stand on its own, the
                    // English-only case, without deriving from device locales.
                    suggestionsOverride = emptyList(),
                    arrangementOverride = PickerArrangement.SinglePane,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tt_lang_first_run").assertIsDisplayed()
    }

    private val gets = mutableListOf<String>()
    private var confirms = 0
    private var dismissals = 0

    /** The source picker with one French suggestion, drawn but not yet acted on. */
    private fun showFrenchSuggestion() {
        compose.setContent {
            var pending by remember { mutableStateOf<String?>(null) }
            TranzlateTheme {
                LanguagePickerContent(
                    target = LanguageRole.SOURCE,
                    languages =
                        listOf(
                            Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
                            Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                        ),
                    selectedId = "en",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    suggestionsOverride =
                        listOf(
                            SuggestedLanguage(
                                id = "fr",
                                displayName = "French",
                                avatar = LanguageAvatar.Code("FR"),
                                reason = SuggestionReason.COMMON_WHERE_YOU_ARE,
                            ),
                        ),
                    onSuggestionGet = { id ->
                        gets += id
                        pending = id
                    },
                    pendingSuggestion = pending,
                    onSuggestionConfirm = {
                        confirms++
                        pending = null
                    },
                    onSuggestionDismiss = {
                        dismissals++
                        pending = null
                    },
                    arrangementOverride = PickerArrangement.SinglePane,
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * The same suggestion, its Get tapped, so the confirm sheet is open — wired
     * through the REAL call site (`LanguagePickerContent` → `DownloadConfirmSheet`,
     * the `onConfirm` / `onDismiss` arguments). [gets] / [confirms] / [dismissals]
     * are what the tests below read; each JUnit method gets a fresh instance, so
     * they start at zero.
     */
    private fun openFrenchConfirm() {
        showFrenchSuggestion()
        compose.onNodeWithTag("tt_lang_suggested_get_fr").performClick()
        compose.waitForIdle()
    }

    /**
     * Div 3: the suggestion Get button draws the `download` glyph the 18a / 18b
     * frames show on it — the icon `SuggestedRow` had none of, and the one the
     * Manage-packs empty-state Get already carries.
     *
     * Mutation decided first: delete the `Icon(Icons.Filled.Download …)` from
     * `SuggestedRow`'s button. Its `tt_lang_suggested_get_icon_fr` tag then never
     * exists and this reddens. `useUnmergedTree` because the button merges its icon
     * and label into one node for TalkBack.
     */
    @Test
    fun `the suggestion Get button draws the download icon`() {
        showFrenchSuggestion()

        compose
            .onNodeWithTag("tt_lang_suggested_get_icon_fr", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Get → confirm, at the render level: tapping a suggestion's Get fires
     * onSuggestionGet, and once the pending id is set the confirm sheet appears
     * titled with the name the CATALOGUE gives that id.
     */
    @Test
    fun `tapping Get raises the confirm sheet titled for the language`() {
        openFrenchConfirm()

        assertThat(gets).containsExactly("fr")
        compose.onNodeWithTag(TT_SHEET_CONFIRM).assertIsDisplayed()
        compose.onNodeWithText("Download French").assertIsDisplayed()
    }

    /**
     * The confirm BUTTON must route to the confirm callback, and this test runs
     * through the REAL host wiring — `onConfirm = onSuggestionConfirm` at the call
     * site (`LanguagePickerContent` → `DownloadConfirmSheet`). `DownloadConfirmSheetTest`
     * only sees a swap INSIDE the sheet, one layer down; a copy-paste slip that
     * swapped these two adjacent call-site arguments would make "Download and use"
     * silently dismiss, and no other test in the module would redden. Asserted as a
     * pair (confirm fired AND dismiss did NOT) so an inversion cannot pass on a
     * count alone.
     */
    @Test
    fun `the confirm button routes through the call site to the confirm callback`() {
        openFrenchConfirm()

        compose.onNodeWithTag(TT_SHEET_CONFIRM_DOWNLOAD).performClick()
        compose.waitForIdle()

        assertThat(confirms).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    /**
     * The other half of the same swap: the Not-now control must route through
     * `onDismiss = onSuggestionDismiss` to the dismiss callback, not confirm. A
     * swap would make "Not now" silently start a download.
     */
    @Test
    fun `the not-now button routes through the call site to the dismiss callback`() {
        openFrenchConfirm()

        compose.onNodeWithTag(TT_SHEET_CONFIRM_NOT_NOW).performClick()
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
        assertThat(confirms).isEqualTo(0)
    }
}
