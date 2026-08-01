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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

/**
 * What a speak request actually did — the UI turns this into guidance, so each
 * value has to be something we can honestly say out loud (EDGE_CASES
 * no-dead-end).
 *
 * There is deliberately **no** "not ready yet" value: a still-binding engine is
 * waited for, never reported (issue #159 co-verify, block 2).
 */
enum class SpeakOutcome {
    /** Audio is on its way. */
    STARTED,

    /** The request was given up on — the engine was released or the user stopped it. Say nothing. */
    CANCELLED,

    /** The engine bound, but has no voice for that language. */
    NO_VOICE,

    /** No speech engine on this device, or it refused to start. */
    ENGINE_UNAVAILABLE,
}

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

    /**
     * Starts reading [text] in [languageTag]. **Suspends** while the engine is
     * still binding: [prepare] starts a ~500ms bind, and a cache hit can put a
     * result on screen before it finishes, so the tap waits for the engine it
     * was promised instead of being told the language is unsupported.
     */
    suspend fun speak(
        text: String,
        languageTag: String,
    ): SpeakOutcome

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
 * How long a speak tap waits for a bind that never reports. The measured bind is
 * 478-601ms on this emulator (`docs/research/issue-149-tts-lifetime.md`) and a
 * failing engine reports `onInit(ERROR)` rather than hanging, so this is the
 * belt-and-braces bound, not the expected path. An engine that has said nothing
 * in five seconds is reported as unavailable — which points the user at the
 * speech engine, the right place to look either way.
 */
private const val BIND_TIMEOUT_MS = 5_000L

/**
 * Runs one platform call and swallows a `RuntimeException` escape, returning
 * null instead (issue #159 co-verify, medium 5).
 *
 * `onEscape` is a parameter rather than a direct `Log.w` so the JVM tests can
 * drive this without `android.util.Log`.
 */
internal inline fun <T> guarded(
    what: String,
    onEscape: (String, RuntimeException) -> Unit,
    action: () -> T,
): T? =
    try {
        action()
    } catch (
        @Suppress("TooGenericExceptionCaught") thrown: RuntimeException,
    ) {
        onEscape(what, thrown)
        null
    }

/**
 * The release SEQUENCE, kept apart from the platform type so a JVM test can
 * drive the escape (issue #159 co-verify, medium 4).
 *
 * The two calls are guarded **separately** on purpose. They used to share one
 * `try`, and by the time it runs the caller has already nulled its field — so a
 * `stop()` that threw skipped `shutdown()` and left an engine nobody could ever
 * release again. That is the exact leak this whole issue exists to prevent.
 */
internal fun guardedRelease(
    stop: () -> Unit,
    shutdown: () -> Unit,
    onEscape: (String, RuntimeException) -> Unit,
) {
    guarded("engine stop", onEscape, stop)
    guarded("engine shutdown", onEscape, shutdown)
}

/**
 * Waits for a bind to REPORT, rather than asking whether it has already reported
 * (issue #159 co-verify, block 2). Null signal = nothing was ever started.
 */
internal suspend fun awaitBind(
    signal: CompletableDeferred<Boolean>?,
    timeoutMs: Long,
): Boolean = signal != null && withTimeoutOrNull(timeoutMs) { signal.await() } == true

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

        /**
         * Completed by the engine's own init callback: true = bound and usable.
         * A speak tap AWAITS this instead of testing it, so a tap that beats the
         * bind is answered with audio and not with a false "unavailable".
         */
        private var bind: CompletableDeferred<Boolean>? = null

        /**
         * Bumped by every [prepare] and [release]. A released engine's init and
         * progress callbacks can still be in flight, and without this they would
         * report readiness — or a playing icon — for an engine that is gone.
         */
        @Volatile
        private var generation = 0

        /** Where a guarded platform escape goes in production. */
        private val escape: (String, RuntimeException) -> Unit = { what, thrown ->
            Log.w(TAG, what, thrown)
        }

        override fun prepare() {
            if (engine != null) return
            val mine = ++generation
            // The constructor reaches the platform, so it is guarded exactly like
            // the release is (issue #159 co-verify, medium 5): this runs from the
            // ViewModel's state funnel and from restoreState(), so an escape here
            // would take the screen down.
            val signal = CompletableDeferred<Boolean>()
            bind = signal
            engine =
                guarded("engine bind", escape) {
                    TextToSpeech(context) { status ->
                        signal.complete(status == TextToSpeech.SUCCESS)
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
            // Nothing will ever call back for an engine that failed to construct.
            if (engine == null) signal.complete(false)
        }

        private fun report(
            boundAt: Int,
            playing: Boolean,
        ) {
            if (boundAt == generation) _speaking.value = playing
        }

        override suspend fun speak(
            text: String,
            languageTag: String,
        ): SpeakOutcome {
            // A speak with no prepare still works; it just pays the bind here.
            prepare()
            val tts = engine ?: return SpeakOutcome.ENGINE_UNAVAILABLE
            val mine = generation
            val bound = awaitBind(bind, BIND_TIMEOUT_MS)
            // Released while we waited (host stopped, face left the result) —
            // the request is stale and the user is no longer looking at it.
            if (mine != generation) return SpeakOutcome.CANCELLED
            if (!bound) return SpeakOutcome.ENGINE_UNAVAILABLE
            val lang = guarded("setLanguage", escape) { tts.setLanguage(Locale.forLanguageTag(languageTag)) }
            if (lang == null || lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                return SpeakOutcome.NO_VOICE
            }
            val started =
                guarded("speak", escape) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS
                } == true
            if (!started) return SpeakOutcome.ENGINE_UNAVAILABLE
            // The icon answers the tap now rather than when onStart arrives —
            // the gap is small but it is the only feedback the button has.
            _speaking.value = true
            return SpeakOutcome.STARTED
        }

        override fun stop() {
            guarded("engine stop", escape) { engine?.stop() }
            _speaking.value = false // no onDone fires for a manual stop
        }

        override fun release() {
            val tts = engine ?: return
            engine = null
            generation++
            // Frees a speak that is waiting on this engine; the generation bump
            // above is what tells it the wait was stale rather than a failure.
            bind?.complete(false)
            bind = null
            _speaking.value = false
            // shutdown() is NOT safe by itself, on ONE of the two paths. AOSP's
            // `connectToEngine()` picks `SystemConnection` or `DirectConnection`
            // from `mIsSystem`; it is `DirectConnection.disconnect`
            // (`TextToSpeech.java` :2430, the path AOSP marks legacy) that calls
            // `unbindService` unguarded, and `runAction` catches only
            // RemoteException — so releasing an engine that never finished
            // binding throws IllegalArgumentException: "Service not registered".
            // The API 31+ `SystemConnection.disconnect` (:2488-2500) unbinds
            // nothing itself. The guard is therefore for the OLDER path and for
            // engines that still take it, not for the measured device. Line-level
            // reading credited to the #159 co-verify lens; not re-verified here.
            guardedRelease(stop = { tts.stop() }, shutdown = { tts.shutdown() }, onEscape = escape)
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
     *
     * The Konsist gate reads THIS function too, not only the class: `@Binds`
     * plus a scope annotation is the idiomatic Hilt way to make a binding
     * process-lifetime, and it re-created the original bug with the class
     * itself left unscoped.
     */
    @Binds
    abstract fun resultSpeaker(impl: AndroidResultSpeaker): ResultSpeaker
}
