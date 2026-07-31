package com.codeboxlk.tranzlate.core.config

/**
 * Remote-tunable values seam (plan §2 `:core:config` — implementation-free).
 *
 * Two families of keys live behind this one interface, and the split is
 * deliberate (see [RemoteConfigKeys]):
 *  - **Product policy** — `limit_free_ai`, `limit_pro_fair_use`, `text_limit_*`,
 *    `ad_*`, `got_*`, `gct_*` (BUSINESS_MODEL §7 · D-2 rev.2 · D-4). These are
 *    OUR keys; until someone creates them in the console the confirmed
 *    [RemoteConfigDefaults] serve.
 *  - **Credentials + legal links** — the keys the owner's live app already
 *    publishes from the shared `tranzlate-offline` Firebase project. Same key
 *    names on purpose, so this app is configured on day one without a second
 *    console entry.
 *
 * Every getter is synchronous and MUST answer immediately: implementations serve
 * the last activated fetch, or the default. [awaitFirstFetch] is the only place a
 * caller may wait, and it must settle on failure too (offline first launch).
 */
interface RemoteConfigSource {
    /** FREE tier's daily AI-quality (GCT/LLM) pool — the counter the paywall shows. */
    fun limitFreeAi(): Int

    /** PRO abuse guard — never marketed, set far above honest use (BUSINESS_MODEL §1). */
    fun limitProFairUse(): Int

    fun adNth(): Int

    fun adMinGapSeconds(): Int

    fun adDailyCap(): Int

    fun textLimitFree(): Int

    fun textLimitPro(): Int

    /** GOT kill-switch (issue #61) — the unofficial tier can be disabled remotely overnight. */
    fun gotEnabled(): Boolean

    fun gotTimeoutMs(): Long

    fun gctTimeoutMs(): Long

    /**
     * Billing-provider (Qonversion) project key. **Empty is a legitimate answer**
     * — first launch before any fetch, or a brand with no subscription. Callers
     * must degrade honestly (no fake purchases), never assume a key.
     */
    fun qonversionKey(): String

    /** Google Cloud Translation v2 key. Empty = the GCT tier is absent, not broken. */
    fun gctApiKey(): String

    /** Play-policy required legal links. Empty until the first successful fetch. */
    fun privacyPolicyUrl(): String

    fun termsUrl(): String

    /** Support address surfaced in Settings. Empty = hide the row, never mailto:"". */
    fun contactEmail(): String

    /**
     * Suspends until the first remote fetch has SETTLED — success or failure —
     * so a caller that needs a credential can wait once instead of polling.
     *
     * Contract: must return (never hang) when the device is offline, and must be
     * safe to call from many coroutines. Static/fake sources return immediately.
     */
    suspend fun awaitFirstFetch()
}

/**
 * The canonical remote key strings — one home, so a console typo is a one-line
 * diff and the mapping is unit-testable.
 *
 * WHY the two naming styles: the `snake_case` block is ours (nothing in the
 * console yet → defaults serve). The PascalCase block is the owner's LIVE key
 * set in the shared `tranzlate-offline` project. Reusing those names is what
 * makes the credentials and legal links work on day one — renaming them would
 * silently ship an unconfigured app.
 *
 * DELIBERATELY NOT REUSED: the live project's `FeatureLimitPerDay` (=20) and
 * `AdGapInMinute` (=15). Their semantics are the OLD product's, and binding them
 * to [limitFreeAi]/[adMinGapSeconds] would let a console value written for a
 * different app silently overwrite our D-2 rev.2 / D-4 decisions. New product
 * policy gets new keys.
 */
object RemoteConfigKeys {
    // ---- Ours (BUSINESS_MODEL §7 · D-4) --------------------------------------
    const val LIMIT_FREE_AI = "limit_free_ai"
    const val LIMIT_PRO_FAIR_USE = "limit_pro_fair_use"
    const val AD_NTH = "ad_nth"
    const val AD_MIN_GAP_S = "ad_min_gap_s"
    const val AD_DAILY_CAP = "ad_daily_cap"
    const val TEXT_LIMIT_FREE = "text_limit_free"
    const val TEXT_LIMIT_PRO = "text_limit_pro"
    const val GOT_ENABLED = "got_enabled"
    const val GOT_TIMEOUT_MS = "got_timeout_ms"
    const val GCT_TIMEOUT_MS = "gct_timeout_ms"

    // ---- The live project's existing keys (do not rename) --------------------
    const val QONVERSION_API_KEY = "QonversionApiKey"
    const val CLOUD_API_KEY = "CloudApiKey"
    const val PRIVACY_POLICY = "PrivacyPolicy"
    const val TERMS_AND_CONDITION = "TermsAndCondition"
    const val CONTACT_EMAIL = "ContactEmail"
}

/** Confirmed product defaults (BUSINESS_MODEL §7 · D-4 · defaults table). */
object RemoteConfigDefaults {
    const val LIMIT_FREE_AI = 5
    const val LIMIT_PRO_FAIR_USE = 2000
    const val AD_NTH = 2
    const val AD_MIN_GAP_SECONDS = 90
    const val AD_DAILY_CAP = 12
    const val TEXT_LIMIT_FREE = 500
    const val TEXT_LIMIT_PRO = 5000
    const val GOT_ENABLED = true
    const val GOT_TIMEOUT_MS = 10_000L
    const val GCT_TIMEOUT_MS = 15_000L

    /**
     * Credentials + links have NO safe hardcoded default: an invented URL is a
     * Play-policy liability and an invented key is a silent outage. Empty means
     * "not configured yet", and every caller must handle it (EDGE_CASES
     * no-dead-end) rather than pretend.
     */
    const val UNSET_TEXT = ""

    /**
     * Fetch throttle. Firebase enforces a client-side minimum interval; 12 h is
     * Google's own recommended production value and keeps us inside the free
     * fetch quota. Debug builds pass 0 so a console change is testable at once.
     */
    const val FETCH_INTERVAL_SECONDS_PROD = 43_200L
    const val FETCH_INTERVAL_SECONDS_DEBUG = 0L

    /**
     * Hard ceiling on [RemoteConfigSource.awaitFirstFetch]. Firebase's own fetch
     * timeout can be as long as 60 s; a paywall tap must not sit that long, so
     * the wait is bounded here and the caller degrades to "not configured".
     */
    const val FIRST_FETCH_TIMEOUT_MS = 8_000L
}
