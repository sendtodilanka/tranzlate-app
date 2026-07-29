package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import javax.inject.Inject

/** "auto" sentinel (Translator contract §1.1) — never persisted (DATA_MODEL). */
private const val AUTO_DETECT_LANG = "auto"

/**
 * THE single ask-flow encoding (plan §2 `:core:domain`):
 *
 *   Cache read (hit → done, zero cost) → Access check → translate →\n *   Usage +1 on SUCCESS ONLY → history write → Ads ask
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
 *    constants: never on start, failure).
 *  - History write on success only (DATA_MODEL `translation`; drawer Recents,
 *    issue #11): C-8 cache-deduped — an identical normalized tuple is never
 *    inserted twice. Skipped while srcLang is the "auto" sentinel because
 *    `Translation.sourceLang` must be a RESOLVED id (DATA_MODEL) and detect
 *    metadata only arrives with the Translation brain. A failed history write
 *    never fails the translation the user is reading.
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
        private val translationRepository: TranslationRepository,
        private val clock: AppClock,
    ) {
        suspend operator fun invoke(
            text: String,
            srcLang: String,
            tgtLang: String,
            mode: ModeId,
        ): TranslationOutcome {
            // C-8 CACHE FIRST (issue #53 A2): the read precedes the gate AND the
            // engine, so a hit charges no quota and spends no API call — the
            // whole point of D-1's "no meter charge on cache hit", previously
            // unimplementable because this lookup ran after both. Engine-AGNOSTIC
            // by owner decision (any prior answer for the pair is acceptable).
            // Skipped for the "auto" sentinel: the pair is unknown until the
            // engines phase resolves detection. A hit asks the Ads brain nothing
            // (open owner decision — conservative default, BUSINESS_MODEL notes).
            if (srcLang != AUTO_DETECT_LANG) {
                val hit =
                    try {
                        translationRepository.cachedAny(text, srcLang, tgtLang)
                    } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                        throw rethrown // never break structured cancellation
                    } catch (
                        @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
                    ) {
                        null // best-effort: a broken cache must never block a translation
                    }
                if (hit != null) {
                    return TranslationOutcome.Success(
                        text = hit.targetText,
                        resolvedEngine = hit.engine,
                        fromCache = true,
                    )
                }
            }
            val metered = mode == ModeId.NLP35
            if (metered) {
                // Loading-gate (DATA_MODEL :48): resolve the entitlement BEFORE any
                // metered decision — a gate must never fire off Loading-as-FREE.
                featureAccess.awaitResolved()
                if (!featureAccess.isEngineAllowed(mode) || usagePolicy.isOver()) {
                    return TranslationOutcome.LimitReached
                }
            }
            val outcome = translator.translate(text, srcLang, tgtLang, mode)
            if (outcome is TranslationOutcome.Success) {
                if (metered) usagePolicy.increment()
                saveToHistory(text, srcLang, tgtLang, outcome)
                adsCoordinator.onTranslationCompleted()
            }
            return outcome
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun saveToHistory(
            text: String,
            srcLang: String,
            tgtLang: String,
            outcome: TranslationOutcome.Success,
        ) {
            if (srcLang == AUTO_DETECT_LANG) return
            try {
                val duplicate =
                    translationRepository.cached(text, srcLang, tgtLang, outcome.resolvedEngine)
                if (duplicate == null) {
                    translationRepository.save(
                        Translation(
                            sourceLang = srcLang,
                            sourceText = text,
                            targetLang = tgtLang,
                            targetText = outcome.text,
                            engine = outcome.resolvedEngine,
                            createdAt = clock.nowMillis(),
                        ),
                    )
                }
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown // never break structured cancellation
            } catch (ignored: Exception) {
                // Best-effort history: a failed write must never fail the
                // translation the user is reading (EDGE_CASES no-dead-end —
                // the Success outcome still surfaces; Recents just misses one row).
            }
        }
    }
