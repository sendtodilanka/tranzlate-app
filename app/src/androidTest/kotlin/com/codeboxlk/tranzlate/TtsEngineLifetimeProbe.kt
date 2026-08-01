package com.codeboxlk.tranzlate

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Experiment E-149 (issue #149) — the half of the speech-engine lifetime question
 * that only a device can answer: what a rebind costs, and what the user hears.
 * Findings, commands and the process-state samples that go with them:
 * `docs/research/issue-149-tts-lifetime.md`.
 *
 * Deliberately NOT a Hilt test and free of Compose/Espresso — it measures the
 * platform, not this app, and the instrumented suite's Espresso rules do not run
 * on API 35+ images (issue #40).
 *
 * It asserts nothing. Every number here is a property of the device it ran on, so
 * an assertion would be a test of the emulator image; what the run is for is the
 * logged dump, read with:
 *
 *   adb -s <device> logcat -d -s TtsLifetimeProbe
 *
 * [holdIdleBindingForHostSampling] exists to be sampled from the host WHILE it
 * runs — `dumpsys activity processes com.google.android.tts` between the
 * MARKER_BOUND and MARKER_RELEASING lines is where the idle cost is visible.
 */
class TtsEngineLifetimeProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** construct → onInit, repeated with a shutdown() between each — the rebind cost. */
    @Test
    fun rebindLatencyAcrossRepeatedShutdowns() {
        repeat(REPEATS) { i ->
            val started = SystemClock.elapsedRealtime()
            val ready = CountDownLatch(1)
            var status = TextToSpeech.ERROR
            val tts =
                TextToSpeech(context) {
                    status = it
                    ready.countDown()
                }
            val signalled = ready.await(WAIT_SECONDS, TimeUnit.SECONDS)
            val initMs = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "rebind#$i signalled=$signalled status=$status initMs=$initMs")
            val downStart = SystemClock.elapsedRealtime()
            tts.shutdown()
            Log.i(TAG, "rebind#$i shutdownMs=${SystemClock.elapsedRealtime() - downStart}")
            Thread.sleep(GAP_MS)
        }
    }

    /**
     * The user-visible number: how long after the tap does audio START, on a
     * fresh instance (construct → init → speak) versus on one already bound.
     */
    @Test
    fun firstAudioLatencyFreshVersusStanding() {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tapped = SystemClock.elapsedRealtime()
        val tts =
            TextToSpeech(context) {
                status = it
                ready.countDown()
            }
        try {
            val signalled = ready.await(WAIT_SECONDS, TimeUnit.SECONDS)
            Log.i(TAG, "fresh: signalled=$signalled status=$status initMs=${SystemClock.elapsedRealtime() - tapped}")
            if (status != TextToSpeech.SUCCESS) return

            val started = CountDownLatch(1)
            var startAt = 0L
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        startAt = SystemClock.elapsedRealtime()
                        started.countDown()
                    }

                    override fun onDone(utteranceId: String?) {
                        done.countDown()
                    }

                    @Deprecated("platform still calls it")
                    override fun onError(utteranceId: String?) {
                        started.countDown()
                        done.countDown()
                    }
                },
            )
            tts.language = java.util.Locale.US
            tts.speak(TEXT, TextToSpeech.QUEUE_FLUSH, null, "probe-cold")
            started.await(WAIT_SECONDS, TimeUnit.SECONDS)
            Log.i(TAG, "fresh: tap→audio ${startAt - tapped}ms (init included)")
            done.await(WAIT_SECONDS, TimeUnit.SECONDS)

            // …and again on the SAME, already-bound instance.
            repeat(REPEATS) { i ->
                val warmStarted = CountDownLatch(1)
                var warmAt = 0L
                val warmDone = CountDownLatch(1)
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            warmAt = SystemClock.elapsedRealtime()
                            warmStarted.countDown()
                        }

                        override fun onDone(utteranceId: String?) {
                            warmDone.countDown()
                        }

                        @Deprecated("platform still calls it")
                        override fun onError(utteranceId: String?) {
                            warmStarted.countDown()
                            warmDone.countDown()
                        }
                    },
                )
                val warmTap = SystemClock.elapsedRealtime()
                tts.speak(TEXT, TextToSpeech.QUEUE_FLUSH, null, "probe-warm-$i")
                warmStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)
                Log.i(TAG, "standing#$i: tap→audio ${warmAt - warmTap}ms")
                warmDone.await(WAIT_SECONDS, TimeUnit.SECONDS)
            }
        } finally {
            tts.shutdown()
        }
    }

    /** Holds one idle binding so the host can sample the engine process around it. */
    @Test
    fun holdIdleBindingForHostSampling() {
        val ready = CountDownLatch(1)
        val tts = TextToSpeech(context) { ready.countDown() }
        try {
            ready.await(WAIT_SECONDS, TimeUnit.SECONDS)
            Log.i(TAG, "MARKER_BOUND")
            Thread.sleep(HOLD_MS)
            Log.i(TAG, "MARKER_RELEASING")
        } finally {
            tts.shutdown()
        }
        Thread.sleep(HOLD_MS)
        Log.i(TAG, "MARKER_RELEASED")
    }

    private companion object {
        const val TAG = "TtsLifetimeProbe"
        const val REPEATS = 5
        const val WAIT_SECONDS = 10L
        const val GAP_MS = 1_000L
        const val HOLD_MS = 25_000L
        const val TEXT = "The quick brown fox jumps over the lazy dog."
    }
}
