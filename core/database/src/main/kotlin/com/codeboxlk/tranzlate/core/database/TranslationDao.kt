package com.codeboxlk.tranzlate.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation ORDER BY created_at DESC")
    fun history(): Flow<List<TranslationEntity>>

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

    @Insert
    suspend fun insert(entity: TranslationEntity): Long

    @Query("UPDATE translation SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    )
}
