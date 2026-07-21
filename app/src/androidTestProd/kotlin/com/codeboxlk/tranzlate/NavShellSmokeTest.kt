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
 * Scaffold smoke: nav shell renders, toggle-filtered destinations navigate.
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
    fun navShell_showsTextPlaceholderByDefault() {
        compose.onNodeWithTag("tt_text_placeholder").assertIsDisplayed()
    }

    @Test
    fun navShell_navigatesToSettings() {
        compose.onNodeWithTag("tt_app_nav_settings").performClick()
        compose.onNodeWithTag("tt_settings_placeholder").assertIsDisplayed()
    }
}
