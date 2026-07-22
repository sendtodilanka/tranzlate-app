package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
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
 */
@Singleton
class TranslatePrefsRepositoryImpl
    @Inject
    constructor(
        private val dataSource: TranzlatePreferencesDataSource,
    ) : TranslatePrefsRepository {
        override val sourceLang: Flow<String> = dataSource.sourceLang

        override val targetLang: Flow<String> = dataSource.targetLang

        override val textMode: Flow<ModeId> =
            dataSource.textMode.map { stored ->
                // Unknown persisted value (e.g. a removed mode) degrades to the
                // safe default — AUTO, never the metered mode (defaults table).
                ModeId.entries.firstOrNull { it.name == stored } ?: ModeId.AUTO
            }

        override suspend fun setSourceLang(id: String) {
            dataSource.setSourceLang(id)
        }

        override suspend fun setTargetLang(id: String) {
            dataSource.setTargetLang(id)
        }

        override suspend fun setLanguagePair(
            sourceId: String,
            targetId: String,
        ) {
            dataSource.setLanguagePair(sourceId, targetId)
        }
    }
