package com.codeboxlk.tranzlate.core.data.config

import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline, deterministic [RemoteConfigSource] — the confirmed BUSINESS_MODEL §7
 * defaults and nothing else.
 *
 * This is no longer a placeholder: it is the **fake variant's** production
 * binding (`:app/src/fake` FakeConfigModule), so Maestro runs never depend on a
 * network fetch or on what a console happens to hold that day. The prod variant
 * binds `FirebaseRemoteConfigSource` instead (`:app/src/prod` TranslateModule).
 *
 * Credentials and legal links resolve to empty here ON PURPOSE — a fake build
 * must not be able to reach a real billing account.
 */
@Singleton
class StaticRemoteConfigSource
    @Inject
    constructor() : RemoteConfigSource {
        override fun limitFreeAi(): Int = RemoteConfigDefaults.LIMIT_FREE_AI

        override fun limitProFairUse(): Int = RemoteConfigDefaults.LIMIT_PRO_FAIR_USE

        override fun adNth(): Int = RemoteConfigDefaults.AD_NTH

        override fun adMinGapSeconds(): Int = RemoteConfigDefaults.AD_MIN_GAP_SECONDS

        override fun adDailyCap(): Int = RemoteConfigDefaults.AD_DAILY_CAP

        override fun textLimitFree(): Int = RemoteConfigDefaults.TEXT_LIMIT_FREE

        override fun textLimitPro(): Int = RemoteConfigDefaults.TEXT_LIMIT_PRO

        override fun gotEnabled(): Boolean = RemoteConfigDefaults.GOT_ENABLED

        override fun gotTimeoutMs(): Long = RemoteConfigDefaults.GOT_TIMEOUT_MS

        override fun gctTimeoutMs(): Long = RemoteConfigDefaults.GCT_TIMEOUT_MS

        override fun qonversionKey(): String = RemoteConfigDefaults.UNSET_TEXT

        override fun gctApiKey(): String = RemoteConfigDefaults.UNSET_TEXT

        override fun privacyPolicyUrl(): String = RemoteConfigDefaults.UNSET_TEXT

        override fun termsUrl(): String = RemoteConfigDefaults.UNSET_TEXT

        override fun contactEmail(): String = RemoteConfigDefaults.UNSET_TEXT

        /** Nothing to fetch — returning at once keeps the fake variant instant. */
        override suspend fun awaitFirstFetch() = Unit
    }
