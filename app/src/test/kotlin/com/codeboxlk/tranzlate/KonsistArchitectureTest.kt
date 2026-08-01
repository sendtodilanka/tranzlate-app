package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.ui.languageLabel
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

    /**
     * R3-B1 regression defence (register R4-O1, mutation M13) — the app runs its
     * startup tasks at create.
     *
     * The Activity tracker used to register itself from inside the lazy
     * `@Provides` for `SubscriptionGateway`. Nothing pulls that gateway before
     * first composition, first composition happens strictly AFTER the first
     * `dispatchActivityResumed`, and Android never replays that callback — so the
     * callbacks registered too late to ever see the first resume, and the **first
     * purchase of every session failed** with `NoForegroundActivity`. Because it
     * needed a rotation or background-and-return to reproduce, it read as
     * flakiness, not as a bug, and survived three review rounds. The fix is the
     * `AppStartupTask` multibinding run from `Application.onCreate`.
     *
     * These are SOURCE-SHAPE assertions, stated honestly: they cannot prove the
     * wiring *executes* (no Android runtime here). What they defend against is
     * the realistic regression — DELETION of the wiring: the injected set, the
     * `onCreate` loop, or the prod `@IntoSet` contribution quietly removed in a
     * refactor. Any of those deletions compiles, every other test stays green,
     * and the bug above comes back looking like flakiness. This gate makes each
     * of them RED instead.
     */
    @Test
    fun `the app runs its startup tasks at create`() {
        // (a) The Application declares the injected task set.
        val application = scope.classes().single { it.name == "TranzlateApplication" }
        val startupTasks = application.properties().single { it.name == "startupTasks" }
        assertThat(startupTasks.hasAnnotationWithName("Inject")).isTrue()

        // (b) onCreate actually runs them.
        val onCreate = application.functions().single { it.name == "onCreate" }
        assertThat(onCreate.text).contains("startupTasks.forEach")

        // (c) The prod source set contributes the Activity tracker to the set.
        //     (The fake variant ships no billing and legitimately contributes
        //     nothing — that is why only prod is asserted.)
        val prodContributions =
            filesUnder("/app/src/prod/")
                .flatMap { it.functions(includeNested = true) }
                .filter { function ->
                    function.returnType?.name == "AppStartupTask" &&
                        function.hasAnnotationWithName("IntoSet")
                }
        assertThat(prodContributions).isNotEmpty()
    }

    /**
     * Issue #149 — a `TextToSpeech` is a bound service connection, and the
     * platform never takes one back on its own: the system binds the engine for
     * us and then disables its own auto-unbind
     * (`TextToSpeechManagerPerUserService.getAutoDisconnectTimeoutMs()` returns
     * `PERMANENT_BOUND_TIMEOUT_MS`, whose contract is "do not unbind"). Measured
     * on API 37, launching the app was enough to pin the engine process at
     * oom_adj 100 in the top-app scheduling group, and backgrounding the app did
     * not release it — only process death did
     * (`docs/research/issue-149-tts-lifetime.md`).
     *
     * What this makes RED — every one of these compiles, and every other test in
     * the suite stays green through all of them:
     *
     *  1. the holder class scoped (`@Singleton` on `AndroidResultSpeaker`);
     *  2. the **binding** scoped — `@Binds @Singleton` in the Hilt module, which
     *     is the idiomatic way to scope a `@Binds` and reinstates the exact
     *     process-lifetime engine the issue opened for. The first version of
     *     this gate read only classes with a `TextToSpeech` property, and a
     *     module has none, so it passed (issue #159 co-verify, block 3);
     *  3. the release deleted (no `shutdown()` call in the holder);
     *  4. the release faked — comments AND string literals are stripped before
     *     matching, so neither a KDoc that mentions `shutdown()` nor a
     *     `Log.d(TAG, "skipping shutdown() for now")` satisfies the rule;
     *  5. the platform calls left unguarded — constructing or shutting down an
     *     engine outside `guarded`/`guardedRelease` (mediums 4/5).
     *
     * SOURCE-SHAPE assertions, stated honestly. They cannot prove the release
     * RUNS — `TextViewModelTest` and `ResultSpeakerTest` do that through the
     * seam and the extracted sequence. What this defends against is the shapes
     * above, all of which have actually been written by somebody.
     *
     * The limit worth naming: a holder that declares its own no-op `shutdown()`
     * and calls it would pass. Nothing available here resolves a call to its
     * declaration, so that is a review question, not a gate question.
     */
    @Test
    fun `no class holds a speech engine it cannot give back`() {
        // PRODUCTION sources only. The rule is about what the running app holds,
        // and `app/src/androidTest/.../TtsEngineLifetimeProbe.kt` deliberately
        // constructs engines and shuts them down bare — measuring the rebind
        // cost IS its job, and wrapping it in the production guards would
        // measure the guards. CI caught this and my local run did not, which is
        // its own finding (#163); CI is the truth here, as it has been twice
        // before in this repo.
        val holders =
            scope.classes(includeNested = true).filter { klass ->
                // A WHITELIST, not a blacklist. The first attempt excluded the
                // test source sets and still failed in CI while passing here —
                // whatever `scopeFromProject()` walks in one environment and not
                // the other (#163), an "only /src/main/" rule cannot be widened
                // by it. The rule is about what the RUNNING app holds, so that
                // is exactly the right scope anyway.
                val path = klass.containingFile.path.replace('\\', '/')
                "/src/main/" in path && klass.properties().any { code(it.text).contains(ENGINE_TYPE) }
            }
        // Vacuous-pass guard: the adapter the rule was written for is in scope.
        assertThat(holders.map { it.name }).contains("AndroidResultSpeaker")

        val processLifetime =
            holders.filter { klass -> SCOPES.any(klass::hasAnnotationWithName) }.map { it.name }
        assertThat(processLifetime).isEmpty()

        val neverReleased =
            holders
                .filter { klass -> klass.functions().none { code(it.text).contains(SHUTDOWN) } }
                .map { it.name }
        assertThat(neverReleased).isEmpty()

        // Mediums 4/5: the engine's construction and its shutdown both reach the
        // platform, and both run from paths whose escape would take the screen
        // down, so each has to sit inside the file's guard helpers.
        val unguarded =
            holders
                .flatMap { it.functions() }
                .filter { function ->
                    val body = code(function.text)
                    (body.contains("$ENGINE_TYPE(") || body.contains(SHUTDOWN)) &&
                        GUARDS.none(body::contains)
                }.map { "${it.containingFile.name}: ${it.name}" }
        assertThat(unguarded).isEmpty()
    }

    /**
     * Issue #159 co-verify (block 1) — the shell gives the speech engine back
     * when the app STOPS.
     *
     * `TextViewModel` is hoisted outside the NavDisplay entries, so it resolves
     * to the Activity's ViewModelStore and `onCleared()` runs only when the
     * Activity finishes. Backgrounding changes no state, so the state funnel
     * never fires — and the device re-measurement showed the engine still at
     * `adj 100` behind an app at `adj 900`, which is the harm #149 opened for.
     * Only the shell's own lifecycle can close that, and only from HERE: the
     * composer is not composed while the picker or Settings is on top.
     *
     * SOURCE-SHAPE assertion, same honesty as the startup-task gate above: it
     * cannot prove the effect RUNS (no Android runtime here) — `TextViewModelTest`
     * pins what the ViewModel does when it is told. What it makes RED is the
     * DELETION, which compiles and leaves every other test green.
     */
    @Test
    fun `the shell hands the speech engine back when the app stops`() {
        val shell = scope.functions(includeNested = true).single { it.name == "TranzlateApp" }
        val body = code(shell.text)

        assertThat(body).contains("LifecycleStartEffect")
        assertThat(body).contains("onHostStarted()")
        assertThat(body).contains("onStopOrDispose")
        assertThat(body).contains("onHostStopped()")
    }

    /**
     * The other half of the same rule: the BINDING must not be scoped either.
     *
     * Split from the test above because it starts from the opposite end — the
     * Hilt graph rather than the field — and because that is precisely the gap
     * the co-verify lens walked through: a class with no `TextToSpeech` property
     * can still hand out one engine for the whole process.
     */
    @Test
    fun `no Hilt binding gives the speech engine process lifetime`() {
        val speechTypes =
            scope
                .classes(includeNested = true)
                .filter { klass -> klass.properties().any { code(it.text).contains(ENGINE_TYPE) } }
                .flatMap { klass -> klass.parents().map { it.name } + klass.name }
                .toSet()
        // Vacuous-pass guard: both ends of the binding under test are in scope.
        assertThat(speechTypes).containsAtLeast("AndroidResultSpeaker", "ResultSpeaker")

        val scopedBindings =
            scope
                .functions(includeNested = true)
                .filter { function ->
                    function.hasAnnotationWithName("Binds") || function.hasAnnotationWithName("Provides")
                }.filter { function ->
                    function.returnType?.name in speechTypes ||
                        function.parameters.any { it.type.name in speechTypes }
                }.filter { function -> SCOPES.any(function::hasAnnotationWithName) }
                .map { "${it.containingFile.name}: ${it.name}" }
        assertThat(scopedBindings).isEmpty()
    }

    /**
     * Comments AND string literals stripped before matching. Both were found by
     * mutation: deleting the real `tts.shutdown()` left the gate green because
     * the comment explaining why that call needs a guard says `shutdown()` too,
     * and after that fix a `Log.d(TAG, "skipping shutdown() for now")` still
     * passed. A rule that reads prose — or a message — is not reading code.
     */
    private fun code(text: String) = text.replace(COMMENT, "").replace(STRING_LITERAL, "")

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

        /** KDoc, block and line comments — prose that must not satisfy a code rule. */
        val COMMENT = Regex("""/\*[\s\S]*?\*/|//[^\n]*""")

        /**
         * Raw and escaped string literals — a log MESSAGE that names a call must
         * not satisfy a rule about making the call (issue #159, block 3).
         */
        val STRING_LITERAL = Regex(""""{3}[\s\S]*?"{3}|"(?:\\.|[^"\\\n])*"""")

        /** The platform type whose instances are bound service connections. */
        const val ENGINE_TYPE = "TextToSpeech"

        /** The only call that ends the binding (AOSP: nothing else does). */
        const val SHUTDOWN = "shutdown()"

        /** `ResultSpeaker.kt`'s escape guards — every platform call goes through one. */
        val GUARDS = listOf("guarded(", "guardedRelease(")

        /**
         * Dagger/Hilt scopes that would outlive one consumer. `@Reusable` is in
         * the list on purpose: it lets Dagger cache and hand back the SAME
         * engine, which is the property that matters here, not the guarantee.
         */
        val SCOPES =
            listOf(
                "Singleton",
                "Reusable",
                "ActivityRetainedScoped",
                "ActivityScoped",
                "ViewModelScoped",
                "FragmentScoped",
                "ServiceScoped",
                "ViewScoped",
            )
    }
}
