package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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

    /** How many collectors [modelStates] currently has; 0 means nobody is watching. */
    private val collectors = AtomicInteger(0)

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
        }.stateIn(downloadScope, SharingStarted.WhileSubscribed(SHARED_STATE_IDLE_MILLIS), emptyMap())

    // One worker owns the disk read. Two refreshes can then never be in flight
    // together, which matters because refreshDownloaded() publishes by
    // assignment: overlapping reads could land out of order and let an older
    // answer overwrite a newer one. The channel is conflated, so a request that
    // arrives while the worker is busy replaces a waiting one instead of
    // queueing behind it. It runs in downloadScope, so a collector leaving
    // mid-read cannot cancel the read it asked for.
    init {
        downloadScope.launch { refreshRequests.receiveAsFlow().collect { refreshDownloaded() } }
    }

    /**
     * The cold face of the hot [states]: subscribing is what asks the disk, so
     * the answer is fresh when a screen opens without any screen having to ask
     * for it.
     *
     * Only the collector that finds the flow unwatched asks. That count is what
     * makes a burst cost exactly one read — a bare conflated send would not,
     * because the worker starts on the first request and everything that
     * arrives after it starts is a second refresh. The picker's two chains
     * subscribe together and now share one round-trip between them.
     *
     * And when the last collector leaves, the next arrival asks again. That is
     * the staleness guard the hot flow needs: a `StateFlow` keeps replaying its
     * final value long after `WhileSubscribed` stopped the upstream, and models
     * can be deleted from Android's app-storage settings while this app is
     * backgrounded — so a replayed map is a claim about the disk, not a
     * reading of it.
     */
    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        states
            .onSubscription { if (collectors.getAndIncrement() == 0) refreshRequests.trySend(Unit) }
            .onCompletion {
                // Clamped rather than trusted: a counter stuck below zero would
                // silently retire the staleness guard for the rest of the
                // process, and an extra read costs one round-trip.
                if (collectors.decrementAndGet() < 0) collectors.set(0)
            }

    override suspend fun download(languageTag: String) {
        if (!store.isCapable(languageTag)) return
        if (activeDownloads.containsKey(languageTag)) return // one in-flight per tag
        // Issue #90 pre-flight: refuse BEFORE enqueue when the disk can't hold
        // a model — a partial download + a generic failure is a dead end.
        if (storageProbe.freeBytes() < REQUIRED_FREE_BYTES) {
            takeTransient(languageTag, OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            return
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
internal const val SHARED_STATE_IDLE_MILLIS = 5_000L

private fun Exception.toFailure(): OfflineModelFailure =
    when (this) {
        is IOException -> OfflineModelFailure.NETWORK
        else -> OfflineModelFailure.UNKNOWN
    }
