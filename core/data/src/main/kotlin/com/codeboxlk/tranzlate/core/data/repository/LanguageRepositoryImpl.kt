package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Language catalog — the bundled static list (spec 02 §4.1) with per-device
 * download state overlaid at read time (§4.2).
 *
 * Two sources, two different kinds of truth:
 * - **What exists and what CAN go offline** is compile-time knowledge, so it
 *   comes from [BundledLanguageCatalog] (or the Room table once something ever
 *   seeds it — nothing calls `upsertAll` today, so the bundled list is what
 *   actually ships).
 * - **What IS downloaded** is device truth that changes while the app runs, so
 *   it comes from [OfflineModelManager] and is applied here. The catalog's own
 *   `offlineDownloaded = false` is never served to callers unchanged.
 *
 * The model-state flow is prefixed with an empty map so the picker paints its
 * full list immediately instead of waiting on an ML Kit round-trip that may be
 * slow, or on a device without Play Services may effectively never answer —
 * an empty list would be a dead end (EDGE_CASES). Rows simply flip to
 * "downloaded" a moment later when the real state arrives.
 */
@Singleton
class LanguageRepositoryImpl
    @Inject
    constructor(
        private val languageDao: LanguageDao,
        private val offlineModelManager: OfflineModelManager,
    ) : LanguageRepository {
        override fun languages(): Flow<List<Language>> =
            combine(
                languageDao.languages(),
                offlineModelManager.modelStates().onStart { emit(emptyMap()) },
            ) { entities, modelStates ->
                val catalog =
                    if (entities.isEmpty()) {
                        BundledLanguageCatalog.all
                    } else {
                        entities.map(LanguageEntity::toDomain)
                    }
                catalog.map { language ->
                    language.copy(
                        offlineDownloaded = modelStates[language.id] == OfflineModelState.Downloaded,
                    )
                }
            }

        /**
         * The id is normalised first: a tag that arrived from ML Kit's
         * Language-ID API or from a restored preference can carry an alternate
         * spelling (`iw`, `fil`, `zh-CN`), and an un-normalised write would
         * update no row at all and lose the signal silently.
         */
        override suspend fun setLastUsed(
            languageId: String,
            atMillis: Long,
        ) {
            languageDao.setLastUsed(BundledLanguageCatalog.canonicalId(languageId) ?: languageId, atMillis)
        }
    }

private fun LanguageEntity.toDomain(): Language =
    Language(
        id = id,
        name = name,
        offlineAvailable = offlineAvailable,
        offlineDownloaded = offlineDownloaded,
        lastUsedAt = lastUsedAt,
    )
