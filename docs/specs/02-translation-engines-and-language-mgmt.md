# Tranzlate — Translation Engines, Offline Models & Language Screen (DESIGN NOTE)

> Clean-room rewrite එකේ **translation "brain"**, **offline-model management**, සහ **language management screen** සඳහා buildable design note එකකි.
> සියලු claims verified research මත පදනම්. Speculation නෑ.

---

## 1. Engine Strategy

### 1.1 Engine matrix

| | **A — Google Cloud Translation** | **B — "free Google online endpoint"** | **C — ML Kit (on-device)** |
|---|---|---|---|
| Official / supported? | ✅ Official, billing-backed | ❌ Unofficial, undocumented `translate_a/single` | ✅ Official SDK |
| Offline? | ❌ network අනිවාර්යයි | ❌ network අනිවාර්යයි | ✅ fully on-device |
| Cost | 500K chars/month free → ඉන්පසු **$20 / M chars** (NMT) | "free" (no agreement) | Free |
| Auth | v2 = API key; **v3 (Advanced) = service account අනිවාර්යයි** | none | none |
| Language coverage | 100+ pairs (secondary sources 130+) | ~web Translate ට සමාන | on-device — runtime `getAllLanguages()` (docs "50+", live table ~90+; **hardcode නොකර runtime count භාවිතා**) |
| SLA / stability | ✅ SLA, versioning, support | ❌ SLA නෑ, notice නැතිව break විය හැක | ✅ stable SDK |
| Privacy | text cloud ට යයි | text cloud ට යයි | text device එකෙන් පිටව යන්නේ නෑ |

Sources:
- Cloud editions/auth: https://docs.cloud.google.com/translate/docs/editions , https://docs.cloud.google.com/translate/docs/api-overview
- Cloud pricing: https://cloud.google.com/translate/pricing
- ML Kit: https://developers.google.com/ml-kit/language/translation , https://developers.google.com/ml-kit/language/translation/translation-language-support

### 1.2 Recommended composition & fallback

```
Tier 1 (DEFAULT):  ML Kit offline model available නම්
                   → free, private, no network — casual/simple translations
                        │  (model නෑ / quality/language coverage අඩු)
                        ▼
Tier 2 (ONLINE):   OFFICIAL Cloud Translation API v3 (Advanced, billing-backed)
                   → accuracy / broader language / quality අවශ්‍ය online cases
```

- **Default = Tier 1 (ML Kit offline).** Model already downloaded නම් network එකක්ම ඕන නෑ, private, free.
- **Online accuracy tier = Tier 2 (official Cloud Translation v3).** Billing + service-account backed, SLA සහිත.
- **Engine B production fallback chain එකට දමන්න එපා** (§2 බලන්න). පවතින app එකේ AUTO chain එකේ (`Offline → Google → Cloud`) middle "Google online" tier එක තමයි ToS-risk carry කරන්නේ — rewrite එකේදී එය official API එකකින් **replace** කරන්න.
- Cost නිසා Google Cloud API අපහසු නම්, safer **official** alternatives: **DeepL API / Microsoft (Azure) Translator / Amazon Translate** — free unofficial endpoint එකකට නොව.

---

## 2. ⚠️ HONEST RISK CALLOUT — the unofficial free Google endpoint

The "free Google online REST API" යනු almost certainly **`translate.googleapis.com/translate_a/single`** — `translate.google.com` වෙබ් backend එක reverse-engineer කරන undocumented endpoint එකකි. **මෙය official API එකක් නොවේ** (`py-googletrans` README disclaimer: unofficial, "not associated with Google").
Sources: https://github.com/ssut/py-googletrans , https://pypi.org/project/googletrans/ , https://policies.google.com/terms?hl=en-US

