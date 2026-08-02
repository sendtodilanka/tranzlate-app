package com.codeboxlk.tranzlate.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DATA_MODEL `translation` table — column names exact.
 * Indices: C-8 cache lookup `(source_text, source_lang, target_lang, engine)`,
 * `favourite`, `created_at DESC` (history paging), and the two saved-by-language
 * pairs behind [TranslationDao.savedCountUsing] (#130 PR-19, U-10).
 */
@Entity(
    tableName = "translation",
    indices = [
        // unique (issue #53 A9): the C-8 dedupe key is enforced by the DB, so a
        // read-then-insert race cannot produce duplicate rows.
        Index(value = ["source_text", "source_lang", "target_lang", "engine"], unique = true),
        Index(value = ["favourite"]),
        Index(value = ["created_at"], orders = [Index.Order.DESC]),
        // U-10 (#130 PR-19): "3 saved phrases use Spanish" is asked while a
        // remove-pack sheet is opening, so it may not walk the user's saved
        // rows. Both columns are paired with `favourite` because the question
        // is always "SAVED rows that use this language" — never one alone — and
        // pairing makes each index COVERING for its branch, so the count is
        // answered without touching the table at all.
        //
        // Two indices rather than one because a language can be used on either
        // side, and SQLite cannot serve `a = ? OR b = ?` from one index. Which
        // shape of query actually reaches them is measured, not assumed —
        // see [TranslationDao.savedCountUsing].
        Index(value = ["favourite", "source_lang"]),
        Index(value = ["favourite", "target_lang"]),
    ],
)
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** BCP-47 id; never "auto" — the resolved detected id (DATA_MODEL). */
    @ColumnInfo(name = "source_lang") val sourceLang: String,
    /** Stored NORMALIZED (C-8): trim + collapse internal whitespace, case-preserved. */
    @ColumnInfo(name = "source_text") val sourceText: String,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    @ColumnInfo(name = "target_text") val targetText: String,
    /** Resolved [com.codeboxlk.tranzlate.core.model.Engine] enum name — never "AUTO" (C-9). */
    @ColumnInfo(name = "engine") val engine: String,
    @ColumnInfo(name = "detected") val detected: Boolean = false,
    @ColumnInfo(name = "favourite") val favourite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
