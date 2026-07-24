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
 * Shell smoke (issue #26, D-5 rev.2): the Home tab renders on Compact WITH the
 * persistent bottom nav (Home / Chat / Camera); Chat is a coming-soon
 * placeholder; the bar hides on a secondary/detail screen; and the ☰ drawer
 * reaches secondary destinations.
 * Runs against the §1.6 FakeTranslateModule (no real engine on any test path).
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
    fun navShell_showsHomeByDefault() {
        compose.onNodeWithTag("tt_text_input").assertIsDisplayed()
        compose.onNodeWithTag("tt_text_translate_btn").assertIsDisplayed()
    }

    @Test
    fun navShell_showsBottomNavOnHome() {
        compose.onNodeWithTag("tt_app_nav_home").assertIsDisplayed()
        compose.onNodeWithTag("tt_app_nav_chat").assertIsDisplayed()
        compose.onNodeWithTag("tt_app_nav_camera").assertIsDisplayed()
    }

    @Test
    fun navShell_chatTabShowsComingSoon() {
        compose.onNodeWithTag("tt_app_nav_chat").performClick()
        compose.onNodeWithTag("tt_coming_soon").assertIsDisplayed()
    }

    @Test
    fun navShell_drawerNavigatesToSettings() {
        compose.onNodeWithTag("tt_text_menu").performClick()
        compose.onNodeWithTag("tt_app_drawer_settings").performClick()
        // tt_settings_back is in the top bar, present before the settings load, so
        // it is the stable marker that navigation reached the Settings screen.
        compose.onNodeWithTag("tt_settings_back").assertIsDisplayed()
    }

    @Test
    fun navShell_hidesBottomNavOnSecondaryScreen() {
        // D-5 rev.2: the bar is top-level-only. Settings is a secondary
        // destination (reached via the drawer), so the nav must not be present.
        compose.onNodeWithTag("tt_text_menu").performClick()
        compose.onNodeWithTag("tt_app_drawer_settings").performClick()
        compose.onNodeWithTag("tt_settings_back").assertIsDisplayed()
        compose.onNodeWithTag("tt_app_nav_home").assertDoesNotExist()
    }
}
