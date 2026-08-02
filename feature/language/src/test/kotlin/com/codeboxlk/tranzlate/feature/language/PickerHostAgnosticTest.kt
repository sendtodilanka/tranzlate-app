package com.codeboxlk.tranzlate.feature.language

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
 * **Two rules, and the first one is the load-bearing one.** The first shipped
 * version matched banned NAMES in the body, and #192's co-verify lens defeated it
 * with an aliased import — the regression back in the file, the suite fully green.
 * So the file's imports are now read as resolved symbols, which is the one place
 * an alias must still spell out what it renamed. Details on that split are on
 * `the picker imports nothing that scopes state to its host`.
 *
 * Read as a named file rather than through Konsist, for the reason
 * `DownloadGateTest` gives: #163 has a Konsist gate passing locally and failing
 * in CI on the same commit, cause still undiagnosed, so a Konsist result from a
 * worktree is not evidence. Konsist would not add resolution here either — it
 * reads declarations, not call targets — so a plain read of one named file gives
 * the same answer in CI as it does here, and owes no explanation for why.
 */
class PickerHostAgnosticTest {
    /**
     * The rule that actually holds, because it reads the IMPORT rather than the
     * call.
     *
     * The body rule below used to be the whole test, and a co-verify lens on
     * #192 walked through it in one line:
     *
     * ```
     * import androidx.compose.runtime.saveable.rememberSaveable as rememberHostCache
     * val queryHostCache = rememberHostCache { query }
     * ```
     *
     * `tests=2 failures=0` — fully green, with the exact regression PR-13 removed
     * back in the file. A text match cannot see through a rename, and the alias
     * hid in the one region [shippedPickerSource] deliberately drops.
     *
     * Nothing available here resolves a call to its declaration — Konsist does not
     * either, and `DownloadGateTest` explains why a Konsist result from a worktree
     * would not be evidence (#163). But the import statement resolves the alias for
     * free: Kotlin puts the REAL symbol on the left of `as`, so
     * `rememberSaveable as anything` still says `rememberSaveable`. Banning the
     * imported symbol therefore covers every spelling of the call, including ones
     * nobody has thought of yet.
     *
     * The two rules together close the ways in:
     *  - **imported**, aliased or not → here, on the resolved symbol;
     *  - **fully qualified at the call site**, no import → the body rule, because
     *    the qualified name contains the banned one;
     *  - **wildcard import** → the call itself must then be spelled bare, so the
     *    body rule has it.
     *
     * That is why the rail preview stopped using `rememberLazyListState()`: the
     * import list is file-wide, so the file may not carry the import at all, and a
     * preview has no state to restore.
     *
     * **The limit, named rather than left to be discovered.** A helper in a
     * SIBLING file that wraps `rememberSaveable` and is called from here would
     * pass both rules — same-package calls need no import. Closing that means a
     * rule over the whole module, which would be red on correct code the day
     * `OfflineLanguagesScreen` legitimately wants a saveable of its own. It is a
     * review question, in the same sense `KonsistArchitectureTest` names for the
     * speech-engine gate.
     */
    @Test
    fun `the picker imports nothing that scopes state to its host`() {
        val imported = pickerImports()

        // Never vacuous: a file that stopped parsing as Kotlin, or moved, has no
        // imports at all and would satisfy the rule by having nothing in it.
        assertThat(imported).isNotEmpty()

        val hostScoped = imported.filter { it.substringAfterLast('.') in HOST_SCOPED_STATE }
        assertWithMessage(
            "LanguagePickerScreen.kt imports $hostScoped — the picker's state must live in " +
                "LanguagePickerViewModel's SavedStateHandle, not in whichever host is drawing it " +
                "(see this test's KDoc). An alias does not help: this rule reads the symbol, not the call.",
        ).that(hostScoped)
            .isEmpty()
    }

    @Test
    fun `the picker screen holds no host-scoped saveable state`() {
        val code = shippedPickerSource()

        // Never vacuous: a moved or renamed screen fails HERE rather than
        // satisfying every "does not contain" below by being empty.
        assertThat(code).contains("fun LanguagePickerScreen")
        assertThat(code).contains("fun LanguagePickerContent")

        HOST_SCOPED_STATE.forEach { banned ->
            assertThat(code).doesNotContain(banned)
        }
    }

