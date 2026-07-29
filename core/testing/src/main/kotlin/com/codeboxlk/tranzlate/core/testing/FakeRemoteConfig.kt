package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource

/** Deterministic config fake — defaults mirror [RemoteConfigDefaults]; override per test. */
class FakeRemoteConfig(
    private val limitFreeAi: Int = RemoteConfigDefaults.LIMIT_FREE_AI,
    private val limitProFairUse: Int = RemoteConfigDefaults.LIMIT_PRO_FAIR_USE,
    private val gotEnabled: Boolean = RemoteConfigDefaults.GOT_ENABLED,
) : RemoteConfigSource {
    override fun limitFreeAi(): Int = limitFreeAi

    override fun limitProFairUse(): Int = limitProFairUse

    override fun adNth(): Int = RemoteConfigDefaults.AD_NTH

    override fun adMinGapSeconds(): Int = RemoteConfigDefaults.AD_MIN_GAP_SECONDS

    override fun adDailyCap(): Int = RemoteConfigDefaults.AD_DAILY_CAP

    override fun textLimitFree(): Int = RemoteConfigDefaults.TEXT_LIMIT_FREE

    override fun textLimitPro(): Int = RemoteConfigDefaults.TEXT_LIMIT_PRO

    override fun gotEnabled(): Boolean = gotEnabled

    override fun gotTimeoutMs(): Long = RemoteConfigDefaults.GOT_TIMEOUT_MS

    override fun gctTimeoutMs(): Long = RemoteConfigDefaults.GCT_TIMEOUT_MS
}
