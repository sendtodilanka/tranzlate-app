package com.codeboxlk.tranzlate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Shell smoke (D-5 rev.3 — Claude Design "Offline Translator M3"): Home is the
 * card stack, there is **no bottom bar and no drawer** (owner-confirmed again,
 * issue #80), and every destination is reached from the top bar or the
 * cards/rows. Runs against the §1.6 FakeTranslateModule (no real engine on any
 * test path).
 *
 * Note: the whole androidTest suite currently fails on API 35+ emulators —
 * Espresso's `onIdle` calls the removed `InputManager.getInstance` (issue #40).
 * Run these on an API ≤ 34 image.
 */
@HiltAndroidTest
class NavShellSmokeTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
    }

    @Test
    fun home_showsTheCardStack() {
        compose.onNodeWithTag("tt_text_card").assertIsDisplayed()
        compose.onNodeWithTag("tt_home_tool_offline").assertIsDisplayed()
        compose.onNodeWithTag("tt_home_tool_camera").assertIsDisplayed()
        compose.onNodeWithTag("tt_home_row_download").assertIsDisplayed()
    }

    @Test
    fun home_hasNoBottomBarAndNoDrawer() {
        // The design reaches everything from the cards/rows; a regression that
        // reintroduces either would break the approved layout (issue #80:
        // owner re-confirmed — the app has NO drawer).
        compose.onNodeWithTag("tt_app_nav_home").assertDoesNotExist()
        compose.onNodeWithTag("tt_app_drawer").assertDoesNotExist()
        compose.onNodeWithTag("tt_home_menu").assertDoesNotExist()
    }

    @Test
    fun home_settingsIconNavigatesToSettings() {
        compose.onNodeWithTag("tt_home_settings").performClick()
        // tt_settings_back is in the top bar, present before the settings load, so
        // it is the stable marker that navigation reached the Settings screen.
        compose.onNodeWithTag("tt_settings_back").assertIsDisplayed()
    }

    @Test
    fun home_cameraToolNavigatesToCamera() {
        compose.onNodeWithTag("tt_home_tool_camera").performClick()
        compose.onNodeWithTag("tt_home_tool_camera").assertDoesNotExist()
    }

    /**
     * The launch blocker this replaced: Camera opened a bare `Box` with one line of
     * centred text — no bar, no arrow, nothing but the invisible system gesture to
     * get out of it. Both halves are asserted, because "it navigated" was already
     * true when it was a dead end; the visible way back is the fix.
     */
    @Test
    fun home_cameraToolLandsOnAnHonestScreenWithAWayBack() {
        compose.onNodeWithTag("tt_home_tool_camera").performClick()

        compose.onNodeWithTag("tt_coming_soon").assertIsDisplayed()
        compose.onNodeWithTag("tt_coming_soon_message").assertIsDisplayed()
        compose.onNodeWithTag("tt_coming_soon_back").assertIsDisplayed().performClick()

        compose.onNodeWithTag("tt_home_tool_camera").assertIsDisplayed()
    }

    @Test
    fun home_conversationToolLandsOnAnHonestScreenWithAWayBack() {
        compose.onNodeWithTag("tt_home_tool_conversation").performClick()

        compose.onNodeWithTag("tt_coming_soon").assertIsDisplayed()
        compose.onNodeWithTag("tt_coming_soon_back").assertIsDisplayed().performClick()

        compose.onNodeWithTag("tt_home_tool_conversation").assertIsDisplayed()
    }
}
