package com.codeboxlk.tranzlate

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val RESULT_TIMEOUT_MS = 3_000L

/**
 * TEST_A11Y_CONTRACT §1.9 (adapted to the amended C-2 + §1.7 row-2 morph note):
 * type → tap `tt_text_translate_btn` → golden result on the Result screen; when
 * the input is blank the same action slot is the MIC (no translate affordance),
 * so a tap must fire NO translation. Golden data via the §1.6 test wiring
 * (FakeTranslateModule replaces the prod TranslateModule).
 */
@HiltAndroidTest
class TextTranslationScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
    }

    @Test
    fun typeThenTranslate_showsGoldenResult() {
        compose.onNodeWithTag("tt_text_input").performTextInput("Good morning")
        // C-5 exact literal — spaced per the approved design ("0 / 5000" style).
        compose.onNodeWithTag("tt_text_counter").assertTextEquals("12 / 500")

        compose.onNodeWithTag("tt_text_translate_btn").performClick()

        compose.waitUntil(RESULT_TIMEOUT_MS) {
            compose.onAllNodesWithTag("tt_text_result").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tt_text_result").assertTextEquals("Bonjour (fake)") // G2 exact golden
    }

    @Test
    fun blankInput_actionIsMicAndFiresNoTranslation() {
        // §1.7 row 2: blank input ⇒ the action slot IS the mic. The design swaps
        // the node itself (mic ⇄ Translate), so the translate affordance is not
        // merely disabled — it does not exist — and tapping the slot cannot
        // start a translation.
        compose.onNodeWithTag("tt_text_translate_btn").assertDoesNotExist()
        compose.onNodeWithTag("tt_text_mic").performClick()

        compose.onNodeWithTag("tt_text_input").assertIsDisplayed() // still on Home
        compose.onAllNodesWithTag("tt_text_loading").assertCountEquals(0)
        compose.onAllNodesWithTag("tt_text_result").assertCountEquals(0)
    }
}
