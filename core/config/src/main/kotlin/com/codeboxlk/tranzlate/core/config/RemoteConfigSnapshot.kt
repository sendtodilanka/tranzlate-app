package com.codeboxlk.tranzlate.core.config

/**
 * An immutable, already-resolved view of every remote value.
 *
 * WHY a snapshot instead of reading the SDK per call: [RemoteConfigSource]'s
 * getters are synchronous and are called from composition and from the
 * translation waterfall. Firebase's own `getString`/`getLong` will fall back to a
 * **blocking disk read with a 5 s timeout** when its in-memory container is still
 * cold (verified in `ConfigCacheClient.getBlocking`, `DISK_READ_TIMEOUT_IN_SECONDS
 * = 5`), which is not something the main thread may ever do. So the platform
 * source resolves ONCE on a background dispatcher and publishes one of these;
 * every getter afterwards is a field read.
 *
 * It also makes the whole key→value mapping a pure function ([readRemoteConfig])
 * that unit tests can drive without Firebase on the classpath.
 */
data class RemoteConfigSnapshot(
    val limitFreeAi: Int,
    val limitProFairUse: Int,
    val adNth: Int,
    val adMinGapSeconds: Int,
    val adDailyCap: Int,
    val textLimitFree: Int,
    val textLimitPro: Int,
    val gotEnabled: Boolean,
    val gotTimeoutMs: Long,
    val gctTimeoutMs: Long,
    val qonversionKey: String,
    val gctApiKey: String,
    val privacyPolicyUrl: String,
    val termsUrl: String,
    val contactEmail: String,
) {
    companion object {
        /** What the app runs on before — and if — any fetch ever lands. */
        val DEFAULTS =
            RemoteConfigSnapshot(
                limitFreeAi = RemoteConfigDefaults.LIMIT_FREE_AI,
                limitProFairUse = RemoteConfigDefaults.LIMIT_PRO_FAIR_USE,
                adNth = RemoteConfigDefaults.AD_NTH,
                adMinGapSeconds = RemoteConfigDefaults.AD_MIN_GAP_SECONDS,
                adDailyCap = RemoteConfigDefaults.AD_DAILY_CAP,
                textLimitFree = RemoteConfigDefaults.TEXT_LIMIT_FREE,
                textLimitPro = RemoteConfigDefaults.TEXT_LIMIT_PRO,
                gotEnabled = RemoteConfigDefaults.GOT_ENABLED,
                gotTimeoutMs = RemoteConfigDefaults.GOT_TIMEOUT_MS,
                gctTimeoutMs = RemoteConfigDefaults.GCT_TIMEOUT_MS,
                qonversionKey = RemoteConfigDefaults.UNSET_TEXT,
                gctApiKey = RemoteConfigDefaults.UNSET_TEXT,
                privacyPolicyUrl = RemoteConfigDefaults.UNSET_TEXT,
                termsUrl = RemoteConfigDefaults.UNSET_TEXT,
                contactEmail = RemoteConfigDefaults.UNSET_TEXT,
            )

        /**
         * The same values keyed by their remote names — handed to the provider as
         * in-app defaults so a missing console key resolves to our decision, not
         * to the SDK's static `""` / `0` / `false`.
         */
        val asRemoteDefaults: Map<String, Any> =
            mapOf(
                RemoteConfigKeys.LIMIT_FREE_AI to DEFAULTS.limitFreeAi,
                RemoteConfigKeys.LIMIT_PRO_FAIR_USE to DEFAULTS.limitProFairUse,
                RemoteConfigKeys.AD_NTH to DEFAULTS.adNth,
                RemoteConfigKeys.AD_MIN_GAP_S to DEFAULTS.adMinGapSeconds,
                RemoteConfigKeys.AD_DAILY_CAP to DEFAULTS.adDailyCap,
                RemoteConfigKeys.TEXT_LIMIT_FREE to DEFAULTS.textLimitFree,
                RemoteConfigKeys.TEXT_LIMIT_PRO to DEFAULTS.textLimitPro,
                RemoteConfigKeys.GOT_ENABLED to DEFAULTS.gotEnabled,
                RemoteConfigKeys.GOT_TIMEOUT_MS to DEFAULTS.gotTimeoutMs,
                RemoteConfigKeys.GCT_TIMEOUT_MS to DEFAULTS.gctTimeoutMs,
                RemoteConfigKeys.QONVERSION_API_KEY to DEFAULTS.qonversionKey,
                RemoteConfigKeys.CLOUD_API_KEY to DEFAULTS.gctApiKey,
                RemoteConfigKeys.PRIVACY_POLICY to DEFAULTS.privacyPolicyUrl,
                RemoteConfigKeys.TERMS_AND_CONDITION to DEFAULTS.termsUrl,
                RemoteConfigKeys.CONTACT_EMAIL to DEFAULTS.contactEmail,
            )
    }
}

