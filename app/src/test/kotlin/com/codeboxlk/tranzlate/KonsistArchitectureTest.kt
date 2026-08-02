package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.ui.languageLabel
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
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
     * SOURCE-SHAPE assertions. What they make RED is every regression that
     * compiles and leaves the rest of the suite green:
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
     *
     * **Partly superseded, deliberately kept (#186).** This KDoc used to say the
     * repo had no Compose unit-test runtime, so nothing could prove the
     * announcement reaches an accessibility service. That is no longer true:
     * `ComposerAnnouncementTest` in `:feature:text` renders the composer under
     * Robolectric and reads the live region out of the semantics tree, which
     * covers case 1 properly rather than by proxy.
     *
     * It is kept anyway, and the division of labour is measured rather than
     * assumed — every mutation below was run:
     *
     *  - `liveRegion` deleted outright → BOTH red. The overlap is real.
     *  - the semantics block computed into an unused local and the unmodified
     *    modifier passed on (#193 row 1, verbatim) → **this gate GREEN, the
     *    behavioural test RED.** Every string matched below is still in the
     *    function text, and nothing reaches the tree. That case is the reason a
     *    shape gate cannot be the last word here.
     *  - the announcing face extracted into a `Modifier` helper — a pure refactor
     *    that changes no behaviour → **this gate RED, the behavioural test
     *    GREEN.** A gate that fails on a refactor and passes on a defect is
     *    exactly backwards, and that is the cost being accepted for cases 2-4.
     *  - the `ShimmerResult` import aliased and the call renamed with it → BOTH
     *    green, which is a hole rather than a success: case 3's "only the
     *    announcing face may draw a shimmer" is a substring match, so a new
     *    surface calling the aliased name would not be seen. Recorded, not fixed
     *    here — it belongs to #193.
     *
     * So: case 1 is now better covered elsewhere; cases 2-4 are still only
     * covered here, which is why nothing is deleted.
     */
    @Test
    fun `the translating state announces that it started`() {
        // `single`, not `firstOrNull`: Konsist reports file names WITHOUT the
        // extension, and a name that stops matching must be loud, not vacuous.
        val composer = filesUnder(TEXT_MAIN).single { it.name == COMPOSER_FILE }
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

    /**
     * Issue #190 — History's write failures reach the user.
     *
     * `HistoryViewModelTest` proves the ViewModel stops the throw and records the
     * failure. Nothing there proves anyone SHOWS it: delete the `LaunchedEffect`
     * from `HistoryScreen` and every one of those tests stays green while the app
     * goes back to a write that fails in silence — the #179 shape, one level down
     * from the crash this issue opened for.
     *
     * SOURCE-SHAPE assertions, same honesty as the gates above: this repo has no
     * Compose unit-test runtime, so nothing here can prove a snackbar appears.
     * What it makes RED is every regression that compiles and leaves the suite
     * green — the surface deleted, the Retry action dropped, one of the three
     * messages left unwired, or the consume moved after the retry (which would
     * clear the failure the retry is about to produce, and put the user back in
     * silence on the second failure).
     *
     * Comments and string literals are stripped first, so a KDoc that explains
     * the surface cannot stand in for having one.
     *
     * ## What defeated the first version of this gate (PR-194 co-verify)
     *
     * It read `HistoryScreen`'s own function text and asserted a list of
     * substrings. Changing `LaunchedEffect(failure)` to `LaunchedEffect(Unit)` —
     * which makes the effect run once, while the failure is still null, and never
     * react again — left every one of those substrings in place and the gate
     * green. The #193 class exactly.
     *
     * So it now reads the WHOLE FILE (extraction into a private helper is a
     * refactor, not a violation), resolves import aliases instead of grepping for
     * one spelling, and asserts the announcing effect's KEY, which is the thing
     * that was broken.
     *
     * KNOWN LIMIT, stated rather than implied: this still cannot prove a snackbar
     * appears — only a device can, and #186 is why. It also assumes the announcing
     * effect and its key live in the same file; a sibling-file extraction would
     * make it fail loudly rather than pass quietly, which is the safe direction.
     */
    @Test
    fun `the History screen shows a write that failed`() {
        val screen = filesUnder(HISTORY_MAIN).single { it.name == HISTORY_SCREEN_FILE }
        val host = code(screen.text)

        // Vacuous-pass guard: the surface is read from the ViewModel at all.
        assertThat(host).contains("viewModel.failure")
        assertThat(host).contains("showSnackbar")

        // §94's way forward, and the copy for all three writes — a message wired
        // for `delete` alone is exactly the partial fix issue #190 warns about.
        assertThat(host).contains("R.string.history_retry")
        HISTORY_FAILURE_STRINGS.forEach { assertThat(host).contains(it) }
        assertThat(host).contains("viewModel.retry(")

        // …and each write gets its OWN message. Asserting the three strings are
        // merely PRESENT is not enough: a branch re-pointed at another write's
        // copy leaves all three `stringResource` calls in place and reads as a
        // pass (measured — mutation M10 turned zero tests red before this).
        val arms =
            HISTORY_ARM
                .findAll(host)
                .associate { it.groupValues[1] to it.groupValues[2].trim() }
        assertThat(arms.keys).containsExactly("DELETE", "RESTORE", "FAVOURITE")
        assertThat(arms.values.toSet()).hasSize(HISTORY_FAILURE_STRINGS.size)

        // The order is load-bearing, not stylistic.
        val consumed = host.indexOf(HISTORY_CONSUME)
        val retried = host.indexOf("viewModel.retry(")
        assertThat(consumed).isGreaterThan(-1)
        assertThat(consumed).isLessThan(retried)

        // The effect that announces a failure must WATCH the failure. An effect
        // keyed on a constant fires once, before there is anything to say, and
        // then never again — the whole surface above becomes decoration.
        val allEffects = effects(screen, host)
        assertThat(allEffects).isNotEmpty() // the alias resolution found something
        val announcing =
            allEffects.filter { effect ->
                HISTORY_ARM.findAll(effect.body).count() == HISTORY_FAILURE_STRINGS.size
            }
        assertThat(announcing).hasSize(1)
        // The key must be the very identifier the body reads to decide there IS a
        // failure — `val pending = <x> ?: return`. Renaming either is a refactor;
        // keying on something else is the defect.
        val watched = ELVIS_SOURCE.find(announcing.single().body)?.groupValues?.get(1)
        assertThat(watched).isNotNull()
        assertThat(announcing.single().keys).contains(watched)

        // The confirmation this failure contradicts is WITHDRAWN before the
        // correction is queued, not merely dismissed. `SnackbarHostState`
        // serialises on a mutex, so a "Translation deleted" that has not reached
        // the screen yet is invisible to `currentSnackbarData` and would still
        // take its four seconds AFTER the error saying it never happened.
        val body = announcing.single().body
        val withdrawn = body.indexOf(SNACKBAR_WITHDRAW)
        assertThat(withdrawn).isGreaterThan(-1)
        assertThat(withdrawn).isLessThan(body.indexOf("showSnackbar"))
    }

    /**
     * Issue #190, PR-194 co-verify — one swipe performs its write ONCE.
     *
     * `confirmValueChange` is a PREDICATE. AOSP asks it "may I move to this
     * value?" at every decision point of a gesture, so a callback that ACTS
     * inside it acts several times. Measured on a device before this gate
     * existed: one delete swipe called `onDelete` **4 times** (three runs,
     * identical) and one save swipe called `setFavourite` **9 times**, landing on
     * the opposite of what the user asked for. Material3 1.4.0 deprecates the
     * parameter outright and offers `SwipeToDismissBox(onDismiss = …)`, which it
     * fires from `LaunchedEffect(settledValue, onDismiss)` — once per settle.
     *
     * That guarantee has a second half the compiler will not enforce: the lambda
     * is part of the effect's KEY, so one rebuilt on every recomposition re-fires
     * the write. `remember` with no keys is what pins it.
     *
     * SOURCE-SHAPE, like its neighbours (#186): it cannot count gesture callbacks,
     * only refuse the shapes that were measured to multiply them.
     */
    @Test
    fun `a History swipe performs its write once`() {
        val screen = filesUnder(HISTORY_MAIN).single { it.name == HISTORY_SCREEN_FILE }
        val host = code(screen.text)

        // Vacuous-pass guard: there is a swipe box here at all.
        assertThat(host).contains(SWIPE_BOX)

        // (1) The deprecated predicate is gone. A named argument cannot be
        //     aliased, so this token is not defeatable by a rename.
        assertThat(host).doesNotContain(SWIPE_PREDICATE)

        // (2) The write runs from the once-per-settle callback, and that callback
        //     is a STABLE instance — `onDismiss = { … }` written inline is a new
        //     lambda per recomposition, a new effect key, and the same defect.
        val dismissName = ON_DISMISS_ARG.find(host)?.groupValues?.get(1)
        assertThat(dismissName).isNotNull()
        val remembered = rememberedBlock(host, localName(screen, REMEMBER_FQ), dismissName!!)
        assertThat(remembered).isNotNull()

        // (3) …and it maps the two swipe directions to two DIFFERENT writes. Both
        //     arms re-pointed at one write leaves every call textually present —
        //     that is #194's own M10 shape, one screen along.
        val arms =
            SWIPE_ARM
                .findAll(remembered!!)
                .associate { it.groupValues[1] to it.groupValues[2].trim() }
        assertThat(arms.keys).containsExactly("StartToEnd", "EndToStart", "Settled")
        assertThat(setOf(arms.getValue("StartToEnd"), arms.getValue("EndToStart"))).hasSize(2)

        // (4) EDGE_CASES §94: the row comes back. A write the screen cannot yet
        //     know the outcome of must not leave a hole where the row was — a
        //     failed delete would otherwise read as "gone" for a row still there.
        val resetting =
            effects(screen, host).filter { it.keys.contains(SETTLED_VALUE) }
        assertThat(resetting).isNotEmpty()
        assertThat(resetting.any { it.body.contains(SWIPE_RESET) }).isTrue()
    }

    /**
     * One `LaunchedEffect(keys…) { body }` in [code], with the alias the file
     * actually uses resolved from its imports (#193: a gate that greps one
     * spelling guards one spelling).
     */
    private data class EffectCall(
        val keys: String,
        val body: String,
    )

    private fun effects(
        file: KoFileDeclaration,
        code: String,
    ): List<EffectCall> {
        val call = "${localName(file, LAUNCHED_EFFECT_FQ)}("
        return code.indicesOf(call).mapNotNull { start ->
            val open = start + call.length - 1
            val close = matching(code, open, '(', ')') ?: return@mapNotNull null
            val braceOpen = code.indexOf('{', close)
            val braceClose = braceOpen.takeIf { it != -1 }?.let { matching(code, it, '{', '}') }
            if (braceClose == null) return@mapNotNull null
            EffectCall(code.substring(open + 1, close), code.substring(braceOpen + 1, braceClose))
        }
    }

    /** The body of `val <name> = remember { … }`, or null if it is not remembered. */
    private fun rememberedBlock(
        code: String,
        remember: String,
        name: String,
    ): String? {
        val declaration = Regex("""val\s+$name\s*=\s*$remember\s*\{""").find(code) ?: return null
        val braceOpen = declaration.range.last
        val braceClose = matching(code, braceOpen, '{', '}') ?: return null
        return code.substring(braceOpen + 1, braceClose)
    }

    /**
     * The local spelling of [fqName]'s simple name in [file] — its import alias
     * when it has one. This is the #197 bar: resolve the alias rather than
     * forbidding it.
     */
    private fun localName(
        file: KoFileDeclaration,
        fqName: String,
    ): String {
        val alias =
            file.imports
                .firstOrNull { it.name == fqName }
                ?.alias
                ?.name
        val simple = fqName.substringAfterLast('.')
        return if (alias.isNullOrBlank() || alias == fqName) simple else alias
    }

    /** Index of the bracket closing the one at [open]. Comments/strings are already gone. */
    private fun matching(
        text: String,
        open: Int,
        opener: Char,
        closer: Char,
    ): Int? {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                opener -> depth++
                closer -> if (--depth == 0) return index
            }
        }
        return null
    }

    /**
     * Issue #195 — the composer shows a star write that failed.
     *
     * `TextViewModelTest` proves the ViewModel stops the throw and records the
     * failure. Nothing there proves anyone SHOWS it: delete the `LaunchedEffect`
     * from `ComposerScreen` and every one of those tests stays green while the
     * app goes back to a star that fails in silence — the #179 shape, one level
     * down from the crash this issue opened for.
     *
     * SOURCE-SHAPE assertions, and the same honesty the gates above owe. This
     * repo has no Compose unit-test runtime (#186), so nothing here can prove a
     * snackbar appears. What it makes RED is every regression that compiles and
     * leaves the suite green: the surface deleted, the Retry action dropped, one
     * of the two messages left unwired, the two arms SWAPPED, or the consume
     * moved after the retry (which would clear the failure the retry is about to
     * produce and put the user back in silence on the second failure).
     *
     * The swap is in that list because it was NOT, and a co-verify lens proved
     * it: swapping the arms so a user un-saving is told "Couldn't save" — the
     * precise harm the two-message design exists to prevent — left this suite
     * BUILD SUCCESSFUL and this testcase green. Asserting the arms are distinct
     * FROM EACH OTHER and that both keys appear SOMEWHERE in the file says
     * nothing about which arm reaches which key, and the swap changes neither
     * property. The arms are now resolved to the string key each one actually
     * names, through the local `val` that binds it. (The rename-resistance
     * claimed for M13 was never the thing that failed here; that claim stands,
     * and this was a different hole beside it.)
     *
     * **Its limits, named rather than implied** (#193 asks for exactly this):
     *
     *  1. It reads the FILE minus its previews, not one function, so a legitimate
     *     extraction into a private helper or a file-level constant still passes —
     *     the #188 defeat, where the violation was re-spelled rather than removed,
     *     does not work here. The cost is that moving the surface into a SIBLING
     *     FILE would fail this gate even though the app is correct. That trade is
     *     deliberate: a false alarm is answered by a human reading the diff, and a
     *     silent pass is not answered at all.
     *  2. The distinctness check reads a `when` over [StarIntent]. Rewriting the
     *     mapping as an `if` empties the match and this FAILS rather than passing
     *     silently — fail-closed on a shape change, so the next author has to say
     *     out loud that both messages are still wired.
     *  3. The arm-to-key resolution follows ONE hop: the arm either names
     *     `R.string.…` itself, or names a `val` in the same file that does. An arm
     *     routed through a second variable, a `when`, or a function call this gate
     *     cannot read resolves to nothing and FAILS — fail-closed again, and for
     *     the same reason: a gate that cannot see the mapping must not report that
     *     the mapping is right.
     *
     * Comments and string literals are stripped first, so a KDoc that explains
     * the surface cannot stand in for having one.
     */
    @Test
    fun `the composer shows a star write that failed`() {
        val composer = filesUnder(TEXT_MAIN).single { it.name == COMPOSER_FILE }
        // Previews removed, not the whole file kept: the two `@PreviewLightDark`
        // failure faces name the same strings, and every assertion below would
        // pass on their strength alone with the real surface deleted.
        val previews =
            composer.functions().filter { fn -> fn.annotations.any { it.name.startsWith("Preview") } }
        val surface = code(previews.fold(composer.text) { text, fn -> text.replace(fn.text, "") })

        // Vacuous-pass guard: the failure is read from the ViewModel at all.
        assertThat(surface).contains("viewModel.$STAR_FAILURE")
        assertThat(surface).contains("showSnackbar")

        // §94's way forward, and the copy for BOTH directions of the toggle — one
        // message wired for `save` alone tells the un-saving user the wrong thing.
        assertThat(surface).contains("R.string.button_retry")
        assertThat(surface).contains("actionLabel")
        STAR_FAILURE_STRINGS.forEach { assertThat(surface).contains(it) }
        assertThat(surface).contains("viewModel.retryStar(")
        assertThat(surface).contains("SnackbarResult.ActionPerformed")

        // …and each direction gets its OWN message. Asserting the two strings are
        // merely PRESENT is not enough: a branch re-pointed at the other one leaves
        // both `stringResource` calls in place and reads as a pass (#190's M10).
        val arms =
            STAR_ARM
                .findAll(surface)
                .associate { it.groupValues[1] to it.groupValues[2].trim() }
        assertThat(arms.keys).containsExactly("SAVE", "REMOVE")
        assertThat(arms.values.toSet()).hasSize(STAR_FAILURE_STRINGS.size)

        // …and each direction gets THE RIGHT one. Distinctness alone survives a
        // swap — proven, not supposed: with the arms exchanged, so that un-saving
        // reports "Couldn't save", this testcase stayed green. Both arms are still
        // distinct and both keys are still in the file; only the mapping is wrong,
        // so only the mapping catches it.
        STAR_ARM_KEYS.forEach { (intent, key) ->
            assertThat(keyBehindArm(surface, arms.getValue(intent))).isEqualTo(key)
        }

        // The order is load-bearing, not stylistic.
        val consumed = surface.indexOf(STAR_CONSUME)
        val retried = surface.indexOf("viewModel.retryStar(")
        assertThat(consumed).isGreaterThan(-1)
        assertThat(consumed).isLessThan(retried)
    }

    /**
     * The string key one `when` arm ultimately names, or null when this gate
     * cannot see it (issue #195 co-verify, F4).
     *
     * Two shapes, because both are honest Compose: the arm may hold the
     * `R.string.…` lookup itself, or — as the composer does, since
     * `stringResource` may not be called from inside a `LaunchedEffect` — it may
     * name a `val` hoisted above the effect. One hop, then it gives up and
     * returns null, which fails the assertion rather than excusing it.
     *
     * @param surface the composer file with comments and string literals already
     *   stripped, so a key mentioned in prose cannot resolve an arm.
     */
    private fun keyBehindArm(
        surface: String,
        rhs: String,
    ): String? {
        STRING_KEY.find(rhs)?.let { return it.groupValues[1] }
        val bindings = STRING_BINDING.findAll(surface).associate { it.groupValues[1] to it.groupValues[2] }
        return bindings[rhs.trim().trimEnd(',')]
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

        // ---- issue #190: History's write failures reach the user ---------------

        /** The screen that owns History's snackbar host; Konsist drops the extension. */
        const val HISTORY_SCREEN_FILE = "HistoryScreen"
        const val HISTORY_MAIN = "/feature/history/src/main/"

        /** One per write path — `delete` alone was the partial fix the issue rules out. */
        val HISTORY_FAILURE_STRINGS =
            listOf(
                "R.string.history_delete_failed",
                "R.string.history_restore_failed",
                "R.string.history_favourite_failed",
            )

        /** Must run BEFORE the retry, or the retry's own failure is cleared unread. */
        const val HISTORY_CONSUME = "viewModel.onFailureShown("

        /**
         * One `when` arm of the failure-to-copy map. The right-hand side is captured
         * to the end of the line rather than as an identifier, so inlining the
         * `stringResource` call into the arm still reads as one distinct message.
         */
        val HISTORY_ARM = Regex("""HistoryWrite\.(DELETE|RESTORE|FAVOURITE)\s*->\s*([^\n]+)""")

        /**
         * `val pending = <x> ?: return…` — [x] is what the announcing effect must
         * be keyed on. Captured from the body rather than assumed, so renaming
         * either the local or the state is a refactor and not a failure.
         */
        val ELVIS_SOURCE = Regex("""val\s+\w+\s*=\s*(\w+)\s*\?:\s*return""")

        /** Withdrawing the queued confirmation, not just dismissing the visible one. */
        const val SNACKBAR_WITHDRAW = ".cancel()"

        // ---- #190 / PR-194 co-verify: one swipe, one write -------------------

        /** Resolved through the file's imports, so an alias cannot silence the rule. */
        const val LAUNCHED_EFFECT_FQ = "androidx.compose.runtime.LaunchedEffect"
        const val REMEMBER_FQ = "androidx.compose.runtime.remember"

        /** Vacuous-pass guard for the swipe gate. */
        const val SWIPE_BOX = "SwipeToDismissBox("

        /**
         * The predicate AOSP polls several times per gesture — deprecated in
         * Material3 1.4.0 "without replacement". A NAMED ARGUMENT, so unlike an
         * import it has exactly one spelling and cannot be aliased away.
         */
        const val SWIPE_PREDICATE = "confirmValueChange"

        /** The once-per-settle callback that replaced it. */
        val ON_DISMISS_ARG = Regex("""onDismiss\s*=\s*(\w+)\s*,""")

        /** One arm of the direction-to-write map inside the remembered callback. */
        val SWIPE_ARM =
            Regex("""SwipeToDismissBoxValue\.(StartToEnd|EndToStart|Settled)\s*->\s*([^\n]+)""")

        /** The state the row's return-to-rest effect must watch, and what it must do. */
        const val SETTLED_VALUE = "settledValue"
        const val SWIPE_RESET = ".reset()"
        // ---- issue #195: the composer's star failure reaches the user ---------

        /** The module root the composer lives under; [COMPOSER_FILE] names the file. */
        const val TEXT_MAIN = "/feature/text/src/main/"

        /** The held failure the screen must read — the gate's vacuous-pass guard. */
        const val STAR_FAILURE = "starFailure"

        /**
         * Which key each direction of the toggle must reach — the MAPPING, which
         * is what the swap defeated, not merely the pair of keys. "Couldn't save"
         * is untrue for an un-save, and it is untrue in exactly the way a gate
         * that only counts distinct arms cannot see.
         */
        val STAR_ARM_KEYS =
            mapOf(
                "SAVE" to "text_star_save_failed",
                "REMOVE" to "text_star_remove_failed",
            )

        /** One per direction of the toggle — derived, so the two lists cannot drift apart. */
        val STAR_FAILURE_STRINGS = STAR_ARM_KEYS.values.map { "R.string.$it" }

        /** An `R.string.…` lookup wherever it sits — inside an arm, or inside the `val` it names. */
        val STRING_KEY = Regex("""R\.string\.(\w+)""")

        /**
         * `val label = stringResource(R.string.key)` — the one hop from an arm's
         * identifier to the key behind it. The right-hand side is read to the end
         * of the line rather than as a call, so a wrapper around `stringResource`
         * still resolves.
         */
        val STRING_BINDING = Regex("""val\s+(\w+)[^\n=]*=\s*[^\n]*R\.string\.(\w+)""")

        /** Must run BEFORE the retry, or the retry's own failure is cleared unread. */
        const val STAR_CONSUME = "viewModel.onStarFailureShown("

        /**
         * One `when` arm of the intent-to-copy map. The right-hand side is captured
         * to the end of the line rather than as an identifier, so inlining the
         * `stringResource` call into the arm still reads as one distinct message.
         */
        val STAR_ARM = Regex("""StarIntent\.(SAVE|REMOVE)\s*->\s*([^\n]+)""")

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
