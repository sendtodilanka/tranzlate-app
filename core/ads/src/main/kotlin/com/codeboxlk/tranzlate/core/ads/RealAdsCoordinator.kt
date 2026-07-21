package com.codeboxlk.tranzlate.core.ads

import com.codeboxlk.ads.AdsGateway
import com.codeboxlk.consent.ConsentGateway
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADS BRAIN (plan §2 — the ONLY show/no-show decision point, D-4).
 *
 * Phase-2 scope: D-4 policy (every N=2 completed translations, min gap 90s,
 * daily cap 12 — RemoteConfig-tunable; never on back-press/utility nav/task
 * start; never for paid tiers), `ConsentGateway` → `AdsGateway.setConsent`
 * wiring, counters via UsageDataSource, frequency mechanics via
 * `:lib:ads` AdFrequencyEngine.
 */
@Singleton
class RealAdsCoordinator
    @Inject
    constructor(
        @Suppress("unused") private val adsGateway: AdsGateway,
        @Suppress("unused") private val consentGateway: ConsentGateway,
    ) : AdsCoordinator {
        // TODO(#4-brains): real implementation — placeholder returns Error(ENGINE) / safe defaults.
        // Safe default: never shows anything.
        override suspend fun onTranslationCompleted() = Unit
    }
