package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import kotlinx.coroutines.flow.Flow

/**
 * History/saved/cache repository contract (DATA_MODEL `translation`).
 * Cache rule C-8: lookup by `(sourceText, sourceLang, targetLang, engine)` on the
 * NORMALIZED source text (trim + collapse internal whitespace, case-preserved) —
 * no sha, no separate cache-key column.
 */
interface TranslationRepository {
    fun history(): Flow<List<Translation>>

    fun favourites(): Flow<List<Translation>>

    /** C-8 cache-first lookup; [sourceText] may be un-normalized — impl normalizes. */
    suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: Engine,
    ): Translation?

    suspend fun save(translation: Translation): Long

    /** D-3: star toggles favourite. */
    suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    )
}