    /**
     * The other half of the same rule: the position the list starts at must come
     * from the caller. `LazyGridState()` with no arguments always starts at the
     * top — a restored scroll position silently thrown away, which is exactly
     * what a state-loss defect looks like from the outside.
     *
     * **This is the test 17a is most likely to break** (#130 PR-14). A landscape
     * arrangement is reached by ROTATING, which destroys the composition and
     * keeps the ViewModel — so a landscape body that built its own list state
     * would throw away the scroll position on every rotation and on no other
     * action, which is exactly the shape of defect nobody notices in review.
     * There is one grid, seeded once, for both arrangements.
     *
     * **What the seed IS changed in #198's co-verify** and the rule is stronger
     * for it. Handing the position through was never enough on its own: PR-14
     * handed through a raw item index, which the two arrangements number
     * differently, so the position survived the rotation and the LANGUAGE did
     * not. So the second assertion below pins where the seed comes from — the
     * handed anchor, resolved against the arrangement being restored INTO.
     */
    @Test
    fun `the list is seeded from the position it was handed`() {
        val code = shippedPickerSource()

        assertThat(code).contains("LazyGridState(seedIndex, listPosition.offset)")
        assertThat(code).contains("pickerAnchorIndex(listPosition.anchorId,")
    }

    /**
     * …and it is built ONCE, not once per arrangement.
     *
     * Seeding correctly in two places is still two scroll positions: rotating
     * between them re-enters the other branch's `remember`, which was keyed to a
     * composition that no longer exists, and the seed only helps the first time.
     * The single construction is what makes the position survive the rotation
     * rather than merely start in the right place.
     */
    @Test
    fun `the picker builds exactly one lazy state`() {
        val constructions = Regex("""LazyGridState\(""").findAll(shippedPickerSource()).count()

        assertWithMessage(
            "LanguagePickerScreen.kt constructs $constructions lazy states. 17a and 15a are two " +
                "arrangements of ONE list; a state per arrangement loses the scroll on every rotation.",
        ).that(constructions)
            .isEqualTo(1)
    }

    /**
     * Every symbol the file imports, resolved through any alias.
     *
     * `import a.b.C as D` yields `a.b.C`: the alias is what the file calls it,
     * and the part before `as` is what it actually is. Trailing semicolons and
     * backticks are stripped so an unusual-but-legal spelling cannot slip a
     * banned symbol past the comparison.
     */
    private fun pickerImports(): List<String> =
        pickerLines()
            .filter(::isImport)
            .map { line ->
                line
                    .trim()
                    .removePrefix("import ")
                    .substringBefore(" as ")
                    .trim()
                    .removeSuffix(";")
                    .replace("`", "")
                    .trim()
            }

    private fun isImport(line: String) = line.trimStart().startsWith("import ")

    private fun pickerLines(): List<String> {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        return checkoutRoot.resolve(PICKER_SOURCE).readText().lines()
    }

    /**
     * The shipped BODY of the screen: imports, comments, string literals and
     * previews all removed.
     *
     * Each exclusion earns its place:
     *  - **Imports**, because they are checked on their own terms above — as
     *    resolved symbols, which is the only reading an alias cannot dodge.
     *    Dropping them here is what let the alias through; the rule that reads
     *    them is what closes it.
     *  - **Strings**, because a testTag or a log line naming a banned token would
     *    otherwise satisfy the rule the way `"skipping shutdown() for now"`
     *    satisfied a Konsist gate in #159.
     *  - **Previews**, because they are not the shipped screen.
     */
    private fun shippedPickerSource(): String {
        val lines = pickerLines()
        val afterImports = lines.indexOfLast(::isImport) + 1
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

/**
 * Every way Compose addresses state through the HOST's `SaveableStateHolder`
 * instead of through the picker. None may be imported by, or named in, the
 * shipped screen: each of the three `remember*` helpers is `rememberSaveable`
 * over one kind of scroll state, so this is one rule, not four.
 *
 * `rememberLazyGridState` and `rememberScrollState` were added by #130 PR-14,
 * which gave the picker a grid and a scrolling side pane. Neither door existed
 * when PR-13 wrote the rule, and a rule that lists the doors has to be extended
 * whenever one is cut — which is the honest cost of naming symbols rather than
 * resolving them, and is written down here rather than left to be rediscovered.
 */
private val HOST_SCOPED_STATE =
    listOf(
        "rememberSaveable",
        "rememberLazyListState",
        "rememberLazyGridState",
        "rememberScrollState",
    )
