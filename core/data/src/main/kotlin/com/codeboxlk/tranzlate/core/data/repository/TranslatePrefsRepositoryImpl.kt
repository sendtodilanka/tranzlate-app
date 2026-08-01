package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [TranslatePrefsRepository] (DATA_MODEL `prefs.*` keys via
 * [TranzlatePreferencesDataSource] — defaults en → fr → AUTO from the DECISIONS
 * defaults table live in the data source).
 *
 * Language ids are CANONICALISED at the door (issue #119 / #123.2): the picker
 * writes catalog ids, but swap and history-reopen write whatever the detector
 * resolved, and both `MlKitLanguageIdentifier` and `GctEngine` can hand back
 * legacy spellings (`iw`, `zh-CN`). Stored raw, such an id makes the picker's
 * radio compare (`language.id == selectedId`) tick nothing while the composer
 * chip reads "Hebrew" — the screen contradicting itself. Canonicalising every
 * write means nothing NEW enters the store in a spelling the catalog cannot
 * serve — and canonicalising every READ means an install that upgraded with
 * `iw` already on disk is served `he` from this seam, so the picker's tick and
 * the composer's request agree on day one instead of after the next write.
 * Both co-verify lenses found the read half missing: the picker canonicalised
 * for itself, so its row ticked correctly while `TextViewModel` still handed
 * the raw tag to the engine and to the history row — two readers, two answers,
 * one store.
 *
 * Behaviour-preserving for already-canonical ids (the resolver maps them to
 * themselves) AND for the `"auto"` detect sentinel, which is not a language:
 * the resolver returns null for it and the `?: id` fallback passes it through
 * unchanged — same contract as `LanguageRepositoryImpl.setLastUsed`.
 */
@Singleton
class TranslatePrefsRepositoryImpl
    @Inject
    constructor(
        private val dataSource: TranzlatePreferencesDataSource,
    ) : TranslatePrefsRepository {
        override val sourceLang: Flow<String> =
            dataSource.sourceLang.map(LanguageTagResolver::canonicalOrSelf)

        override val targetLang: Flow<String> =
            dataSource.targetLang.map(LanguageTagResolver::canonicalOrSelf)

        override val textMode: Flow<ModeId> =
            dataSource.textMode.map { stored ->
                // Unknown persisted value (e.g. a removed mode) degrades to the
                // safe default — AUTO, never the metered mode (defaults table).
                ModeId.entries.firstOrNull { it.name == stored } ?: ModeId.AUTO
            }

        override suspend fun setSourceLang(id: String) {
            dataSource.setSourceLang(LanguageTagResolver.canonicalOrSelf(id))
        }

        override suspend fun setTargetLang(id: String) {
            dataSource.setTargetLang(LanguageTagResolver.canonicalOrSelf(id))
        }

        override suspend fun setLanguagePair(
            sourceId: String,
            targetId: String,
        ) {
            // Both ids resolved BEFORE the one atomic edit — canonicalisation
            // must never split the pair write the swap depends on.
            dataSource.setLanguagePair(
                LanguageTagResolver.canonicalOrSelf(sourceId),
                LanguageTagResolver.canonicalOrSelf(targetId),
            )
        }
    }
