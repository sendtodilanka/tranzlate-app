package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.TranslationDao
import com.codeboxlk.tranzlate.core.database.TranslationEntity
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C-8 cache rule: `source_text` is STORED normalized (trim + collapse internal
 * whitespace, case-preserved); lookup hits the
 * `(source_text, source_lang, target_lang, engine)` index on the normalized value.
 * No sha, no separate cache-key column.
 */
internal fun normalizeSourceText(raw: String): String = raw.trim().replace(WHITESPACE_RUN, " ")

private val WHITESPACE_RUN = Regex("\\s+")

@Singleton
class TranslationRepositoryImpl
    @Inject
    constructor(
        private val translationDao: TranslationDao,
    ) : TranslationRepository {
        override fun history(): Flow<List<Translation>> =
            translationDao.history().map { entities -> entities.map(TranslationEntity::toDomain) }

        override fun favourites(): Flow<List<Translation>> =
            translationDao.favourites().map { entities -> entities.map(TranslationEntity::toDomain) }

        override suspend fun cachedAny(
            sourceText: String,
            sourceLang: String,
            targetLang: String,
        ): Translation? =
            translationDao
                .cachedAny(normalizeSourceText(sourceText), sourceLang, targetLang)
                ?.toDomain()

        override suspend fun cached(
            sourceText: String,
            sourceLang: String,
            targetLang: String,
            engine: Engine,
        ): Translation? =
            translationDao
                .cached(normalizeSourceText(sourceText), sourceLang, targetLang, engine.name)
                ?.toDomain()

        override suspend fun save(translation: Translation): Long = translationDao.insert(translation.toEntity())

        override suspend fun setFavourite(
            id: Long,
            favourite: Boolean,
        ) {
            translationDao.setFavourite(id, favourite)
        }
    }

private fun TranslationEntity.toDomain(): Translation =
    Translation(
        id = id,
        sourceLang = sourceLang,
        sourceText = sourceText,
        targetLang = targetLang,
        targetText = targetText,
        engine = Engine.valueOf(engine),
        detected = detected,
        favourite = favourite,
        createdAt = createdAt,
    )

private fun Translation.toEntity(): TranslationEntity =
    TranslationEntity(
        id = id,
        sourceLang = sourceLang,
        sourceText = normalizeSourceText(sourceText),
        targetLang = targetLang,
        targetText = targetText,
        engine = engine.name,
        detected = detected,
        favourite = favourite,
        createdAt = createdAt,
    )
