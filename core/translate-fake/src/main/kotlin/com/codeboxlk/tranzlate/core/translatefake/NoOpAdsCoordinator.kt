package com.codeboxlk.tranzlate.core.translatefake

import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator

/** NoOp ads brain (plan §6.4): fake variants never load or show anything. */
class NoOpAdsCoordinator : AdsCoordinator {
    override suspend fun onTranslationCompleted() = Unit
}
