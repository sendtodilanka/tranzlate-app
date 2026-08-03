package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The thin MLKit seam (issue #72 — lens OPEN-1 made the ownership logic
 * testable): everything above it is pure coroutine/state logic.
 */
internal interface ModelStore {
    suspend fun downloadedTags(): Set<String>

    suspend fun download(tag: String)

    suspend fun delete(tag: String)

    fun isCapable(tag: String): Boolean

    fun capableTags(): Set<String>
}

internal class MlKitModelStore
    @Inject
    constructor() : ModelStore {
        private val manager = RemoteModelManager.getInstance()

        override suspend fun downloadedTags(): Set<String> =
            manager
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
                .map { it.language }
                .toSet()

        override suspend fun download(tag: String) {
            manager
                .download(
                    TranslateRemoteModel.Builder(tag).build(),
                    // Deliberately NO requireWifi (issue #90 ruling): the metered
                    // gate is a consent dialog in OUR code before this is ever
                    // reached — requireWifi's mid-download behaviour is untested
                    // (silent-hang risk, research doc X6).
                    DownloadConditions.Builder().build(),
                ).await()
        }

        override suspend fun delete(tag: String) {
            manager.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await()
        }

        override fun isCapable(tag: String): Boolean = TranslateLanguage.fromLanguageTag(tag) != null

        override fun capableTags(): Set<String> = TranslateLanguage.getAllLanguages().toSet()
    }

/**
 * Offline-model manager impl (spec 02 §3 · §5.2): source of truth =
 * the store's downloaded set; transient Downloading/Deleting/Failed overrides
 * in memory. The VERIFIED MLKit limits are honoured — no progress %, and no
 * true PLATFORM cancel exists, so [delete] cancels OUR download coroutine
 * (the last-writer ghost the PR-73 lens caught) and deletes whatever landed;
 * the platform may still finish its own transfer in the background.
 *
 * Ownership rule: only the job registered in [activeDownloads] may publish a
 * download's outcome — a Stop that raced it wins by cancelling first.
 */
