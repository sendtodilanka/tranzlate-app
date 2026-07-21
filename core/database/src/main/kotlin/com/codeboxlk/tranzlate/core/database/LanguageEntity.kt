package com.codeboxlk.tranzlate.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** DATA_MODEL `language` table — catalog re-derived fresh (BCP-47 verified). */
@Entity(tableName = "language")
data class LanguageEntity(
    /** BCP-47 id. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "offline_available") val offlineAvailable: Boolean,
    @ColumnInfo(name = "offline_downloaded") val offlineDownloaded: Boolean,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
)
