package com.codeboxlk.tranzlate.core.data.config

import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO(#4-brains): real implementation — placeholder returns safe defaults.
 * Firebase-Remote-Config-backed source (keys `limit_free`, `limit_plus`,
 * `ad_nth`, `ad_min_gap_s`, `ad_daily_cap`, `text_limit`) lands with the brains
 * phase; until then the confirmed D-2/D-4 defaults serve.
 */
@Singleton
class StaticRemoteConfigSource
    @Inject
    constructor() : RemoteConfigSource {
        override fun limitFree(): Int = RemoteConfigDefaults.LIMIT_FREE

        override fun limitPlus(): Int = RemoteConfigDefaults.LIMIT_PLUS

        override fun adNth(): Int = RemoteConfigDefaults.AD_NTH

        override fun adMinGapSeconds(): Int = RemoteConfigDefaults.AD_MIN_GAP_SECONDS

        override fun adDailyCap(): Int = RemoteConfigDefaults.AD_DAILY_CAP

        override fun textLimit(): Int = RemoteConfigDefaults.TEXT_LIMIT
    }
