package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Two Manage-packs row facts the ViewModel/model tests cannot see, because they
 * are decisions taken inside `@Composable` code (#130 PR-23, rev3 ruling's fourth
 * cause). Rendered from the real content + the real classifier, so a mutation to
 * either decision reddens here rather than passing on a green pure-logic test.
 *
 * 1. **Retry on a Failed row, out-of-space INCLUDED (#250).** PR-23 shipped a
 *    `state.cause != STORAGE` guard that drew NOTHING for a space-failed row — a
 *    permanent dead-end: the red line promised an action with no control to tap,
 *    and freeing space elsewhere never restored one (the manager's transient state
 *    only moves on a `download()`). `origin/main` drew an unconditional Retry for
 *    every cause; removing it was the regression. The honesty of that retry (a
 *    still-full disk gets a snackbar, not silence) is the ViewModel's job and is
 *    pinned in `OfflineLanguagesViewModelTest`; here we pin that the CONTROL is
 *    drawn at all.
 *
 * 2. **The usage line's NoRecord/Today mapping (`packUsageText`).** The co-verify
 *    lens swapped `manage_used_never` and `manage_used_today` and all 421 tests
 *    stayed green — the mapping was rendered by nothing. These read the line off
 *    the `tt_manage_usage_line` node, so the swap now reddens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ManagePacksRowRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(
        rows: List<OfflineLanguageRow>,
        usage: Map<String, Long> = emptyMap(),
        nowMillis: Long = 0L,
    ) {
        val sections =
            buildManagePacksSections(rows, usage = usage, targetId = "", nowMillis = nowMillis, locale = Locale.ENGLISH)
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

    /** The row usage line reads it the way the composable does. */
    private fun onDeviceLine(usageWord: Int): String =
        context.getString(R.string.text_lang_on_device_size, context.getString(usageWord))

    // ── #250: the space-failed row keeps its Retry ─────────────────────────────

    /**
     * Mutation decided first: re-add `if (state.cause != OfflineModelFailure.STORAGE)`
     * around the Retry `Button` in `PackRowControl` — the space-failed row then draws
     * no control and this reddens, which is the #250 dead-end returning.
     */
    @Test
    fun `a space-failed row renders a Retry control`() {
        show(listOf(OfflineLanguageRow("ta", "Tamil", OfflineModelState.Failed(OfflineModelFailure.STORAGE))))

        compose.onNodeWithTag("tt_manage_retry").assertIsDisplayed()
    }

    /**
     * The enumerating angle on the same mutation: with a network AND a space failure
     * in the section, BOTH rows carry Retry. Re-adding the `!= STORAGE` guard drops
     * the count to 1 and this reddens; a green here means "no cause is special", the
     * whole of the #250 fix. The network row is the control that keeps the space
     * assertion non-vacuous — it proves the tag is the right one and the pill draws.
     */
    @Test
    fun `every failed cause including out-of-space renders a Retry control`() {
        show(
            listOf(
                OfflineLanguageRow("hi", "Hindi", OfflineModelState.Failed(OfflineModelFailure.NETWORK)),
                OfflineLanguageRow("ta", "Tamil", OfflineModelState.Failed(OfflineModelFailure.STORAGE)),
            ),
        )

        compose.onAllNodesWithTag("tt_manage_retry").assertCountEquals(2)
    }

    // ── packUsageText: NoRecord vs Today are drawn distinctly ───────────────────

    /**
     * A downloaded pack with NO usage stamp reads "no recorded use yet" (ruling ⑧),
     * never a fabricated date. Expected line built from the SAME resources the row
     * draws from, so the assertion pins the MAPPING, not the wording: swapping
     * `manage_used_never`/`manage_used_today` in `packUsageText` makes this row read
     * "used today" and this reddens.
     */
    @Test
    fun `a pack with no recorded use shows the never line`() {
        show(listOf(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded)))

        compose
            .onNodeWithTag("tt_manage_usage_line", useUnmergedTree = true)
            .assertTextEquals(onDeviceLine(R.string.manage_used_never))
    }

    /**
     * A pack used within the last day reads "used today". `usage["es"] == nowMillis`
     * → elapsed 0 → `PackUsage.Today`. The mirror of the test above: the same
     * `manage_used_*` swap makes this row read "no recorded use yet" and reddens.
     */
    @Test
    fun `a pack used today shows the today line`() {
        show(
            listOf(OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded)),
            usage = mapOf("es" to 0L),
            nowMillis = 0L,
        )

        compose
            .onNodeWithTag("tt_manage_usage_line", useUnmergedTree = true)
            .assertTextEquals(onDeviceLine(R.string.manage_used_today))
    }
}
