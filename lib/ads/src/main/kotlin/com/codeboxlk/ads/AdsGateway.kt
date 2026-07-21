package com.codeboxlk.ads

import android.app.Activity

/**
 * Host-app-agnostic ads configuration (the host `@Provides` this).
 * Never ship Google TEST unit ids in production — the old app's revenue-zero bug;
 * ids arrive from the host's per-brand build config.
 */
data class AdsConfig(
    val applicationId: String,
    val bannerUnitId: String,
    val interstitialUnitId: String,
)

/**
 * Frequency POLICY VALUES (host-tunable, e.g. via remote config):
 * every [nth] completed action, at least [minGapSeconds] apart, at most
 * [dailyCap] per day.
 */
data class AdPolicyConfig(
    val nth: Int,
    val minGapSeconds: Int,
    val dailyCap: Int,
)

/**
 * Public ads API — load/show + consent input. The AdMob SDK stays `internal`
 * behind this surface.
 *
 * BOUNDARY (plan §2 one-home rule): this library carries frequency MECHANICS
 * only ([AdFrequencyEngine]); the app-level show/no-show DECISION (paid tier,
 * never-on-back-press, which events count) lives in the host's ads brain and
 * must never leak in here.
 */
interface AdsGateway {
    /** Consent input from the host's consent flow; ads must not personalize without it. */
    fun setConsent(granted: Boolean)

    /** Preload an interstitial; true when a fill is ready. */
    suspend fun loadInterstitial(): Boolean

    /** Show a loaded interstitial; true when actually shown. */
    fun showInterstitial(activity: Activity): Boolean
}

/**
 * Pure frequency mechanics over [AdPolicyConfig] — deterministic and host-agnostic.
 * The host feeds its own counters; this only answers "does the cadence allow it?".
 */
class AdFrequencyEngine(private val policy: AdPolicyConfig) {

    fun isEligible(
        actionsSinceLastAd: Int,
        lastShownEpochMillis: Long,
        shownToday: Int,
        nowEpochMillis: Long,
    ): Boolean =
        actionsSinceLastAd >= policy.nth &&
            nowEpochMillis - lastShownEpochMillis >= policy.minGapSeconds * MILLIS_PER_SECOND &&
            shownToday < policy.dailyCap

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/**
 * SDK-free stand-in until the AdMob integration phase: never loads, never shows.
 * Safe default — no ad calls, no test ids.
 */
class NoOpAdsGateway(
    @Suppress("unused") private val config: AdsConfig,
) : AdsGateway {
    override fun setConsent(granted: Boolean) = Unit

    override suspend fun loadInterstitial(): Boolean = false

    override fun showInterstitial(activity: Activity): Boolean = false
}
