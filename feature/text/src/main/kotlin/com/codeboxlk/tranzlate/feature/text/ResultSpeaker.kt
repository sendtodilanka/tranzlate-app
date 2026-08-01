package com.codeboxlk.tranzlate.feature.text

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/**
 * TTS ask-surface for the result face (issue #84). The screen owns the icon
 * state through [speaking]; the platform engine stays behind this seam so the
 * ViewModel tests fake it.
 *
 * The seam carries its own LIFETIME (issue #149), because a text-to-speech
 * engine is a bound service connection and not a value: whoever holds one is
 * holding another process open. [prepare] and [release] are how the consumer
 * says when that is worth doing.
 */
interface ResultSpeaker {
    /** True while an utterance is playing — drives the play ⇄ stop icon. */
    val speaking: StateFlow<Boolean>

    /**
     * Binds an engine ahead of the first [speak], so the tap does not pay the
     * bind. Idempotent — calling it on a prepared speaker does nothing.
     */
    fun prepare()

    /** Starts reading [text] in [languageTag]; false = engine/language unavailable. */
    fun speak(
        text: String,
        languageTag: String,
    ): Boolean

    fun stop()

    /**
     * Gives the engine back. Idempotent, and never final: a later [prepare] or
     * [speak] binds a fresh one.
     */
    fun release()
}

private const val UTTERANCE_ID = "tranzlate_result"

private const val TAG = "ResultSpeaker"

/**
 * Android TextToSpeech adapter (old app's SpeechHelper studied as behaviour
 * reference — written fresh): async engine init, QUEUE_FLUSH per speak, the
 * progress listener drives [speaking] (its callbacks arrive off-main —
 * StateFlow writes are thread-safe), and [stop] flips the state itself because
 * the platform fires no onDone for a manual stop.
 *
 * LIFETIME (issue #149). This class used to be a `@Singleton` that built its
 * engine in a field initializer and never shut it down. Measured on API 37
 * (`docs/research/issue-149-tts-lifetime.md`): merely launching the app started
 * `com.google.android.tts` and pinned it at oom_adj 100 with the top-app
 * scheduling group — no translation, no speak tap — and it stayed pinned there
 * with the app in the BACKGROUND, dropping to a cached, killable process only
 * when our process died. That is the platform behaving as designed: the system
 * binds the engine for us with `BIND_AUTO_CREATE | BIND_SCHEDULE_LIKE_TOP_APP`
 * and disables its own auto-unbind
 * (`TextToSpeechManagerPerUserService.getAutoDisconnectTimeoutMs()` returns
 * `PERMANENT_BOUND_TIMEOUT_MS`, whose value is 0 and whose contract is "do not
 * unbind"). Nothing but `shutdown()` ends it.
 *
 * The opposite extreme — shut down after every utterance, the shape the #130
 * ruling gives the offline-voice CATALOG — was measured too, and it is not free
 * either: a rebind costs ~500ms, and tap→audio on a fresh engine was 670ms
 * against 1-8ms on one already bound. Enumeration can pay that once; a play/stop
 * toggle would pay it on every tap.
 *
 * So the engine lives exactly as long as its consumer has something to say, and
 * [release] is called when that stops being true — which is also where Google's
 * own guidance puts it ("It is good practice … to call this method in the
 * onDestroy() method of an Activity").
 */
class AndroidResultSpeaker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ResultSpeaker {
        private val _speaking = MutableStateFlow(false)
        override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

        private var engine: TextToSpeech? = null

        @Volatile
        private var ready = false

        /**
         * Bumped by every [prepare] and [release]. A released engine's init and
         * progress callbacks can still be in flight, and without this they would
         * report readiness — or a playing icon — for an engine that is gone.
         */
        @Volatile
        private var generation = 0

        override fun prepare() {
            if (engine != null) return
            val mine = ++generation
            ready = false
            engine =
                TextToSpeech(context) { status ->
                    if (mine == generation) ready = status == TextToSpeech.SUCCESS
                }.apply {
                    setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = report(mine, playing = true)

                            override fun onDone(utteranceId: String?) = report(mine, playing = false)

                            @Deprecated("platform still calls it")
                            override fun onError(utteranceId: String?) = report(mine, playing = false)
                        },
                    )
                }
        }

        private fun report(
            boundAt: Int,
            playing: Boolean,
        ) {
            if (boundAt == generation) _speaking.value = playing
        }

        override fun speak(
            text: String,
            languageTag: String,
        ): Boolean {
            // A speak with no prepare still works; it just pays the bind here,
            // and the engine is not ready within this call — so the caller is
            // told "unavailable" exactly as it would be for a missing engine.
            prepare()
            val tts = engine ?: return false
            if (!ready) return false
            val result = tts.setLanguage(Locale.forLanguageTag(languageTag))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return false
            }
            return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) ==
                TextToSpeech.SUCCESS
        }

        override fun stop() {
            engine?.stop()
            _speaking.value = false // no onDone fires for a manual stop
        }

        override fun release() {
            val tts = engine ?: return
            engine = null
            generation++
            ready = false
            _speaking.value = false
            try {
                tts.stop()
                tts.shutdown()
            } catch (
                @Suppress("TooGenericExceptionCaught") thrown: RuntimeException,
            ) {
                // shutdown() is NOT safe by itself: AOSP routes it to
                // `unbindService` with no guard (TextToSpeech.java:956-961 →
                // Connection.disconnect :2430), and `runAction` catches only
                // RemoteException — so releasing an engine that never finished
                // binding throws IllegalArgumentException: "Service not
                // registered". This runs from onCleared and from every state
                // change, so an escape here would take the screen down.
                Log.w(TAG, "engine release", thrown)
            }
        }
    }

/** UI-platform helper binding — NOT one of the four brain seams (plan §6.1). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ResultSpeakerModule {
    /**
     * Deliberately UNSCOPED (issue #149). A `@Singleton` here would hand the
     * same engine to every future consumer — Voice and Dialog both want one —
     * and then one screen's [ResultSpeaker.release] would cut another screen's
     * audio. One engine per consumer, released by that consumer.
     */
    @Binds
    abstract fun resultSpeaker(impl: AndroidResultSpeaker): ResultSpeaker
}
