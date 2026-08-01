package com.codeboxlk.tranzlate.di

import android.content.Context
import android.speech.tts.TextToSpeech
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The device's offline-voice answer, taken from Android's TTS engine exactly
 * ONCE per process and then cached (issue #130 rev.3 U-3).
 *
 * `TextToSpeech` is a bound service connection, not a value: constructing one
 * binds to the engine's process and keeps it bound until `shutdown()`. Holding
 * a standing instance to answer a question whose answer cannot change while the
 * app is foregrounded is the documented leak class in the audit (ruling risk
 * R4) — so this class connects, asks, caches and disconnects, and every path out
 * of [enumerate] goes through the `finally` that disconnects.
 *
 * The shape below is load-bearing at every step:
 *
 * - **[mutex] makes it one-shot.** Two screens asking at the same moment must
 *   not each bind an engine. The lock is held across the whole enumeration, so
 *   the second caller waits for the first one's answer instead of starting a
 *   second connection. There is no unlocked double-checked fast path: the ask
 *   happens once per process, and an uncontended `Mutex` costs less than the
 *   `@Volatile` read the fast path would need to be correct.
 * - **The init callback is awaited under a timeout.** `TextToSpeech`'s only
 *   readiness signal is `OnInitListener`, and an engine that never binds —
 *   disabled, crashed, or in the middle of a Play Store update — simply never
 *   calls it. Awaiting it unbounded would park a caller forever, which is a
 *   permanently blank speaker column and, if a screen ever awaited it in
 *   `combine`, a permanently blank screen. [INIT_TIMEOUT_MS] bounds that.
 * - **`getVoices()` is null-guarded.** It genuinely returns null rather than an
 *   empty set: AOSP's `TextToSpeech.java` `getVoices()` (:1721-1726) hands the
 *   call to `runAction`, whose failure return is the `null` default (:853-854),
 *   which happens whenever the service connection has gone away between init
 *   and the call. Kotlin sees the SDK's un-annotated return as a platform type,
 *   so nothing in the type system would have caught the NPE.
 * - **Network-required voices are filtered out.** `Voice.isNetworkConnectionRequired`
 *   is the whole basis of the claim the mark makes; without the filter the mark
 *   would promise offline speech that needs a connection.
 * - **Locales become catalog ids through [LanguageTagResolver].** The engine
 *   answers with locales the catalog has no row for unless they are resolved
 *   (`es-ES` → `es`), and some that resolve to a DIFFERENT row than their
 *   language subtag (`zh-HK` → `zh-TW`, `pt-BR` → `pt-BR` and not `pt`).
 *
 * Failure in any of that is the empty set, never an exception: see
 * [OfflineVoiceCatalog] for why the seam is false-negative-safe by design.
 *
 * PACKAGE VISIBILITY: on API 30+ this can only see a TTS engine the app manifest
 * has declared an interest in — `<queries>` for
 * `android.intent.action.TTS_SERVICE`. Read the comment on that block before
 * touching it; it carries what experiment E-V1 could and could not measure
 * (`docs/research/issue-130-e-v1-voice-enumeration.md`).
 */
class AndroidOfflineVoiceCatalog internal constructor(
    private val dispatchers: DispatcherProvider,
    private val connect: (onReady: (Boolean) -> Unit) -> SpeechEngine,
    private val initTimeoutMillis: Long = INIT_TIMEOUT_MS,
    /**
     * Monotonic milliseconds, for the freshness window only. `System.nanoTime`
     * rather than `SystemClock.elapsedRealtime` because this class is covered
     * by JVM unit tests where the Android class is not mocked and every call
     * throws — the same seam `RealOfflineModelManager` uses for the same
     * reason. Tests pass the scheduler's virtual clock.
     */
    private val elapsedMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : OfflineVoiceCatalog {
    constructor(
        context: Context,
        dispatchers: DispatcherProvider,
    ) : this(
        dispatchers = dispatchers,
        connect = { onReady -> PlatformSpeechEngine(context, onReady) },
    )

    private val mutex = Mutex()
    private var cached: Set<String>? = null

    /**
     * Answered once, but not once FOREVER — and never cached when the device
     * failed to answer.
     *
     * A busy or cold engine returns `null` from [readVoices], which is "could
     * not answer", not "no voices". Storing that would make one unlucky moment
     * at startup mean "this device cannot speak" for the rest of the process; a
     * co-verify lens measured exactly that, twice.
     *
     * Even a real answer expires. Voices are installed and removed from
     * Settings → Text-to-speech, which is somewhere the user goes WHILE this
     * app is backgrounded — and PR-12's whole flow is to send them there and
     * bring them back. `RealOfflineModelManager` reached the same conclusion
     * about its own cache after its own lens, for the same reason and in the
     * same words: a replayed answer is a claim about the device, not a reading
     * of it.
     */
    override suspend fun offlineVoiceLanguageIds(): Set<String> =
        mutex.withLock {
            cached?.takeIf { !answerIsStale() } ?: enumerate().also { fresh ->
                if (fresh != null) {
                    cached = fresh
                    answeredAtMillis = elapsedMillis()
                }
            } ?: cached.orEmpty()
        }

    private var answeredAtMillis: Long = Long.MIN_VALUE

    private fun answerIsStale(): Boolean =
        answeredAtMillis == Long.MIN_VALUE || elapsedMillis() - answeredAtMillis >= ANSWER_FRESH_MS

    /** `null` = the device could not answer; the caller keeps whatever it had. */
    private suspend fun enumerate(): Set<String>? = withContext(dispatchers.io) { readVoices()?.let(::offlineIdsOf) }

    /**
     * The engine round-trip, and the ONLY part of this class allowed to fail.
     *
     * Every way the ask can end WITHOUT an answer — nothing to bind to, an
     * engine that never initialises, one that reports failure, a read that
     * raises — leaves [voices] as the empty list it started as. `null` is
     * therefore reserved for the one thing it means on the platform:
     * `getVoices()` itself answered null. Keeping those apart is what lets the
     * null guard in [offlineIdsOf] be a guard rather than untestable decoration
     * — and that guard lives OUTSIDE this function's `catch`, which would
     * otherwise swallow the very NPE it exists to prevent.
     */
    private suspend fun readVoices(): List<InstalledVoice>? {
        var engine: SpeechEngine? = null
        var voices: List<InstalledVoice>? = emptyList()
        try {
            val ready = CompletableDeferred<Boolean>()
            // Held as a `val` too: the lambda below cannot smart-cast the
            // nullable `var` the `finally` needs.
            val connected = connect { initialised -> ready.complete(initialised) }
            engine = connected
            // The ruling names `withTimeout`; this is that timeout in its
            // non-throwing form. A silent engine is an expected outcome here,
            // not an error — the assignment simply never happens.
            withTimeoutOrNull(initTimeoutMillis) {
                if (ready.await()) voices = connected.installedVoices()
            }
        } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
            // The CALLER went away (screen closed). Not our failure to absorb.
            throw rethrown
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
        ) {
            // A dead engine surfaces as anything from IllegalArgumentException in
            // the constructor to a DeadObjectException mid-call. Every one of
            // them means the same thing to a caller: no offline voices.
            voices = emptyList()
        } finally {
            // shutdown() is NOT safe by itself. AOSP routes it to
            // `unbindService` with no guard (TextToSpeech.java:956-961 →
            // Connection.disconnect :2430), and `runAction` catches only
            // RemoteException — so releasing an engine that never bound throws
            // IllegalArgumentException: "Service not registered". A lens
            // reproduced it. Escaping here would reach the picker's `stateIn`,
            // which has no `.catch`, and crash the screen this seam exists to
            // keep quiet.
            try {
                engine?.shutdown()
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                // The engine is gone either way; that is all shutdown had to achieve.
            }
        }
        return voices
    }

    /**
     * Every catalog id a voice can serve — not merely the first one it resolves
     * to.
     *
     * `canonicalId` stops at the first catalog row that matches, and the
     * catalog carries `fr-FR` and `pt-BR` rows alongside bare `fr` and `pt`. So
     * an `fr-FR` voice used to resolve to `fr-FR` and stop — a row that is not
     * offline-capable and therefore never drawn — while the French row that IS
     * drawn got no mark at all. Spanish appeared to work only because the
     * catalog happens to have no `es-ES` row to absorb it. A lens measured it:
     * on every device with Google TTS, French and Portuguese were silently
     * voice-less while `speak(text, "fr")` worked perfectly.
     *
     * A device that can speak `fr-FR` can speak French. So each voice
     * contributes the exact id AND the base-language id, when the catalog
     * knows them.
     */
    private fun offlineIdsOf(voices: List<InstalledVoice>): Set<String> =
        voices
            .filterNot(InstalledVoice::requiresNetwork)
            .flatMap { voice ->
                val tag = voice.languageTag
                listOfNotNull(
                    LanguageTagResolver.canonicalId(tag),
                    LanguageTagResolver.canonicalId(tag.substringBefore('-')),
                )
            }.toSet()

    internal companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        /**
         * How long an answer stays good. Voices are installed from Settings →
         * Text-to-speech, which is somewhere PR-12 deliberately sends the user
         * — so an answer has to expire fast enough that coming back shows the
         * voice they just installed. Short, because the cost of being wrong is
         * a mark that is missing from a screen the user is looking at, and the
         * cost of re-asking is one service bind.
         */
        internal const val ANSWER_FRESH_MS = 5_000L

        /**
         * Long enough for a cold engine bind (the first `TextToSpeech` of a
         * process starts the engine's own process), short enough that a wedged
         * engine does not hold the one-shot lock for a user-visible stretch.
         * Nothing on the main thread waits on this — the enumeration runs on IO
         * and its consumers paint before it answers.
         *
         * `internal` so the timeout test asserts against the SHIPPED number
         * rather than a copy of it that could drift.
         */
        const val INIT_TIMEOUT_MS = 5_000L
    }
}

