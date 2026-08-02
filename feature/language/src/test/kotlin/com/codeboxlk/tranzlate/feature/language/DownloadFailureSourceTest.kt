package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * **One failure map, and only one** — the rule the rev3 ruling's REJECT §7.8
 * states ("gate / failure-map / string-set තුන්වැනි copy — bounce at review")
 * and #175 is the bill for.
 *
 * ## Why a source rule rather than a behaviour test
 *
 * The compiler already stops the OLD keys coming back: `text_lang_error_*` and
 * `offline_error_*` no longer exist, so a screen reaching for them does not
 * build. What the compiler cannot stop is the way the second copy actually
 * arrived — a screen writing a fresh `when` over `OfflineModelFailure` into
 * fresh keys of its own. That compiles, ships, and reads as an ordinary line in
 * review; it is what `OfflineLanguagesScreen` did, and every gate in the
 * repository stayed green for the life of it.
 *
 * So this reads the two screens as text and refuses the SHAPE. Honest about
 * being a source rule, in the terms `PickerHostAgnosticTest` uses: it does not
 * prove the two screens agree — `PackFailureCopyTest` renders both failed rows
 * and compares the sentences, which is the claim. This one stops the shape that
 * makes them disagree from being written in the first place.
 *
 * ## What it deliberately allows
 *
 * Naming a cause is fine; BRANCHING on one is not. Both screens' previews build
 * a `Failed(OfflineModelFailure.STORAGE)` row and must go on doing so — a
 * preview set with no failed row would breach rule 7. The rule therefore matches
 * a cause followed by `->`, which is a `when` branch and nothing else.
 *
 * ## The limit, named rather than left to be discovered
 *
 * A THIRD file — a new screen, or a helper beside these two — could spell its
 * own `when` and this would not see it, because the rule names two paths. That
 * is deliberate: a module-wide ban would be red the day something legitimately
 * branches on a cause for a reason that is not copy (a retry policy, say), and a
 * rule that goes red on correct code gets deleted. The two files named here are
 * the two that HAVE done it. A third is a review question, and REJECT §7.8 is
 * the reviewer's instruction.
 */
class DownloadFailureSourceTest {
    /**
     * A `when` branch on a cause: one or more `OfflineModelFailure.X` constants,
     * comma-separated across any amount of whitespace, ending in an arrow.
     *
     * Matching the arrow rather than the word `when` is what makes the rule
     * survive formatting — the branch may be on one line or six, and the `when`
     * itself may be an expression body, an argument or a local `val`.
     */
    private val causeBranch =
        Regex("""OfflineModelFailure\.\w+\s*(?:,\s*(?:OfflineModelFailure\.\w+\s*,?\s*)*)?->""")

    @Test
    fun `no screen spells its own failure-cause map`() {
        SCREEN_SOURCES.forEach { path ->
            val branches = causeBranch.findAll(sourceOf(path)).map { it.value.trim() }.toList()
            assertWithMessage(
                "$path branches on a download-failure cause ${branches.size} time(s): $branches. " +
                    "That decision has ONE home — call downloadFailureCopy(cause) in " +
                    "DownloadFailure.kt (issue #175; the rev3 ruling's REJECT §7.8 bounces a " +
                    "third copy). Naming a cause to build preview data is fine; branching on " +
                    "one here is what put two different sentences on two screens.",
            ).that(branches)
                .isEmpty()
        }
    }

    /** …and both screens really do read the shared map, so the rule above is not vacuous. */
    @Test
    fun `both screens read the shared map`() {
        SCREEN_SOURCES.forEach { path ->
            assertWithMessage("$path should render its failed row through downloadFailureCopy()")
                .that(sourceOf(path))
                .contains("downloadFailureCopy(")
        }
    }

    /** The map itself is the one place that IS allowed to branch — and it must. */
    @Test
    fun `the shared map is the one place that branches`() {
        assertWithMessage("DownloadFailure.kt is the home of the cause branch and must contain one")
            .that(causeBranch.containsMatchIn(sourceOf(MAP_SOURCE)))
            .isTrue()
    }

    private fun sourceOf(path: String): String {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        return checkoutRoot.resolve(path).readText()
    }
}

private const val FEATURE_SOURCE_DIR = "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language"

private val SCREEN_SOURCES =
    listOf(
        "$FEATURE_SOURCE_DIR/LanguagePickerScreen.kt",
        "$FEATURE_SOURCE_DIR/OfflineLanguagesScreen.kt",
    )

private const val MAP_SOURCE = "$FEATURE_SOURCE_DIR/DownloadFailure.kt"
