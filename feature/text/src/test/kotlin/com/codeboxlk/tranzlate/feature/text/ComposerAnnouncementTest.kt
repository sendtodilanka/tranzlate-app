package com.codeboxlk.tranzlate.feature.text

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.ModeId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The three C-4 announcements the composer makes, checked in the semantics tree the
 * accessibility service actually reads (#186).
 *
 * ## Why this file exists
 *
 * `a11y_translating` was the subject of #174: the string shipped in three locales with
 * zero call sites, so a TalkBack user who tapped Translate heard nothing until the outcome
 * landed. `a11y_result_ready` and `a11y_error` were "wired" long before that and, as #186
 * records, **had never been verified by anything at all**.
 *
 * The strongest tool available when #174 was fixed was a source-shape assertion —
 * `KonsistArchitectureTest`'s "the translating state announces that it started", which
 * matches the function's own text for `liveRegion` and the string reference. That test
 * **stays**, and its KDoc now records which of its four cases this file supersedes and
 * which it does not. The short version: it still catches a translating branch that
 * renders no announcing face at all, and it cannot fail when the code says `liveRegion`
 * and the semantics tree does not carry one — the #193 defeat, which is a two-line
 * refactor away and which this file does catch.
 *
 * ## What is asserted, and why not the English words
 *
 * Every expectation is read back out of the SAME string resource the composable uses.
 * Hard-coding "Translating…" would make the test a second, unowned copy of product copy,
 * which is exactly the authority problem C-3 exists to prevent — and it would go red on a
 * `fil`/`pt-rBR` device while the app behaved perfectly.
 */
@RunWith(RobolectricTestRunner::class)
// Fixed window: the composer chooses between three chrome fits by measured height
// (`composerFitFor`), and on a small default screen the MINIMAL fit renders a different
// tree. Pinning the window keeps these tests about announcements rather than about layout.
@Config(qualifiers = "w411dp-h891dp")
class ComposerAnnouncementTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val request =
        TranslateRequest(
            text = "Good morning",
            sourceLang = "en",
            targetLang = "es",
            mode = ModeId.AUTO,
        )

    private fun show(state: TextUiState) {
        compose.setContent {
            TranzlateTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Composer(state)
                }
            }
        }
    }

    @Composable
    private fun Composer(state: TextUiState) {
        ComposerPaneContent(
            input = request.text,
            sourceLangId = "en",
            targetLangId = "es",
            uiState = state,
            onInputChange = {},
            onTranslate = { true },
            onRetry = {},
            onSwapLanguages = { true },
            onPickLanguage = {},
            onBack = {},
            onNotify = {},
            onClearAll = {},
            modifier = Modifier,
        )
    }

    /**
     * A live region is a semantics PROPERTY, not a call. Matching on the property is what
     * makes this immune to the #193 family of defeats: an aliased import, a wrapper
     * function or a helper extracted out of the composable all leave this value exactly
     * where it was, and computing the modifier into an unused local removes it.
     */
    private fun hasLiveRegion(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

    private fun hasAnnouncement(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    // ---- the three announcements, as reusable checks -------------------------
    //
    // Extracted because each one has TWO render sites, not one. `grep -c
    // 'TranslatingFace()'` = 2: the portrait read face inside `ComposerReadBody`, and
    // `ResultPane`, which the permanent two-pane shape (#56) draws instead. Rule 11's
    // first cause is #146 converting 2 of 6 call sites; covering the portrait one and
    // calling the announcement verified would be that same miss. The only difference
    // between the two runs is the window, so the assertions are written once.

    /**
     * #174, as behaviour rather than as source text.
     *
     * Polite by ruling (#174 plan §3): this fires while TalkBack is still reading the
     * Translate control's own label, and Assertive would talk over it — so the MODE is
     * asserted, not merely the presence of some region.
     */
    private fun checkTranslatingAnnouncement() {
        show(TextUiState.Translating(request))

        compose
            .onNodeWithTag("tt_text_loading", useUnmergedTree = true)
            .assert(hasLiveRegion(LiveRegionMode.Polite))
            .assert(hasAnnouncement(context.getString(R.string.a11y_translating)))
    }

    /**
     * `a11y_result_ready`, verified for the first time.
     *
     * The announcement is FORMATTED with the translated text (#76): TalkBack hears
     * "Translation ready: …" while a sighted user sees only the text, so asserting the
     * bare template would pass on a node that announces the wrong thing.
     */
    private fun checkResultAnnouncement() {
        val translated = "Buenos días"
        show(
            TextUiState.Result(
                request = request,
                translatedText = translated,
                transliteration = null,
                engine = Engine.OFFLINE_MLKIT,
            ),
        )

        compose
            .onNodeWithTag("tt_text_result", useUnmergedTree = true)
            .assert(hasLiveRegion(LiveRegionMode.Polite))
            .assert(hasAnnouncement(context.getString(R.string.a11y_result_ready, translated)))
    }

    /**
     * `a11y_error`, verified for the first time.
     *
     * Assertive here and Polite above is the contract's own distinction (§2.3): a failure
     * ENDS the task and is allowed to interrupt, while "it started" is not. A test that
     * accepted any live region would let those two swap without noticing.
     *
     * The region lives on the message LEAF inside `ErrorCard`, not on the card container —
     * a #104 lens catch, because on the container TalkBack read the failure twice. So the
     * assertion walks to the announcing node by its value rather than by the container tag.
     */
    private fun checkErrorAnnouncement() {
        show(TextUiState.Error(request = request, cause = AttemptCause.OFFLINE))

        val expected =
            context.getString(
                R.string.a11y_error,
                context.getString(R.string.text_error_offline),
            )

        compose
            .onNode(hasAnnouncement(expected), useUnmergedTree = true)
            .assert(hasLiveRegion(LiveRegionMode.Assertive))
    }

    // ---- render site 1: the portrait read face -------------------------------

    @Test
    fun `the translating face announces politely that work started`() = checkTranslatingAnnouncement()

    @Test
    fun `a finished translation announces itself with the result text`() = checkResultAnnouncement()

    @Test
    fun `a failed translation announces the reason assertively`() = checkErrorAnnouncement()

    // ---- render site 2: the permanent two-pane result pane -------------------
    //
    // `permanentTwoPane` is `expandedWidth && !compactHeight`, so the window has to be
    // wide AND tall; a landscape phone is expanded-ish and compact-height, which takes
    // the `splitResultOnly` branch instead and would silently test the wrong thing.

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the two-pane translating face announces politely too`() = checkTranslatingAnnouncement()

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the two-pane result announces itself with the result text`() = checkResultAnnouncement()

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the two-pane failure announces the reason assertively`() = checkErrorAnnouncement()
}
