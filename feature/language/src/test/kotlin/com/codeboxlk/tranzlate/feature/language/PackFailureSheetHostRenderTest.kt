package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `PackFailureSheetHost` as the SCREEN wires it (#240) — the join that no other
 * test in the module can see.
 *
 * `PackFailureSheetsTest` renders 19b and 19d in isolation, and `DownloadFailure`
 * unit tests prove the cause map. What escapes both is the HOST
 * (`LanguagePickerScreen.kt:410-448`): that a `PackFailureRequest` is turned into
 * exactly the right sheet, that Retry both dismisses AND re-runs the download for
 * the right id, and — the branch nobody drew a line through — that a request the
 * host cannot draw a sheet for draws nothing at all rather than crashing or
 * drawing the wrong one.
 *
 * ## What this adds over `PackFailureSheetsTest`
 *
 * `PackFailureSheetsTest` already drives `LanguagePickerContent` for two host
 * cases (`19b's Manage packs clears the sheet…`, `19d's Retry clears the sheet…`)
 * — so the plan-doc's "nothing passes a non-null packFailure into
 * LanguagePickerContent" is stale, and this file does NOT re-prove the ordering.
 * It proves the three things those two tests do not:
 *  1. **Retry carries the CORRECT id.** A two-row catalog whose FAILED row is the
 *     second one, so "retry the first / the wrong language" has a wrong answer to
 *     give; the existing test uses a one-row catalog where any id looks right.
 *  2. **Routing is exclusive.** Each request draws its own sheet AND not the
 *     other one — the cross-absence the existing tests never assert.
 *  3. **The two no-draw branches** (`name == null`, and an `Interrupted` whose
 *     cause maps to the NoSpace sheet) draw NEITHER sheet.
 *
 * ## The no-draw finding, pinned rather than endorsed
 *
 * The `Interrupted` branch guards with `sheet is DownloadFailureSheet.Interrupted`
 * (`:435`). A `PackFailureRequest.Interrupted` whose cause is `STORAGE` resolves
 * to the NoSpace sheet, fails that guard, and the host draws **nothing** — no
 * sheet, no guidance, a silent dead-end (EDGE_CASES §7). Today that state is
 * unreachable: `LanguagePickerViewModel.packFailureRequest` (`:691-708`) routes
 * `STORAGE` to `PackFailureRequest.NoSpace`, so an `Interrupted` request never
 * carries `STORAGE`. But the TYPE permits it, and the safety lives in a different
 * function — nothing at the host fails loudly if that invariant is ever broken.
 * The test below pins the CURRENT behaviour so a future change to it is a
 * conscious edit to a red test, not a silent one; the risk is written up for the
 * report, not encoded as "expected and fine".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PackFailureSheetHostRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /** Every callback the host can fire, in fire order — a Retry that also dismissed shows both. */
    private val events = mutableListOf<String>()

    /**
     * Two rows, and the FAILED one below is the SECOND (`de`) — so a mutation that
     * retries the first row, or resolves the id from the wrong place, gives "es"
     * where the test demands "de".
     */
    private val catalog =
        listOf(
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
            Language("de", "German", offlineAvailable = true, offlineDownloaded = false),
        )

    private fun showHost(
        packFailure: PackFailureRequest?,
        languages: List<Language> = catalog,
    ) {
        compose.setContent {
            TranzlateTheme {
                LanguagePickerContent(
                    target = LanguageRole.SOURCE,
                    languages = languages,
                    selectedId = "zz",
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    onBack = {},
                    packFailure = packFailure,
                    onDownload = { events += "retry:$it" },
                    onManagePacks = { events += "manage" },
                    onDismissFailure = { events += "dismiss" },
                )
            }
        }
        compose.waitForIdle()
    }

    // ---- 1. Retry fires BOTH onDismiss() AND onRetry(request.id), correct id ----------------

    /**
     * The primary #240 assertion. Retry must call `onDismiss()` then
     * `onRetry(request.id)` — both, in that order, for the id the request names.
     *
     * Mutation this reddens (proven RED→GREEN in the PR body): dropping the
     * `onDismiss()` from the retry lambda (`:440`) leaves `["retry:de"]`; retrying
     * the wrong row leaves `["dismiss", "retry:es"]`. Either misses
     * `["dismiss", "retry:de"]`.
     */
    @Test
    fun `Retry dismisses and re-runs the download for the failed id, in that order`() {
        showHost(PackFailureRequest.Interrupted(id = "de", cause = OfflineModelFailure.NETWORK))

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).performClick()

        assertThat(events).containsExactly("dismiss", "retry:de").inOrder()
    }

    // ---- 2. Routing: NoSpace -> 19b, Interrupted -> 19d, each excluding the other ------------

    /** `NoSpace` draws 19b (its Manage packs action) and not 19d. */
    @Test
    fun `NoSpace routes to the 19b sheet and not 19d`() {
        showHost(
            PackFailureRequest.NoSpace(
                freeBytes = 12L * 1024 * 1024,
                volumeBytes = 64L * 1024 * 1024 * 1024,
            ),
        )

        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertExists()
        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertDoesNotExist()
    }

    /**
     * `Interrupted` draws 19d — its Retry action AND the language it names, "German
     * did not download", resolved through the same CLDR lookup the rows use — and
     * not 19b.
     */
    @Test
    fun `Interrupted routes to the 19d sheet and not 19b`() {
        showHost(PackFailureRequest.Interrupted(id = "de", cause = OfflineModelFailure.NETWORK))

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertExists()
        compose.onNodeWithText("German did not download").assertExists()
        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertDoesNotExist()
    }

    // ---- 3. The no-draw branches ------------------------------------------------------------

    /**
     * `name == null` — the catalogue has not arrived, so the id resolves to no row
     * name. The host draws no sheet (the KDoc's "waits a frame"): the row that will
     * carry the failure is not on screen yet either. Reachable and intended, and it
     * self-heals once the catalogue lands.
     *
     * Mutation this reddens (proven in the PR body): giving `name` a fallback of
     * the raw id (`nameOf(request.id) ?: request.id`, `:433`) draws a sheet titled
     * "de did not download", so `TT_SHEET_FAILED_RETRY` appears.
     *
     * Not vacuous: `Interrupted routes to the 19d sheet` above asserts this same
     * tag PRESENT off the same finder, so an always-absent finder would fail there.
     */
    @Test
    fun `Interrupted whose name has not resolved yet draws no sheet`() {
        showHost(
            PackFailureRequest.Interrupted(id = "de", cause = OfflineModelFailure.NETWORK),
            languages = emptyList(),
        )

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertDoesNotExist()
    }

    /**
     * **The finding, pinned.** An `Interrupted` request carrying `STORAGE` resolves
     * to the NoSpace sheet, fails the host's `sheet is …Interrupted` guard (`:435`),
     * and draws NOTHING — neither 19d nor 19b. Unlike the `name == null` case this
     * does NOT self-heal: the cause never changes, so a sheet never appears.
     *
     * This asserts the CURRENT behaviour, not an endorsement of it. It is a silent
     * dead-end that the type system permits and only the ViewModel's cause→request
     * routing keeps unreachable; the report recommends narrowing the type or making
     * the host draw SOMETHING. If either fix lands, this test reddens on purpose so
     * the change is deliberate.
     */
    @Test
    fun `Interrupted carrying a STORAGE cause draws no sheet`() {
        showHost(PackFailureRequest.Interrupted(id = "es", cause = OfflineModelFailure.STORAGE))

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertDoesNotExist()
    }

    /** The resting state, and the positive control for "no sheet": a null request draws neither. */
    @Test
    fun `no failure draws no sheet`() {
        showHost(null)

        compose.onNodeWithTag(TT_SHEET_FAILED_RETRY).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_SPACE_MANAGE).assertDoesNotExist()
    }
}
