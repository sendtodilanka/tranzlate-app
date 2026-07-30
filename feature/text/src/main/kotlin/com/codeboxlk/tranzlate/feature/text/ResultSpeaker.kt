package com.codeboxlk.tranzlate.feature.text

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import javax.inject.Singleton

/**
 * TTS ask-surface for the result face (issue #84). The screen owns the icon
 * state through [speaking]; the platform engine stays behind this seam so the
 * ViewModel tests fake it.
 */
interface ResultSpeaker {
    /** True while an utterance is playing — drives the play ⇄ stop icon. */
    val speaking: StateFlow<Boolean>

    /** Starts reading [text] in [languageTag]; false = engine/language unavailable. */
    fun speak(
        text: String,
        languageTag: String,
    ): Boolean

    fun stop()
}

private const val UTTERANCE_ID = "tranzlate_result"

/**
 * Android TextToSpeech adapter (old app's SpeechHelper studied as behaviour
 * reference — written fresh): async engine init, QUEUE_FLUSH per speak, the
 * progress listener drives [speaking] (its callbacks arrive off-main —
 * StateFlow writes are thread-safe), and [stop] flips the state itself because
 * the platform fires no onDone for a manual stop.
 */
@Singleton
class AndroidResultSpeaker
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ResultSpeaker {
        private val _speaking = MutableStateFlow(false)
        override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

        private var ready = false
        private val tts: TextToSpeech =
            TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
            }.apply {
                setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _speaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _speaking.value = false
                        }

                        @Deprecated("platform still calls it")
                        override fun onError(utteranceId: String?) {
                            _speaking.value = false
                        }
                    },
                )
            }

        override fun speak(
            text: String,
            languageTag: String,
        ): Boolean {
            if (!ready) return false
            val result = tts.setLanguage(Locale.forLanguageTag(languageTag))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return false
            }
            return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) ==
                TextToSpeech.SUCCESS
        }

        override fun stop() {
            tts.stop()
            _speaking.value = false // no onDone fires for a manual stop
        }
    }

/** UI-platform helper binding — NOT one of the four brain seams (plan §6.1). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ResultSpeakerModule {
    @Binds
    abstract fun resultSpeaker(impl: AndroidResultSpeaker): ResultSpeaker
}
