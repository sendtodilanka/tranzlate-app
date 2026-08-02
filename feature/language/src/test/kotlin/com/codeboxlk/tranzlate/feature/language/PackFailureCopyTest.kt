package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Issue #175, reproduced and then closed** — rendered on both screens (#130
 * PR-18).
 *
 * The harm was never a shape: it was two sentences. Lose the connection while a
 * pack downloads and the language picker said *"No connection. Reconnect and try
 * again."* while Settings → Offline languages said *"Download failed — check
 * your connection, then retry"*. One fault, two voices, and a user who meets
 * both has no way to know it is one problem. Nothing could go red about it,
 * because each screen was internally consistent.
 *
 * So the reproduction is the harm itself: **render each screen's failed row and
 * read the sentence off it.** Before the change these two tests disagree; after
 * it they both read the one shared resource. A source-shape rule ("both call the
 * same function") would have been cheaper and would not have reproduced
 * anything — a later PR can call the shared map and then override the text at
 * the draw site, and only a rendered assertion notices.
 *
 * The Retry control is here for a different reason: it is the deviation the rev3
 * ruling asks PR-18 to close (*"icon → spec filled pill"*), and the mutation it
 * has to survive is the icon coming back. A test on the TAG survives that
 * mutation intact — the tag was already `tt_lang_retry_<id>` — so every
 * assertion below is on the LABEL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PackFailureCopyTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** One offline-capable language, failed for one cause, on either screen. */
    private val catalogue =
        listOf(Language(id = "hi", name = "Hindi", offlineAvailable = true, offlineDownloaded = false))

    private val failedNetwork = mapOf("hi" to OfflineModelState.Failed(OfflineModelFailure.NETWORK))

    private var retried = mutableListOf<String>()

    private fun showPicker(states: Map<String, OfflineModelState> = failedNetwork) {
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
                    offlineStates = states,
                    onDownload = { retried += it },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showOfflineManager(cause: OfflineModelFailure = OfflineModelFailure.NETWORK) {
        compose.setContent {
            TranzlateTheme {
                OfflineLanguagesContent(
                    rows = listOf(OfflineLanguageRow("hi", "Hindi", OfflineModelState.Failed(cause))),
                    onDownload = { retried += it },
                    onDelete = {},
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /** Screen A. The sentence a user reads when a download drops out under the picker. */
    @Test
    fun `the picker's failed row reads the shared sentence`() {
        showPicker()

        compose
            .onNodeWithText(context.getString(R.string.lang_pack_error_network), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Screen B. The SAME assertion against the SAME resource — which is the
     * whole of #175. Before PR-18 this screen drew `offline_error_network`, a
     * different sentence, and this test would have failed on the text while
     * every other check in the repository stayed green.
     */
    @Test
    fun `the offline manager's failed row reads the same shared sentence`() {
        showOfflineManager()

        compose
            .onNodeWithTag("tt_offline_error_line")
            .assertTextEquals(context.getString(R.string.lang_pack_error_network))
    }

    /**
     * The two screens are checked against one resource above; this checks the
     * resource is the sentence the STRINGS catalogue says it is, so that a copy
     * edit in `strings.xml` alone cannot silently reopen the divergence from the
     * other side.
     */
    @Test
    fun `the shared sentence states the fact and the way out`() {
        assertThat(context.getString(R.string.lang_pack_error_network))
            .isEqualTo("No connection. Reconnect and try again.")
        assertThat(context.getString(R.string.lang_pack_error_storage))
            .isEqualTo("Not enough space. Free some up and try again.")
        assertThat(context.getString(R.string.lang_pack_error_generic))
            .isEqualTo("Download didn't finish. Try again.")
    }

    /**
     * The 15a deviation, as the user meets it: a WORD, not a circular arrow.
     *
     * `assertTextEquals` and not `assertExists` — the tag did not change, so a
     * mutation that puts `Icons.Filled.Refresh` back leaves every tag-based
     * assertion in this repository green.
     */
    @Test
    fun `the failed row's action is a labelled Retry`() {
        showPicker()

        compose.onNodeWithTag("tt_lang_retry_hi").assertTextEquals("Retry")
    }

    /** And it still does what the icon did. */
    @Test
    fun `the Retry pill asks for the download again`() {
        showPicker()

        compose.onNodeWithTag("tt_lang_retry_hi").performClick()

        assertThat(retried).containsExactly("hi")
    }

    /**
     * WCAG 2.5.3 *Label in Name*: the accessible name must contain the visible
     * label, or a voice-control user saying "tap Retry" reaches nothing.
     *
     * This is a rule the LABEL created — while the control was an icon there was
     * no visible text for a name to contain, and `cd_text_lang_retry` read "Try
     * downloading Hindi again". Asserted as `contains` rather than as the exact
     * string, because the requirement is containment and pinning the whole
     * sentence would make every future copy edit a test edit.
     */
    @Test
    fun `the Retry pill's spoken name contains its visible label`() {
        showPicker()

        val spoken = context.getString(R.string.cd_text_lang_retry, "Hindi")

        assertThat(spoken).contains(context.getString(R.string.lang_sheet_failed_retry))
        assertThat(spoken).contains("Hindi")
    }

    /** No failure, no pill — the state every row is in almost always. */
    @Test
    fun `a row that has not failed carries no Retry`() {
        showPicker(states = emptyMap())

        compose.onNodeWithTag("tt_lang_retry_hi").assertDoesNotExist()
    }
}
