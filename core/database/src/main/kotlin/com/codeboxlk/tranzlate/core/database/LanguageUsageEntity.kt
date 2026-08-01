package com.codeboxlk.tranzlate.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One "this language was actually USED in a translation" stamp per (id, role) —
 * the store Manage packs' honesty reads from (issue #122, rev.3 ruling R6).
 *
 * Written on translation SUCCESS only, never on selection: a language the user
 * merely tapped in the picker proves nothing about whether its pack is safe to
 * delete. [role] is a [com.codeboxlk.tranzlate.core.model.LanguageRole] name
 * stored as TEXT; the repository impl is the only writer and only ever writes
 * enum names, so no converter is registered for a two-value string.
 */
@Entity(tableName = "language_usage", primaryKeys = ["lang_id", "role"])
data class LanguageUsageEntity(
    /** Canonical BCP-47 id — normalised BEFORE the write, never a raw alternate spelling. */
    @ColumnInfo(name = "lang_id") val langId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
)
