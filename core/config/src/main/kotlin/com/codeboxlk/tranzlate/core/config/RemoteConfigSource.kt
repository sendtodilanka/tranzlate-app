package com.codeboxlk.tranzlate.core.config

/**
 * Remote-tunable values seam (plan §2 `:core:config` — implementation-free).
 * Keys per DECISIONS: `limit_free`, `limit_plus` (D-2), `ad_nth`, `ad_min_gap_s`,
 * `ad_daily_cap` (D-4), `text_limit` (defaults table). The Firebase-backed
 * implementation lands with the brains phase; until then a static source serves
 * [RemoteConfigDefaults].
 */
interface RemoteConfigSource {
    fun limitFree(): Int

    fun limitPlus(): Int

    fun adNth(): Int

    fun adMinGapSeconds(): Int

    fun adDailyCap(): Int

    fun textLimit(): Int
}

/** Confirmed product defaults (D-2 · D-4 · defaults table). */
object RemoteConfigDefaults {
    const val LIMIT_FREE = 20
    const val LIMIT_PLUS = 100
    const val AD_NTH = 2
    const val AD_MIN_GAP_SECONDS = 90
    const val AD_DAILY_CAP = 12
    const val TEXT_LIMIT = 500
}