**ToS risk (flagged, not endorsed):**
- Google ToS එක automated/reverse-engineered access තහනම් කරයි; Google APIs ToS එක API එකෙන් "substantially similar machine translation engine" එකක් build කිරීම තහනම් කරයි. Consumer web endpoint එක **කිසිම API agreement එකක් යටතේ නෑ**.
- නිශ්චිත enforcement outcome case-by-case (fully "verified" නෑ) — නමුත් ToS text (automated access + competing-MT-engine clauses) verified. Risk: account/IP action, service withdrawal.

**Reliability risk (verified):**
- No SLA, no versioning, no support. එකම IP → HTTP **429**; errors → Google-side **IP ban**. README: "please use this library if you don't care about stability" + official API එක use කරන ලෙස recommend කරයි.

**➡️ Recommended safer path:** production rewrite එකෙන් `translate_a/single` endpoint එක **අයින් කරන්න**. Online tier එකට **official Cloud Translation v3** (හෝ DeepL/Azure/Amazon). Casual default එකට **ML Kit offline** — network/legal exposure එකක්ම නෑ.

---

## 3. Offline Model Download — the ML Kit limitation + realistic UX

### 3.1 API limitation (VERIFIED — මෙය තමයි කලින් fail වුණේ)

`RemoteModelManager` surface:
```
download(RemoteModel, DownloadConditions) → Task<Void>   // success/failure listener විතරයි
isModelDownloaded(RemoteModel)            → Task<Boolean>
getDownloadedModels(Class<T>)             → Task<Set<T>>
deleteDownloadedModel(RemoteModel)        → Task<Void>
```
- **NO progress:** `download()` එක `Task<Void>` — byte/percent progress callback එකක් **නෑ**. `addOnSuccessListener` / `addOnFailureListener` දෙක විතරයි. → progress bar implement කරන්න බැරි වුණේ මේ නිසාමයි.
- **NO cancel:** in-flight download එකක් abort කරන public API එකක් **නෑ**. `Task<Void>` එකට user-cancellable pattern exposed නෑ; download already-in-progress නම් existing task එකම return වේ — duplicate call එකකින්වත් නවත්වන්න බෑ. `deleteDownloadedModel()` = **සම්පූර්ණ වූ** model එකක් අයින් කරයි, mid-download abort එකක් නොවේ.
- Google Translate app එකේ %/pause-resume UX එක **Google-internal** download infra එකකින් — public SDK එකට expose කර නෑ.

Sources: https://developers.google.com/android/reference/com/google/mlkit/common/model/RemoteModelManager , https://developers.google.com/ml-kit/language/translation/android

Context: language pack ~**30MB**, Wi-Fi පමණක් download recommend; on-device ~90+ languages (English pivot internally).

### 3.2 Best-effort UX — user ට ලැබෙන දේ vs නොලැබෙන දේ

| CAN ✅ | CANNOT ❌ |
|---|---|
| Indeterminate spinner / linear progress (`determinate = false`) | සැබෑ byte/percent progress |
| ~30MB size-based **estimated** % (0→90 cap, completion-ට 100 snap) — **"ඇස්තමේන්තුගත" label එකෙන් honest** | in-flight download එකක් guaranteed **stop/abort** |
| Completion detect — `download()` success listener **primary** + `getDownloadedModels()` / `isModelDownloaded()` polling backup (coroutine loop 1–2s) | duplicate-call cancel |
| "STOP" = UI-level cancel + completion-පසු `deleteDownloadedModel()` best-effort cleanup | background download එක Play services තුළ stop කිරීමේ guarantee |
| WorkManager `CoroutineWorker` + `Task.await()` (`kotlinx-coroutines-play-services`) → system retry + Wi-Fi constraint | worker cancel → underlying ML Kit download stop guarantee |
| `DownloadConditions.Builder().requireWifi()` → metered-network protection | progress/pause hooks |

**Honest copy (කලින් fail වීම වළක්වන එකම realistic path):** `"Downloading… (~30MB, Wi-Fi)"`, "STOP" tap කළ විට `"download background එකේ දිගටම යා හැක"`. සැබෑ bytes ලෙස කිසි විටෙක misrepresent නොකරන්න.

