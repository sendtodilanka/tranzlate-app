# Research — Issue #8: LLM enhancement for Tranzlate (size + Sinhala constraints)

> Read-only research record (Mandatory Rule 4). Owner directive 2026-07-22: app has NO LLM (conversation UI works without one); tiny-LLM enhancement welcome IF (a) app size not bloated, (b) Sinhala gap honestly resolved.
> Method: workflow `wf_543e7109-349` — research lenses 4 (Gemini Nano/AICore · on-device small models · cloud LLM APIs · translation-specific models) → synthesis → 2 adversarial verifications (9 load-bearing claims re-fetched; engineering-coherence check vs DECISIONS/DATA_MODEL/specs). **Verdicts: ok ×2**; corrections folded in below. All prices/sizes/lists verified 2026-07-22 — sources inline.

## 1. Verified landscape (2026-07-22)

### Gemini Nano (ML Kit GenAI / AICore)
- APIs: Summarization/Proofreading/Rewriting/ImageDescription (Beta) + **Prompt API (Beta — "no SLA or deprecation policy")** + SpeechRecognition (Alpha). Google sanctions "short translations" as a Prompt-API use case. [developers.google.com/ml-kit/genai]
- Devices: flagship allowlist **~70 models / 13 brands** (Pixel 9/10, S25/S26, OnePlus 13/15…); unlocked bootloaders excluded; `checkFeatureStatus()` mandatory at runtime.
- **Languages: Sinhala නෑ** — Summarization en/ja/ko; Proofread/Rewrite en/ja/fr/de/it/es/ko; ImageDesc en. Prompt API language list **publish වෙලාම නෑ** (si unverified).
- App size: **0 MB** — model AICore/system-managed (Private Compute Services), shared across apps; offline inference after one-time online model fetch.

### On-device small models (LiteRT-LM — MediaPipe LLM API is now maintenance-only)
- Sizes (verified from litert-community HF trees): Gemma-3 270M ≈ **249–304 MB** · Gemma-3 1B int4 ≈ **555–722 MB** (q8 ≈ 1.0–1.07 GB) · Gemma-3n E2B .task ≈ **3.14 GB** · Gemma-4 E2B ≈ **2.0–3.3 GB** (rt claims <1.5 GB RAM w/ 2/4-bit).
- **Sinhala: small-model evidence negative** — SinhalaMMLU (arXiv 2509.03162): best frontier 62–67%; comparable small open models ≈ 22–25% (near-random). Gemma license = pass-through restrictions, NOTICE, not OSI.

