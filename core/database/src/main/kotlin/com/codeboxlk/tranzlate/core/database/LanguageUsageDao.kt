package com.codeboxlk.tranzlate.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageUsageDao {
    /** Composite-key upsert: a repeat use MOVES the stamp, never duplicates the row. */
    @Upsert
    suspend fun upsert(usage: LanguageUsageEntity)

    @Query("SELECT * FROM language_usage WHERE role = :role")
    fun usageFor(role: String): Flow<List<LanguageUsageEntity>>
}