### 3.3 Model states (sealed)

```
NotDownloaded → Downloading(indeterminate) → Downloaded
                        │                        │
                        └──── Failed(cause) ◄─────┘ (retry)
```
Source-of-truth = `getDownloadedModels()` + ViewModel in-flight tracking (`MutableStateFlow<Set<downloadingTags>>`).

---

## 4. Language Management Screen (Google-Translate-style)

### 4.1 List source — **bundled static list**, `getSupportedLanguages` API එකෙන් නොවේ

- App offline-first — language picker එක network/OAuth මත depend වීම anti-pattern. `getSupportedLanguages` (v3) OAuth `cloud-platform` scope සහිත **server-side** call එකක් — mobile client එකෙන් කෙලින්ම නොකරන්න.
- Canonical static list = **`Database.kt` seed data (180+ language names) reuse** කරන්න.
- Localized display name → Android `Locale(tag).getDisplayLanguage()` (API display-name call එකක් ඕන නෑ).
- Optional freshness → **Firebase Remote Config** override list (`RemoteConfigManager` already exists).

Source: https://developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateLanguage , https://docs.cloud.google.com/translate/docs/reference/rest/v3/projects/getSupportedLanguages

### 4.2 ML-Kit-capable subset — runtime intersection

- `TranslateLanguage.getAllLanguages()` → BCP-47 tags list (offline-capable source-of-truth). Validate single tag → `fromLanguageTag(tag)` (invalid → null).
- **static full list ∩ `getAllLanguages()`** → row එකක් `offline-capable` ද `online-only` ද තීරණය. Broad Cloud/NMT list 130+ නිසා **බහුතරය online-only**, minority offline-capable.
- Count **hardcode නොකර** `getAllLanguages().size` runtime භාවිතා (version bump එකකදී වෙනස් විය හැක).

### 4.3 Per-row states — 6-state sealed model

```kotlin
sealed interface LanguageRowState {
  data object OnlineOnly            // getAllLanguages() එකේ නෑ → download control නෑ, "Online only" badge
  data object NotDownloaded         // offline-capable + getDownloadedModels() එකේ නෑ → download ⬇
  data object Downloading           // indeterminate CircularProgressIndicator + ⏹ "stop" (= delete-to-cancel). % පෙන්නන්න එපා
  data object Downloaded            // ✓ / delete 🗑, "~30MB" size hint
  data object Deleting              // transient spinner
  data class  Failed(val cause: …)  // network/wifi/storage → retry; requireWifi fail → "Wi-Fi ඕන" message
}
```
State source-of-truth = `getDownloadedModels()` + in-flight `MutableStateFlow<Set<downloadingTags>>`.

### 4.4 Download control UX (no-progress / no-cancel constraint යටතේ)

1. `download(model, conditions)` fire → row `Downloading` (indeterminate spinner).
2. **"Stop" tap** → `deleteDownloadedModel()` (partial model clear best-effort) + downloading-set එකෙන් remove → `NotDownloaded`. (background download Play services තුළ දිගටම යා හැක — honest messaging.)
3. **Success listener** → `getDownloadedModels()` refresh → `Downloaded`.
4. **Failure** → `Failed(cause)`.
5. `requireWifi()` නිසා metered network එකේ silently fail විය හැක → **"Wi-Fi පමණයි" override toggle** එකක් `DownloadConditions` එකට.

### 4.5 Search / recent / detected sections

- **Search:** static list එකේ `languageCode` + localized `displayName` දෙකටම filter (`Locale.getDisplayLanguage`).
- **Recent:** DataStore recently-used tags (app `sourceKey`/`targetKey` memory extend).
- **Detect language:** **source picker එකේ විතරක්** top එකේ `"auto"` pseudo-entry — ML Kit Language Identification backed, real BCP-47 tag එකක් නොවේ; **target list එකේ නොපෙන්වන්න**.

---

## 5. App-structure plug-in

