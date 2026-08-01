package com.codeboxlk.tranzlate.domain.translate

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import com.codeboxlk.tranzlate.domain.usage.SpendResult
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** "auto" sentinel (Translator contract §1.1) — never persisted (DATA_MODEL). */
private const val AUTO_DETECT_LANG = "auto"

/**
 * THE single ask-flow encoding (plan §2 `:core:domain`):
 *
 *   Cache read (hit → done, zero cost) → Access resolve → atomic spend →
 *   translate → refund on failure → history write → Ads ask
 *
 * APP_STRUCTURE's flow is sequenced HERE once, so no feature can ever re-sequence
 * it (screens just ask; they don't do the work).
 *
 * Rules encoded:
 *  - Metering applies to the metered mode only (D-2 Advanced-AI pool; C-10 — AUTO
 *    and the free engines never charge quota, so they never hit the limit gate).
 *  - At-limit metered ask short-circuits to [TranslationOutcome.LimitReached]
 *    BEFORE any engine call (G11) — no quota burn, no network.
 *  - The metered gate is ONE atomic [UsagePolicy.trySpend] on the RESOLVED tier
 *    (issue #53 A4 — the old check→translate→increment shape let a double-tap
 *    race 4/5 into 6/5). DECISIONS' success-only constant survives via
 *    [UsagePolicy.refund]: failure and cancellation return the spend, so the
 *    net charge lands on success only.
 *  - History write on success only (DATA_MODEL `translation`; drawer Recents,
 *    issue #11): C-8 cache-deduped — an identical normalized tuple is never
 *    inserted twice. `Translation.sourceLang` must be a RESOLVED id, so an auto
 *    ask writes the id [resolvedSource] derives and an undetected one writes
 *    nothing at all. A failed history write never fails the translation the
 *    user is reading.
 *  - The Ads brain is ASKED after every completed translation (D-4 counts
 *    completions); the show/no-show DECISION stays inside [AdsCoordinator].
 *  - Language-usage stamps on SUCCESS only (issue #122, ruling R6): resolved
 *    source as SOURCE, target as TARGET — never on selection, never the "auto"
 *    sentinel. Engine-agnostic AND cache-agnostic on purpose: an online-served
 *    or cache-served answer still proves the user uses that language, and not
 *    stamping it would nudge deleting an actively-used pack. Fire-and-forget
 *    on [externalScope]: the stamp never delays the outcome and its failure is
 *    its own, never the translation's.
 */
