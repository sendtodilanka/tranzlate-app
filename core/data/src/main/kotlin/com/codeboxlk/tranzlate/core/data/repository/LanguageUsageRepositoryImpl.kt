package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageUsageDao
import com.codeboxlk.tranzlate.core.database.LanguageUsageEntity
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed translation-use stamps (issue #122) — the `language_usage` table,
 * one row per (canonical id, role), success-stamped by `TranslateTextUseCase`
 * and nothing else (ruling R6: selection must never write here, which is why
 * this class is deliberately NOT a dependency of `LanguageRepositoryImpl` or
 * any picker code).
 *
 * The id is canonicalised exactly as `LanguageRepositoryImpl.setLastUsed` does:
 * a detect result can arrive as an alternate spelling (`iw`, `fil`, `zh-CN`),
 * and an un-normalised write would record a language the catalog has no row
 * for — the stamp would be kept and then never matched by Manage packs.
 */
@Singleton
internal class LanguageUsageRepositoryImpl
    @Inject
    constructor(
        private val languageUsageDao: LanguageUsageDao,
    ) : LanguageUsageRepository {
        override suspend fun stampUse(
            languageId: String,
            role: LanguageRole,
            atMillis: Long,
        ) {
            val canonical = BundledLanguageCatalog.canonicalId(languageId) ?: languageId
            languageUsageDao.upsert(
                LanguageUsageEntity(langId = canonical, role = role.name, lastUsedAt = atMillis),
            )
        }

        override fun lastUsed(role: LanguageRole): Flow<Map<String, Long>> =
            languageUsageDao
                .usageFor(role.name)
                .map { rows -> rows.associate { row -> row.langId to row.lastUsedAt } }
    }
