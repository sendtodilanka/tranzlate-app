package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The picker screen keeps no state its host owns (#130 PR-13).
 *
 * The behaviour half of this promise — that the query and the list position come
 * back after the process dies — is held by `LanguagePickerViewModelTest`. This is
 * the other half: that the screen does not ALSO keep a copy somewhere the host
 * owns, which is where the state was before this PR and where it would silently
 * return to.
 *
 * **A rule about source, and honest about being one.** It cannot run a host swap:
 * this repo has no Compose unit-test runtime (#186) and CI compiles instrumented
 * tests without running them (#40), so a test that mounted the picker in two
 * hosts would be a test nobody ever sees fail. What it does make impossible is
 * the regression that is actually likely — a later PR reaching for
 * `rememberSaveable` inside the picker because that is the obvious thing to reach
 * for, re-tying the state to whichever host is composing it and undoing this PR
 * without touching a line of it.
 *
 * Read as a named file rather than through Konsist, for the reason
 * `DownloadGateTest` gives: #163 has a Konsist gate passing locally and failing
 * in CI on the same commit, cause still undiagnosed, so a Konsist result from a
 * worktree is not evidence. One named file, read directly, answers the same
 * everywhere.
 */
class PickerHostAgnosticTest {
    @Test
    fun `the picker screen holds no host-scoped saveable state`() {
        val code = shippedPickerSource()

        // Never vacuous: a moved or renamed screen fails HERE rather than
        // satisfying every "does not contain" below by being empty.
        assertThat(code).contains("fun LanguagePickerScreen")
        assertThat(code).contains("fun LanguagePickerContent")

        listOf("rememberSaveable", "rememberLazyListState").forEach { banned ->
            assertThat(code).doesNotContain(banned)
        }
    }

    /**
     * The other half of the same rule: the position the list starts at must come
     * from the caller. `LazyListState()` with no arguments always starts at the
     * top — a restored scroll position silently thrown away, which is exactly
     * what a state-loss defect looks like from the outside.
     */
    @Test
    fun `the list is seeded from the position it was handed`() {
        assertThat(shippedPickerSource())
            .contains("LazyListState(listPosition.index, listPosition.offset)")
    }

    /**
     * The shipped BODY of the screen: imports, comments, string literals and
     * previews all removed.
     *
     * Each exclusion earns its place:
     *  - **Imports**, because the previews below legitimately import
     *    `rememberLazyListState` and an import is not a call.
     *  - **Strings**, because a testTag or a log line naming a banned token would
     *    otherwise satisfy the rule the way `"skipping shutdown() for now"`
     *    satisfied a Konsist gate in #159.
     *  - **Previews**, because they are not the shipped screen: a preview's own
     *    `rememberLazyListState()` is host-scoped by definition, and correctly so.
     */
    private fun shippedPickerSource(): String {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        val lines =
            checkoutRoot
                .resolve(PICKER_SOURCE)
                .readText()
                .lines()
        val afterImports = lines.indexOfLast { it.startsWith("import ") } + 1
        val body =
            lines
                .drop(afterImports)
                .joinToString("\n")
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("//.*"), "")
                .replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")
        val previews = body.indexOf("@PreviewLightDark")
        return if (previews == -1) body else body.take(previews)
    }
}

private const val PICKER_SOURCE =
    "feature/language/src/main/kotlin/com/codeboxlk/tranzlate/feature/language/LanguagePickerScreen.kt"