class TranslateTextUseCase
    @Inject
    constructor(
        private val translator: Translator,
        private val featureAccess: FeatureAccess,
        private val usagePolicy: UsagePolicy,
        private val adsCoordinator: AdsCoordinator,
        private val translationRepository: TranslationRepository,
        private val languageUsageRepository: LanguageUsageRepository,
        private val clock: AppClock,
        @param:ApplicationScope private val externalScope: CoroutineScope,
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
                    // A cache-served answer is still a USE of both languages
                    // (the pair is resolved by construction on this path) — not
                    // stamping it would let a pack the user exercises daily
                    // look months stale in Manage packs (ruling R6 rationale).
                    stampLanguageUse(resolvedSrcLang = srcLang, tgtLang = tgtLang)
                    return TranslationOutcome.Success(
                        text = hit.targetText,
                        resolvedEngine = hit.engine,
                        fromCache = true,
                    )
                }
            }
            val metered = mode == ModeId.NLP35
            var spentTier: Tier? = null
            if (metered) {
                // Loading-gate (DATA_MODEL :48): resolve the entitlement BEFORE any
                // metered decision — a gate must never fire off Loading-as-FREE.
                val resolved = featureAccess.awaitResolved()
                if (!featureAccess.isEngineAllowed(mode)) {
                    // A3: access denial is NOT quota — the old LimitReached
                    // masking told a blocked user they were out of free uses.
                    return TranslationOutcome.NotEntitled
                }
                val tier = if (resolved is Entitlement.Paid) resolved.tier else Tier.FREE
                // A4: ONE atomic check-and-spend — the only gate. Tier-aware:
                // PRO spends against the fair-use pool, never the FREE 5/day.
                if (usagePolicy.trySpend(tier) == SpendResult.OVER) {
                    return TranslationOutcome.LimitReached
                }
                spentTier = tier
            }
            val outcome =
                try {
                    translator.translate(text, srcLang, tgtLang, mode)
                } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                    // A cancelled attempt is not a success — return the spend even
                    // though this scope is dying.
                    spentTier?.let { refundSafely(it) }
                    throw rethrown
                }
            if (outcome is TranslationOutcome.Success) {
                // Resolved ONCE for both stores (issue #151). The two seams used
                // to derive this separately — the stamp canonicalised inside its
                // repository, the history row did not — so a single detection
                // could land as `he` in one table and `iw` in the other.
                val resolvedSrcLang = resolvedSource(srcLang, outcome)
                // Success-only, resolved-only (ruling R6): auto resolves to the
                // DETECTED id or — undetected — stamps no source at all. The
                // literal "auto" sentinel is never a language and never stored.
                stampLanguageUse(resolvedSrcLang = resolvedSrcLang, tgtLang = tgtLang)
                saveToHistory(text, resolvedSrcLang, tgtLang, outcome)
                adsCoordinator.onTranslationCompleted()
            } else {
                // Success-only net spend (DECISIONS): failure returns the charge.
                spentTier?.let { refundSafely(it) }
            }
            return outcome
        }

        /**
         * The ONE derivation of "which language was this actually from" (issue
         * #151). Two rules meet here:
         *
         *  - A concrete ask is already resolved — it arrives through the prefs
         *    seam, which canonicalises on read AND write (#141), and it is the
         *    same value the C-8 cache read and the engine call above used. It is
         *    passed through untouched on purpose: re-spelling it HERE and not
         *    there would query the store under one id and write it under another.
         *  - A detected ask is NOT resolved. `MlKitLanguageIdentifier` hands back
         *    the platform's tag verbatim and `GctEngine` reports whatever the API
         *    said, so the answer can be a legacy or regional spelling (`iw`, `in`,
         *    `zh-CN`) that no catalog row carries. That one is canonicalised, at
         *    this door, before any store sees it.
         *
         * [LanguageTagResolver.canonicalOrSelf], not `canonicalId`: a tag the
         * catalog cannot serve stays itself rather than becoming null and taking
         * the history row down with it — a translation the user is reading must
         * not go unrecorded because the detector named a language we have no row
         * for.
         *
         * @return the resolved source, or null when an auto ask came back with no
         *   detect metadata at all — nothing to stamp, nothing to write.
         */
        private fun resolvedSource(
            srcLang: String,
            outcome: TranslationOutcome.Success,
        ): String? =
            if (srcLang == AUTO_DETECT_LANG) {
                outcome.detectedSource?.let(LanguageTagResolver::canonicalOrSelf)
            } else {
                srcLang
            }

        /**
         * BOTH refund paths go through here (PR-59 lens NOTE-1): a refund must
         * land even when the scope is already cancelled — a contended mutex
         * inside a cancelled coroutine would otherwise throw and leak the spend.
         */
        private suspend fun refundSafely(tier: Tier) = withContext(NonCancellable) { usagePolicy.refund(tier) }

        /**
         * BOTH success paths (engine and cache) stamp through here. Launched on
         * the application scope, NOT awaited: the user is already reading the
         * translation, so a slow or dying disk must cost the stamp, never add
         * latency or fail the outcome — and a screen scope cancelling mid-write
         * must not lose it. Each role is isolated so a failed source stamp
         * still lets the target stamp land.
         *
         * @param resolvedSrcLang RESOLVED source id, or null when an auto ask
         *   came back without detect metadata — then only the target is stamped
         *   (the sentinel must never be stored, ruling R6).
         */
        private fun stampLanguageUse(
            resolvedSrcLang: String?,
            tgtLang: String,
        ) {
            val atMillis = clock.nowMillis()
            externalScope.launch {
                if (resolvedSrcLang != null) {
                    stampSafely(resolvedSrcLang, LanguageRole.SOURCE, atMillis)
                }
                stampSafely(tgtLang, LanguageRole.TARGET, atMillis)
            }
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun stampSafely(
            languageId: String,
            role: LanguageRole,
            atMillis: Long,
        ) {
            try {
                languageUsageRepository.stampUse(languageId, role, atMillis)
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown // never break structured cancellation
            } catch (ignored: Exception) {
                // Best-effort: Manage packs misses one stamp; nothing else may notice.
            }
        }

        /**
         * @param resolvedSrcLang from [resolvedSource] — already canonical, never
         *   the sentinel. Null means an auto ask came back undetected: that stays
         *   unwritten, because `Translation.sourceLang` must be a resolved id
         *   (DATA_MODEL; issue #61 closed the older "no history on auto" gap).
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun saveToHistory(
            text: String,
            resolvedSrcLang: String?,
            tgtLang: String,
            outcome: TranslationOutcome.Success,
        ) {
            if (resolvedSrcLang == null) return
            try {
                val duplicate =
                    translationRepository.cached(text, resolvedSrcLang, tgtLang, outcome.resolvedEngine)
                if (duplicate == null) {
                    translationRepository.save(
                        Translation(
                            sourceLang = resolvedSrcLang,
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
