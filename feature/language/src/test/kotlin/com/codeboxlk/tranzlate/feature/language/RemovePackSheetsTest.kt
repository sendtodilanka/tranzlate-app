package com.codeboxlk.tranzlate.feature.language

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
 * Sheets 19f and 19g, RENDERED (#186's Compose runtime in this module, #130 PR-19).
 *
 * ## What a source-shape test could not catch here
 *
 * The mutation this file is written against is [MobileDataSheetTest]'s: swap the
 * two actions' lambdas and leave both labels and both testTags exactly where
 * they are. Every check available without a renderer stays green, and the
 * shipped behaviour is that "Cancel" deletes the user's pack. So each test finds
 * a node BY TAG, asserts the label ON that node, and asserts which callback it
 * fires — and records both callbacks, because a sheet that fired both on one tap
 * would pass a test that only looked for the one it wanted.
 *
 * ## And the tests that are about the COPY
 *
 * The export's 19g says the target switches to English and that saved phrases
 * need a connection to reopen. Both are false about this app (see
 * [RemoveInUseSheet]), so two tests here assert their absence. They look
 * peculiar — a test that a sentence is NOT on screen — and they are the point of
 * the PR: without them, "fix the copy" is a commit message rather than a
 * property, and the drawn wording comes back the next time someone builds from
 * the frames.
 *
 * ## The limit, named because a mutation found it rather than because it was
 * ## anticipated
 *
 * Dropping `tone = TranzlateSheetTone.Loss` from 19g's confirm action — "Remove
 * anyway" drawn as an ordinary primary button instead of the error-filled one
 * spec §5 reserves for loss and stopping — **survives every test in this file**,
 * and no rewrite of them fixes it. Colour is not in a semantics tree:
 * `assertTextEquals` and `performClick` read what TalkBack reads, and TalkBack
 * has no opinion about a container colour. It is the same limit
 * `MobileDataSheetTest` records for its checkbox glyph, for the same reason.
 *
 * It is checked on the device screenshots in the PR body instead, and named here
 * so a green suite is not mistaken for a claim about the button's colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RemovePackSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private var removals = 0
    private var dismissals = 0

    private fun showRemoveSheet(savedCount: Int = 0) {
        compose.setContent {
            TranzlateTheme {
                RemovePackSheet(
                    visible = true,
                    languageName = "Spanish",
                    savedCount = savedCount,
                    onRemove = { removals++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showInUseSheet(savedCount: Int) {
        compose.setContent {
            TranzlateTheme {
                RemoveInUseSheet(
                    visible = true,
                    languageName = "Spanish",
                    savedCount = savedCount,
                    onRemoveAnyway = { removals++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()
    }

    // ---- 19f ------------------------------------------------------------------------------------

    @Test
    fun `19f names the language in its title`() {
        showRemoveSheet()

        compose.onNodeWithText("Remove Spanish?").assertExists()
    }

    @Test
    fun `19f's filled action says Remove and removes`() {
        showRemoveSheet()

        compose.onNodeWithTag(TT_SHEET_REMOVE_CONFIRM).assertTextEquals("Remove")
        compose.onNodeWithTag(TT_SHEET_REMOVE_CONFIRM).performClick()

        assertThat(removals).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `19f's text action says Cancel and removes nothing`() {
        showRemoveSheet()

        compose.onNodeWithTag(TT_SHEET_REMOVE_CANCEL).assertTextEquals("Cancel")
        compose.onNodeWithTag(TT_SHEET_REMOVE_CANCEL).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(removals).isEqualTo(0)
    }

    /**
     * The verb in the button matches the verb in the title — the export's own
     * caption for this frame, and the reason "Delete" appears on neither.
     */
    @Test
    fun `19f uses one verb for the title and the button`() {
        showRemoveSheet()

        compose.onNodeWithText("Remove Spanish?").assertExists()
        compose.onNodeWithTag(TT_SHEET_REMOVE_CONFIRM).assertTextEquals("Remove")
    }

    /** #230: at zero the reassurance line is absent — never a "0 saved" row. */
    @Test
    fun `19f draws no saved line when nothing is saved`() {
        showRemoveSheet(savedCount = 0)

        compose.onNodeWithTag(TT_SHEET_REMOVE_SAVED).assertDoesNotExist()
    }

    /**
     * #230: the reassurance line is on 19f too. Plural: "3 saved phrases use
     * Spanish", never "3 saved phrase".
     *
     * Mutation decided first (rule 11): drop `supportingContent = {
     * SavedPhrasesLine(...) }` from `RemovePackSheet`. The line is then drawn at
     * no count, so both the tag and the sentence redden.
     */
    @Test
    fun `19f reads the saved count in the plural`() {
        showRemoveSheet(savedCount = 3)

        compose.onNodeWithTag(TT_SHEET_REMOVE_SAVED).assertExists()
        compose
            .onNodeWithText(
                "3 saved phrases use Spanish. They stay saved and still open without a connection.",
            ).assertExists()
    }

    /** #230: the singular, where a plain `%1$d` string would misread "1 saved phrases". */
    @Test
    fun `19f reads a single saved phrase in the singular`() {
        showRemoveSheet(savedCount = 1)

        compose
            .onNodeWithText(
                "1 saved phrase uses Spanish. It stays saved and still opens without a connection.",
            ).assertExists()
    }

    // ---- 19g ------------------------------------------------------------------------------------

    @Test
    fun `19g's filled action says Remove anyway and removes`() {
        showInUseSheet(savedCount = 3)

        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CONFIRM).assertTextEquals("Remove anyway")
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CONFIRM).performClick()

        assertThat(removals).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun `19g's text action says Cancel and removes nothing`() {
        showInUseSheet(savedCount = 3)

        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CANCEL).assertTextEquals("Cancel")
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CANCEL).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(removals).isEqualTo(0)
    }

    /** Plural: "3 saved phrases use Spanish", never "3 saved phrase". */
    @Test
    fun `19g reads the saved count in the plural`() {
        showInUseSheet(savedCount = 3)

        compose
            .onNodeWithText(
                "3 saved phrases use Spanish. They stay saved and still open without a connection.",
            ).assertExists()
    }

    /**
     * The singular. A `%1$d` inside a plain string would render "1 saved
     * phrases" here, which is the bug the plural resource exists to prevent.
     */
    @Test
    fun `19g reads a single saved phrase in the singular`() {
        showInUseSheet(savedCount = 1)

        compose
            .onNodeWithText(
                "1 saved phrase uses Spanish. It stays saved and still opens without a connection.",
            ).assertExists()
    }

    /** Zero is drawn as absence — never "0 saved phrases use Spanish". */
    @Test
    fun `19g draws no line at all when nothing is saved`() {
        showInUseSheet(savedCount = 0)

        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_SAVED).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CONFIRM).assertExists()
    }

    /**
     * **The correction, as a property.** The export draws *"Removing it switches
     * the target to English."* Nothing in this app switches a language selection
     * when a pack is removed, so the word must not be on the sheet — in any
     * form, naming any language.
     */
    @Test
    fun `19g promises no target switch`() {
        showInUseSheet(savedCount = 3)

        compose.onNodeWithText("switches", substring = true).assertDoesNotExist()
        compose.onNodeWithText("switch", substring = true).assertDoesNotExist()
        compose
            .onNodeWithText(
                "It is your target language, and it stays your target. " +
                    "Translations into Spanish will need a connection until you download it again.",
            ).assertExists()
    }

    /**
     * The other half. The export draws *"They stay saved and will need a
     * connection to reopen."* Reopening a saved phrase needs no connection:
     * `TextViewModel.onHistoryPick` shows the stored answer without calling an
     * engine. The sheet may not tell the user otherwise.
     */
    @Test
    fun `19g promises no connection is needed to reopen a saved phrase`() {
        showInUseSheet(savedCount = 3)

        compose.onNodeWithText("connection to reopen", substring = true).assertDoesNotExist()
        compose.onNodeWithText("still open without a connection", substring = true).assertExists()
    }

    // ---- both -----------------------------------------------------------------------------------

    /** Nothing asked, nothing drawn — the state the screen is in almost always. */
    @Test
    fun `an unasked question draws neither sheet`() {
        compose.setContent {
            TranzlateTheme {
                RemovePackSheet(
                    visible = false,
                    languageName = "Spanish",
                    savedCount = 3,
                    onRemove = { removals++ },
                    onDismiss = { dismissals++ },
                )
                RemoveInUseSheet(
                    visible = false,
                    languageName = "Spanish",
                    savedCount = 3,
                    onRemoveAnyway = { removals++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(TT_SHEET_REMOVE_CONFIRM).assertDoesNotExist()
        compose.onNodeWithTag(TT_SHEET_REMOVE_IN_USE_CONFIRM).assertDoesNotExist()
    }
}
