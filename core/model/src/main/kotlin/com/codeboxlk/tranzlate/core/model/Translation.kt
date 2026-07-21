package com.codeboxlk.tranzlate.core.model

/**
 * A completed translation (DATA_MODEL `translation` table, domain shape).
 *
 * @property sourceLang BCP-47 id (e.g. `en`); never `"auto"` — the *resolved* detected id.
 * @property sourceText stored NORMALIZED per C-8: trim + collapse internal whitespace,
 *   case-preserved. Cache lookup uses `(sourceText, sourceLang, targetLang, engine)`.
 * @property engine resolved engine (C-9) — never AUTO.
 * @property detected true when the source language came from auto-detect
 *   (drives the "%s (Detected)" label).
 * @property favourite D-3 star toggles this.
 * @property createdAt epoch millis from the injectable [com.codeboxlk.tranzlate.core.common.AppClock].
 */
data class Translation(
    val id: Long = 0L,
    val sourceLang: String,
    val sourceText: String,
    val targetLang: String,
    val targetText: String,
    val engine: Engine,
    val detected: Boolean = false,
    val favourite: Boolean = false,
    val createdAt: Long,
)
