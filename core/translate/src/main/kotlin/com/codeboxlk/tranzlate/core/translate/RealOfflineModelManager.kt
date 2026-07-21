package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-model manager impl home (spec 02 §3): source-of-truth =
 * `getDownloadedModels()` + in-flight downloading-set; WorkManager-backed
 * downloads; indeterminate progress + delete-to-cancel (verified MLKit limits).
 */
@Singleton
class RealOfflineModelManager
    @Inject
    constructor() : OfflineModelManager {
        // TODO(#4-brains): real implementation — placeholder returns Error(ENGINE) / safe defaults.
        override fun modelStates(): Flow<Map<String, OfflineModelState>> = flowOf(emptyMap())

        override suspend fun download(languageTag: String) = Unit

        override suspend fun delete(languageTag: String) = Unit
    }
