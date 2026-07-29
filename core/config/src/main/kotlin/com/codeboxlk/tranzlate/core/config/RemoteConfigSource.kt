package com.codeboxlk.tranzlate.core.config

/**
 * Remote-tunable values seam (plan §2 `:core:config` — implementation-free).
 * Keys per BUSINESS_MODEL §7 (D-2 rev.2): `limit_free_ai`, `limit_pro_fair_use`,
 * `text_limit_free`, `text_limit_pro`, plus `ad_nth`, `ad_min_gap_s`,
 * `ad_daily_cap` (D-4). The Firebase-backed implementation lands with the
 * brains phase; until then a static source serves [RemoteConfigDefaults].
 */
interface RemoteConfigSource {
    /** FREE tier's daily AI-quality (GCT/LLM) pool — the counter the paywall shows. */
    fun limitFreeAi(): Int

    /** PRO abuse guard — never marketed, set far above honest use (BUSINESS_MODEL §1). */
    fun limitProFairUse(): Int

    fun adNth(): Int

    fun adMinGapSeconds(): Int

    fun adDailyCap(): Int

    fun textLimitFree(): Int

    fun textLimitPro(): Int

    /** GOT kill-switch (issue #61) — the unofficial tier can be disabled remotely overnight. */
    fun gotEnabled(): Boolean

    fun gotTimeoutMs(): Long

    fun gctTimeoutMs(): Long
}

/** Confirmed product defaults (BUSINESS_MODEL §7 · D-4 · defaults table). */
object RemoteConfigDefaults {
    const val LIMIT_FREE_AI = 5
    const val LIMIT_PRO_FAIR_USE = 2000
    const val AD_NTH = 2
    const val AD_MIN_GAP_SECONDS = 90
    const val AD_DAILY_CAP = 12
    const val TEXT_LIMIT_FREE = 500
    const val TEXT_LIMIT_PRO = 5000
    const val GOT_ENABLED = true
    const val GOT_TIMEOUT_MS = 10_000L
    const val GCT_TIMEOUT_MS = 15_000L
}
