package com.codeboxlk.tranzlate.core.model

/**
 * Domain mode = the user's engine selection (DECISIONS C-9 — verbatim).
 *
 * `AUTO` resolves among FREE engines only (C-10): it never silently falls into
 * metered NLP35, never charges quota, never needs the metered trigger.
 */
enum class ModeId {
    AUTO,
    ML2_MINI,
    ML2_ONLINE,
    NLP35,
}

/**
 * Persisted/resolved engine (DECISIONS C-9 — verbatim). Stored in
 * `Translation.engine` and used in the cache key (C-8); never `"AUTO"`.
 */
enum class Engine {
    OFFLINE_MLKIT,
    ONLINE_GOOGLE,
    ONLINE_CLOUD_NLP,
}

/**
 * C-9 mapping: ML2_MINI→OFFLINE_MLKIT · ML2_ONLINE→ONLINE_GOOGLE · NLP35→ONLINE_CLOUD_NLP.
 * AUTO returns null — it is resolved at runtime by the Translation brain among free
 * engines only (C-10), so it has no static image.
 */
fun ModeId.resolvedEngineOrNull(): Engine? =
    when (this) {
        ModeId.AUTO -> null
        ModeId.ML2_MINI -> Engine.OFFLINE_MLKIT
        ModeId.ML2_ONLINE -> Engine.ONLINE_GOOGLE
        ModeId.NLP35 -> Engine.ONLINE_CLOUD_NLP
    }
