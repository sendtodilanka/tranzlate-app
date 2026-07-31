package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.config.effectiveGctApiKey
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.EngineAttempt
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.core.model.TranslationOutcome
import com.codeboxlk.tranzlate.core.translate.engine.EngineResult
import com.codeboxlk.tranzlate.core.translate.engine.GctEngine
import com.codeboxlk.tranzlate.core.translate.engine.GotEngine
import com.codeboxlk.tranzlate.core.translate.engine.MlKitEngine
import com.codeboxlk.tranzlate.core.translate.engine.MlKitLanguageIdentifier
import com.codeboxlk.tranzlate.core.translate.engine.TranslateEngine
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.translate.Translator
import com.codeboxlk.tranzlate.domain.usage.SpendResult
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Translator contract §1.1 "auto" sentinel. */
private const val AUTO = "auto"

/**
 * Bounded wait on the entitlement at the paid tail (PR-58 lens N1): a gateway
 * stuck at Loading must NEVER hold a translation hostage or default to a spend.
 */
private const val ENTITLEMENT_WAIT_MS = 3_000L

/**
 * TRANSLATION BRAIN orchestrator (issue #61 — the owner-approved pipeline).
 *
 * AUTO = the waterfall: Language ID → MLKit (offline) → GOT (free online,
 * kill-switched) → GCT (paid, QUOTA-GATED tail per BUSINESS_MODEL). Every
 * failed or skipped tier lands in the A3 trace — the owner's error dialog
 * reads it verbatim ("MLKit: fr not downloaded · GOT: offline").
 *
 * Direct modes run their single engine only; NLP35's quota is the USE CASE's
 * job (it metered before calling) — no double-spend.
 *
 * A deliberately disabled GOT (kill-switch) is skipped SILENTLY: an ops
 * decision is not user-actionable, so it earns no trace entry (plan §61).
 */
