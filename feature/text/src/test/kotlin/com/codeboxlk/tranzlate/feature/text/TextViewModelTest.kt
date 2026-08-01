package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.EngineAttempt
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeLanguageUsageRepository
import com.codeboxlk.tranzlate.core.testing.FakeRemoteConfig
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * TEST_A11Y_CONTRACT §1.8 state-machine tests (as amended for issue #11 — C-2
 * explicit trigger; no debounce states) against the §1.2 golden table via
 * constructor-injected fakes. StandardTestDispatcher (not Unconfined) so the
 * synchronous Translating frame is observable before the scheduler runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    @Suppress("LongParameterList") // the test builder aggregates one fake per seam
    private fun viewModel(
        translator: Translator = FakeTranslator(),
        prefs: FakeTranslatePrefsRepository = FakeTranslatePrefsRepository(),
        clock: FakeClock = FakeClock(),
        handle: SavedStateHandle = SavedStateHandle(),
        usage: FakeUsagePolicy = FakeUsagePolicy(left = 5),
        access: FakeFeatureAccess = FakeFeatureAccess(),
        repository: FakeTranslationRepository = FakeTranslationRepository(),
        speaker: FakeResultSpeaker = FakeResultSpeaker(),
        catalog: List<Language> = FakeLanguageRepository.DEFAULT_CATALOG,
    ): TextViewModel =
        textViewModel(
            dispatcher,
            translator,
            prefs,
            clock,
            handle,
            usage,
            access,
            repository,
            speaker,
            catalog,
        )

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    // ---- launch honesty: Home's download-row count is REAL -------------------
    // Home used to print "133 available · 2 updates ready", a design-mock string
    // nothing computed. These pin the replacement to the catalog so the number
    // can never drift back into fiction.

    @Test
    fun `offline language count reports only the offline-capable catalog rows`() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    catalog =
                        listOf(
                            Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                            Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                            // Online-only: it belongs in the picker, never in the
                            // "available offline" count.
                            Language("cy", "Welsh", offlineAvailable = false, offlineDownloaded = false),
                        ),
                )
            backgroundScope.launch { vm.offlineLanguageCount.collect() }
            advanceUntilIdle()

            assertThat(vm.offlineLanguageCount.value).isEqualTo(2)
        }

    @Test
    fun `offline language count is zero for a catalog with nothing offline-capable`() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    catalog =
                        listOf(
                            Language("cy", "Welsh", offlineAvailable = false, offlineDownloaded = false),
                        ),
                )
            backgroundScope.launch { vm.offlineLanguageCount.collect() }
            advanceUntilIdle()

            // Not a placeholder: the plural resource reads correctly at 0, so an
            // empty catalog says so instead of inventing a number.
            assertThat(vm.offlineLanguageCount.value).isEqualTo(0)
        }

    // ---- issue #70: swap never writes "auto" into TARGET ---------------------

    @Test
    fun `swap with a concrete source stays a plain atomic pair write`() {
        val prefs = FakeTranslatePrefsRepository()
        val vm = viewModel(prefs = prefs)
        settle()

        assertThat(vm.onSwapLanguages()).isTrue()
        settle()

        assertThat(prefs.source.value).isEqualTo("fr")
        assertThat(prefs.target.value).isEqualTo("en")
    }

    @Test
    fun `swap with Detect resolves through the shown result's detected language`() {
        val prefs = FakeTranslatePrefsRepository().apply { source.value = "auto" }
        val vm = viewModel(prefs = prefs)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate() // G7 auto->fr detects "en"
        settle()

        assertThat(vm.onSwapLanguages()).isTrue()
        settle()

        assertThat(prefs.source.value).isEqualTo("fr")
        assertThat(prefs.target.value).isEqualTo("en") // the DETECTED id — never "auto"
    }

    @Test
    fun `swapAvailable follows Detect state and the shown result`() {
        val prefs = FakeTranslatePrefsRepository().apply { source.value = "auto" }
        val vm = viewModel(prefs = prefs)
        settle()
        assertThat(vm.swapAvailable.value).isFalse() // Detect + nothing shown

        vm.onInputChange("Good morning")
        vm.onTranslate() // G7 detects "en"
        settle()
        assertThat(vm.swapAvailable.value).isTrue() // the resolve path is now REACHABLE

        prefs.source.value = "en"
        settle()
        assertThat(vm.swapAvailable.value).isTrue() // concrete source always can
    }

    @Test
    fun `swap with Detect and nothing to resolve reports false and writes nothing`() {
        val prefs = FakeTranslatePrefsRepository().apply { source.value = "auto" }
        val vm = viewModel(prefs = prefs)
        settle()

        assertThat(vm.onSwapLanguages()).isFalse()
        settle()

        assertThat(prefs.source.value).isEqualTo("auto") // untouched
        assertThat(prefs.target.value).isEqualTo("fr")
    }

    // ---- issue #84: result actions — reverse + speak -------------------------

    @Test
    fun `reverse makes the result the input, swaps the pair and re-translates`() {
        val prefs = FakeTranslatePrefsRepository()
        val vm = viewModel(prefs = prefs)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate() // G1 en->fr "Bonjour (fake)"
        settle()

        assertThat(vm.onReverse()).isTrue()
        settle()

        assertThat(vm.input.value).isEqualTo("Bonjour (fake)") // C-7: result becomes input
        assertThat(prefs.source.value).isEqualTo("fr")
        assertThat(prefs.target.value).isEqualTo("en")
        val state = vm.uiState.value
        assertThat(state).isInstanceOf(TextUiState.Result::class.java)
        assertThat((state as TextUiState.Result).request.sourceLang).isEqualTo("fr")
    }

    @Test
    fun `reverse through auto-detect uses the RESOLVED source`() {
        val prefs = FakeTranslatePrefsRepository().apply { source.value = "auto" }
        val vm = viewModel(prefs = prefs)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate() // G7 detects "en"
        settle()

        assertThat(vm.onReverse()).isTrue()
        settle()

        assertThat(prefs.source.value).isEqualTo("fr")
        assertThat(prefs.target.value).isEqualTo("en") // the DETECTED id, never "auto"
    }

    // ---- issue #68: tap-to-reopen + star-to-save -----------------------------

    @Test
    fun `a favourited result restores with the star FILLED - the first tap must not un-save`() {
        val repository = FakeTranslationRepository()
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle, repository = repository)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()
        first.onToggleFavourite()
        settle()
        assertThat(repository.saved.single().favourite).isTrue()

        val reborn = viewModel(handle = handle, repository = repository) // process death
        settle()

        // PR-69 lens OPEN-1: the restore bypassed the setter, the star rendered
        // unfilled, and the "save" tap silently DELETED the favourite.
        assertThat(reborn.resultFavourite.value).isTrue()

        reborn.onToggleFavourite() // what a user believing "unsaved" would tap...
        settle()
        assertThat(repository.saved.single().favourite).isFalse() // ...now correctly UN-saves
    }

    @Test
    fun `history pick restores input, pair and the stored result`() {
        val prefs = FakeTranslatePrefsRepository()
        val vm = viewModel(prefs = prefs)
        settle()

        vm.onHistoryPick(
            Translation(
                id = 7,
                sourceLang = "de",
                sourceText = "Hallo Welt",
                targetLang = "en",
                targetText = "Hello world",
                engine = Engine.ONLINE_GOOGLE,
                createdAt = 1L,
            ),
        )
        settle()

        assertThat(vm.input.value).isEqualTo("Hallo Welt")
        assertThat(prefs.source.value).isEqualTo("de")
        assertThat(prefs.target.value).isEqualTo("en")
        val state = vm.uiState.value as TextUiState.Result
        assertThat(state.translatedText).isEqualTo("Hello world")
        assertThat(state.resolvedSourceLang).isEqualTo("de")
    }

    @Test
    fun `star toggle flips the history row and the icon state follows`() {
        val repository = FakeTranslationRepository()
        val vm = viewModel(repository = repository)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate() // G1 en->fr writes the history row
        settle()
        assertThat(vm.resultFavourite.value).isFalse()

        vm.onToggleFavourite()
        settle()

        assertThat(repository.saved.single().favourite).isTrue()
        assertThat(vm.resultFavourite.value).isTrue()

        vm.onToggleFavourite()
        settle()

        assertThat(repository.saved.single().favourite).isFalse()
        assertThat(vm.resultFavourite.value).isFalse()
    }

    // ---- issue #53 A3: the gate's answers get their own face -----------------

    @Test
    fun `at-limit metered ask lands on the Limit face - not a generic error`() {
        val prefs = FakeTranslatePrefsRepository().apply { mode.value = ModeId.NLP35 }
        val vm = viewModel(prefs = prefs, usage = FakeUsagePolicy(left = 0))
        settle()
        vm.onInputChange("Quota text")

        vm.onTranslate()
        settle()

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(TextUiState.Limit::class.java)
        assertThat((state as TextUiState.Limit).notEntitled).isFalse()
    }

    @Test
    fun `denied engine lands on the not-entitled Limit face`() {
        val prefs = FakeTranslatePrefsRepository().apply { mode.value = ModeId.NLP35 }
        val access = FakeFeatureAccess().apply { engineAllowed = false }
        val vm = viewModel(prefs = prefs, access = access)
        settle()
        vm.onInputChange("Good morning")

        vm.onTranslate()
        settle()

        val state = vm.uiState.value
        assertThat(state).isInstanceOf(TextUiState.Limit::class.java)
        assertThat((state as TextUiState.Limit).notEntitled).isTrue()
    }

    @Test
    fun `Limit face survives process death like every other face`() {
        val handle = SavedStateHandle()
        val prefs = FakeTranslatePrefsRepository().apply { mode.value = ModeId.NLP35 }
        val first = viewModel(prefs = prefs, handle = handle, usage = FakeUsagePolicy(left = 0))
        settle()
        first.onInputChange("Quota text")
        first.onTranslate()
        settle()
        assertThat(first.uiState.value).isInstanceOf(TextUiState.Limit::class.java)

        val second = viewModel(handle = handle) // fresh VM, same saved state
        settle()

        assertThat(second.uiState.value).isInstanceOf(TextUiState.Limit::class.java)
    }

    // ---- Blank input (G9 / contract §1.9 adapted to amended C-2) -------------

    @Test
    fun `blank input fires no translate call`() {
        val translator = FakeTranslator()
        val vm = viewModel(translator)
        settle()

        assertThat(vm.onTranslate()).isFalse()
        vm.onInputChange("   ")
        assertThat(vm.onTranslate()).isFalse()
        settle()

        assertThat(translator.calls).isEmpty()
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Idle)
    }

    // ---- Golden happy path (G2: en→fr AUTO → "Bonjour (fake)") ---------------

    @Test
    fun `tap translate transitions Translating to golden Result`() {
        val translator = FakeTranslator()
        val vm = viewModel(translator)
        settle()

        vm.onInputChange("Good morning")
        assertThat(vm.onTranslate()).isTrue()

        val request = TranslateRequest("Good morning", "en", "fr", ModeId.AUTO)
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Translating(request))

        settle()

        assertThat(vm.uiState.value).isEqualTo(
            TextUiState.Result(
                request = request,
                translatedText = "Bonjour (fake)", // G2 exact golden
                transliteration = null,
                engine = Engine.OFFLINE_MLKIT,
                resolvedSourceLang = "en",
            ),
        )
        assertThat(translator.calls.last().mode).isEqualTo(ModeId.AUTO) // spy
    }

    // ---- Error → Retry (G10 pattern: forcedFailure NETWORK) ------------------

    @Test
    fun `network failure surfaces Error and retry after recovery reaches Result`() {
        val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
        val vm = viewModel(translator)
        settle()

        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()

        val request = TranslateRequest("Good morning", "en", "fr", ModeId.AUTO)
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Error(request, AttemptCause.OFFLINE))

        translator.forcedFailure = null // network back
        vm.onRetry()
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Translating(request)) // replays EXACT request
        settle()

        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)
        assertThat((vm.uiState.value as TextUiState.Result).translatedText).isEqualTo("Bonjour (fake)")
    }

    // ---- Swap (UI_SPEC §2.2 ⇄ — always available, atomic pair write) ---------

    @Test
    fun `swap exchanges source and target languages`() {
        val prefs = FakeTranslatePrefsRepository()
        val vm = viewModel(prefs = prefs)
        settle()
        assertThat(vm.sourceLang.value).isEqualTo("en")
        assertThat(vm.targetLang.value).isEqualTo("fr")

        vm.onSwapLanguages()
        settle()

        assertThat(vm.sourceLang.value).isEqualTo("fr")
        assertThat(vm.targetLang.value).isEqualTo("en")
    }

    // ---- Greeting (UI_SPEC §2.1 time-aware, FakeClock-deterministic) ---------

    @Test
    fun `greeting period maps FakeClock instants in a fixed zone`() {
        val colombo = ZoneId.of("Asia/Colombo") // FakeClock's contract zone (§1.5)

        fun periodAt(utc: String) = greetingPeriodFor(Instant.parse(utc).toEpochMilli(), colombo)

        assertThat(periodAt("2026-07-21T01:30:00Z")).isEqualTo(GreetingPeriod.MORNING) // 07:00 local
        // 14:30 local — the FakeClock default instant
        assertThat(periodAt("2026-07-21T09:00:00Z")).isEqualTo(GreetingPeriod.AFTERNOON)
        assertThat(periodAt("2026-07-21T14:00:00Z")).isEqualTo(GreetingPeriod.EVENING) // 19:30 local
        // 03:30 local — late night reads Evening
        assertThat(periodAt("2026-07-21T22:00:00Z")).isEqualTo(GreetingPeriod.EVENING)
    }

    @Test
    fun `viewmodel greeting matches FakeClock time in the system zone`() {
        val clock = FakeClock()
        val vm = viewModel(clock = clock)

        assertThat(vm.greeting)
            .isEqualTo(greetingPeriodFor(clock.nowMillis(), ZoneId.systemDefault()))
    }

    // ---- Input cap + clear ---------------------------------------------------

    @Test
    fun `over-limit input is kept intact, never truncated`() {
        val vm = viewModel()
        settle()
        val long = "a".repeat(TEXT_CHAR_LIMIT + 100)

        vm.onInputChange(long)

        // spec-01 §8/§9: the input is NOT truncated — the action blocks instead.
        assertThat(vm.input.value).isEqualTo(long)
    }

    @Test
    fun `over-limit input blocks the translate action`() {
        val translator = FakeTranslator()
        val vm = viewModel(translator = translator)
        settle()
        vm.onInputChange("a".repeat(TEXT_CHAR_LIMIT + 1))

        val started = vm.onTranslate()
        settle()

        assertThat(started).isFalse()
        assertThat(translator.calls).isEmpty()
    }

    @Test
    fun `reverse moves the result into the input, swaps languages and re-translates`() {
        val vm = viewModel()
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()
        val first = vm.uiState.value as TextUiState.Result

        vm.onReverse()
        settle()

        // C-7 post-condition: input == prior result, languages swapped.
        assertThat(vm.input.value).isEqualTo(first.translatedText)
        assertThat(vm.sourceLang.value).isEqualTo(first.request.targetLang)
        assertThat(vm.targetLang.value).isEqualTo(first.request.sourceLang)
        val reversed = vm.uiState.value
        assertThat(reversed).isNotEqualTo(first)
    }

    @Test
    fun `clear returns composer and state to Idle`() {
        val vm = viewModel()
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)

        vm.onClearAll()

        assertThat(vm.input.value).isEmpty()
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Idle)
    }

    // ---- Process-death recovery (SavedStateHandle-backed last request) -------

    /** Issue #48: the result itself is restored — no replay, so no second API call. */
    @Test
    fun `the result survives process death`() {
        val handle = SavedStateHandle()
        val translator = FakeTranslator()
        val first = viewModel(translator, handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()
        val before = first.uiState.value as TextUiState.Result

        // "Process death": a NEW ViewModel over the same restored handle.
        val second = viewModel(translator, handle = handle)
        settle()

        val after = second.uiState.value
        assertThat(after).isInstanceOf(TextUiState.Result::class.java)
        assertThat((after as TextUiState.Result).translatedText).isEqualTo("Bonjour (fake)")
        assertThat(after.engine).isEqualTo(before.engine)
        assertThat(after.request).isEqualTo(before.request)
        // Restored from the record, not re-translated.
        assertThat(translator.calls).hasSize(1)
    }

    /**
     * The trap this fix had to avoid: leaving 5a discards the draft, so the next
     * composer must open EMPTY even after a kill — never showing the old result.
     */
    @Test
    fun `a dismissed composer leaves nothing to restore`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()
        first.onComposerDismissed()

        val second = viewModel(handle = handle)
        settle()

        assertThat(second.uiState.value).isEqualTo(TextUiState.Idle)
        assertThat(second.input.value).isEmpty()
    }

    /** The error card and its Retry must come back too — not just a success. */
    @Test
    fun `an error survives process death`() {
        val handle = SavedStateHandle()
        val translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE }
        val first = viewModel(translator, handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()
        assertThat(first.uiState.value).isInstanceOf(TextUiState.Error::class.java)

        val after = viewModel(translator, handle = handle).uiState.value

        assertThat(after).isInstanceOf(TextUiState.Error::class.java)
        assertThat((after as TextUiState.Error).cause).isEqualTo(AttemptCause.OFFLINE)
    }

    /**
     * A translation the system interrupted is resumed, not reported as a failure
     * that never happened — and the resumed job must still be cancellable, or a
     * dismissed composer could have a result pushed back into it.
     */
    @Test
    fun `an interrupted translation resumes and stays cancellable`() {
        val handle = SavedStateHandle()
        val translator = FakeTranslator()
        val first = viewModel(translator, handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate() // marks Translating, then completes
        settle()
        // Rewind the record to the moment before the outcome landed.
        handle["text_state_kind"] = "translating"

        val second = viewModel(translator, handle = handle)
        assertThat(second.uiState.value).isInstanceOf(TextUiState.Translating::class.java)

        // Leave 5a while that resumed job is still in flight.
        second.onComposerDismissed()
        settle()

        assertThat(second.uiState.value).isEqualTo(TextUiState.Idle)
        assertThat(second.input.value).isEmpty()
    }

    /** The same race on the first translation rather than a restored one. */
    @Test
    fun `dismissing mid-translation cannot push a result back`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle = handle)
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        vm.onComposerDismissed() // before the coroutine is allowed to finish
        settle()

        assertThat(vm.uiState.value).isEqualTo(TextUiState.Idle)
        assertThat(viewModel(handle = handle).uiState.value).isEqualTo(TextUiState.Idle)
    }

    /** An unrebuildable record (an enum constant renamed by an update) is dropped, not left. */
    @Test
    fun `an unreadable record is cleared instead of lingering`() {
        val handle = SavedStateHandle()
        handle["text_state_kind"] = "result"
        handle["text_result_text"] = "Bonjour (fake)"
        handle["text_result_engine"] = "AN_ENGINE_THAT_NO_LONGER_EXISTS"

        assertThat(viewModel(handle = handle).uiState.value).isEqualTo(TextUiState.Idle)
        assertThat(handle.get<String>("text_state_kind")).isNull()
        assertThat(handle.get<String>("text_result_text")).isNull()
    }

    /** Same guarantee for the ✕ clear action. */
    @Test
    fun `clearing leaves nothing to restore`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()
        first.onClearAll()

        assertThat(viewModel(handle = handle).uiState.value).isEqualTo(TextUiState.Idle)
    }

    // ---- issue #103: the shimmer floor (owner: no loading flash) -------------

    /** Takes [afterMs] of VIRTUAL time, then fails — a slow failure. */
    private class SlowFailingTranslator(
        private val afterMs: Long,
    ) : Translator {
        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
            mode: ModeId,
        ): TranslationOutcome {
            delay(afterMs)
            return TranslationOutcome.Error(listOf(EngineAttempt(Engine.OFFLINE_MLKIT, AttemptCause.OFFLINE)))
        }
    }

    @Test
    fun `an instant failure holds the shimmer for the floor, then shows the error`() {
        val vm = viewModel(translator = FakeTranslator(forcedFailure = AttemptCause.OFFLINE))
        vm.onInputChange("Good morning")
        settle()
        vm.onTranslate()

        dispatcher.scheduler.advanceTimeBy(400)
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Translating::class.java)

        dispatcher.scheduler.advanceTimeBy(150)
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Error::class.java)
    }

    @Test
    fun `a failure slower than the floor is NOT delayed further`() {
        val vm = viewModel(translator = SlowFailingTranslator(afterMs = 900))
        vm.onInputChange("Good morning")
        settle()
        vm.onTranslate()

        dispatcher.scheduler.advanceTimeBy(899)
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Translating::class.java)

        dispatcher.scheduler.advanceTimeBy(2)
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Error::class.java)
    }

    /** The floor must never hold back an answer the user could already have (C-8 cache / offline MLKit). */
    @Test
    fun `a success is never delayed by the floor`() {
        val vm = viewModel()
        vm.onInputChange("Good morning")
        settle()
        vm.onTranslate()
        // runCurrent, NOT advanceUntilIdle: draining virtual time would also
        // drain a floor if one wrongly applied, so the test could never fail.
        dispatcher.scheduler.runCurrent()

        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)
    }

    // ---- issue #151: one detection, one spelling ----------------------------

    /**
     * The read face and the history row are written by two different classes
     * from the same detected tag, and the star joins them back together by
     * value. Canonicalise one side only and the star misses the row the use
     * case just wrote — reads unsaved, then saves a second copy under the
     * legacy spelling. Reverting EITHER half turns the single row into two.
     */
    @Test
    fun `a detected legacy tag leaves one row that the star can still find`() {
        val prefs = FakeTranslatePrefsRepository().apply { source.value = "auto" }
        val repository = FakeTranslationRepository()
        val vm = viewModel(translator = detecting("iw"), prefs = prefs, repository = repository)
        settle()

        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()

        val state = vm.uiState.value as TextUiState.Result
        assertThat(state.resolvedSourceLang).isEqualTo("he")
        assertThat(repository.saved.single().sourceLang).isEqualTo("he")
        assertThat(vm.resultFavourite.value).isFalse()

        vm.onToggleFavourite()
        settle()

        assertThat(repository.saved).hasSize(1) // the star found its own row
        assertThat(repository.saved.single().favourite).isTrue()
        assertThat(vm.resultFavourite.value).isTrue()
    }

    /**
     * Rows written before the detect door closed keep their spelling, and that
     * is the whole of the "tolerate legacy on read" decision: a reopened row
     * must stay findable by the same lookups it was findable by yesterday.
     * Canonicalise the id on the way out of the store and the star queries past
     * the row it is showing — the pre-existing star reads as unsaved and the
     * next tap writes a canonical duplicate beside it.
     */
    @Test
    fun `reopening a legacy row keeps its stored spelling and stars that row`() =
        runTest(dispatcher) {
            val repository = FakeTranslationRepository()
            repository.save(
                Translation(
                    sourceLang = "iw",
                    sourceText = "Good morning",
                    targetLang = "en",
                    targetText = "Boker tov",
                    engine = Engine.ONLINE_GOOGLE,
                    createdAt = 1L,
                ),
            )
            val vm = viewModel(repository = repository)
            settle()

            vm.onHistoryPick(repository.saved.single())
            settle()

            val state = vm.uiState.value as TextUiState.Result
            assertThat(state.resolvedSourceLang).isEqualTo("iw")
            assertThat(state.request.sourceLang).isEqualTo("iw")

            vm.onToggleFavourite()
            settle()

            assertThat(repository.saved).hasSize(1) // no canonical twin was created
            assertThat(repository.saved.single().favourite).isTrue()
        }

    /** G7's shape with the detected tag swapped — the fixture rule's "add a row", not "mutate a tuple". */
    private fun detecting(tag: String) =
        FakeTranslator(
            golden =
                mapOf(
                    FakeTranslator.GoldenKey("Good morning", "auto", "fr", ModeId.AUTO) to
                        TranslationOutcome.Success("Bonjour (fake)", Engine.OFFLINE_MLKIT, detectedSource = tag),
                ),
        )
}
