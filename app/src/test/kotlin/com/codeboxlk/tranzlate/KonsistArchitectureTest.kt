package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.ui.languageLabel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
     * Issue #192 co-verify (F1) — nobody but the gate holds an unanswered
     * consent question, and nothing gives the gate a lifetime.
     *
     * The harm, concretely. Before #130 PR-13 the pending language id was a
     * `private val` inside `DownloadGate` and nothing in the app could reach it.
     * PR-13 moved it behind `ConsentQuestionStore` so it could survive process
     * death — a public interface, bound in `ViewModelComponent`, therefore
     * injectable into any `@HiltViewModel`. A co-verify lens compiled the
     * consequence and this session re-ran it: a ViewModel taking
     * `ConsentQuestionStore` + `OfflineModelManager` can drain the question the
     * user is being asked and start that download itself — no gate, no metered
     * check, no `ConsentedDownload`. `:app:assembleTranzlateProdDebug`
     * **succeeded** with that ViewModel in the tree. Issue #90's ruling walked
     * around, one layer above where #166's lens caught it.
     *
     * The fix is structural, not a rule: the store has NO Hilt binding, and the
     * gate has no `@Inject` constructor to force one. The offending ViewModel now
     * fails to compile. This gate exists because Dagger's own error message —
     * *"cannot be provided without an @Provides-annotated method"* — reads as an
     * instruction to add the binding back, and because moving the gate's
     * construction into a `@Provides` moved it out of sight of
     * `DownloadGateTest`'s "the gate owns no scope" rule, which reads one file.
     *
     * Three assertions, in the order they would fail:
     *
     *  1. **Who may even name the type.** Two-sided on purpose: an exact set, so
     *     a scope that silently loses files goes RED instead of passing
     *     vacuously (#110), and a new consumer goes red with its own name in the
     *     message. Comments are stripped, so a KDoc discussing the store never
     *     counts as holding one. It is a NAME rule, so it fires on an aliased
     *     import too — an alias cannot hide the symbol on the left of `as`,
     *     which is precisely the hole the same lens found in the picker's gate.
     *  2. **No binding hands the store out**, in case a future consumer lives in
     *     one of the three files that are allowed to name it.
     *  3. **No binding scopes the gate.** One gate per state holder is what keeps
     *     a half-answered dialog on the screen that raised it; `@ViewModelScoped`
     *     on the `@Provides` would carry the picker's question into Settings →
     *     Offline languages, where "Download once" would spend mobile data on a
     *     language nobody asked for there.
     *
     * Known over-trigger, stated rather than hidden: assertion 1 matches the
     * substring, so `InMemoryConsentQuestionStore` — harmless, it is an empty
     * store of your own — would also land here. Loud is the right side to err on
     * for a consent rule, and the message says which file to look at.
     */
    @Test
    fun `no Hilt binding hands out an unanswered consent question`() {
        val mainSources = filesUnder(MAIN_SOURCES)
        assertThat(mainSources).isNotEmpty()

        // (1) The exact set of production files that may name the store.
        val namers =
            mainSources
                .filter { code(it.text).contains(CONSENT_STORE) }
                .map { it.name }
                .toSet()
        assertWithMessage(
            "Only the store's own file, the gate that holds one and the composition root " +
                "may name $CONSENT_STORE — see this test's KDoc for the bypass that opens",
        ).that(namers)
            .isEqualTo(CONSENT_STORE_NAMERS)

        val bindings =
            scope
                .functions(includeNested = true)
                .filter { it.hasAnnotationWithName("Binds") || it.hasAnnotationWithName("Provides") }
        // Vacuous-pass guard: the binding this rule is about is in scope.
        assertThat(bindings.map { it.name }).contains(GATE_PROVIDER)

        // (2) …and none of them puts the store in the graph.
        val handedOut =
            bindings
                .filter { it.returnType?.name?.contains(CONSENT_STORE) == true }
                .map { "${it.containingFile.name}: ${it.name}" }
        assertThat(handedOut).isEmpty()

        // (3) …and none of them gives the gate a lifetime beyond one state holder.
        val scopedGate =
            bindings
                .filter { function ->
                    function.returnType?.name == GATE_TYPE ||
                        function.parameters.any { it.type.name.contains(CONSENT_STORE) }
                }.filter { function -> SCOPES.any(function::hasAnnotationWithName) }
                .map { "${it.containingFile.name}: ${it.name}" }
        assertThat(scopedGate).isEmpty()
    }

    /**
     * Issue #174 — the translating state announces that it started.
     *
     * `a11y_translating` shipped in three locales with ZERO call sites, so a
     * TalkBack user who activated Translate heard nothing at all until the
     * outcome arrived — the one moment where silence is indistinguishable from a
     * tap that never registered. It read as a dead string to a reference sweep
     * (#172 nearly deleted it); it was a missing announcement.
     *
     * SOURCE-SHAPE assertions, stated with the same honesty as the two gates
     * above: this repo has no Compose unit-test runtime (no Robolectric, no
     * `createComposeRule` anywhere), and the instrumented suite is compiled but
     * unrunnable (#40). So this cannot prove the announcement REACHES TalkBack —
     * only a device can. What it makes RED is every regression that compiles and
     * leaves the rest of the suite green:
     *
     *  1. the `liveRegion` deleted while the string stays — the exact shape that
     *     produced #174, and the one a "the string is referenced" test misses;
     *  2. the region rendered OUTSIDE the `Translating` branch, where it would
     *     linger and read "Translating…" over a finished result;
     *  3. a `Translating` branch that renders no announcing face — the second
     *     render site, or a third one added later (rule 11's first cause: #146
     *     converted 2 of 6 call sites);
     *  4. the composable renamed away, which would empty every assertion here.
     *
     * Comments and string literals are stripped before matching, so a KDoc that
     * explains the live region cannot satisfy the rule about having one.
     */
    @Test
    fun `the translating state announces that it started`() {
        // `single`, not `firstOrNull`: Konsist reports file names WITHOUT the
        // extension, and a name that stops matching must be loud, not vacuous.
        val composer = filesUnder("/feature/text/src/main/").single { it.name == COMPOSER_FILE }
        // Comments stripped everywhere below, so a KDoc that explains the live
        // region can never stand in for having one. Previews are excluded from
        // the render rules: a preview legitimately calls the face directly, and
        // it is not a state branch.
        val functions =
            composer
                .functions()
                .filterNot { fn -> fn.annotations.any { it.name.startsWith("Preview") } }
                .associate { it.name to it.text.replace(COMMENT, "") }

        // (4) Vacuous-pass guard FIRST. Every rule below is "for each X, X has
        //     property P", which an empty X satisfies perfectly — and this repo
        //     has already lost a Konsist gate to a silently emptied scope (#110).
        assertThat(functions.keys).contains(TRANSLATING_FACE)

        // (1) The announcement and the live region are on the SAME semantics
        //     block. Asserting the string alone is exactly what let #174 survive:
        //     the string shipped in three locales and announced nothing.
        val face = functions.getValue(TRANSLATING_FACE)
        assertThat(code(face)).contains("R.string.a11y_translating")
        assertThat(code(face)).contains("liveRegion")
        // Polite by ruling (plan §3): this fires while TalkBack is still reading
        // the Translate control's own label, and Assertive would talk over it.
        assertThat(code(face)).contains("LiveRegionMode.Polite")
        // The host tag is a string literal, so it survives comment-stripping only.
        assertThat(face).contains(LOADING_TAG)

        val renderers = functions.filterKeys { it != TRANSLATING_FACE }

        // (3) The shimmer is decorative on its own, so inside this screen the ONLY
        //     thing allowed to draw one is the announcing face. A new translating
        //     surface that reaches for the bare component — or a converted one
        //     reverted — is the #146 "2 of 6 call sites" shape, and lands here.
        val bareShimmer = renderers.filterValues { it.contains("$SHIMMER(") }.keys
        assertThat(bareShimmer).isEmpty()

        // (3b) Both render sites still exist and both announce. Scoped per
        //      FUNCTION, because the file also holds a non-rendering
        //      `is TextUiState.Translating ->` (the `requestText` getter) that
        //      owes no announcement.
        val branch = "is $STATE_MARKER$TRANSLATING ->"
        val silentBranches =
            renderers
                .filterValues { fn ->
                    fn.contains(branch) && !fn.contains("$TRANSLATING_FACE(")
                }.keys
        assertThat(silentBranches).isEmpty()
        assertThat(renderers.filterValues { it.contains("$TRANSLATING_FACE(") })
            .hasSize(RENDER_SITES)

        // (2) …and no OTHER state branch renders it, which is the only way this
        //     fix could double-announce: a region that outlives its state reads
        //     "Translating…" over a finished result. Walk back from each call to
        //     the nearest preceding state marker — it must be Translating.
        val callsOutsideTranslating =
            renderers.flatMap { (name, fn) ->
                fn
                    .indicesOf("$TRANSLATING_FACE(")
                    .filter { call ->
                        val marker = fn.lastIndexOf(STATE_MARKER, call)
                        marker == -1 || !fn.startsWith("$STATE_MARKER$TRANSLATING", marker)
                    }.map { name }
            }
        assertThat(callsOutsideTranslating).isEmpty()
    }

    /** Every start index of [token] — the state-branch walk needs positions, not counts. */
    private fun String.indicesOf(token: String): List<Int> =
        generateSequence(indexOf(token).takeIf { it != -1 }) { previous ->
            indexOf(token, previous + token.length).takeIf { it != -1 }
        }.toList()

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

        // ---- issue #192 F1: the consent question's reachability ---------------

        /** Production sources — the rule is about what the running app can obtain. */
        const val MAIN_SOURCES = "/src/main/"

        /** The storage seam PR-13 introduced; holding one means holding the open question. */
        const val CONSENT_STORE = "ConsentQuestionStore"

        /**
         * Everything allowed to name it: the file that declares it, the gate whose
         * question it is, and the composition root that builds both. A fourth name
         * here is a fourth thing that can drain a question the user is still
         * looking at.
         */
        val CONSENT_STORE_NAMERS = setOf("ConsentQuestionStore", "DownloadGate", "DownloadGateModule")

        /** The gate itself — no binding of it may carry a scope. */
        const val GATE_TYPE = "DownloadGate"

        /** The one `@Provides` that builds it; named so an emptied scope is loud. */
        const val GATE_PROVIDER = "downloadGate"

        // ---- issue #174: the translating announcement -------------------------

        /** Konsist reports file names without the extension (cf. [PREVIEW_EXEMPT]). */
        const val COMPOSER_FILE = "ComposerScreen"

        /** The one composable allowed to draw a translating face — it carries the live region. */
        const val TRANSLATING_FACE = "TranslatingFace"

        /** The decorative component. Bare inside this screen = a silent translating state. */
        const val SHIMMER = "ShimmerResult"

        /** Sealed-state prefix; the walk back from a call site looks for this. */
        const val STATE_MARKER = "TextUiState."

        /** The in-progress state whose branch owes the announcement. */
        const val TRANSLATING = "Translating"

        /** Contract §2.3 live-region host for the translating state. */
        const val LOADING_TAG = "\"tt_text_loading\""

        /**
         * Portrait read face + split result pane. A literal, not a derived count:
         * if a third translating surface lands, this gate should FAIL and make
         * someone say out loud that it announces too.
         */
        const val RENDER_SITES = 2

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
