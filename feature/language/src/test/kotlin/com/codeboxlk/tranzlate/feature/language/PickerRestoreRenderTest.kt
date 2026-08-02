package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One saved position means one language, across a rotation — rendered (#186, PR #198).
 *
 * ## The gap this fills, measured rather than argued
 *
 * PR #198 fixed a real shipped bug: a raw grid index was carried across a rotation, the
 * two arrangements emit a different number of items above the catalog, and the user
 * browsing at English in portrait landed on Finnish in landscape. The fix names the
 * LANGUAGE instead, and `PickerListPositionTest` pins the arithmetic — `pickerAnchorIndex`
 * with the prefix removed reddens two of its cases. That part needs no Compose runtime,
 * and this file does not duplicate it.
 *
 * What no test could reach is the WIRING. Dropping the prefix at the call site instead of
 * inside the function —
 * `pickerAnchorIndex(listPosition.anchorId, sections.visible, 0)` — reproduces the exact
 * user-visible bug, and it passed all 147 tests in this module, both Konsist gates and the
 * whole build. The only thing standing over that call was
 * `PickerHostAgnosticTest`'s `code.contains("pickerAnchorIndex(listPosition.anchorId,")`,
 * a substring the mutation leaves untouched — the #193 shape exactly.
 *
 * ## How the rotation is simulated
 *
 * `key(arrangement)` around the content. A rotation destroys the composition and builds a
 * new one, so `remember` must run again; flipping a parameter alone would keep the same
 * grid state and prove nothing about restoring. The position the FIRST composition
 * reported is what seeds the second, which is the actual path — the ViewModel holds it and
 * hands it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PickerRestoreRenderTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Deep enough that the catalog scrolls, and the anchor sits far enough down that a
     * missing prefix lands on a different language rather than clamping to the top and
     * accidentally passing.
     */
    private val catalogue =
        listOf(
            "af" to "Afrikaans",
            "sq" to "Albanian",
            "am" to "Amharic",
            "ar" to "Arabic",
            "hy" to "Armenian",
            "az" to "Azerbaijani",
            "eu" to "Basque",
            "be" to "Belarusian",
            "bn" to "Bengali",
            "bs" to "Bosnian",
            "bg" to "Bulgarian",
            "ca" to "Catalan",
            "hr" to "Croatian",
            "cs" to "Czech",
            "da" to "Danish",
            "nl" to "Dutch",
            "en" to "English",
            "et" to "Estonian",
            "fi" to "Finnish",
            "fr" to "French",
            "gl" to "Galician",
            "ka" to "Georgian",
            "de" to "German",
            "el" to "Greek",
            "gu" to "Gujarati",
            "ht" to "Haitian Creole",
            "he" to "Hebrew",
            "hi" to "Hindi",
            "hu" to "Hungarian",
            "is" to "Icelandic",
            "id" to "Indonesian",
            "ga" to "Irish",
            "it" to "Italian",
            "ja" to "Japanese",
            "kn" to "Kannada",
            "ko" to "Korean",
            "lv" to "Latvian",
            "lt" to "Lithuanian",
            "mk" to "Macedonian",
            "ms" to "Malay",
            "ml" to "Malayalam",
            "mt" to "Maltese",
            "mr" to "Marathi",
            "no" to "Norwegian",
            "fa" to "Persian",
            "pl" to "Polish",
            "pt" to "Portuguese",
            "ro" to "Romanian",
            "ru" to "Russian",
            "sr" to "Serbian",
            "sk" to "Slovak",
            "sl" to "Slovenian",
            "es" to "Spanish",
            "sw" to "Swahili",
            "sv" to "Swedish",
            "ta" to "Tamil",
            "te" to "Telugu",
            "th" to "Thai",
            "tr" to "Turkish",
            "uk" to "Ukrainian",
        ).map { (id, name) ->
            Language(id = id, name = name, offlineAvailable = true, offlineDownloaded = false)
        }

    /** Recents exist so the single-pane arrangement HAS a prefix to lose. */
    private val recents = mapOf("en" to 3L, "de" to 2L, "fr" to 1L)

    private companion object {
        /**
         * Czech — 14th of the 60 rows in the collated catalogue, so 46 sit below it.
         *
         * Both halves of that matter. Far enough DOWN that losing the 5-item prefix lands
         * on a different language rather than clamping to the top; far enough from the END
         * that the grid can actually put it at the top of the viewport. An anchor near the
         * bottom clamps, the reported language is whatever the last screenful starts with,
         * and the test fails for a reason that has nothing to do with the invariant — which
         * is what the first draft of this file did.
         */
        const val ANCHOR = "cs"
    }

    @Test
    fun `a position restored in the other arrangement still names the same language`() {
        var arrangement by mutableStateOf(PickerArrangement.SinglePane)
        var reported: PickerListPosition? = null

        compose.setContent {
            TranzlateTheme {
                key(arrangement) {
                    LanguagePickerContent(
                        target = LanguageRole.TARGET,
                        languages = catalogue,
                        selectedId = "zz",
                        query = "",
                        onQueryChange = {},
                        onSelect = {},
                        onBack = {},
                        recents = recents,
                        // Null on the first pass, then whatever the first composition
                        // reported — the round trip the ViewModel actually performs.
                        listPosition = reported ?: PickerListPosition(anchorId = ANCHOR),
                        onListPositionChange = { reported = it },
                        arrangementOverride = arrangement,
                    )
                }
            }
        }
        compose.waitForIdle()

        // Portrait: the seed put the anchor at the top of the viewport, and the grid
        // reports back the language whose key it is anchored to. Equal means the index
        // the screen computed and the index the grid used agree.
        assertThat(reported?.anchorId).isEqualTo(ANCHOR)

        // Rotate. The prefix changes — 17a moves the detect row and the recents section
        // into the side pane — so a restore that carried a NUMBER would move the user.
        arrangement = PickerArrangement(twoPane = true, columns = 1)
        compose.waitForIdle()

        assertThat(reported?.anchorId).isEqualTo(ANCHOR)
    }
}
