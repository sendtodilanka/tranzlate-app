package com.codeboxlk.tranzlate.di

import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The catalog's rules, exercised without a device (issue #130 rev.3 U-3).
 *
 * `TextToSpeech` cannot be constructed in a JVM test and `Voice` cannot be
 * constructed at all, which is why [SpeechEngine] exists: everything the
 * catalog DECIDES — how long it waits, what it does with a null voice list,
 * which voices count, how a locale becomes a catalog id, and when the engine is
 * released — is decided on this side of that seam and is asserted here. What
 * only a device can answer (does the platform actually hand us voices, and does
 * package visibility let us see the engine at all) is experiment E-V1:
 * `docs/research/issue-130-e-v1-voice-enumeration.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidOfflineVoiceCatalogTest {
    /**
     * The hang case, and the reason there is a timeout at all: an engine that
     * binds but never calls `OnInitListener` — disabled, crashed, or mid-update
     * — leaves the await with nothing to resume it. Unbounded, this call never
     * returns, and the first caller keeps the one-shot lock forever, so EVERY
     * later caller hangs behind it too.
     *
     * Virtual time, never a real delay: the assertion is that exactly the
     * shipped timeout elapsed, which is also what proves the wait ended because
     * of the timeout and not because something else answered.
     */
    @Test
    fun `an engine that never initialises answers empty when the timeout expires`() =
        runTest {
            val connector = TestConnector(initialises = NEVER_SIGNALS)
            val catalog = catalog(connector)

            val ids = catalog.offlineVoiceLanguageIds()

            assertThat(ids).isEmpty()
            assertThat(testScheduler.currentTime)
                .isEqualTo(AndroidOfflineVoiceCatalog.INIT_TIMEOUT_MS)
        }

    /**
     * The timeout path is exactly the path a leaked engine would hide on: the
     * enumeration never reached its normal end, so only the `finally` can
     * release the binding. A `TextToSpeech` left bound holds the engine's
     * process alive for the lifetime of ours — the leak class this whole
     * connect-ask-disconnect shape exists to prevent (ruling risk R4).
     */
    @Test
    fun `the engine is released even when the timeout expires`() =
        runTest {
            val connector = TestConnector(initialises = NEVER_SIGNALS)

            catalog(connector).offlineVoiceLanguageIds()

            assertThat(connector.engine.shutdowns).isEqualTo(1)
        }

    /**
     * `getVoices()` really returns null — AOSP `TextToSpeech.java` routes it
     * through `runAction`, whose failure return is the null default, which is
     * what a connection that died between init and the call produces. The SDK
     * carries no nullability annotation, so Kotlin would have let the NPE
     * through to a screen that was only decorating a list row.
     */
    @Test
    fun `a null voice list answers empty instead of throwing`() =
        runTest {
            val connector = TestConnector(engine = FakeSpeechEngine(voices = null))

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).isEmpty()
            assertThat(connector.engine.shutdowns).isEqualTo(1)
        }

    /**
     * The mapping the whole seam exists for. Every row below is chosen because a
     * naive `voice.locale.language` read gets it WRONG:
     *
     * - `es-ES` → `es` needs the truncation the resolver does (RFC 4647 lookup);
     * - `pt-BR` → `pt-BR` must NOT truncate, because the catalog carries both;
     * - `zh-HK` → `zh-TW` is a table entry, not any part of the tag;
     * - `iw-IL` → `he` is a legacy alias, and `iw` is not a catalog id at all.
     *
     * `qq-ZZ` is the language nothing can serve: dropped rather than passed
     * through, because an id the catalog has no row for can only ever mark
     * nothing.
     */
    @Test
    fun `voice locales are resolved to canonical catalog ids`() =
        runTest {
            val connector =
                TestConnector(
                    engine =
                        FakeSpeechEngine(
                            voices =
                                listOf(
                                    offlineVoice("es-ES"),
                                    offlineVoice("pt-BR"),
                                    offlineVoice("zh-HK"),
                                    offlineVoice("iw-IL"),
                                    offlineVoice("qq-ZZ"),
                                ),
                        ),
                )

            val ids = catalog(connector).offlineVoiceLanguageIds()

            // `pt` and `zh` are the fix, not noise: a pt-BR voice must mark the
            // bare `pt` row, because that is the row the packs screen draws.
            // This assertion used to read exactly the defect — pt-BR alone.
            assertThat(ids).containsExactly("es", "pt-BR", "pt", "zh-TW", "zh", "he")
        }

    /**
     * The mark claims "this device speaks it with no connection". A voice that
     * needs the network is the one thing that would make that claim false, so
     * the filter is the claim.
     */
    @Test
    fun `voices that need the network are not offline voices`() =
        runTest {
            val connector =
                TestConnector(
                    engine =
                        FakeSpeechEngine(
                            voices =
                                listOf(
                                    offlineVoice("de-DE"),
                                    InstalledVoice(languageTag = "fr-FR", requiresNetwork = true),
                                ),
                        ),
                )

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).containsExactly("de")
        }

    /** An engine that reports failure has no voices to offer, and is still released. */
    @Test
    fun `an engine that reports an init failure answers empty`() =
        runTest {
            val connector =
                TestConnector(
                    engine = FakeSpeechEngine(voices = listOf(offlineVoice("en-US"))),
                    initialises = false,
                )

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).isEmpty()
            assertThat(connector.engine.shutdowns).isEqualTo(1)
        }

    /**
     * A dead binder mid-call raises from the read itself. It must reach the
     * caller as "no offline voices", not as an exception on a screen — and the
     * binding must still be released.
     */
    @Test
    fun `a failing voice read answers empty and still releases the engine`() =
        runTest {
            val connector = TestConnector(engine = FakeSpeechEngine(failOnRead = true))

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).isEmpty()
            assertThat(connector.engine.shutdowns).isEqualTo(1)
        }

    /**
     * One-shot: the answer cannot change while the app is running (installing a
     * voice means leaving the app), so a second ask must be the cache, not a
     * second binding.
     */
    @Test
    fun `the engine is bound once and the answer is cached`() =
        runTest {
            val connector = TestConnector(engine = FakeSpeechEngine(voices = listOf(offlineVoice("en-US"))))
            val catalog = catalog(connector)

            val first = catalog.offlineVoiceLanguageIds()
            val second = catalog.offlineVoiceLanguageIds()

            assertThat(first).containsExactly("en")
            assertThat(second).isEqualTo(first)
            assertThat(connector.connections).isEqualTo(1)
            assertThat(connector.engine.shutdowns).isEqualTo(1)
        }

    /**
     * What the mutex is FOR. Two screens can ask in the same frame; without the
     * lock each would bind its own engine, and the "one connection, briefly"
     * property this class is built around would hold only when nobody raced.
     *
     * The connector is deliberately slow to initialise so the second caller
     * arrives while the first still holds the lock — the interleaving a
     * fast-answering fake would never produce.
     */
    @Test
    fun `concurrent callers share the one enumeration`() =
        runTest {
            val connector =
                TestConnector(
                    engine = FakeSpeechEngine(voices = listOf(offlineVoice("en-US"))),
                    initialises = NEVER_SIGNALS,
                )
            val catalog = catalog(connector)
            val answers = mutableListOf<Set<String>>()

            backgroundScope.launch { answers += catalog.offlineVoiceLanguageIds() }
            backgroundScope.launch { answers += catalog.offlineVoiceLanguageIds() }
            runCurrent()
            connector.signalReady()
            runCurrent()

            assertThat(answers).hasSize(2)
            answers.forEach { assertThat(it).containsExactly("en") }
            assertThat(connector.connections).isEqualTo(1)
        }

    // ---- what a co-verify lens measured, each with its own red bar ---------

    /**
     * The catalog carries `fr-FR` and `pt-BR` rows next to bare `fr` and `pt`,
     * and `canonicalId` stops at the first row that matches. So an `fr-FR`
     * voice used to resolve to `fr-FR` — a row that is not offline-capable and
     * therefore never drawn — while the French row that IS drawn got nothing.
     * A device that can speak fr-FR can speak French.
     *
     * Spanish is here as the control: it passed before this fix only because
     * the catalog happens to have no `es-ES` row to absorb the voice.
     */
    @Test
    fun `a regional voice marks the language its row actually uses`() =
        runTest {
            val connector =
                TestConnector(
                    engine =
                        FakeSpeechEngine(
                            voices = listOf(offlineVoice("fr-FR"), offlineVoice("pt-BR"), offlineVoice("es-ES")),
                        ),
                )

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).containsAtLeast("fr", "pt", "es")
        }

    /**
     * `TextToSpeech.shutdown()` is not safe by itself: AOSP routes it to
     * `unbindService` with no guard, so releasing an engine that never bound
     * throws `IllegalArgumentException: Service not registered`. A lens
     * reproduced it. Escaping here reaches the picker's `stateIn`, which has
     * no `.catch`, and takes the screen down.
     */
    @Test
    fun `an engine that throws on release still answers instead of crashing`() =
        runTest {
            val connector =
                TestConnector(
                    engine =
                        FakeSpeechEngine(
                            voices = listOf(offlineVoice("en-US")),
                            failOnShutdown = true,
                        ),
                )

            val ids = catalog(connector).offlineVoiceLanguageIds()

            assertThat(ids).containsExactly("en")
        }

    /**
     * "Could not answer" is not "no voices". A busy or cold engine returns null
     * from the read; caching that would make one unlucky moment at startup mean
     * this device cannot speak for the rest of the process. The lens measured
     * exactly that, twice.
     */
    @Test
    fun `a device that could not answer is asked again, not written off`() =
        runTest {
            val connector = TestConnector(engine = FakeSpeechEngine(voices = null))
            val subject = catalog(connector)

            assertThat(subject.offlineVoiceLanguageIds()).isEmpty()
            connector.engine.installVoices(listOf(offlineVoice("en-US")))
            val second = subject.offlineVoiceLanguageIds()

            assertThat(second).containsExactly("en")
            assertThat(connector.connections).isEqualTo(2)
        }

    /**
     * Even a real answer expires. Voices are installed from Settings →
     * Text-to-speech, and PR-12's whole flow is to send the user there and
     * bring them back — so an answer that never expires means the mark they
     * just earned never appears. Same conclusion `RealOfflineModelManager`
     * reached about its own cache, for the same reason.
     */
    @Test
    fun `a voice installed while the app was away is noticed on return`() =
        runTest {
            val connector = TestConnector(engine = FakeSpeechEngine(voices = listOf(offlineVoice("en-US"))))
            val subject = catalog(connector)

            assertThat(subject.offlineVoiceLanguageIds()).containsExactly("en")
            connector.engine.installVoices(listOf(offlineVoice("en-US"), offlineVoice("si-LK")))
            advanceTimeBy(AndroidOfflineVoiceCatalog.ANSWER_FRESH_MS + 1)

            assertThat(subject.offlineVoiceLanguageIds()).containsAtLeast("en", "si")
        }

    private fun TestScope.catalog(connector: TestConnector) =
        AndroidOfflineVoiceCatalog(
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            connect = connector::connect,
            elapsedMillis = { testScheduler.currentTime },
        )
}

/** [TestConnector.initialises] value meaning "this engine never calls back at all". */
private val NEVER_SIGNALS: Boolean? = null

private fun offlineVoice(languageTag: String) = InstalledVoice(languageTag = languageTag, requiresNetwork = false)

/**
 * Stands in for one `TextToSpeech` binding and records what was done to it.
 *
 * @param voices what `getVoices()` answers; `null` is the platform's real
 *   failure return, not a test convenience.
 * @param failOnRead the binder died between init and the read.
 */
private class FakeSpeechEngine(
    private var voices: List<InstalledVoice>? = emptyList(),
    private val failOnRead: Boolean = false,
    /** AOSP's `shutdown()` routes to an unguarded `unbindService` — see the impl. */
    private val failOnShutdown: Boolean = false,
) : SpeechEngine {
    /** Lets a test move the device's voices between two asks. */
    fun installVoices(installed: List<InstalledVoice>) {
        voices = installed
    }

    var shutdowns: Int = 0
        private set

    override fun installedVoices(): List<InstalledVoice>? {
        check(!failOnRead) { "the engine died between init and the voice read" }
        return voices
    }

    override fun shutdown() {
        shutdowns++
        require(!failOnShutdown) { "Service not registered: android.speech.tts" }
    }
}

/**
 * Stands in for the platform's connect-and-call-back handshake.
 *
 * @param initialises `true` signals success as soon as the catalog connects,
 *   `false` signals failure, and [NEVER_SIGNALS] signals nothing ever. The
 *   immediate signalling is faithful: AOSP dispatches `ERROR` from inside the
 *   `TextToSpeech` constructor when no engine can be resolved, so the callback
 *   can genuinely land before the catalog holds the engine reference.
 */
private class TestConnector(
    val engine: FakeSpeechEngine = FakeSpeechEngine(),
    private val initialises: Boolean? = true,
) {
    var connections: Int = 0
        private set

    private var onReady: ((Boolean) -> Unit)? = null

    fun connect(callback: (Boolean) -> Unit): SpeechEngine {
        connections++
        onReady = callback
        initialises?.let(callback)
        return engine
    }

    /** Late readiness, for the race the mutex exists to lose gracefully. */
    fun signalReady() {
        onReady?.invoke(true)
    }
}
