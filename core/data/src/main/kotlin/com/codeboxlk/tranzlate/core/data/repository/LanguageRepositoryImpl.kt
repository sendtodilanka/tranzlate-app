package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private val preferences: TranzlatePreferencesDataSource,
    ) : LanguageRepository {
        override fun languages(): Flow<List<Language>> =
            combine(
                languageDao.languages(),
                offlineModelManager.modelStates().onStart { emit(emptyMap()) },
                // Same guard as the line above, for the same reason: `combine`
                // waits for EVERY source, so an unprefixed third one would let
                // a slow DataStore read hold the whole catalog behind a
                // "Loading languages…" with no retry. Recents are decoration;
                // they must never gate the list.
                //
                // `distinctUntilChanged` sits UPSTREAM of `onStart`, not after
                // it: `dataStore.data` re-emits on every unrelated preference
                // write (theme, mode, consent), and each identical map would
                // otherwise rebuild 194 rows. Downstream of `onStart` it would
                // also swallow the first real value whenever that value is
                // itself empty — a fresh install — collapsing this source to a
                // single emission and stranding anything waiting for the one
                // after it.
                preferences.recentLanguages
                    .distinctUntilChanged()
                    .onStart { emit(emptyMap()) },
            ) { entities, modelStates, recents ->
                val catalog =
                    if (entities.isEmpty()) {
                        BundledLanguageCatalog.all
                    } else {
                        entities.map(LanguageEntity::toDomain)
                    }
                catalog.map { language ->
                    language.copy(
                        offlineDownloaded = modelStates[language.id] == OfflineModelState.Downloaded,
                        lastUsedAt = recents[language.id] ?: language.lastUsedAt,
                    )
                }
            }

        /**
         * The id is normalised first: a tag that arrived from ML Kit's
         * Language-ID API or from a restored preference can carry an alternate
         * spelling (`iw`, `fil`, `zh-CN`), and an un-normalised write would
         * record a language the catalog has no row for — the signal would be
         * kept and then never matched.
         *
         * The write goes to preferences, not to the `language` table. The table
         * is never seeded (`upsertAll` has no production caller), so the DAO's
         * `UPDATE … WHERE id = ?` matched zero rows every time and the picker's
         * Recent section could never populate — the section rendered empty for
         * every user, forever, while looking implemented. The DAO write is kept
         * ALONGSIDE for the day the table is seeded; preferences are what the
         * overlay above actually reads.
         */
        override suspend fun setLastUsed(
            languageId: String,
            atMillis: Long,
        ) {
            val canonical = BundledLanguageCatalog.canonicalId(languageId) ?: languageId
            preferences.recordLanguageUse(canonical, atMillis)
            languageDao.setLastUsed(canonical, atMillis)
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