/**
 * The slice of the platform TTS engine [AndroidOfflineVoiceCatalog] uses.
 *
 * It exists so the catalog's decisions — the timeout, the null guard, the
 * network filter, the id resolution, the shutdown discipline — are testable as
 * plain JVM logic. `TextToSpeech` cannot be constructed in a unit test and its
 * `Voice` objects cannot be built at all, so without this seam every one of
 * those rules would only ever be exercised on a device.
 */
internal interface SpeechEngine {
    /**
     * The engine's installed voices, or `null` when it could not answer — the
     * real return the null guard exists for (see the class doc for the AOSP
     * lines).
     */
    fun installedVoices(): List<InstalledVoice>?

    /** Releases the bound engine. Must be safe to call after any failure. */
    fun shutdown()
}

/**
 * One installed voice, reduced to the two facts the catalog decides on.
 *
 * [languageTag] is a BCP-47 STRING, not a `java.util.Locale`, and the
 * conversion happens on the device. `Locale`'s legacy-code canonicalisation
 * differs between the desktop JVM and Android — the same reason
 * [LanguageTagResolver] refuses to route through it — so a seam carrying
 * `Locale` would make unit-test results a claim about the JVM rather than about
 * the phone.
 */
internal data class InstalledVoice(
    val languageTag: String,
    val requiresNetwork: Boolean,
)

/** The real engine: one `TextToSpeech` connection, read once, then shut down. */
private class PlatformSpeechEngine(
    context: Context,
    onReady: (Boolean) -> Unit,
) : SpeechEngine {
    /**
     * The listener can fire before this constructor returns — AOSP dispatches
     * `ERROR` synchronously when no engine can be resolved at all — which is
     * why readiness is signalled through a `CompletableDeferred` the caller
     * already holds rather than through this object.
     */
    private val tts = TextToSpeech(context) { status -> onReady(status == TextToSpeech.SUCCESS) }

    override fun installedVoices(): List<InstalledVoice>? =
        tts.voices?.map { voice ->
            InstalledVoice(
                languageTag = voice.locale.toLanguageTag(),
                requiresNetwork = voice.isNetworkConnectionRequired,
            )
        }

    override fun shutdown() = tts.shutdown()
}
