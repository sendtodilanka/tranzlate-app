package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-model manager impl (spec 02 §3 · §5.2): source of truth =
 * `getDownloadedModels()`; transient Downloading/Deleting/Failed overrides in
 * memory. The VERIFIED MLKit limits are honoured — no progress %, no true
 * cancel: [delete] doubles as delete-to-cancel while Downloading.
 *
 * In-process awaits this batch; WorkManager resilience is a recorded follow-up
 * (plan issue-72). Failures map to the §4.3 causes best-effort.
 */
@Singleton
class RealOfflineModelManager
    @Inject
    constructor() : OfflineModelManager {
        private val manager = RemoteModelManager.getInstance()
        private val capable: Set<String> by lazy { TranslateLanguage.getAllLanguages().toSet() }

        private val downloaded = MutableStateFlow<Set<String>>(emptySet())
        private val transient = MutableStateFlow<Map<String, OfflineModelState>>(emptyMap())

        override fun modelStates(): Flow<Map<String, OfflineModelState>> =
            combine(downloaded, transient) { down, trans ->
                mergeModelStates(capable, down, trans)
            }.onStart { refreshDownloaded() }

        override suspend fun download(languageTag: String) {
            if (TranslateLanguage.fromLanguageTag(languageTag) == null) return
            setTransient(languageTag, OfflineModelState.Downloading)
            try {
                manager
                    .download(
                        TranslateRemoteModel.Builder(languageTag).build(),
                        DownloadConditions.Builder().build(),
                    ).await()
                refreshDownloaded()
                clearTransient(languageTag)
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                clearTransient(languageTag)
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") cause: Exception,
            ) {
                setTransient(languageTag, OfflineModelState.Failed(cause.toFailure()))
            }
        }

        override suspend fun delete(languageTag: String) {
            setTransient(languageTag, OfflineModelState.Deleting)
            try {
                manager
                    .deleteDownloadedModel(TranslateRemoteModel.Builder(languageTag).build())
                    .await()
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                // Deleting something absent (or a cancelled download's partial
                // state) is success by outcome — the refresh below tells truth.
            } finally {
                refreshDownloaded()
                clearTransient(languageTag)
            }
        }

        private suspend fun refreshDownloaded() {
            downloaded.value =
                try {
                    manager
                        .getDownloadedModels(TranslateRemoteModel::class.java)
                        .await()
                        .map { it.language }
                        .toSet()
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
            transient.value = transient.value + (tag to state)
        }

        private fun clearTransient(tag: String) {
            transient.value = transient.value - tag
        }
    }

private fun Exception.toFailure(): OfflineModelFailure =
    when (this) {
        is IOException -> OfflineModelFailure.NETWORK
        else -> OfflineModelFailure.UNKNOWN
    }
