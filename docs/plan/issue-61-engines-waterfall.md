---
status: accepted   # owner directive 2026-07-29: "1 idan piliwalata karagena yanna … oya okkoma wada tika yourself karala iwara karala thiyanna" — the pipeline itself was approved earlier (waterfall + detection + active monitor + timeouts, session of 2026-07-29)
issue: 61
title: Engines phase — MLKit→GOT→GCT waterfall, detection, pre-flight, quota-gated tail
date: 2026-07-29
author: Claude (Opus 5)
---

# Plan — issue #61

The owner-approved translate pipeline, exactly as specified across D-E1, C-10 rev.2,
BUSINESS_MODEL and the owner's own description (keyboard/STT input → DB check →
MLKit→GOT→GCT → informative actionable errors; active internet monitor; Language ID
with "und" fallback; GCT timeout):

```
ask(text, src, tgt, AUTO)
  └─ C-8 cache read (live since #55 — engine-agnostic, zero cost)
  └─ src == "auto" → ML Kit Language ID (on-device)
        · confident → resolvedSrc            · "und"/low → resolvedSrc stays "auto"
  └─ Tier 1  ML Kit offline   — needs resolvedSrc + BOTH models downloaded
        · resolvedSrc == "auto"      → trace += (OFFLINE_MLKIT, SKIPPED_SOURCE_UNKNOWN)
        · model missing              → trace += (OFFLINE_MLKIT, MODEL_NOT_DOWNLOADED)
        · unsupported tag            → trace += (OFFLINE_MLKIT, UNSUPPORTED_PAIR)
  └─ pre-flight: ConnectivityMonitor.isOnline() == false
        → trace += (ONLINE_GOOGLE, OFFLINE) + (ONLINE_CLOUD_NLP, OFFLINE) → Error(trace)
  └─ Tier 2  GOT (unofficial translate_a/single — D-E1 risk accepted)
        · kill-switch `got_enabled` (RemoteConfig) off → silent skip (ops, not user-actionable)
        · sl = resolvedSrc-or-auto; the response's `src` field IS captured (old app ignored it)
        · per-call timeout `got_timeout_ms`
  └─ Tier 3  GCT (official Cloud Translation **v2 REST, API-key**) — the QUOTA-GATED paid tail
        · key absent (white-label config) → tier absent from the chain (documented; no fake trace entry)
        · entitlement: withTimeoutOrNull(bounded) { awaitResolved() } — unresolved → never spend →
          trace += (ONLINE_CLOUD_NLP, SKIPPED_NO_QUOTA)   [closes PR-58 lens N1 for the reachable path]
        · UsagePolicy.trySpend(tier) OVER → trace += (ONLINE_CLOUD_NLP, SKIPPED_NO_QUOTA)
        · engine failure after spend → refund (success-only constant, same shape as the use case)
  └─ nothing succeeded → Error(trace)  — the owner's dialog reads this verbatim
```

Direct modes (ML2_MINI / ML2_ONLINE / NLP35) run their single engine only — no tail
gate in the translator for NLP35 (the use case already meters that mode; no double-spend).
`Success.detectedSource` is populated from Language ID or the engine's own detection, and
the use case's history write now persists auto-detect results under the RESOLVED source
(closing the recorded "no history on auto" gap).

## Batches

- **E1 — seams:** `ConnectivityMonitor` (interface core/common; real `NetworkCallback`-backed
  impl + prod binding; fake in core/testing) · RemoteConfig keys `got_enabled`,
  `got_timeout_ms`, `gct_timeout_ms` (+ defaults, static source, test FixedConfig).
- **E2 — adapters:** catalog pins (mlkit translate 17.0.3 already pinned; language-id;
  okhttp; coroutines-play-services) · `MlKitLanguageIdentifier` ("und"→null) ·
  `MlKitEngine` (downloaded-models check → MODEL_NOT_DOWNLOADED; tag check → UNSUPPORTED_PAIR) ·
  `GotEngine` (OkHttp GET, JsonElement parse of the array-of-arrays shape, detected `src` captured) ·
  `GctEngine` (v2 REST `?key=`, detectedSourceLanguage captured). Parsers unit-tested with
  golden payloads; engines constructor-inject an internal HTTP seam for tests.
- **E3 — waterfall:** `RealTranslator` orchestration per the diagram + the tail gate +
  use-case `detectedSource` history write + unit suite over fake engines (every trace shape
  in the diagram) + **device smoke on the Play AVD: the rebuild's first real translation**
  (models absent → MLKit trace entry → GOT answers). HIGH-RISK (usage spend mid-waterfall)
  → adversarial cross-model lens per Rule 5.

## Acceptance
Unit: every branch of the diagram produces the exact trace listed. Device: prod variant,
`Good morning` en→fr returns a real GOT translation; auto-detect on French input resolves
`fr` and writes history under the resolved source. Gates: full suite · detekt/spotless ·
fake+prod assemble · CI. Engine additions never touch the fake variant's golden table.

## Post-lens follow-ups (PR #62 cross-model lens, recorded not lost)
- **N4:** ML Kit non-English pairs may pivot through English and need the EN model too — the
  src+tgt downloaded-check could mis-report `ENGINE_ERROR` instead of `MODEL_NOT_DOWNLOADED`
  for fr→de-without-en. Device-verify when the offline manager (Screen B) lands; if confirmed,
  require EN for non-EN pairs.
- **N5:** Language ID can detect tags outside the picker catalog ("haw") which then persist as
  `Translation.sourceLang` — History's label lookup must fall back to the raw id (verify in the
  History batch; alternatively treat unknown detects like "und").
- **N7:** `NET_CAPABILITY_VALIDATED` can false-negative on some AVDs/VPNs → both online tiers
  skip. Correct-by-semantics (captive portal = offline); watch for smoke flakiness.