@Singleton
class RealTranslator internal constructor(
    private val tier1Offline: TranslateEngine,
    private val tier2FreeOnline: TranslateEngine,
    private val tier3Paid: TranslateEngine,
    private val identify: suspend (String) -> String?,
    private val connectivity: ConnectivityMonitor,
    private val config: RemoteConfigSource,
    private val appConfig: AppConfig,
    private val featureAccess: FeatureAccess,
    private val usagePolicy: UsagePolicy,
) : Translator {
    @Inject
    internal constructor(
        mlkit: MlKitEngine,
        got: GotEngine,
        gct: GctEngine,
        identifier: MlKitLanguageIdentifier,
        connectivity: ConnectivityMonitor,
        config: RemoteConfigSource,
        appConfig: AppConfig,
        featureAccess: FeatureAccess,
        usagePolicy: UsagePolicy,
    ) : this(
        tier1Offline = mlkit,
        tier2FreeOnline = got,
        tier3Paid = gct,
        identify = identifier::identify,
        connectivity = connectivity,
        config = config,
        appConfig = appConfig,
        featureAccess = featureAccess,
        usagePolicy = usagePolicy,
    )

    override suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String,
        mode: ModeId,
    ): TranslationOutcome =
        when (mode) {
            ModeId.AUTO -> {
                waterfall(text, srcLang, tgtLang)
            }

            ModeId.ML2_MINI -> {
                single(tier1Offline, text, srcLang, tgtLang)
            }

            ModeId.ML2_ONLINE -> {
                single(tier2FreeOnline, text, srcLang, tgtLang)
            }

            // Keyless brands have no paid tier ANYWHERE — the direct mode gets
            // the same guard the waterfall has (PR-62 lens OPEN-3), and no
            // network call ever carries key="".
            ModeId.NLP35 -> {
                if (gctConfigured()) {
                    single(tier3Paid, text, srcLang, tgtLang)
                } else {
                    TranslationOutcome.Error(
                        listOf(EngineAttempt(tier3Paid.engine, AttemptCause.ENGINE_ERROR)),
                    )
                }
            }
        }

    private suspend fun single(
        engineImpl: TranslateEngine,
        text: String,
        srcLang: String,
        tgtLang: String,
    ): TranslationOutcome =
        when (val result = engineImpl.translate(text, srcLang, tgtLang)) {
            is EngineResult.Success -> {
                TranslationOutcome.Success(
                    text = result.text,
                    resolvedEngine = engineImpl.engine,
                    detectedSource = result.detectedSource,
                )
            }

            is EngineResult.Failure -> {
                TranslationOutcome.Error(listOf(EngineAttempt(engineImpl.engine, result.cause)))
            }
        }

    @Suppress("ReturnCount") // each tier's success IS a return — the waterfall's whole shape
    private suspend fun waterfall(
        text: String,
        srcLang: String,
        tgtLang: String,
    ): TranslationOutcome {
        val attempts = mutableListOf<EngineAttempt>()

        // Detection (owner rule): short/ambiguous text identifies as "und" →
        // resolvedSrc stays "auto" and MLKit is skipped, online detects server-side.
        var detected: String? = null
        var resolvedSrc = srcLang
        if (srcLang == AUTO) {
            detected = identify(text)
            resolvedSrc = detected ?: AUTO
        }

        // Tier 1 — ML Kit offline.
        if (resolvedSrc == AUTO) {
            attempts += EngineAttempt(tier1Offline.engine, AttemptCause.SKIPPED_SOURCE_UNKNOWN)
        } else {
            when (val result = tier1Offline.translate(text, resolvedSrc, tgtLang)) {
                is EngineResult.Success -> {
                    return TranslationOutcome.Success(
                        text = result.text,
                        resolvedEngine = tier1Offline.engine,
                        detectedSource = detected,
                    )
                }

                is EngineResult.Failure -> {
                    attempts += EngineAttempt(tier1Offline.engine, result.cause)
                }
            }
        }

        // Pre-flight (active monitor): offline → both online tiers are dead-on-arrival;
        // say so instead of burning timeouts.
        if (!connectivity.isOnline()) {
            attempts += EngineAttempt(tier2FreeOnline.engine, AttemptCause.OFFLINE)
            if (gctConfigured()) {
                attempts += EngineAttempt(tier3Paid.engine, AttemptCause.OFFLINE)
            }
            return TranslationOutcome.Error(attempts)
        }

        // Tier 2 — GOT, behind the remote kill-switch.
        if (config.gotEnabled()) {
            when (val result = tier2FreeOnline.translate(text, resolvedSrc, tgtLang)) {
                is EngineResult.Success -> {
                    return TranslationOutcome.Success(
                        text = result.text,
                        resolvedEngine = tier2FreeOnline.engine,
                        detectedSource = detected ?: result.detectedSource,
                    )
                }

                is EngineResult.Failure -> {
                    attempts += EngineAttempt(tier2FreeOnline.engine, result.cause)
                }
            }
        }

        // Tier 3 — GCT: present only when the brand ships a key; quota-gated.
        if (gctConfigured()) {
            val success = gctTail(text, resolvedSrc, tgtLang, detected, attempts)
            if (success != null) return success
        }

        return TranslationOutcome.Error(attempts)
    }

    /**
     * The quota-gated paid tail. A Success returns; every may-not-run outcome
     * (unresolved entitlement, OVER, real engine failure after a refunded
     * spend) lands in [attempts] and returns null so the FULL trace survives.
     */
    private suspend fun gctTail(
        text: String,
        resolvedSrc: String,
        tgtLang: String,
        detected: String?,
        attempts: MutableList<EngineAttempt>,
    ): TranslationOutcome.Success? {
        // Bounded Loading-wait (PR-58 lens N1): unresolved entitlement NEVER spends.
        val entitlement = withTimeoutOrNull(ENTITLEMENT_WAIT_MS) { featureAccess.awaitResolved() }
        if (entitlement == null) {
            attempts += EngineAttempt(tier3Paid.engine, AttemptCause.SKIPPED_NO_QUOTA)
            return null
        }
        val tier = if (entitlement is Entitlement.Paid) entitlement.tier else Tier.FREE
        if (usagePolicy.trySpend(tier) == SpendResult.OVER) {
            attempts += EngineAttempt(tier3Paid.engine, AttemptCause.SKIPPED_NO_QUOTA)
            return null
        }
        val result =
            try {
                tier3Paid.translate(text, resolvedSrc, tgtLang)
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                withContext(NonCancellable) { usagePolicy.refund(tier) }
                throw rethrown
            }
        return when (result) {
            is EngineResult.Success -> {
                TranslationOutcome.Success(
                    text = result.text,
                    resolvedEngine = tier3Paid.engine,
                    detectedSource = detected ?: result.detectedSource,
                )
            }

            is EngineResult.Failure -> {
                // Success-only constant: the failed attempt's spend comes back.
                withContext(NonCancellable) { usagePolicy.refund(tier) }
                attempts += EngineAttempt(tier3Paid.engine, result.cause)
                null
            }
        }
    }

    // Remote-first (see effectiveGctApiKey): a brand can ship keyless and be
    // switched on from the console, and a revoked key can be replaced without a
    // Play release. Blank on either side still means "no paid tier at all".
    private fun gctConfigured(): Boolean = config.effectiveGctApiKey(appConfig).isNotBlank()
}
