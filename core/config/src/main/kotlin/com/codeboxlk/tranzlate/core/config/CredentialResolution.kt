package com.codeboxlk.tranzlate.core.config

/**
 * THE one precedence rule for the two credentials that can arrive from either
 * side of the white-label split — one home, so the engine chain, the paywall and
 * the DI wiring can never disagree about which key is live.
 *
 * **Remote wins when it is non-blank; the per-brand build config is the floor.**
 *
 * Why that order and not the reverse:
 *  - Remote is the ROTATION channel. A leaked or revoked key has to be
 *    replaceable without a Play release, and that only works if remote overrides.
 *  - Build config is the FIRST-LAUNCH floor. Remote Config serves nothing until
 *    a fetch has been activated, so a brand that hardcodes its key stays working
 *    on a cold, offline first launch — remote simply upgrades it later.
 *
 * Both sides may legitimately be blank (a brand with no paid tier, or a fresh
 * install that has not fetched yet). Blank means **not configured** — callers
 * must degrade honestly and must never send `key=""` to an API.
 */
fun RemoteConfigSource.effectiveGctApiKey(appConfig: AppConfig): String = gctApiKey().ifBlank { appConfig.gctApiKey }

/** See [effectiveGctApiKey] — same precedence, billing side. */
fun RemoteConfigSource.effectiveQonversionKey(appConfig: AppConfig): String =
    qonversionKey().ifBlank { appConfig.qonversionKey }
