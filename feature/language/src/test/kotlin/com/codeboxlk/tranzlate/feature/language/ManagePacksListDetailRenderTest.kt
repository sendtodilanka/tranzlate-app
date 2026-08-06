package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The three things 20d (#130 PR-26) does that only a render can see: the width GATE
 * that admits the two-pane, the per-role "source" line's honest dates, and a row
 * tap moving the detail. Each is a decision inside `@Composable` code — the class of
 * defect the rev.3 ruling's fourth cause names — so each mutation reddens HERE and
 * would pass every pure-logic test in the module.
 *
 * The width gate is exercised for real: `ManagePacksContent` reads
 * `rememberWindowInfo().isExpanded`, and Robolectric's `@Config(qualifiers=...)`
 * drives `currentWindowAdaptiveInfo()` — verified before this test was written by a
 * throwaway probe that read EXPANDED at `w1280dp-h800dp` and COMPACT at `w411dp`.
 * So the two width tests below mount the SAME content and differ only in the
 * qualifier, the way `PickerDialogRenderTest` measures the dialog card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class ManagePacksListDetailRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Two downloaded, date-less packs — sorted German, Spanish (alphabetical, both NoRecord). */
    private fun twoPacks() =
        listOf(
            OfflineLanguageRow("de", "German", OfflineModelState.Downloaded),
            OfflineLanguageRow("es", "Spanish", OfflineModelState.Downloaded),
        )

    private fun show(
        rows: List<OfflineLanguageRow>,
        usage: Map<String, Long> = emptyMap(),
        usageAsSource: Map<String, Long> = emptyMap(),
        usageAsTarget: Map<String, Long> = emptyMap(),
        nowMillis: Long = 0L,
    ) {
        val sections =
            buildManagePacksSections(rows, usage = usage, targetId = "", locale = Locale.ENGLISH)
        compose.setContent {
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = sections,
                    storage = StorageCard.FreeOnly(packCount = rows.size, freeBytes = 8L * 1024 * 1024 * 1024),
                    nudge = null,
                    suggestions = emptyList(),
                    capable = 59,
                    total = 194,
                    usageAsSource = usageAsSource,
                    usageAsTarget = usageAsTarget,
                    nowMillis = nowMillis,
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

    // ── the width gate ─────────────────────────────────────────────────────────

    /**
     * At EXPANDED width the two-pane draws, so the detail pane is present.
     *
     * Mutation (decided first): delete the `windowInfo.isExpanded ->` arm of the
     * `when` in `ManagePacksContent` so every width falls through to the single
     * pane. The detail then never mounts and this reddens — while the paired compact
     * test below stays green, which is what makes the two together a GATE and not
     * just "a detail exists".
     */
    @Test
    fun `at expanded width the detail pane is shown`() {
        show(twoPacks())

        compose.onNodeWithTag("tt_manage_detail").assertIsDisplayed()
    }

    /**
     * At COMPACT width the phone's single pane draws and there is NO detail.
     *
     * Mutation (decided first): drop the width guard so the two-pane is
     * unconditional (e.g. `windowInfo.isExpanded ->` becomes `true ->`). The detail
     * then mounts on a phone and this reddens — the 20d two-pane leaking onto a
     * screen with no room for it.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `at compact width there is no detail pane`() {
        show(twoPacks())

        compose.onNodeWithTag("tt_manage_detail").assertDoesNotExist()
    }

    // ── the per-role "source" line (fed by #122), honest per role ───────────────

    /**
     * The detail's source line shows the store's REAL date; a role with no stamp
     * shows the honest date-less line, never a fabricated date (ruling ⑧).
     *
     * German was translated FROM three days ago and never INTO. Two mutations this
     * reddens under, decided first:
     * - honesty: `packRoleUsage` (or the pane) fabricates a date for the empty
     *   target role — the target line reads a date instead of `manage_used_never`;
     * - wiring: the pane ignores `usageAsSource` (reads the empty target map, or a
     *   constant NoRecord) — the source line reads `manage_used_never` instead of
     *   "used 3 days ago".
     */
    @Test
    fun `the detail source line shows the real date and a date-less role shows none`() {
        val now = 100L * DAY_MILLIS
        show(
            rows = listOf(OfflineLanguageRow("de", "German", OfflineModelState.Downloaded)),
            usage = mapOf("de" to now - 3 * DAY_MILLIS),
            usageAsSource = mapOf("de" to now - 3 * DAY_MILLIS),
            usageAsTarget = emptyMap(),
            nowMillis = now,
        )

        // The rev5 20d line combines role + bucket ("As source · used 3 days ago"), so
        // the honesty mutation (a fabricated date on the empty target role) still reddens
        // the target assertion below.
        compose
            .onNodeWithTag("tt_manage_detail_source", useUnmergedTree = true)
            .assertTextEquals(
                context.getString(
                    R.string.manage_detail_role_line,
                    context.getString(R.string.manage_detail_role_source),
                    context.resources.getQuantityString(R.plurals.manage_used_days, 3, 3),
                ),
            )
        compose
            .onNodeWithTag("tt_manage_detail_target", useUnmergedTree = true)
            .assertTextEquals(
                context.getString(
                    R.string.manage_detail_role_line,
                    context.getString(R.string.manage_detail_role_target),
                    context.getString(R.string.manage_used_never),
                ),
            )
    }

    // ── selection drives the detail ─────────────────────────────────────────────

    /**
     * A tap on a list row makes the detail show THAT pack.
     *
     * The default is the first row (German); a tap on the second (Spanish) must move
     * the detail to Spanish. Mutation (decided first): drop `onSelectPack` / never
     * write `selectedId`, so the detail is pinned to `displayed.first()`. The
     * post-tap assertion then still reads "German" and reddens — the list-detail's
     * one job, selection, gone.
     */
    @Test
    fun `selecting a list row updates the detail pane`() {
        show(twoPacks())

        compose.onNodeWithTag("tt_manage_detail_name", useUnmergedTree = true).assertTextEquals("German")

        compose.onAllNodesWithTag("tt_manage_select_row")[1].performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("tt_manage_detail_name", useUnmergedTree = true).assertTextEquals("Spanish")
    }
}
