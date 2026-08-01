package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
 *
 * Offline-VOICE truth (issue #130 rev.3 U-3) is overlaid the same way and for
 * a sharper version of the same reason: enumerating TTS voices binds a service
 * in another process, and on a device with no engine at all that ask ends in a
 * five-second timeout. A voice mark is decoration on a row; it must never be
 * the reason the row is not on screen.
 */
@Singleton
class LanguageRepositoryImpl
    @Inject
    constructor(
        private val languageDao: LanguageDao,
        private val offlineModelManager: OfflineModelManager,
        private val offlineVoices: OfflineVoiceCatalog,
        private val preferences: TranzlatePreferencesDataSource,
    ) : LanguageRepository {
        /**
         * The device's offline-voice answer as a flow: nothing, then the answer.
         *
         * `distinctUntilChanged` sits BELOW `onStart` here, which is the exact
         * opposite of where the recents source needs it two blocks down — and
         * the difference is real, not a slip. Recents keep arriving, so
         * swallowing a real value that happens to equal the prefix would strand
         * every reader waiting for the one after the paint. The voice catalog
         * is a ONE-SHOT: there is no later value to wait for, so when the
         * device's answer is "none" it is identical to the prefix in both value
         * and meaning, and re-emitting it would rebuild 194 rows to change
         * nothing.
         */
        private val offlineVoiceIds: Flow<Set<String>> =
            flow { emit(offlineVoices.offlineVoiceLanguageIds()) }
                .onStart { emit(emptySet()) }
                .distinctUntilChanged()

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
                offlineVoiceIds,
            ) { entities, modelStates, recents, voiceIds ->
                val catalog =
                    if (entities.isEmpty()) {
                        BundledLanguageCatalog.all
                    } else {
                        entities.map(LanguageEntity::toDomain)
                    }
                catalog.map { language ->
                    language.copy(
                        offlineDownloaded = modelStates[language.id] == OfflineModelState.Downloaded,
                        // Purely additive: the row is built either way, and an
                        // id the device cannot speak simply carries no mark.
                        hasOfflineVoice = language.id in voiceIds,
                        lastUsedAt = recents[language.id] ?: language.lastUsedAt,
                    )
                }
            }

        /**
         * Per-role recents for a picker's own section (16a).
         *
         * The two sides deliberately read DIFFERENT keys, and the asymmetry is
         * the #122 split's, not an oversight here:
         * - **TARGET** reads the target key alone, because 16a's header names
         *   the role ("Recently used as target") and must be true of every row
         *   under it.
         * - **SOURCE** reads the merged union, which is what the shipped 15a
         *   picker already renders under its role-neutral "Recent" header. The
         *   pre-split legacy key carries no side, so a source-only read would
         *   silently drop an upgrader's whole recents list to make a header
         *   that never claimed a role slightly more precise.
         *
         * `distinctUntilChanged` because `dataStore.data` re-emits on every
         * unrelated preference write and an identical map would rebuild 194
         * rows to change nothing. It sits at the end here, not upstream of an
         * `onStart` as in [languages]: this flow has no prefix to be swallowed.
         */
        override fun recentSelections(role: LanguageRole): Flow<Map<String, Long>> =
            when (role) {
                LanguageRole.SOURCE -> preferences.recentLanguages
                LanguageRole.TARGET -> preferences.recentTargetLanguages
            }.distinctUntilChanged()

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
         *
         * Per-role since issue #130 rev.3: the pick lands under its side's key
         * (16a material) while the merged overlay above keeps serving the same
         * union view the shipped 15a picker renders. Selection ONLY — the
         * translate-use store (`LanguageUsageRepository`) is deliberately not a
         * dependency here, so a pick can never masquerade as a use (ruling R6).
         */
        override suspend fun setLastUsed(
            languageId: String,
            role: LanguageRole,
            atMillis: Long,
        ) {
            val canonical = BundledLanguageCatalog.canonicalId(languageId) ?: languageId
            when (role) {
                LanguageRole.SOURCE -> preferences.recordSourceLanguageUse(canonical, atMillis)
                LanguageRole.TARGET -> preferences.recordTargetLanguageUse(canonical, atMillis)
            }
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
