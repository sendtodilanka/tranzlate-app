package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sheet 19a, RENDERED (#186's Compose runtime in this module, #130 PR-17).
 *
 * ## Why this is not a source-shape test
 *
 * The mutation this file exists for is the one #197 was about: swap the two
 * actions' lambdas and leave both labels and both testTags exactly where they
 * are. Every check available without a renderer stays green — the strings are
 * all still present, in the same file, in the same order — and the shipped
 * behaviour is that "Not now" spends the user's mobile data. A test that clicks
 * a node identified by its TAG and asserts both the LABEL on that node and the
 * callback it fires is the only thing that separates the two.
 *
 * The second mutation the same test covers is the label swap — lambdas correct,
 * `Download now` moved onto the text action. Tag and label have to agree, and
 * a check that only asserts "both strings appear somewhere" cannot see it.
 *
 * All four callbacks are recorded, not just the expected one: a sheet that fired
 * BOTH on one tap would pass a test that only asserted the one it wanted.
 *
 * ## The limit, named because a mutation found it rather than because it was
 * ## anticipated
 *
 * `Checkbox(checked = !alwaysAsk, onCheckedChange = null)` — the tick drawn
 * backwards on a consent surface — **survives every test in this file**, and it
 * survives them for a reason no rewrite of them fixes. The row carries the
 * `toggleable` and therefore carries the `ToggleableState` semantics; the
 * `Checkbox` inside it is read-only (`onCheckedChange = null`) and contributes
 * no semantics at all, which is the whole point of that Material3 pattern —
 * TalkBack hears ONE checkable row rather than a box followed by loose text.
 * `assertIsOn` reads what TalkBack reads. Nothing in a semantics tree reads a
 * PIXEL, so no JVM test in this project can tell the two apart, and adding a
 * `toggleableState` of our own to the box would only add a third spelling of the
 * same value for a mutation to leave alone.
 *
 * It is checked on a device instead — the sheet screenshot in the PR body, on
 * `Resizable_Experimental` — and recorded here so the next reader knows the
 * green suite is not a claim about the glyph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MobileDataSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var downloads = 0
    private var dismissals = 0
    private val toggles = mutableListOf<Boolean>()

    private fun showSheet(alwaysAsk: Boolean = true) {
        compose.setContent {
            TranzlateTheme {
                MobileDataSheet(
                    visible = true,
                    alwaysAsk = alwaysAsk,
                    onAlwaysAskChange = { toggles += it },
                    onDownloadNow = { downloads++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the filled action says Download now and downloads`() {
        showSheet()

        compose.onNodeWithTag(TT_SHEET_DATA_DOWNLOAD).assertTextEquals("Download now")
        compose.onNodeWithTag(TT_SHEET_DATA_DOWNLOAD).performClick()

        assertThat(downloads).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `the text action says Not now and downloads nothing`() {
        showSheet()

        compose.onNodeWithTag(TT_SHEET_DATA_NOT_NOW).assertTextEquals("Not now")
        compose.onNodeWithTag(TT_SHEET_DATA_NOT_NOW).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(downloads).isEqualTo(0)
    }

    /**
     * The word the ruling's REJECT §7.8 refuses until experiment E-W1 has run.
     * Both shipped dialogs carried it while nothing in the app queued anything
     * for Wi-Fi; this asserts the promise is gone from the surface that replaced
     * them, rather than trusting that nobody types it back in.
     */
    @Test
    fun `the sheet promises no Wi-Fi wait`() {
        showSheet()

        compose.onNodeWithTag(TT_SHEET_DATA_NOT_NOW).assertTextEquals("Not now")
        compose
            .onNodeWithTag(TT_SHEET_DATA_DOWNLOAD)
            .assertTextEquals("Download now")
    }

    /**
     * Ticked means "keep asking". The box is the INVERSE of the stored
     * `allowMobileData`, so a checkbox rendered from the raw preference reads
     * exactly backwards on a consent surface — and looks right in a diff.
     */
    @Test
    fun `the box is ticked while the app is still asking`() {
        showSheet(alwaysAsk = true)

        compose.onNodeWithTag(TT_SHEET_DATA_ALWAYS_ASK).assertIsOn()
    }

    @Test
    fun `the box is clear once the answer has been made standing`() {
        showSheet(alwaysAsk = false)

        compose.onNodeWithTag(TT_SHEET_DATA_ALWAYS_ASK).assertIsOff()
    }

    /**
     * The whole row is the target (C-14's 48dp floor), and it reports the value
     * the user asked for — not a toggle of whatever the row last drew.
     */
    @Test
    fun `tapping the row asks for the opposite of what it shows`() {
        showSheet(alwaysAsk = true)

        compose.onNodeWithTag(TT_SHEET_DATA_ALWAYS_ASK).performClick()

        assertThat(toggles).containsExactly(false)
        assertThat(downloads).isEqualTo(0)
        assertThat(dismissals).isEqualTo(0)
    }

    /** Nothing open, nothing drawn — the state both screens are in almost always. */
    @Test
    fun `an unasked question draws no sheet`() {
        compose.setContent {
            TranzlateTheme {
                MobileDataSheet(
                    visible = false,
                    alwaysAsk = true,
                    onAlwaysAskChange = { toggles += it },
                    onDownloadNow = { downloads++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(TT_SHEET_DATA_DOWNLOAD).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_DATA_NOT_NOW).assertDoesNotExist()
    }
}
