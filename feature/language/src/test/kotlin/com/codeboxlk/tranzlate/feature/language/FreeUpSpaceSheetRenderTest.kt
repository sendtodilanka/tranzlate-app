package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * The 20e "Free up space" cleanup sheet as it renders and as the screen wires it
 * (#130 PR-25). The pure stale-SELECTOR ([stalePacks]) and the batch-remove
 * VM path ([OfflineLanguagesViewModel.removePacks]) are pinned in
 * `ManagePacksModelTest` / `OfflineLanguagesViewModelTest`; what only a render test
 * can reach lives here — that every stale pack starts CHECKED, that the checks
 * survive a process death (`rememberSaveable`), that Remove carries the CHECKED
 * ids, that the nudge's "Review N packs" opens the sheet, and that a date-less pack
 * never appears in it.
 *
 * Each test names the mutation it would fail under, decided before it was written
 * (rule 11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FreeUpSpaceSheetRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun daysAgo(days: Long): Long = NOW - days * DAY_MILLIS

    private fun stale(
        id: String,
        name: String,
        monthsAgo: Int,
        state: OfflineModelState = OfflineModelState.Downloaded,
    ) = PackRow(
        id = id,
        displayName = name,
        state = state,
        usage = PackUsage.MonthsAgo(monthsAgo),
        lastUsedMillis = daysAgo(monthsAgo * 30L),
        inUse = false,
        isPivot = false,
    )

    private val twoStale = listOf(stale("de", "German", 4), stale("pl", "Polish", 6))

    private fun showSheet(
        stalePacks: List<PackRow> = twoStale,
        onRemovePacks: (List<String>) -> Unit = {},
    ) {
        compose.setContent {
            TranzlateTheme {
                FreeUpSpaceSheet(
                    visible = true,
                    stalePacks = stalePacks,
                    storage = null,
                    onRemovePacks = onRemovePacks,
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Every stale pack is listed AND starts checked — the pre-selection the brief
     * pins. Mutation decided first: seed the [rememberSaveable] selection empty
     * (`mutableStateListOf()` instead of all ids) — both rows render OFF and the two
     * `assertIsOn` reddens.
     */
    @Test
    fun `every stale pack is listed and pre-checked`() {
        showSheet()

        compose.onNodeWithTag(freeRowTag("de")).assertIsOn()
        compose.onNodeWithTag(freeRowTag("pl")).assertIsOn()
    }

    /**
     * A process death mid-cleanup must not undo the user's choices. Uncheck one, kill
     * and restore, and it stays unchecked.
     *
     * Mutation decided first (rule 11): change the selection from `rememberSaveable`
     * to plain `remember` — the restore then re-runs the initializer, every box comes
     * back checked, and the final `assertIsOff` reddens. `StateRestorationTester`
     * drives the same save/restore the framework does on a real kill.
     */
    @Test
    fun `unchecking a pack survives process death`() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            TranzlateTheme {
                FreeUpSpaceSheet(
                    visible = true,
                    stalePacks = twoStale,
                    storage = null,
                    onRemovePacks = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(freeRowTag("de")).assertIsOn().performClick()
        compose.onNodeWithTag(freeRowTag("de")).assertIsOff()

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        // The un-tick survived the kill; the pack the user KEPT ticked did too.
        compose.onNodeWithTag(freeRowTag("de")).assertIsOff()
        compose.onNodeWithTag(freeRowTag("pl")).assertIsOn()
    }

    /**
     * Remove carries EVERY still-checked pack to the batch. Uncheck "de", tap Remove,
     * and only "pl" — the one left checked — is handed over.
     *
     * Mutation decided first: have the primary action pass `selectedIds.take(1)` (or
     * the whole stale list ignoring the checks) — the captured list then differs from
     * `[pl]` and this reddens. Proves the sheet reads the live selection, not a fixed
     * list.
     */
    @Test
    fun `Remove hands the batch every checked pack`() {
        val removed = mutableListOf<List<String>>()
        showSheet(onRemovePacks = { removed += it })

        compose.onNodeWithTag(freeRowTag("de")).performClick() // uncheck de
        compose.waitForIdle()
        compose.onNodeWithTag(TT_SHEET_FREE_REMOVE).performClick()
        compose.waitForIdle()

        assertThat(removed).containsExactly(listOf("pl"))
    }

    /**
     * With nothing checked there is nothing to remove, so Remove is disabled — never
     * an enabled button that deletes a batch of zero. Mutation: drop `enabled =
     * selectedCount > 0` and the button stays enabled, reddening `assertIsNotEnabled`.
     */
    @Test
    fun `Remove is disabled when every pack is unchecked`() {
        showSheet()

        compose.onNodeWithTag(freeRowTag("de")).performClick()
        compose.onNodeWithTag(freeRowTag("pl")).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TT_SHEET_FREE_REMOVE).assertIsNotEnabled()
    }

    /**
     * The nudge's "Review N packs" opens the sheet, and a date-less pack the nudge
     * never counted is not in it either — proven end to end through the real
     * [stalePacks] the screen computes.
     *
     * "pl" has no usage stamp (NoRecord); "de" is 120 days stale. The nudge counts 1,
     * its action opens the sheet, the sheet lists "de" and NOT "pl".
     *
     * Mutation decided first: point the nudge's `onReviewPacks` at `{}` — the sheet
     * never opens and `TT_SHEET_FREE`'s `assertExists` reddens. Separately, widening
     * [stalePacks] to include date-less packs makes the "pl" `assertDoesNotExist`
     * redden.
     */
    @Test
    fun `the nudge Review action opens the sheet over only the stale packs`() {
        val onDevice =
            listOf(
                OfflineLanguageRow("de", "German", OfflineModelState.Downloaded),
                OfflineLanguageRow("pl", "Polish", OfflineModelState.Downloaded),
            )
        val sections =
            buildManagePacksSections(
                rows = onDevice,
                usage = mapOf("de" to daysAgo(120)), // pl omitted → NoRecord
                targetId = "",
                nowMillis = NOW,
                locale = Locale.ENGLISH,
            )
        val staleRows = stalePacks(sections.onDevice, NOW)

        compose.setContent {
            TranzlateTheme {
                ManagePacksContent(
                    loading = false,
                    sections = sections,
                    storage = null,
                    nudge = hygieneNudge(sections.onDevice, NOW),
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
                    stalePacks = staleRows,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TT_SHEET_FREE).assertDoesNotExist()

        compose.onNodeWithTag("tt_manage_nudge_review").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TT_SHEET_FREE).assertExists()
        compose.onNodeWithTag(freeRowTag("de")).assertExists()
        compose.onNodeWithTag(freeRowTag("pl")).assertDoesNotExist()
    }

    private companion object {
        const val NOW: Long = 1_800_000_000_000L
    }
}
