package com.codeboxlk.tranzlate

import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.codeboxlk.tranzlate.core.common.DefaultDispatcherProvider
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.di.AndroidOfflineVoiceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Experiment E-V1 (issue #130 rev.3, risk R4) — the half of the offline-voice
 * seam that only a device can answer. Findings and the exact commands:
 * `docs/research/issue-130-e-v1-voice-enumeration.md`.
 *
 * Deliberately NOT a Hilt test and deliberately free of Compose/Espresso: it
 * measures the platform, and the instrumented suite's Espresso rules do not run
 * on API 35+ images (issue #40).
 *
 * Its assertions are the ones that hold on EVERY device, including one with no
 * TTS engine at all — a probe that demanded voices would be a test of the
 * emulator image, not of this code. What the run is actually for is the logged
 * dump: whether the engine is visible at all, what locales it reports, and what
 * they resolve to. Read it with:
 *
 *   adb -s <device> logcat -d -s OfflineVoiceProbe
 */
class OfflineVoiceEnumerationProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The `<queries>` half. Package visibility is what this measures: with the
     * manifest block the TTS engines resolve, without it this list is empty on
     * API 30+ and every later step in the enumeration is working with nothing.
     */
    @Test
    fun ttsEnginesAreVisibleToThisApp() {
        val engines =
            context.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.GET_META_DATA,
            )

        Log.i(TAG, "visible TTS engines: ${engines.size} -> ${engines.map { it.serviceInfo.packageName }}")
    }

    /**
     * The enumeration end to end, against the real `TextToSpeech`. The invariant
     * that holds everywhere: whatever comes back is made of CATALOG ids, because
     * an id no catalog row carries could only ever mark nothing.
     */
    @Test
    fun offlineVoiceIdsAreCatalogIds() {
        val catalog = AndroidOfflineVoiceCatalog(context, DefaultDispatcherProvider())

        val ids = runBlocking { catalog.offlineVoiceLanguageIds() }

        Log.i(TAG, "offline voice ids (${ids.size}): ${ids.sorted()}")
        val strangers = ids - LanguageTagResolver.canonicalIds.toSet()
        assertTrue("ids no catalog row can carry: $strangers", strangers.isEmpty())
    }

    /**
     * The raw platform answer, dumped rather than asserted — this is the row
     * that tells us whether `Voice.locale` arrives as a BCP-47-shaped tag or as
     * one of the ISO3 locales some engines build (`eng-USA`), which the resolver
     * could not match and which would make the whole feature silently empty.
     */
    @Test
    fun rawVoiceLocalesAreDumpedForTheRecord() {
        val ready = java.util.concurrent.CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts =
            TextToSpeech(context) {
                status = it
                ready.countDown()
            }
        try {
            val signalled = ready.await(INIT_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            Log.i(TAG, "onInit signalled=$signalled status=$status (SUCCESS=${TextToSpeech.SUCCESS})")
            val voices = tts.voices
            Log.i(TAG, "getVoices() -> ${voices?.size ?: "null"}")
            voices.orEmpty().sortedBy { it.name }.forEach { voice ->
                Log.i(
                    TAG,
                    "voice=${voice.name} locale=${voice.locale} tag=${voice.locale.toLanguageTag()} " +
                        "network=${voice.isNetworkConnectionRequired} " +
                        "canonical=${LanguageTagResolver.canonicalId(voice.locale.toLanguageTag())}",
                )
            }
        } finally {
            tts.shutdown()
        }
    }

    private companion object {
        const val TAG = "OfflineVoiceProbe"
        const val INIT_WAIT_SECONDS = 10L
    }
}
