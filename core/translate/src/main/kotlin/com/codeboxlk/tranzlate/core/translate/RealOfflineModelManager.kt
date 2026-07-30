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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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

    override fun modelStates(): Flow<Map<String, OfflineModelState>> =
        combine(downloaded, transient) { down, trans ->
            mergeModelStates(store.capableTags(), down, trans)
        }.onStart { refreshDownloaded() }

    override suspend fun download(languageTag: String) {
        if (!store.isCapable(languageTag)) return
        if (activeDownloads.containsKey(languageTag)) return // one in-flight per tag
        // Issue #90 pre-flight: refuse BEFORE enqueue when the disk can't hold
        // a model — a partial download + a generic failure is a dead end.
        if (storageProbe.freeBytes() < REQUIRED_FREE_BYTES) {
            setTransient(languageTag, OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            return
        }
        setTransient(languageTag, OfflineModelState.Downloading)
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
        setTransient(languageTag, OfflineModelState.Deleting)
        // PR-83 lens OPEN-1: the delete itself must ALSO outlive the caller —
        // a nav-away mid-delete cancelled the caller-scoped coroutine before
        // the finally could clear, stranding a dead-end Deleting spinner in
        // the singleton forever. Same medicine as download(): manager scope,
        // and the sync clear runs BEFORE any suspending refresh.
        downloadScope
            .launch {
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
                    clearTransient(languageTag)
                    refreshDownloaded()
                }
            }.join()
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

    private fun clearTransient(tag: String) {
        transient.update { it - tag }
    }
}

/**
 * Pre-flight free-space budget (issue #90): the observed de<->en pair is
 * 45.7MB on disk (research E3) — x3 headroom for the store + unzip staging.
 */
private const val REQUIRED_FREE_BYTES = 150L * 1024 * 1024

private fun Exception.toFailure(): OfflineModelFailure =
    when (this) {
        is IOException -> OfflineModelFailure.NETWORK
        else -> OfflineModelFailure.UNKNOWN
    }
