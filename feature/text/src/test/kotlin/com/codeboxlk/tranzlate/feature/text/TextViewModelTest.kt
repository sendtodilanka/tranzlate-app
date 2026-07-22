package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.FailureReason
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private class RecordingAdsCoordinator : AdsCoordinator {
        var completedCount = 0

        override suspend fun onTranslationCompleted() {
            completedCount++
        }
    }

    private class FakeTranslatePrefsRepository : TranslatePrefsRepository {
        val source = MutableStateFlow("en")
        val target = MutableStateFlow("fr")
        val mode = MutableStateFlow(ModeId.AUTO)

        override val sourceLang: Flow<String> = source
        override val targetLang: Flow<String> = target
        override val textMode: Flow<ModeId> = mode

        override suspend fun setSourceLang(id: String) {
            source.value = id
        }

        override suspend fun setTargetLang(id: String) {
            target.value = id
        }

        override suspend fun setLanguagePair(
            sourceId: String,
            targetId: String,
        ) {
            source.value = sourceId
            target.value = targetId
        }
    }

    private class FakeLanguageRepository : LanguageRepository {
        override fun languages(): Flow<List<Language>> =
            flowOf(
                listOf(
                    Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                    Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
                ),
            )

        override suspend fun setLastUsed(
            languageId: String,
            atMillis: Long,
        ) = Unit
    }

    private fun viewModel(
        translator: FakeTranslator = FakeTranslator(),
        prefs: FakeTranslatePrefsRepository = FakeTranslatePrefsRepository(),
        clock: FakeClock = FakeClock(),
        handle: SavedStateHandle = SavedStateHandle(),
    ): TextViewModel {
        val useCase =
            TranslateTextUseCase(
                translator,
                FakeFeatureAccess(),
                FakeUsagePolicy(left = 5),
                RecordingAdsCoordinator(),
                FakeTranslationRepository(),
                clock,
            )
        return TextViewModel(
            translateText = useCase,
            prefs = prefs,
            languageRepository = FakeLanguageRepository(),
            dispatchers = TestDispatcherProvider(dispatcher),
            clock = clock,
            savedStateHandle = handle,
        )
    }

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

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
            ),
        )
        assertThat(translator.calls.last().mode).isEqualTo(ModeId.AUTO) // spy
    }

    // ---- Error → Retry (G10 pattern: forcedFailure NETWORK) ------------------

    @Test
    fun `network failure surfaces Error and retry after recovery reaches Result`() {
        val translator = FakeTranslator().apply { forcedFailure = FailureReason.NETWORK }
        val vm = viewModel(translator)
        settle()

        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()

        val request = TranslateRequest("Good morning", "en", "fr", ModeId.AUTO)
        assertThat(vm.uiState.value).isEqualTo(TextUiState.Error(request, FailureReason.NETWORK))

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

    @Test
    fun `restoreResultIfNeeded replays the persisted request after process death`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        settle()
        first.onInputChange("Good morning")
        first.onTranslate()
        settle()

        // "Process death": a NEW ViewModel over the same restored handle starts Idle.
        val second = viewModel(handle = handle)
        settle()
        assertThat(second.uiState.value).isEqualTo(TextUiState.Idle)

        second.restoreResultIfNeeded()
        settle()

        assertThat(second.uiState.value).isInstanceOf(TextUiState.Result::class.java)
        assertThat((second.uiState.value as TextUiState.Result).translatedText).isEqualTo("Bonjour (fake)")
    }
}