/**
 * Provider-agnostic value reader — the ONE thing a platform implementation has to
 * supply. Keeping it this small is what lets [readRemoteConfig] be a pure,
 * Firebase-free function.
 */
interface RemoteValueReader {
    fun string(key: String): String

    fun long(key: String): Long

    fun boolean(key: String): Boolean
}

/**
 * THE key→typed mapping, in one testable place.
 *
 * Two guards, and only two, because each one prevents a specific real failure:
 *  - **Timeouts are clamped to positive.** A console value of `0` reaches OkHttp
 *    as `callTimeout(0)`, which means *no timeout at all* — a typo would hang the
 *    translation waterfall forever, so 0 or negative falls back to the default.
 *  - **Cadence divisors are clamped to at least 1.** `ad_nth = 0` would make
 *    "every Nth action" meaningless.
 *
 * Everything else is passed through verbatim — including `limit_free_ai = 0`,
 * which is a legitimate ops decision ("no free AI today") and must NOT be
 * second-guessed into a default.
 */
fun readRemoteConfig(reader: RemoteValueReader): RemoteConfigSnapshot =
    RemoteConfigSnapshot(
        limitFreeAi = reader.long(RemoteConfigKeys.LIMIT_FREE_AI).toInt(),
        limitProFairUse = reader.long(RemoteConfigKeys.LIMIT_PRO_FAIR_USE).toInt(),
        adNth = reader.positiveInt(RemoteConfigKeys.AD_NTH, RemoteConfigDefaults.AD_NTH),
        adMinGapSeconds = reader.long(RemoteConfigKeys.AD_MIN_GAP_S).toInt(),
        adDailyCap = reader.long(RemoteConfigKeys.AD_DAILY_CAP).toInt(),
        textLimitFree =
            reader.positiveInt(RemoteConfigKeys.TEXT_LIMIT_FREE, RemoteConfigDefaults.TEXT_LIMIT_FREE),
        textLimitPro =
            reader.positiveInt(RemoteConfigKeys.TEXT_LIMIT_PRO, RemoteConfigDefaults.TEXT_LIMIT_PRO),
        gotEnabled = reader.boolean(RemoteConfigKeys.GOT_ENABLED),
        gotTimeoutMs =
            reader.positiveLong(RemoteConfigKeys.GOT_TIMEOUT_MS, RemoteConfigDefaults.GOT_TIMEOUT_MS),
        gctTimeoutMs =
            reader.positiveLong(RemoteConfigKeys.GCT_TIMEOUT_MS, RemoteConfigDefaults.GCT_TIMEOUT_MS),
        qonversionKey = reader.string(RemoteConfigKeys.QONVERSION_API_KEY).trim(),
        gctApiKey = reader.string(RemoteConfigKeys.CLOUD_API_KEY).trim(),
        privacyPolicyUrl = reader.string(RemoteConfigKeys.PRIVACY_POLICY).trim(),
        termsUrl = reader.string(RemoteConfigKeys.TERMS_AND_CONDITION).trim(),
        contactEmail = reader.string(RemoteConfigKeys.CONTACT_EMAIL).trim(),
    )

private fun RemoteValueReader.positiveLong(
    key: String,
    fallback: Long,
): Long = long(key).takeIf { it > 0 } ?: fallback

private fun RemoteValueReader.positiveInt(
    key: String,
    fallback: Int,
): Int = long(key).toInt().takeIf { it > 0 } ?: fallback
