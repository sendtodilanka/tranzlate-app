package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import android.content.res.Configuration
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
import java.util.Locale

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
        val sections =
            buildManagePacksSections(
                rows = listOf(OfflineLanguageRow("hi", "Hindi", OfflineModelState.Failed(cause))),
                usage = emptyMap(),
                targetId = "",
                nowMillis = 0L,
                locale = Locale.ENGLISH,
            )
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
                    // The failure SENTENCE is this test's subject; the actions are
                    // inert here (retry/stop/remove each have their own coverage).
                    onGet = { retried += it },
                    onStopDownload = {},
                    onRetry = { retried += it },
                    onRemove = {},
                    onDismissNudge = {},
                    onBrowseAll = {},
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
            .onNodeWithTag("tt_manage_error_line")
            .assertTextEquals(context.getString(R.string.lang_pack_error_network))
    }

    /**
     * **Issue #250, the design call PR-23 owns.** A download that failed for lack
     * of space must NOT offer a Retry: retrying without freeing space just
     * re-fails, which is the #234 dead-control class. Every other cause keeps its
     * Retry.
     *
     * Mutation decided first (rule 11): drop the `if (state.cause != STORAGE)`
     * guard in `PackRowControl` so a space failure draws the pill too — the space
     * test's `assertDoesNotExist` then reddens. The network test is the control
     * that keeps the mutation honest: a guard that hid EVERY Retry would pass the
     * space case vacuously, and that would redden the network test instead.
     */
    @Test
    fun `a space-failed row offers no dead Retry`() {
        showOfflineManager(OfflineModelFailure.STORAGE)
        compose.onNodeWithTag("tt_manage_retry").assertDoesNotExist()
    }

    @Test
    fun `a network-failed row keeps its Retry`() {
        showOfflineManager(OfflineModelFailure.NETWORK)
        compose.onNodeWithTag("tt_manage_retry").assertIsDisplayed()
    }

    /** The retryable failure's pill does what it says — asks for the download again. */
    @Test
    fun `the manager's Retry asks for the download again`() {
        showOfflineManager(OfflineModelFailure.NETWORK)

        compose.onNodeWithTag("tt_manage_retry").performClick()

        assertThat(retried).containsExactly("hi")
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

    // ---- #219: the pack size is one figure ------------------------------------------------------

    /**
     * **Issue #219.** Four user-facing strings state how big a language pack is,
     * and they disagreed in three wordings: `20–45 MB` on the two sheets,
     * `~30MB` on the offline manager's subtitle and `about 30 MB` in Settings —
     * the last two contradicted by every pack this project has measured. 19b
     * does arithmetic in front of the user with that figure (*"There is 12 MB
     * free. A language pack usually needs …"*), so the wrong number is wrong
     * where it is being relied on.
     *
     * The assertion is **agreement**, not a literal. A test comparing each
     * string to a hardcoded `40–65 MB` would go green on a future sweep that
     * moved three of the four to a new figure and forgot the fourth — which is
     * the exact defect, one figure later. It extracts whatever range each
     * sentence states and requires the set to have size one.
     *
     * **Declared limit, because a test that quietly covers less than it appears
     * to is worse than a smaller one.** This covers **3 of the 4** strings.
     * `settings_mobile_data_supporting` ships from `:feature:settings` and a
     * Robolectric test here cannot resolve another module's `R`. The fourth is
     * held by enumeration and by `STRINGS_settings.md` alone.
     */
    @Test
    fun `the pack size is one figure, stated once`() {
        val stated =
            mapOf(
                // The free-space argument is deliberately NOT a size. 19b's
                // sentence carries two figures — what the device has and what a
                // pack costs — and only the second is this test's subject; a
                // realistic "12 MB" here would be the first thing the pattern
                // found and the assertion would be about the fixture.
                "lang_sheet_space_body" to context.getString(R.string.lang_sheet_space_body, "«free»"),
                "lang_sheet_data_body" to context.getString(R.string.lang_sheet_data_body),
                "offline_subtitle" to context.getString(R.string.offline_subtitle),
            ).mapValues { (key, sentence) ->
                requireNotNull(PACK_SIZE.find(sentence)) { "$key states no pack size: $sentence" }.value
            }

        assertThat(stated.values.toSet()).hasSize(1)
        assertThat(stated.values.first()).isEqualTo("40–65 MB")
    }

    // ---- #229: one action, one verb, three locales ----------------------------------------------

    /**
     * **Issue #229.** The 🗑 on the offline manager said *"Delete %1$s"* to a
     * screen reader and opened a sheet titled *"Remove %1$s?"* with a *Remove*
     * button. One action, two verbs, and only the spoken one disagreed — 19f's
     * own caption is *"The verb in the button matches the verb in the title."*
     *
     * Asserted as **agreement** and not as `isEqualTo("Remove %1$s")`. The wrong
     * fix for this issue is to move the visible copy to "Delete"; a literal
     * assertion survives that, and this does not.
     */
    @Test
    fun `the bin's spoken verb is the sheet's verb`() {
        assertVerbAgrees(context)
    }

    /**
     * The same agreement in the other two locales, because all three had drifted
     * separately: `fil` said `Burahin` against a sheet saying `Alisin`, `pt-rBR`
     * said `Excluir` against `Remover`. A fix to `values/` alone would have left
     * two thirds of the defect shipping.
     *
     * **Each case asserts its own locale's confirm label first.** A locale test
     * whose configuration does not take effect reads the English resources and
     * passes — a test that cannot fail, which is what #242 is a list of. The
     * guard makes the configuration itself the thing under test.
     */
    @Test
    fun `the bin's spoken verb is the sheet's verb in every locale`() {
        val filipino = context.localized(Locale("fil"))
        assertThat(filipino.getString(R.string.lang_sheet_remove_confirm)).isEqualTo("Alisin")
        assertVerbAgrees(filipino)

        val brazilian = context.localized(Locale("pt", "BR"))
        assertThat(brazilian.getString(R.string.lang_sheet_remove_confirm)).isEqualTo("Remover")
        assertVerbAgrees(brazilian)
    }

    private fun assertVerbAgrees(localized: Context) {
        val spoken = localized.getString(R.string.offline_cd_delete, "Spanish")
        val onTheButton = localized.getString(R.string.lang_sheet_remove_confirm)

        assertThat(spoken.substringBefore(' ')).isEqualTo(onTheButton)
        assertThat(spoken).contains("Spanish")
    }

    private fun Context.localized(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })

    private companion object {
        /** `40–65 MB`, `20–45 MB`, `de 40 a 65 MB`, `~30MB` — whatever the sentence states. */
        val PACK_SIZE = Regex("""~?\d+\s*(?:[–-]|\ba\b)?\s*\d*\s*MB""")
    }
}
