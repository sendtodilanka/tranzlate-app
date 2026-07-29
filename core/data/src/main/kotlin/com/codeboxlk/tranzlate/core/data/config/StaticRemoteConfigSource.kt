package com.codeboxlk.tranzlate.core.data.config

import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO(#4-brains): real implementation — placeholder returns safe defaults.
 * Firebase-Remote-Config-backed source (keys `limit_free_ai`, `limit_pro_fair_use`,
 * `ad_nth`, `ad_min_gap_s`, `ad_daily_cap`, `text_limit_free`, `text_limit_pro`)
 * lands with the brains phase; until then the confirmed BUSINESS_MODEL §7
 * defaults serve.
 */
@Singleton
class StaticRemoteConfigSource
    @Inject
    constructor() : RemoteConfigSource {
        override fun limitFreeAi(): Int = RemoteConfigDefaults.LIMIT_FREE_AI

        override fun limitProFairUse(): Int = RemoteConfigDefaults.LIMIT_PRO_FAIR_USE

        override fun adNth(): Int = RemoteConfigDefaults.AD_NTH

        override fun adMinGapSeconds(): Int = RemoteConfigDefaults.AD_MIN_GAP_SECONDS

        override fun adDailyCap(): Int = RemoteConfigDefaults.AD_DAILY_CAP

        override fun textLimitFree(): Int = RemoteConfigDefaults.TEXT_LIMIT_FREE

        override fun textLimitPro(): Int = RemoteConfigDefaults.TEXT_LIMIT_PRO

        override fun gotEnabled(): Boolean = RemoteConfigDefaults.GOT_ENABLED

        override fun gotTimeoutMs(): Long = RemoteConfigDefaults.GOT_TIMEOUT_MS

        override fun gctTimeoutMs(): Long = RemoteConfigDefaults.GCT_TIMEOUT_MS
    }
