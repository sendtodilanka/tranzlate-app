package com.codeboxlk.tranzlate.feature.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sheet 18a-confirm, RENDERED (#186's Compose runtime, #130 PR-21).
 *
 * Same shape of test as `MobileDataSheetTest`: the mutation it exists for is the
 * one no source-shape check catches — swap the confirm/dismiss lambdas and leave
 * both labels and both tags where they are, and "Not now" downloads while
 * "Download and use" backs out. Clicking a node by its TAG and asserting the
 * LABEL on it and the callback it fires is the only thing that separates them.
 *
 * It also pins the size line at the measured **40–65 MB** rather than the export's
 * pre-#219 "20–45 MB": a rendered assertion is the one that reads what actually
 * reaches the user, not the string constant a diff would rubber-stamp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DownloadConfirmSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var confirms = 0
    private var dismissals = 0

    private fun showSheet(name: String? = "Spanish") {
        compose.setContent {
            TranzlateTheme {
                DownloadConfirmSheet(
                    languageName = name,
                    onConfirm = { confirms++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the title names the language`() {
        showSheet("Spanish")

        compose.onNodeWithText("Download Spanish").assertIsDisplayed()
    }

    /**
     * 40, not 20 — the measured on-disk footprint (#219), not the frame's guess.
     * The substring keeps this ASCII, and the digit is the whole difference.
     */
    @Test
    fun `the size line states the measured pack size`() {
        showSheet()

        compose.onNodeWithText("A language pack is usually 40", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the filled action says Download and use and confirms`() {
        showSheet()

        compose.onNodeWithTag(TT_SHEET_CONFIRM_DOWNLOAD).assertTextEquals("Download and use")
        compose.onNodeWithTag(TT_SHEET_CONFIRM_DOWNLOAD).performClick()

        assertThat(confirms).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `the text action says Not now and dismisses`() {
        showSheet()

        compose.onNodeWithTag(TT_SHEET_CONFIRM_NOT_NOW).assertTextEquals("Not now")
        compose.onNodeWithTag(TT_SHEET_CONFIRM_NOT_NOW).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(confirms).isEqualTo(0)
    }

    /** A name that has not resolved draws nothing — a sheet titled "Download " is worse than none. */
    @Test
    fun `a null name draws no sheet`() {
        showSheet(name = null)

        compose.onNodeWithTag(TT_SHEET_CONFIRM).assertDoesNotExist()
    }
}
