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
 * Shell smoke (D-5 rev.3 + issue #74): Home is the card stack with NO bottom
 * bar; the app DRAWER exists behind Home's menu button (closed by default,
 * opens on tap). Runs against the §1.6 FakeTranslateModule (no real engine on
 * any test path).
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
    fun home_hasNoBottomBarAndDrawerStartsClosed() {
        compose.onNodeWithTag("tt_app_nav_home").assertDoesNotExist()
        // PR-75 lens O2: the old "no drawer" assertion passed only because a
        // CLOSED ModalDrawerSheet is unplaced — assert the real contract instead:
        // closed by default, OPENS from Home's menu button.
        compose.onNodeWithTag("tt_app_drawer").assertDoesNotExist()
        compose.onNodeWithTag("tt_home_menu").performClick()
        compose.onNodeWithTag("tt_app_drawer").assertIsDisplayed()
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
}
