package com.codeboxlk.tranzlate.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation ORDER BY created_at DESC")
    fun history(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translation ORDER BY created_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translation WHERE favourite = 1 ORDER BY created_at DESC")
    fun favourites(): Flow<List<TranslationEntity>>

    /** C-8 cache lookup — [sourceText] must already be normalized by the caller. */
    @Query(
        "SELECT * FROM translation " +
            "WHERE source_text = :sourceText AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND engine = :engine " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
    ): TranslationEntity?

    /** Newest row for the tuple regardless of engine (read-side relaxation, issue #53 A2). */
    @Query(
        "SELECT * FROM translation " +
            "WHERE source_text = :sourceText AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun cachedAny(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationEntity?

    /**
     * IGNORE + the unique C-8 index (issue #53 A9): two concurrent identical
     * translations race to one row - the loser gets -1, never a duplicate.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TranslationEntity): Long

    @Query("UPDATE translation SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    )
}
