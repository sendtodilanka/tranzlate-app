package com.codeboxlk.tranzlate

import com.google.common.truth.Truth.assertThat
import com.lemonappdev.konsist.api.Konsist
import org.junit.Test
import java.io.File

/**
 * Architecture gates (plan §8) — ship in the scaffold PR and run in CI:
 *  1. Ring-2 JVM purity — the contract modules never touch Android.
 *  2. Features never import brain impl packages (one-home rule).
 *  3. `:lib:*` AARs import nothing from the app (zero project coupling).
 *  4. FROZEN package — `Translator` stays at
 *     `com.codeboxlk.tranzlate.domain.translate` (TEST_A11Y_CONTRACT :26).
 */
class KonsistArchitectureTest {
    /**
     * `scopeFromProject()` walks the whole checkout, and this project runs
     * agents in git worktrees under `.claude/worktrees/`. A worktree is a
     * second copy of every source file, so while one exists each declaration is
     * found twice and the FROZEN-package gate fails with `[Translator,
     * Translator]`. Those copies are sliced out.
     *
     * The slice is RELATIVE to the checkout being scanned, not an absolute path
     * match (issue #110). An absolute `contains("/.claude/worktrees/")` is true
     * of *every* file when the test itself runs inside a worktree — which is
     * exactly when an agent runs it — so the scope emptied and all four gates
     * failed with nothing wrong. Relativising makes the rule mean what it says:
     * ignore worktrees nested inside THIS checkout.
     */
    private val checkoutRoot: String =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, SETTINGS_FILE).isFile }
            .path
            .replace('\\', '/')

    private val scope =
        Konsist.scopeFromProject().slice { file ->
            val relative =
                file.path
                    .replace('\\', '/')
                    .removePrefix(checkoutRoot)
                    .trimStart('/')
            !relative.startsWith(NESTED_WORKTREES)
        }

    /**
     * The gate that stops a vacuous pass. Every assertion below is of the form
     * "no file does X", which an empty scope satisfies perfectly — so a scope
     * that silently loses its files would turn four gates green while checking
     * nothing. #110 was the loud version of that failure; this is the guard for
     * the quiet one.
     */
    @Test
    fun `the architecture scope is not empty`() {
        assertThat(scope.files).isNotEmpty()
        assertThat(filesUnder("/core/domain/")).isNotEmpty()
    }

    private fun filesUnder(vararg pathFragments: String) =
        scope.files.filter { file ->
            val path = file.path.replace('\\', '/')
            pathFragments.any { path.contains(it) }
        }

    @Test
    fun `ring-2 contract modules are JVM-pure`() {
        val ring2 =
            filesUnder(
                "/core/common/src/main/",
                "/core/model/src/main/",
                "/core/domain/src/main/",
                "/core/config/src/main/",
                "/core/testing/src/main/",
            )
        assertThat(ring2).isNotEmpty()

        // Allowlist (plan §8: kotlin/kotlinx/javax.inject/dagger hilt-core; plus
        // java.time for AppClock/FakeClock, org.junit for the :core:testing rule,
        // and the project's own JVM-pure packages).
        val allowedPrefixes =
            listOf(
                "kotlin.",
                "kotlinx.",
                "javax.inject.",
                "dagger.",
                "java.",
                "org.junit.",
                "com.codeboxlk.tranzlate.",
            )
        val violations =
            ring2.flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { import -> allowedPrefixes.none(import::startsWith) }
                    .map { "${file.path}: $it" }
            }
        assertThat(violations).isEmpty()

        val androidLeaks =
            ring2.flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it.startsWith("android.") || it.startsWith("androidx.") }
                    .map { "${file.path}: $it" }
            }
        assertThat(androidLeaks).isEmpty()
    }

    @Test
    fun `features never import brain impl packages`() {
        val featureFiles = filesUnder("/feature/")
        assertThat(featureFiles).isNotEmpty()

        val brainImplPrefixes =
            listOf(
                "com.codeboxlk.tranzlate.core.translate.",
                "com.codeboxlk.tranzlate.core.translatefake.",
                "com.codeboxlk.tranzlate.core.access.",
                "com.codeboxlk.tranzlate.core.usage.",
                "com.codeboxlk.tranzlate.core.ads.",
            )
        val violations =
            featureFiles.flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { import -> brainImplPrefixes.any(import::startsWith) }
                    .map { "${file.path}: $it" }
            }
        assertThat(violations).isEmpty()
    }

    @Test
    fun `lib AARs are project-independent`() {
        val libFiles = filesUnder("/lib/subscription/src/", "/lib/ads/src/", "/lib/consent/src/")
        assertThat(libFiles).isNotEmpty()

        val violations =
            libFiles.flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it.startsWith("com.codeboxlk.tranzlate") }
                    .map { "${file.path}: $it" }
            }
        assertThat(violations).isEmpty()
    }

    @Test
    fun `Translator stays in the FROZEN contract package`() {
        val translator = scope.interfaces().filter { it.name == "Translator" }
        assertThat(translator).hasSize(1)
        assertThat(translator.single().packagee?.name)
            .isEqualTo("com.codeboxlk.tranzlate.domain.translate")
        assertThat(translator.single().path.replace('\\', '/'))
            .contains("/core/domain/src/main/")
    }

    /**
     * CLAUDE.md mandatory rule 7 — every file that declares UI composables must
     * ship at least one `@PreviewLightDark` in the SAME file. The owner reviews
     * UI from previews, so a preview-less UI file is an unreviewable deliverable.
     *
     * "UI composable" = `@Composable` returning Unit. Value-returning composables
     * (`rememberWindowInfo()`, `adaptiveScreenMargin()`, `languageLabel()`) draw
     * nothing and are correctly out of scope. [PREVIEW_EXEMPT] carries the one
     * structural exception with its reason.
     *
     * Scope is FILE level: whether a file's previews cover every meaningful STATE
     * is a judgement the human co-verify lens makes. What this gate makes
     * impossible is the regression that keeps recurring — a whole screen or item
     * file landing with no preview at all.
     */
    @Test
    fun `every UI file ships a PreviewLightDark`() {
        val uiFiles =
            filesUnder("/feature/", "/core/ui/src/main/", "/core/designsystem/src/main/")
                .filter { file -> file.path.contains("/src/main/") }
                .filter { file -> file.name !in PREVIEW_EXEMPT }
                .filter { file ->
                    file.functions().any { function ->
                        function.hasAnnotationWithName("Composable") &&
                            !function.annotations.any { it.name.startsWith("Preview") } &&
                            function.returnType?.name.orEmpty() in setOf("", "Unit")
                    }
                }

        val missing =
            uiFiles
                .filter { file ->
                    file.functions().none { function ->
                        function.annotations.any { it.name == "PreviewLightDark" }
                    }
                }.map { it.name }

        assertThat(missing).isEmpty()
    }

    private companion object {
        /**
         * `Theme` is the theme WRAPPER: it renders only whatever content is passed
         * in, so a preview of it alone would show an empty screen. Every previewed
         * composable in the app already exercises it (`TranzlateTheme { … }`).
         */
        val PREVIEW_EXEMPT = setOf("Theme")

        /** Marks the checkout root — a worktree has one of its own, which is the point. */
        const val SETTINGS_FILE = "settings.gradle.kts"

        /** Worktrees nested inside the checkout being scanned; never the checkout itself. */
        const val NESTED_WORKTREES = ".claude/worktrees/"
    }
}