@Singleton
class RealOfflineModelManager internal constructor(
    private val store: ModelStore,
    private val storageProbe: StorageProbe,
    scope: CoroutineScope?,
    /**
     * Monotonic milliseconds, for the freshness window below only. Monotonic
     * and not wall-clock on purpose: a user changing the device time must not
     * be able to make a stale disk reading look current. `System.nanoTime`
     * rather than `SystemClock.elapsedRealtime` because this class is covered
     * by JVM unit tests, where the Android class is not mocked and every call
     * throws. Tests pass the test scheduler's virtual clock instead.
     */
    private val elapsedMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : OfflineModelManager {
    @Inject
    internal constructor(
        store: MlKitModelStore,
        storageProbe: StorageProbe,
    ) : this(store as ModelStore, storageProbe, null)

    /**
     * Downloads run in the MANAGER's scope (issue #82): the owner's scenario —
     * leave the screen mid-download, come back — must show the live truth, and
     * a caller-scope coroutine died with the screen, stranding `Downloading`.
     */
    private val downloadScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val downloaded = MutableStateFlow<Set<String>>(emptySet())
    private val transient = MutableStateFlow<Map<String, OfflineModelState>>(emptyMap())
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    /**
     * The delete-side ownership register (issue #123 item 3, risk R1): the same
     * rule download() already obeys — only the job that set a transient may
     * clear it. Without it, a delete's finally cleared UNCONDITIONALLY, so a
     * re-download started mid-delete (the picker's ⬇ on a Deleting row) had
     * its Downloading wiped by the stale delete landing, and the row lied.
     */
    private val activeDeletes = ConcurrentHashMap<String, Job>()

    /**
     * ML Kit's translatable-tag list is an SDK constant — `TranslateLanguage
     * .getAllLanguages()` answers from a static array, never the device — so
     * reading it ONCE is the whole of its truth. It used to be read inside the
     * merge, which is once per emission per collector: three screens watching a
     * single download rebuilt the same ~59-tag set on every row transition, for
     * a value that cannot change while the process lives.
     */
    private val capableTags: Set<String> by lazy { store.capableTags() }

    /** Refresh asks for the worker below; conflated on purpose — see [modelStates]. */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * The ONE upstream every screen shares (issue #130 rev.3, U-13). Cold, this
     * flow multiplied by its collectors: the picker alone opens two chains — its
     * own transient watch and the catalog repository's overlay — so ONE screen
     * ran the merge twice and paid the ML Kit round-trip twice, and the packs
     * screen and the text screen each added another.
     *
     * `WhileSubscribed(5s)` rather than `Eagerly`: with no screen watching there
     * is nothing worth merging, and the grace window is long enough that a
     * rotation or a nav hop does not tear the upstream down and build it again.
     * The scope is [downloadScope], the process-lifetime scope issues #82/#83
     * already sanctioned — this adds no second lifetime, which the rev3 ruling
     * caps at two for the whole app.
     */
    private val states: StateFlow<Map<String, OfflineModelState>> =
        combine(downloaded, transient) { down, trans ->
            mergeModelStates(capableTags, down, trans)
        }.stateIn(
            downloadScope,
            // replayExpiration = 0 is load-bearing, not tidiness. The default
            // is Long.MAX_VALUE: the StateFlow would keep replaying its last
            // map long after WhileSubscribed stopped the upstream, so a screen
            // opening later would render "Downloaded" for a model the user had
            // since deleted from Android's app-storage settings, and hold that
            // lie until the disk read came back. Expiring the cache means the
            // first frame is the empty seed — "nothing known yet", which every
            // consumer already handles — instead of a confident wrong answer.
            SharingStarted.WhileSubscribed(SHARED_STATE_IDLE_MILLIS, replayExpirationMillis = 0),
            emptyMap(),
        )

    /** Monotonic stamp of the last COMPLETED read; Long.MIN_VALUE = never read. */
    private val lastReadAtMillis = AtomicLong(Long.MIN_VALUE)

    // One worker owns the disk read. Two refreshes can then never be in flight
    // together, which matters because refreshDownloaded() publishes by
    // assignment: overlapping reads could land out of order and let an older
    // answer overwrite a newer one. The channel is conflated, so a request that
    // arrives while the worker is busy replaces a waiting one instead of
    // queueing behind it. It runs in downloadScope, so a collector leaving
    // mid-read cannot cancel the read it asked for.
    //
    // The catch is deliberately Throwable and deliberately here rather than
    // inside refreshDownloaded(). This worker is the ONLY reader of the
    // request channel, so anything that terminates it retires refreshing for
    // the rest of the process — every later trySend would land in a conflated
    // buffer nobody reads, and every screen from then on would show whatever
    // the last successful read said. refreshDownloaded() rethrows
    // CancellationException by design, and `Task.await()` raises exactly that
    // when an ML Kit task is CANCELLED rather than failed, so one cancelled
    // task would otherwise be enough. Only this worker's own cancellation —
    // the process scope going down — is allowed to stop it.
    init {
        downloadScope.launch {
            refreshRequests.receiveAsFlow().collect {
                if (!readIsStale()) return@collect
                try {
                    refreshDownloaded()
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") failed: Throwable,
                ) {
                    currentCoroutineContext().ensureActive()
                    // Someone else's failure. The map keeps its last truth and
                    // the next subscription asks again.
                }
                lastReadAtMillis.set(elapsedMillis())
            }
        }
    }

    /**
     * The cold face of the hot [states]: subscribing is what asks the disk, so
     * the answer is fresh when a screen opens, without any screen having to ask.
     *
     * EVERY subscription asks; the worker decides. It skips a request that
     * arrives within [SHARED_STATE_IDLE_MILLIS] of the last completed read, so
     * the picker's two chains — its own transient watch and the catalog
     * repository's overlay — subscribe together and share one round-trip, while
     * a screen opened ten minutes later gets a genuine reading.
     *
     * That freshness window replaced a subscriber COUNT, which looked
     * equivalent and was not: with the count, a refresh happened only when the
     * flow went from unwatched to watched, so a second screen opening while the
     * first was still watching inherited whatever the shared map already held.
     * Models can be deleted from Android's app-storage settings while this app
     * is backgrounded, so the second screen would state "Downloaded" for a pack
     * that is gone. The old cold flow refreshed once per collector and could
     * not have that bug; a shared flow has to earn it back.
     */
    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        states.onSubscription { refreshRequests.trySend(Unit) }

    /**
     * The synchronous half runs here and its answer is RETURNED, not left for a
     * caller to infer from the state map (issue #234). A refusal repeated for the
     * same reason writes an equal value into a conflating flow and is invisible
     * there — see [DownloadAttempt] for why that is a property of the channel
     * rather than of this method. The transfer itself still goes to
     * [downloadScope] and still reports through `modelStates()`.
     */
    override suspend fun download(languageTag: String): DownloadAttempt {
        if (!store.isCapable(languageTag)) return DownloadAttempt.Ignored
        if (activeDownloads.containsKey(languageTag)) return DownloadAttempt.Ignored // one in-flight per tag
        // Issue #90 pre-flight: refuse BEFORE enqueue when the disk can't hold
        // a model — a partial download + a generic failure is a dead end.
        if (storageProbe.freeBytes() < REQUIRED_FREE_BYTES) {
            val cause = OfflineModelFailure.STORAGE
            takeTransient(languageTag, OfflineModelState.Failed(cause))
            return DownloadAttempt.Refused(cause)
        }
        takeTransient(languageTag, OfflineModelState.Downloading)
        val job =
            downloadScope.launch(start = CoroutineStart.LAZY) {
                val self = currentCoroutineContext().job
                try {
                    store.download(languageTag)
                    if (owns(languageTag, self)) {
                        refreshDownloaded()
                        clearTransient(languageTag)
                    }
                } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                    // Cancelled by Stop: delete() owns the row's state now.
                    throw rethrown
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") cause: Exception,
                ) {
                    if (owns(languageTag, self)) {
                        setTransient(languageTag, OfflineModelState.Failed(cause.toFailure()))
                    }
                } finally {
                    activeDownloads.remove(languageTag, self)
                }
            }
        activeDownloads[languageTag] = job
        job.start() // registered BEFORE it runs — Stop can never miss the window
        return DownloadAttempt.Started
    }

    override suspend fun delete(languageTag: String) {
        // Delete-to-cancel, actually delivered: the in-flight download loses
        // ownership AND its coroutine before we touch the model.
        activeDownloads.remove(languageTag)?.cancel()
        // PR-83 lens OPEN-1: the delete itself must ALSO outlive the caller —
        // a nav-away mid-delete cancelled the caller-scoped coroutine before
        // the finally could clear, stranding a dead-end Deleting spinner in
        // the singleton forever. Same medicine as download(): manager scope,
        // and the sync clear runs BEFORE any suspending refresh.
        val job =
            downloadScope.launch(start = CoroutineStart.LAZY) {
                val self = currentCoroutineContext().job
                try {
                    store.delete(languageTag)
                } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                    throw rethrown
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
                ) {
                    // Deleting something absent (or a cancelled download's partial
                    // state) is success by outcome — the refresh below tells truth.
                } finally {
                    // Ownership-checked, exactly like download() (issue #123): a
                    // re-download that started mid-delete revoked this job's claim
                    // via takeTransient, so a stale delete may not wipe its state.
                    if (activeDeletes.remove(languageTag, self)) {
                        clearTransient(languageTag)
                    }
                    // The refresh stays unconditional: whatever raced us, the
                    // platform delete DID run — the downloaded set must tell truth.
                    refreshDownloaded()
                }
            }
        activeDeletes[languageTag] = job
        setTransient(languageTag, OfflineModelState.Deleting)
        job.start() // registered BEFORE it runs — same window rule as download()
        job.join()
    }

    private fun owns(
        tag: String,
        job: Job,
    ): Boolean = activeDownloads[tag] === job

    /**
     * True when the last completed read is old enough that a new subscriber
     * deserves a real one. Never-read always qualifies.
     */
    private fun readIsStale(): Boolean {
        val last = lastReadAtMillis.get()
        return last == Long.MIN_VALUE || elapsedMillis() - last >= SHARED_STATE_IDLE_MILLIS
    }

    private suspend fun refreshDownloaded() {
        downloaded.value =
            try {
                store.downloadedTags()
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                downloaded.value // unknown → keep the last truth we had
            }
    }

    private fun setTransient(
        tag: String,
        state: OfflineModelState,
    ) {
        transient.update { it + (tag to state) }
    }

    /**
     * Overwrites the row's transient AND revokes any in-flight delete's right
     * to clear it — download()'s side of the issue-#123 ownership rule. The
     * delete job itself is left running: the platform has no cancel for it,
     * and its unconditional refresh keeps the downloaded set truthful.
     */
    private fun takeTransient(
        tag: String,
        state: OfflineModelState,
    ) {
        activeDeletes.remove(tag)
        setTransient(tag, state)
    }

    private fun clearTransient(tag: String) {
        transient.update { it - tag }
    }
}

/**
 * Pre-flight free-space budget (issue #90): the observed de<->en pair is
 * 45.7MB on disk (research E3) — x3 headroom for the store + unzip staging.
 */
private const val REQUIRED_FREE_BYTES = 150L * 1024 * 1024

/**
 * How long the shared merge survives its last collector. The same window every
 * `stateIn` in the app uses, and for the same reason: it has to outlive a
 * configuration change, which is the only pause a watching screen takes that
 * is not the user leaving. `internal` so the sharing tests advance virtual time
 * against the production number instead of a copy of it.
 */
private const val NANOS_PER_MILLI = 1_000_000L

internal const val SHARED_STATE_IDLE_MILLIS = 5_000L

private fun Exception.toFailure(): OfflineModelFailure =
    when (this) {
        is IOException -> OfflineModelFailure.NETWORK
        else -> OfflineModelFailure.UNKNOWN
    }
