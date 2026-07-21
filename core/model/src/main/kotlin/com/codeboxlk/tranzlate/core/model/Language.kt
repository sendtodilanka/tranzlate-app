package com.codeboxlk.tranzlate.core.model

/**
 * Language catalog row (DATA_MODEL `language` table, domain shape).
 * Catalog is re-derived fresh (180+ languages, BCP-47 verified) — a bundled static
 * list intersected with MLKit runtime capability (spec 02 §4.1/§4.2); never fetched
 * from a Cloud API on the phone.
 *
 * @property id BCP-47 id (PK).
 * @property offlineAvailable in MLKit's `getAllLanguages()` set (offline-capable).
 * @property offlineDownloaded model currently downloaded on this device.
 */
data class Language(
    val id: String,
    val name: String,
    val offlineAvailable: Boolean,
    val offlineDownloaded: Boolean,
    val lastUsedAt: Long? = null,
)