**Two owners — clear separation:**

### 5.1 Translation "brain" / module — **engine choice owner**
- Interface: `TranslateInterface` (already exists). Concrete engines: `OfflineTranslate` (ML Kit) + official-Cloud engine (v3). **Unofficial-endpoint engine එක drop කරන්න.**
- `TranslateRepository` = orchestrator: model tier resolve, fallback chain (§1.2), DB dedup (`fetchTranslationId()` — existing translation reuse).
- ViewModels **engine එක කෙලින්ම නොකැඳවයි** — repository/use-case හරහා පමණයි. Result `Flow` → `StateFlow<UiState>` (Phase 3 pattern).

### 5.2 Downloads manager — **model-state owner**
- `@Singleton MLKitClient` = ML Kit wrapper (existing `alreadyDownloadedLanguages()` = `getDownloadedModels()` wrap). `download()` එක `suspend fun` (`Task.await()`) ලෙස expose.
- **`MLKitModelRepository`** (Phase 2 planned interface, backed by `MLKitClient`) = download/remove/list — language screen ViewModel මෙයට inject වේ.
- Background download → **WorkManager `CoroutineWorker`** + `Task.await()` + `DownloadConditions.requireWifi()` (system retry + Wi-Fi). Worker cancel → **your** job නවතී; underlying ML Kit download stop guarantee නෑ (honest).
- Row-state source-of-truth: `getDownloadedModels()` + ViewModel in-flight `StateFlow<Set<tags>>`.

**Boundary rule:** brain = *"කුමන engine එකින් translate කරන්නද"*; downloads manager = *"කුමන offline model download/delete වී තිබේද"*. Language screen downloads-manager එකට කතා කරයි; translate flow brain එකට කතා කරයි. දෙක overlap වන එකම point = ML Kit model availability (brain, offline tier eligible දැයි බැලීමට `getDownloadedModels()` කියවයි).

---

## ✅ DECISIONS APPLIED (supersede any conflicting text above)

### D-E1 — Engines (product decision)
Keep **all three** engines: **ML Kit (offline, default) · GOT (free online, broad coverage) · GCT (paid, accurate)**.
- **GOT = the unofficial `translate_a/single` endpoint — RISK ACCEPTED by product owner** (ToS/reliability documented above; not re-litigated). Kept as the free online tier.
- Composition / fallback (AUTO): **offline (ML Kit) → free online (GOT) → accurate (GCT)**. Fixed engines selectable directly.

### D-E2 — Language UX = SEPARATE picking from downloading (Google-Translate pattern)
Do **not** put the full language list + download controls on one screen. Two screens, one job each:

**Screen A — Language Picker** (opens when choosing source/target)
- Purpose: pick a language to translate. **Every** language is selectable (online covers all).
- Layout: search bar · **Recent** · **All** (alphabetical) · **Detect language** (source only).
- Each row: a small non-blocking badge only — `⬇ downloaded` (offline ready) / `☁ online-only`. **No** download button here.
- Source = static bundled list (reuse existing 180+ seed; optional Remote Config override). No Cloud API call from the client.

**Screen B — Offline languages** (in Settings → "Offline translation")
- Purpose: manage offline packs. Shows **only ML-Kit-capable** languages (runtime `TranslateLanguage.getAllLanguages()` ∩ bundled list).
- Each row: size + control per 6-state model → NotDownloaded(⬇) / Downloading(indeterminate + Stop) / Downloaded(🗑 delete) / Deleting / Failed(retry) / (OnlineOnly langs never appear here).
- Download control = ML Kit best-effort (indeterminate progress + delete-to-cancel + WorkManager) per §3 — no true % / cancel (API limit, verified).

### Plugs into app structure
- **Translation "brain"/module** owns engine choice + fallback (offline→GOT→GCT).
- **Offline-downloads manager** (own module) owns model state (the 6-state model), used by Screen B.
- Screen A (picker) reads the static list + downloaded-state; never manages downloads.
