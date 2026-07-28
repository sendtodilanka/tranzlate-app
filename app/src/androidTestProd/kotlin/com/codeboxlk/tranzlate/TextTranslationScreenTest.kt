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
 * TEST_A11Y_CONTRACT §1.9 against screen 5a (issue #46): Home is no longer
 * editable — it carries only the preview card, and typing happens on the
 * composer the card opens. So every case starts by opening 5a.
 *
 * §1.7 row 2 still holds inside the composer: with a blank input the action slot
 * IS the mic — the translate affordance does not exist, so a tap cannot start a
 * translation. Golden data via the §1.6 test wiring (FakeTranslateModule
 * replaces the prod TranslateModule).
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

    /** Home only previews; the editable field lives on 5a. */
    private fun openComposer() {
        compose.onNodeWithTag("tt_text_card").performClick()
        compose.waitUntil(RESULT_TIMEOUT_MS) {
            compose.onAllNodesWithTag("tt_text_input").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun typeThenTranslate_showsGoldenResult() {
        openComposer()
        compose.onNodeWithTag("tt_text_input").performTextInput("Good morning")
        // C-5 exact literal — spaced per the approved design ("0 / 5000" style).
        compose.onNodeWithTag("tt_text_counter").assertTextEquals("12 / 500")

        compose.onNodeWithTag("tt_text_translate_btn").performClick()

        compose.waitUntil(RESULT_TIMEOUT_MS) {
            compose.onAllNodesWithTag("tt_text_result").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tt_text_result").assertTextEquals("Bonjour (fake)") // G2 exact golden
        // The result is a tonal card, and the read face drops the editing
        // affordances entirely (owner: mic/Translate exist only while editing).
        compose.onNodeWithTag("tt_text_result_card").assertIsDisplayed()
        compose.onNodeWithTag("tt_text_translate_btn").assertDoesNotExist()
        compose.onNodeWithTag("tt_text_mic").assertDoesNotExist()
        compose.onNodeWithTag("tt_text_counter").assertDoesNotExist()
    }

    @Test
    fun blankInput_actionIsMicAndFiresNoTranslation() {
        openComposer()

        compose.onNodeWithTag("tt_text_translate_btn").assertDoesNotExist()
        compose.onNodeWithTag("tt_text_mic").performClick()

        compose.onNodeWithTag("tt_text_input").assertIsDisplayed() // still editing on 5a
        compose.onAllNodesWithTag("tt_text_loading").assertCountEquals(0)
        compose.onAllNodesWithTag("tt_text_result").assertCountEquals(0)
    }

    /** Requirement D: leaving 5a discards the draft — Home never shows old text. */
    @Test
    fun backFromComposer_clearsTheDraft() {
        openComposer()
        compose.onNodeWithTag("tt_text_input").performTextInput("Good morning")
        compose.onNodeWithTag("tt_composer_back").performClick()

        compose.waitUntil(RESULT_TIMEOUT_MS) {
            compose.onAllNodesWithTag("tt_home_input_preview").fetchSemanticsNodes().isNotEmpty()
        }
        openComposer()
        compose.onNodeWithTag("tt_text_counter").assertTextEquals("0 / 500")
    }
}
