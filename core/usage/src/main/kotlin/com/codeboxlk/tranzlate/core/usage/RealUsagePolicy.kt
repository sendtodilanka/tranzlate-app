package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.domain.usage.UsagePolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USAGE BRAIN (plan §2 — the one home for the metered daily counter).
 *
 * Phase-2 scope: `usage.advanced_ai_count` via UsageDataSource, device-local-
 * midnight reset through [AppClock] (reset when local date(now) != date(reset_epoch)),
 * D-2 limits from RemoteConfigSource, success-only increment.
 */
@Singleton
class RealUsagePolicy
    @Inject
    constructor(
        @Suppress("unused") private val clock: AppClock,
    ) : UsagePolicy {
        // TODO(#4-brains): real implementation — placeholder returns Error(ENGINE) / safe defaults.
        // Safe defaults: full free allowance, never over, no warning, no-op increment.
        override fun remaining(): Int = RemoteConfigDefaults.LIMIT_FREE

        override fun isOver(): Boolean = false

        override fun warningMessage(): String? = null

        override suspend fun increment() = Unit
    }
