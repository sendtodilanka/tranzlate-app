package com.codeboxlk.tranzlate.feature.language

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The card as it is actually drawn (#130 PR-16) — rendered, because the three
 * things the ruling asks for tests of are all things a source rule cannot see.
 *
 * [PickerDialogWindow] is deliberately free of injection so it can be mounted
 * here; the ViewModel scope it does not carry is
 * [PickerDialogScopeTest]'s subject.
 *
 * **`BasicAlertDialog` clamps its content to 280–560dp**, and the landscape card
 * is 720dp. The reading that says the clamp is inert against fixed constraints
 * is in `PickerDialogWindow`'s comment; the two width tests below are what make
 * it evidence rather than a reading — if that clamp ever bites, the landscape
 * one goes red with the measured number in the message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp")
class PickerDialogRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private var dismissals = 0
    private var managePacks = 0
    private var open by mutableStateOf(true)

    /**
     * **Six distinct initial letters, and that is a requirement rather than
     * padding.** `AlphabetRail` refuses to draw below `MIN_RAIL_LETTERS` = 3, so
     * the first version of this fixture — Afrikaans, Albanian, Bulgarian — had
     * two letters and made `the card draws no A to Z rail` pass on a screen where
     * no arrangement draws one. Caught by running mutation M6 (`rail = true` in
     * the dialog branch) and finding this file still green.
     */
    private val catalogue =
        listOf(
            Language("af", "Afrikaans", offlineAvailable = true, offlineDownloaded = true),
            Language("sq", "Albanian", offlineAvailable = true, offlineDownloaded = false),
            Language("bg", "Bulgarian", offlineAvailable = true, offlineDownloaded = false),
            Language("ca", "Catalan", offlineAvailable = true, offlineDownloaded = false),
            Language("da", "Danish", offlineAvailable = true, offlineDownloaded = false),
            Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
            Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
            Language("de", "German", offlineAvailable = false, offlineDownloaded = false),
        )

    @Composable
    private fun ColumnScope.Picker(host: PickerHost) {
        LanguagePickerContent(
            target = LanguageRole.SOURCE,
            languages = catalogue,
            selectedId = "af",
            query = "",
            onQueryChange = {},
            onSelect = {},
            onBack = {
                dismissals++
                open = false
            },
            modifier = Modifier.weight(1f),
            host = host,
        )
    }

    /** The card with the real picker inside it — the production composition minus Hilt. */
    private fun showCard() {
        compose.setContent {
            TranzlateTheme {
                if (open) {
                    PickerDialogWindow(onDismiss = {
                        dismissals++
                        open = false
                    }) {
                        Picker(PickerHost.DIALOG)
                        PickerDialogActions(
                            onManagePacks = { managePacks++ },
                            onCancel = {
                                dismissals++
                                open = false
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The SAME catalogue in the other host — the control for the rail assertion. */
    private fun showFullScreenPicker() {
        compose.setContent {
            TranzlateTheme { Column { Picker(PickerHost.NAV_ENTRY) } }
        }
        compose.waitForIdle()
    }

    /** Nothing but the card, for the geometry tests — no catalogue to lay out. */
    private fun showEmptyCard() {
        compose.setContent {
            TranzlateTheme {
                PickerDialogWindow(onDismiss = { dismissals++ }) { Column {} }
            }
        }
        compose.waitForIdle()
    }

    // ---- geometry: the export's two measurements ----------------------------

    /** `from|to · tablet portrait` — an 800dp-wide window draws a 560dp card. */
    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `the card is 560dp wide in a tablet portrait window`() {
        showEmptyCard()

        val width = compose.onNodeWithTag("tt_lang_dialog").getUnclippedBoundsInRoot().width
        assertWithMessage("measured $width against the export's 560dp")
            .that(width.value)
            .isWithin(1f)
            .of(560f)
    }

    /**
     * `from|to · tablet landscape` — a 1280dp-wide window draws a 720dp card,
     * which is past `BasicAlertDialog`'s own 560dp maximum. This is the test that
     * turns that reading into a measurement.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the card is 720dp wide in a tablet landscape window`() {
        showEmptyCard()

        val width = compose.onNodeWithTag("tt_lang_dialog").getUnclippedBoundsInRoot().width
        assertWithMessage(
            "measured $width against the export's 720dp — if this reads 560 then " +
                "BasicAlertDialog's DialogMaxWidth clamp is NOT inert and the card needs a " +
                "plain Dialog instead",
        ).that(width.value)
            .isWithin(1f)
            .of(720f)
    }

    /** …and it never fills the window: the screen behind it is the point of the host. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `the card leaves the screen behind it visible`() {
        showEmptyCard()

        val bounds = compose.onNodeWithTag("tt_lang_dialog").getUnclippedBoundsInRoot()
        assertThat(bounds.width.value).isLessThan(1280f)
        assertThat(bounds.height.value).isAtMost(1280f * 0.78f)
    }

    // ---- dismissal: three ways out, and back is the one with no control ------

    /**
     * **Back dismisses the card.** The ruling names this test, and it is the only
     * way out that has nothing on screen to point at, so it is the one that would
     * strand the user if `dismissOnBackPress` were ever defaulted away.
     *
     * Driven through the real platform dialog Compose opened — `ComponentDialog`
     * routes `onBackPressed()` through the same dispatcher a system back gesture
     * does — rather than by calling the callback this test would otherwise be
     * asserting about.
     */
    @Test
    fun `the back press dismisses the card`() {
        showCard()

        val dialog = checkNotNull(ShadowDialog.getLatestDialog()) { "no dialog was shown" }
        compose.runOnUiThread { dialog.onBackPressed() }
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
    }

    /** The card's leading cross — a Close, not a back arrow, and it changes nothing. */
    @Test
    fun `the close control dismisses the card`() {
        showCard()

        compose.onNodeWithTag("tt_lang_back").performClick()
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
    }

    /** The docked Cancel. */
    @Test
    fun `the cancel action dismisses the card`() {
        showCard()

        compose.onNodeWithTag("tt_lang_dialog_cancel").performClick()
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
    }

    /**
     * A tap on the scrim. The platform cannot serve this one with the dialog
     * window filling the display — there is no "outside" for it to detect — so it
     * is drawn from the layer the card is centred in, and this is what pins that
     * the layer is still doing it.
     */
    @Test
    fun `a tap on the scrim dismisses the card`() {
        showCard()

        // A corner, not `performClick()`: that taps the node's CENTRE, which is
        // where the card is. The first version of this test did exactly that and
        // was asserting that the card swallows its own taps.
        compose.onNodeWithTag("tt_lang_dialog_scrim").performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(1)
    }

    /** …and the same gesture landing ON the card does not, or every row tap would close it. */
    @Test
    fun `a tap on the card itself does not dismiss it`() {
        showCard()

        compose.onNodeWithTag("tt_lang_dialog").performClick()
        compose.waitForIdle()

        assertThat(dismissals).isEqualTo(0)
    }

    // ---- the docked action --------------------------------------------------

    /**
     * "Manage packs" is a NAVIGATION, so the card must not swallow it as a
     * dismissal — the shell owns the order in which the two happen
     * (`TranzlateApp.manageLanguagePacks`, and `PickerHostRoutingTest` pins it).
     */
    @Test
    fun `the docked manage action reports itself and does not dismiss`() {
        showCard()

        compose.onNodeWithTag("tt_lang_dialog_manage").performClick()
        compose.waitForIdle()

        assertThat(managePacks).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    // ---- what the card does NOT draw ---------------------------------------

    /**
     * **No A–Z rail.** Counted off the export's markup rather than assumed: 15a
     * and 17a each draw all twenty-six letters, and the four tablet frames draw
     * none.
     *
     * The reason is the geometry the card is in — the rail is a drag target
     * pinned to the trailing edge, and in a card that edge has the scrim a few dp
     * beyond it, so a drag that slips off dismisses the thing it was scrolling.
     */
    @Test
    fun `the card draws no A to Z rail`() {
        showCard()

        compose.onNodeWithTag("tt_lang_rail").assertDoesNotExist()
    }

    /**
     * The control that stops the assertion above being about the fixture.
     *
     * The SAME catalogue in the full-screen host DOES draw the rail, so "no rail
     * in the card" is a fact about the host and not about eight languages being
     * too few for one.
     */
    @Test
    fun `the same catalogue draws a rail in the full-screen host`() {
        showFullScreenPicker()

        compose.onNodeWithTag("tt_lang_rail").assertIsDisplayed()
    }

    /** …and the control it does draw instead is still there. */
    @Test
    fun `the card keeps the permanent search field`() {
        showCard()

        compose.onNodeWithTag("tt_lang_search").assertIsDisplayed()
    }

    /**
     * **The counter is not the rail, and this test is the regression that
     * proved it.**
     *
     * The first build of this PR gated the "All languages" header on the same
     * boolean it gated the rail on, because until the card existed the two were
     * always equal. The card was the first host to want one without the other,
     * and the result — found by running the app on `emulator-5554` at 800×1280,
     * not by any test here — was that "5 of 59 packs on device" disappeared from
     * every tablet frame, in all four of which the export draws it.
     *
     * `useUnmergedTree`, because the header row merges its two texts.
     */
    @Test
    fun `the card still states how many packs are on the device`() {
        showCard()

        compose.onNodeWithTag("tt_lang_counter", useUnmergedTree = true).assertIsDisplayed()
    }
}
