package com.codeboxlk.tranzlate.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageDao {

    @Query("SELECT * FROM language ORDER BY name")
    fun languages(): Flow<List<LanguageEntity>>

    @Upsert
    suspend fun upsertAll(languages: List<LanguageEntity>)

    @Query("UPDATE language SET last_used_at = :atMillis WHERE id = :languageId")
    suspend fun setLastUsed(languageId: String, atMillis: Long)
}
