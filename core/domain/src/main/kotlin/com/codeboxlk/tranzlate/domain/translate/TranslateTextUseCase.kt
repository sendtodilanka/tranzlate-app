package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import javax.inject.Inject

/**
 * THE single ask-flow encoding (plan §2 `:core:domain`):
 *
 *   Access check → translate → Usage +1 on SUCCESS ONLY → Ads ask
 *
 * APP_STRUCTURE's flow is sequenced HERE once, so no feature can ever re-sequence
 * it (screens just ask; they don't do the work).
 *
 * Rules encoded:
 *  - Metering applies to the metered mode only (D-2 Advanced-AI pool; C-10 — AUTO
 *    and the free engines never charge quota, so they never hit the limit gate).
 *  - At-limit metered ask short-circuits to [TranslationOutcome.LimitReached]
 *    BEFORE any engine call (G11) — no quota burn, no network.
 *  - Usage increments once, on engine success only (DECISIONS engineering
 *    constants: never on start, failure — cache-hit short-circuiting lands with
 *    the repository wiring in the Text vertical, phase 3).
 *  - The Ads brain is ASKED after every completed translation (D-4 counts
 *    completions); the show/no-show DECISION stays inside [AdsCoordinator].
 */
class TranslateTextUseCase
    @Inject
    constructor(
        private val translator: Translator,
        private val featureAccess: FeatureAccess,
        private val usagePolicy: UsagePolicy,
        private val adsCoordinator: AdsCoordinator,
    ) {
        suspend operator fun invoke(
            text: String,
            srcLang: String,
            tgtLang: String,
            mode: ModeId,
        ): TranslationOutcome {
            val metered = mode == ModeId.NLP35
            if (metered && (!featureAccess.isEngineAllowed(mode) || usagePolicy.isOver())) {
                return TranslationOutcome.LimitReached
            }
            val outcome = translator.translate(text, srcLang, tgtLang, mode)
            if (outcome is TranslationOutcome.Success) {
                if (metered) usagePolicy.increment()
                adsCoordinator.onTranslationCompleted()
            }
            return outcome
        }
    }
