package com.codeboxlk.tranzlate

import com.google.common.truth.Truth.assertThat
import com.lemonappdev.konsist.api.Konsist
import org.junit.Test

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
     * Translator]`. Slicing them out keeps the gate about *our* sources.
     */
    private val scope =
        Konsist.scopeFromProject().slice { file ->
            !file.path.replace('\\', '/').contains("/.claude/worktrees/")
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
}
