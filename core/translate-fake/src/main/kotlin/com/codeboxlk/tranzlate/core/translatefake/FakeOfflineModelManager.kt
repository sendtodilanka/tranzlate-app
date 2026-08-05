package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic 6-state offline-model fake (plan §6.4; spec 02 §3.3/§4.3).
 * Seed covers every state so the offline-manager UI can be demoed/tested against
 * a fixed matrix; transitions are synchronous and deterministic:
 *   download: NotDownloaded/Failed → Downloading → Downloaded
 *   delete:   Downloaded → Deleting → NotDownloaded (also delete-to-cancel while Downloading)
 * OnlineOnly rows ignore both (no download control — §4.3).
 */
class FakeOfflineModelManager : OfflineModelManager {
    private val states = MutableStateFlow(SEED)

    private val packEventsFlow =
        MutableSharedFlow<PackEvent>(
            replay = 0,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val packEvents: SharedFlow<PackEvent> = packEventsFlow.asSharedFlow()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = states.asStateFlow()

    override suspend fun download(languageTag: String): DownloadAttempt {
        val current = states.value[languageTag] ?: return DownloadAttempt.Ignored
        if (current is OfflineModelState.OnlineOnly || current is OfflineModelState.Downloaded) {
            return DownloadAttempt.Ignored
        }
        states.update { it + (languageTag to OfflineModelState.Downloading) }
        packEventsFlow.tryEmit(PackEvent.DownloadStarted(languageTag))
        states.update { it + (languageTag to OfflineModelState.Downloaded) }
        packEventsFlow.tryEmit(PackEvent.DownloadSucceeded(languageTag))
        // The fake has no disk and therefore no free-space pre-flight to refuse
        // with — every download it accepts starts and finishes. `Refused` is
        // reachable only against a real volume (`RealOfflineModelManager`).
        return DownloadAttempt.Started
    }

    override suspend fun delete(languageTag: String) {
        when (states.value[languageTag]) {
            OfflineModelState.Downloaded -> {
                states.update { it + (languageTag to OfflineModelState.Deleting) }
                states.update { it + (languageTag to OfflineModelState.NotDownloaded) }
                packEventsFlow.tryEmit(PackEvent.Deleted(languageTag))
            }

            // delete-to-cancel (spec 02 §4.4 step 2)
            OfflineModelState.Downloading -> {
                states.update { it + (languageTag to OfflineModelState.NotDownloaded) }
                packEventsFlow.tryEmit(PackEvent.Deleted(languageTag))
            }

            else -> {
                Unit
            }
        }
    }

    companion object {
        /** Deterministic seed — golden languages downloaded; every state represented. */
        val SEED: Map<String, OfflineModelState> =
            mapOf(
                "en" to OfflineModelState.Downloaded,
                "fr" to OfflineModelState.Downloaded,
                "de" to OfflineModelState.NotDownloaded,
                "es" to OfflineModelState.NotDownloaded,
                "ja" to OfflineModelState.OnlineOnly,
                "ta" to OfflineModelState.Failed(OfflineModelFailure.NETWORK),
            )
    }
}