### Translation-specific models
- **ML Kit on-device translate = 59 languages, `si` ABSENT** (row-enumerated; spec-02's "~90+" figure needs doc fix). **GCT NMT officially supports `si`** (Translation-LLM list does NOT).
- **NLLB-200-distilled-600M (~594 MB int8, si-capable, phone-proven via RTranslator) = CC-BY-NC-4.0 → COMMERCIAL LICENSE BLOCKER** (ads+subscription app can't ship it).
- MADLAD-400-3B: Apache-2.0, si included, but ~**1.65 GB** quantized (over Play's 1.5 GB per-asset-pack limit → split packs or self-hosted only). TranslateGemma-4B: `si` tag exists but core-55 membership **unverified**.

### Cloud LLM (Firebase AI Logic — GA, backend-less client SDK)
- Models (firebase docs 2026-07-08): gemini-3.5-flash GA · **gemini-3.1-flash-lite GA** ($0.25/M in, $1.50/M out — output includes thinking tokens!) · 2.5-flash-lite retirement window opened 2026-07-22. Free tier on Spark plan; App Check (Play Integrity) enforcement critical pre-launch; per-user rate limits built in.
- Cost: short call (200 in/150 out) ≈ **$0.000275** → $27.50/100k calls; D-2 20/day metering caps worst case (~$55/day @10k maxed free users, RemoteConfig-tunable).
- Comparators: Claude Haiku 4.5 $1/$5 · gpt-5.4-mini $0.75/$4.50 · gemini-3.5-flash-lite $0.30/$2.50.
- **Sinhala: cloud Gemini/Claude-class models DO produce Sinhala** (SinhalaMMLU-tested; NotebookLM added si) but Google's official language list omits si and small-tier (Flash-Lite/Haiku/mini) Sinhala scores are **unpublished — verified data නෑ** → must gate with our own eval.

## 2. Phased plan (verdict-corrected)

| Phase | What | Size | Sinhala | Cost |
|---|---|---|---|---|
| **0 (now)** | Conversation UI ships with existing engines only (MLKit/GOT/GCT) — no LLM dependency | 0 | GCT NMT (official si) | — |
| **1** | **Cloud LLM enhancer** (Firebase AI Logic + Flash-Lite class) behind the engine interface, consuming the EXISTING Advanced-AI metered credit (D-2; no new billing surface). Features: tone rewrite, "explain", disambiguation. **Exit criterion for si: our Sinhala golden-set eval passes**; fail → `llm_enhance_enabled_si=false` (RemoteConfig) and si routes to GCT NMT **within the explicitly-selected Advanced-AI mode** (C-10 untouched — AUTO never enters metered). App Check + kill-switch + model-name RemoteConfig pin. "Conversation polish" sub-feature = post-MVP (Voice/Dialog v2). | ~0 MB (client SDK only, low-MB unverified) | Gated-honest (eval or off) | ~$0.000275/call, metered-capped |
| **2** | **Gemini Nano extras** on flagship allowlist (proofread/rewrite/Prompt) — free, offline, system-managed. **Depends on Phase 1** (degrade path = cloud; without it degrade = GCT). Beta APIs behind feature flags. | ~0 MB | **නෑ (documented)** — en/major langs bonus only | $0/call |
| **3 (golden harness — precondition folded into Phase 1)** | Sinhala(+ta/en) golden eval set (~100–300 pairs, native-reviewed), CI eval per model rotation, per-language×feature RemoteConfig flags | 0 | The honesty mechanism | ~cents |
| **4 (DEFERRED — watchlist, no ship)** | On-device LLM weights: blocked today (NLLB license / small-Gemma si quality / MADLAD size). **Reopen triggers:** (a) TranslateGemma core-55 si confirmed w/ mobile quant, (b) commercially-licensed <1 GB model passes golden set. If ever: LiteRT-LM + optional Wi-Fi on-demand download (existing WorkManager pattern / Play Asset Delivery) — never APK-bundled | 0 now; 250 MB–1.7 GB optional download if revived | Only via trigger-proof | $0 now |

**Bottom lines:** SIZE — shipping phases add **0 MB model weights** to the APK (thin client SDKs only); condition (a) satisfied. SINHALA — no on-device option fixes si today (MLKit translate si absent; Nano si absent; small models near-random); **proven si paths = GCT NMT (official) + gated cloud LLM**; we never promise si LLM quality without our own eval passing; condition (b) satisfied honestly.

## 3. Implementation notes (when scheduled)
- Conventions amendment: C-9 `Engine` enum + C-8 cache key + `Translation.engine` (DATA_MODEL) extended for the enhancer; "charge once, on engine success only" applies to LLM-enhance calls.
- Docs fix: spec-02 "~90+ languages" → ML Kit's actual 59 (si absent).
- All LLM access behind the Translation-brain interface — zero UI/app-code change when phases land (architecture already supports this).

## 4. Sources (load-bearing, re-verified by adversarial pass)
developers.google.com/ml-kit/genai (+ per-API language pages, release-notes) · developer.android.com/ai/gemini-nano · github.com/google-ai-edge/LiteRT-LM · huggingface.co/litert-community/* (exact MB) · huggingface NLLB-200 card (CC-BY-NC) · developers.google.com/ml-kit/language/translation/translation-language-support (59, si absent) · cloud.google.com/translate/docs/languages (si NMT) · firebase.google.com/docs/ai-logic{,/models,/pricing} · ai.google.dev/gemini-api/docs/pricing · platform.claude.com pricing · developers.openai.com pricing · arxiv.org/abs/2509.03162 (SinhalaMMLU) · ollama.com/library/gemma3/tags
