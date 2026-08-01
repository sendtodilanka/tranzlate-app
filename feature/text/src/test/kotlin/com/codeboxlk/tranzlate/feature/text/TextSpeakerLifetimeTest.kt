package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.ViewModelStore
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test

/**
 * WHEN a speech engine is held, and what the speak button is allowed to say
 * (issues #84 · #149 · #159 co-verify).
 *
 * Its own class rather than another section of `TextViewModelTest`: a
 * `TextToSpeech` is a bound service connection, not a value, so these are
 * lifetime tests rather than state-machine ones — and the two concerns together
 * put that class past detekt's `LargeClass`. The harness is shared
 * (`TextTestHarness.kt`).
 *
 * Measured on API 37 (`docs/research/issue-149-tts-lifetime.md`): while an
 * engine is held its process sits at `oom_adj 100` in the top-app scheduling
 * group, and the platform never takes it back on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextSpeakerLifetimeTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private fun viewModel(
        speaker: FakeResultSpeaker = FakeResultSpeaker(),
        translator: Translator = FakeTranslator(),
    ) = textViewModel(dispatcher, translator = translator, speaker = speaker)

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `speak toggles play and stop through the seam`() {
        val speaker = FakeResultSpeaker()
        val vm = resultOnScreen(speaker)

        vm.onSpeak() // play
        settle()
        assertThat(speaker.speaks).isEqualTo(1)
        assertThat(speaker.lastLanguage).isEqualTo("fr") // reads in the TARGET language
        assertThat(vm.speaking.value).isTrue()

        vm.onSpeak() // stop
        settle()
        assertThat(speaker.stops).isEqualTo(1)
        assertThat(vm.speaking.value).isFalse()
    }

    @Test
    fun `leaving the composer stops any reading`() {
        val speaker = FakeResultSpeaker()
        val vm = resultOnScreen(speaker)
        vm.onSpeak()
        settle()

        vm.onComposerDismissed()

        assertThat(speaker.stops).isEqualTo(1)
        assertThat(vm.speaking.value).isFalse()
    }

    // ---- issue #159 co-verify (block 2): "not ready yet" is not "unavailable" -
    // prepare() starts a ~500ms bind at Translating, and a CACHE HIT renders the
    // result immediately — so a tap can land while the engine is still coming.
    // On device that tap said "Speech isn't available for this language on this
    // device" and the same button worked seconds later. The window is real; the
    // message was false.

    @Test
    fun `a tap that beats the bind waits for the engine instead of blaming the language`() {
        val speaker = FakeResultSpeaker().apply { bindDelayMs = 500 }
        val vm = resultOnScreen(speaker)

        vm.onSpeak()

        // Mid-bind: nothing has been said to the user, and nothing is owed.
        assertThat(vm.speakNotice.value).isNull()

        settle()
        assertThat(speaker.speaks).isEqualTo(1)
        assertThat(vm.speaking.value).isTrue()
        assertThat(vm.speakNotice.value).isNull()
    }

    @Test
    fun `a second tap during the bind stops the request instead of queueing another`() {
        val speaker = FakeResultSpeaker().apply { bindDelayMs = 500 }
        val vm = resultOnScreen(speaker)

        vm.onSpeak()
        vm.onSpeak() // the stop half of the toggle, before any audio exists
        settle()

        assertThat(speaker.speaks).isEqualTo(0)
        assertThat(speaker.stops).isEqualTo(1)
        assertThat(vm.speakNotice.value).isNull()
    }

    @Test
    fun `a language with no voice says so, and the message is owed only once`() {
        val speaker = FakeResultSpeaker().apply { outcome = SpeakOutcome.NO_VOICE }
        val vm = resultOnScreen(speaker)

        vm.onSpeak()
        settle()

        assertThat(vm.speakNotice.value).isEqualTo(SpeakOutcome.NO_VOICE)
        vm.onSpeakNoticeShown()
        assertThat(vm.speakNotice.value).isNull()
    }

    @Test
    fun `a device with no speech engine gets the engine message, not the language one`() {
        val speaker = FakeResultSpeaker().apply { outcome = SpeakOutcome.ENGINE_UNAVAILABLE }
        val vm = resultOnScreen(speaker)

        vm.onSpeak()
        settle()

        assertThat(vm.speakNotice.value).isEqualTo(SpeakOutcome.ENGINE_UNAVAILABLE)
    }

    @Test
    fun `a request abandoned mid-bind says nothing at all`() {
        // The engine was released while the tap waited — backgrounding, or the
        // face leaving the result. Reporting that as a failure would put a
        // message on screen about a request the user already walked away from.
        val speaker = FakeResultSpeaker().apply { outcome = SpeakOutcome.CANCELLED }
        val vm = resultOnScreen(speaker)

        vm.onSpeak()
        settle()

        assertThat(vm.speakNotice.value).isNull()
    }

    // ---- issue #149: the speech engine's lifetime ----------------------------
    // A TextToSpeech is a bound service connection. Measured on API 37
    // (docs/research/issue-149-tts-lifetime.md): while one is held, the engine
    // process sits at oom_adj 100 in the top-app scheduling group and the
    // platform never takes it back on its own. These pin WHEN we hold one.

    /** A started host with a translated result on screen — the state that can speak. */
    private fun resultOnScreen(speaker: FakeResultSpeaker): TextViewModel {
        val vm = viewModel(speaker = speaker)
        vm.onHostStarted()
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()
        check(vm.uiState.value is TextUiState.Result)
        return vm
    }

    @Test
    fun `no speech engine is held until an answer is on its way`() {
        val speaker = FakeResultSpeaker()
        val vm = viewModel(speaker = speaker)
        vm.onHostStarted()
        settle()

        // The regression this defends: the adapter used to build its engine in a
        // field initializer of a @Singleton, so reaching Home — typing nothing,
        // translating nothing — was already enough to pin the TTS process.
        assertThat(speaker.prepares).isEqualTo(0)
        assertThat(speaker.held).isFalse()

        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()

        assertThat(speaker.held).isTrue()
    }

    @Test
    fun `the engine is bound alongside the translation, not after it`() {
        val speaker = FakeResultSpeaker()
        val vm = viewModel(speaker = speaker)
        vm.onHostStarted()
        settle()
        vm.onInputChange("Good morning")

        vm.onTranslate()

        // startTranslation sets Translating SYNCHRONOUSLY, so this frame is the
        // shimmer. The whole latency argument for preparing at Translating
        // rather than at Result lives HERE: the ~500ms bind has to be running
        // beside the translation, not in front of the first tap's audio. Until
        // this assertion existed, moving prepare() to Result left the suite
        // green — the PR's central justification was unpinned.
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Translating::class.java)
        assertThat(speaker.held).isTrue()

        settle()
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)
        assertThat(speaker.prepares).isEqualTo(1) // ONE engine across both faces
    }

    @Test
    fun `the engine is given back when the composer is left`() {
        val speaker = FakeResultSpeaker()
        val vm = resultOnScreen(speaker)
        assertThat(speaker.held).isTrue()

        vm.onComposerDismissed()
        settle()

        assertThat(speaker.releases).isEqualTo(1)
        assertThat(speaker.held).isFalse()
    }

    @Test
    fun `a failed translation holds no engine - there is nothing to read`() {
        val speaker = FakeResultSpeaker()
        val vm =
            viewModel(
                translator = FakeTranslator().apply { forcedFailure = AttemptCause.OFFLINE },
                speaker = speaker,
            )
        vm.onHostStarted()
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        settle()

        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Error::class.java)
        assertThat(speaker.held).isFalse()
    }

    @Test
    fun `the engine is given back when the host goes away`() {
        val speaker = FakeResultSpeaker()
        val vm = resultOnScreen(speaker)
        assertThat(speaker.held).isTrue()

        // A result left on screen never reaches a face that releases, so the
        // host's own end has to. onCleared is protected — a ViewModelStore is
        // how the platform reaches it, and how this test does.
        val store = ViewModelStore()
        store.put("text", vm)
        store.clear()

        assertThat(speaker.releases).isEqualTo(1)
        assertThat(speaker.held).isFalse()
    }

    // ---- issue #159 co-verify (block 1): backgrounding is the reported harm ---
    // This ViewModel is hoisted OUTSIDE the NavDisplay entries, so it lives in
    // the Activity's ViewModelStore and onCleared() runs only when the Activity
    // finishes. Backgrounding a result changes no state at all — so before this,
    // pressing HOME on a result left the engine bound at adj 100 behind an app
    // at adj 900, which is the "before the fix" row of the research record.

    @Test
    fun `backgrounding a result gives the engine back, and returning binds it again`() {
        val speaker = FakeResultSpeaker()
        val vm = resultOnScreen(speaker)
        assertThat(speaker.held).isTrue()

        vm.onHostStopped() // HOME, with the result still the current face
        settle()

        assertThat(speaker.releases).isEqualTo(1)
        assertThat(speaker.held).isFalse()
        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)

        vm.onHostStarted() // back to the same result

        // Re-bound on return, so the first tap after coming back is still warm —
        // the whole point of preparing early is not spent by the release.
        assertThat(speaker.held).isTrue()
        assertThat(speaker.prepares).isEqualTo(2)
    }

    @Test
    fun `a stopped host binds nothing, even when an answer arrives`() {
        val speaker = FakeResultSpeaker()
        val vm = viewModel(speaker = speaker)
        vm.onHostStarted()
        settle()
        vm.onInputChange("Good morning")
        vm.onTranslate()
        vm.onHostStopped() // backgrounded while the translation was in flight
        settle()

        assertThat(vm.uiState.value).isInstanceOf(TextUiState.Result::class.java)
        assertThat(speaker.held).isFalse()
    }
}
