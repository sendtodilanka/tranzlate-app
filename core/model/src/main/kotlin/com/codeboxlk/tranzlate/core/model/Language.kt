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
 * @property hasOfflineVoice this device can READ THIS LANGUAGE ALOUD with no
 *   connection (issue #130 rev.3 U-3). Unrelated to the three fields above: a
 *   translate pack and a TTS voice are separate installs from separate sources,
 *   and either can be present without the other. Device truth, so — like
 *   [offlineDownloaded] — no compile-time catalog may claim it; it is overlaid
 *   at read time and defaults to the honest `false`.
 */
data class Language(
    val id: String,
    val name: String,
    val offlineAvailable: Boolean,
    val offlineDownloaded: Boolean,
    val hasOfflineVoice: Boolean = false,
    val lastUsedAt: Long? = null,
)
