package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Language catalog over the Room table.
 *
 * TODO(#4-brains): seed the bundled static 180+ list (spec 02 §4.1 — BCP-47
 * verified, re-derived fresh) and intersect with MLKit `getAllLanguages()` at
 * runtime (§4.2) when the Translation brain lands. Skeleton serves the DB
 * contents as-is.
 */
@Singleton
class LanguageRepositoryImpl
    @Inject
    constructor(
        private val languageDao: LanguageDao,
    ) : LanguageRepository {
        override fun languages(): Flow<List<Language>> =
            languageDao.languages().map { entities -> entities.map(LanguageEntity::toDomain) }

        override suspend fun setLastUsed(
            languageId: String,
            atMillis: Long,
        ) {
            languageDao.setLastUsed(languageId, atMillis)
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
