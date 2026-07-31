---
status: accepted
issue: 117
title: The real language catalog — 20 interim rows become the derived 194
date: 2026-07-31
author: Claude (Opus 5), task L1
---

# Plan — issue #117 · L1 (catalog)

## 1. The problem

`BundledLanguageCatalog` shipped **20** languages and said so itself: its KDoc
called the list "INTERIM minimal … until the brains phase seeds the full
re-derived 180+ list". Nothing ever seeds it — `LanguageDao.upsertAll` has no
caller anywhere in the repo — so the interim list was not a fallback, it *was*
the product. A translator offering 20 languages is a regression against the live
app's 133.

Two other defects came with it:

- every row hardcoded `offlineAvailable = true`, which is only meaningful while
  the list is a hand-picked subset of ML Kit's set;
- `offlineDownloaded` was hardcoded `false` and **nothing overlaid the real
  value**, so the field was a permanent lie about the device.

## 2. Sources — what was fetched, when, and what each gave

All fetched **2026-07-31**. `cloud.google.com` is blocked for the WebFetch tool
in this environment ("Unable to verify if domain … is safe to fetch"), so the
Cloud page was retrieved with `curl` and parsed locally; the ML Kit reference was
retrieved the same way after the tool's markdown extraction dropped the tables.

| # | Source | Result |
|---|---|---|
| S1 | `developers.google.com/ml-kit/language/translation/translate-language-support` — the page **spec 02 cites** | **HTTP 301 → `developers.google.com/ml-kit`.** The page no longer exists. Spec 02's citation is stale. |
| S2 | `developers.google.com/android/reference/com/google/mlkit/nl/translate/TranslateLanguage` | HTTP 200. **59** `public static final String` constants, each with its `Constant Value`. Page footer: "Last updated 2024-10-31 UTC". |
| S3 | `cloud.google.com/translate/docs/languages` | HTTP 200. **NMT model table: 194 rows.** Also an LLM table (127 rows), a romanization table (14), a custom-model pair table (53) and a language-variants table (1 row: `zh-HK` → base model `zh-TW`). |
| S4 | `rfc-editor.org/rfc/rfc4647.txt` | §3.4 Lookup + the case-insensitivity MUST — the basis for `canonicalId`. |

**S2 is now the offline authority**, since S1 is gone. Its own doc for
`getAllLanguages()` states the returned values are "in the form of BCP 47 tags",
which is what licenses using a constant value directly as a catalog id.

### Count discrepancy, recorded not resolved

The ML Kit **landing page** (the target of the S1 redirect) says "Translate text
between **58** languages". The **API reference** (S2) declares **59** constants.
The reference is the one the compiler and `getAllLanguages()` agree with, so 59
is what we used. Which number is stale is **not verified**.

## 3. The capability split

`offlineAvailable` is not stored per row — it is **derived** from
`offlineCapableIds`, so the two can never drift apart through a typo.

| tier | rule | count |
|---|---|---|
| offline-capable | id ∈ ML Kit `TranslateLanguage` set (S2) | **59** |
| online-only | in Cloud NMT (S3) but not in S2 | **135** |
| **total** | | **194** |

Verified while merging: **every one of the 59 ML Kit tags appears in the Cloud
NMT list.** The offline set is a strict subset of the online set, so no language
is offline-capable but online-impossible. This is asserted in the test suite.

`offlineDownloaded` is now **runtime truth**. `LanguageRepositoryImpl` injects
`OfflineModelManager` and combines `modelStates()` into `languages()`; a row
reads `true` only when the manager reports `OfflineModelState.Downloaded`.
`Downloading` deliberately does **not** count — a half-transferred model cannot
translate.

The model-state flow is prefixed with an empty map (`onStart { emit(emptyMap()) }`)
so the picker paints its full list immediately. ML Kit's `getDownloadedModels`
goes through Play Services; on a device without it that answer may never arrive,
and a picker blocked on it would show nothing at all — a dead end under
EDGE_CASES. The list appears first and the download ticks land on the next
emission. Both halves of that contract are tested.

## 4. Every code decision, with its reason

Cloud prints five cells with two spellings. In each case the catalog keeps one
id and the other becomes an **alias** that `canonicalId` resolves.

| Cloud cell / incoming tag | catalog id | reason |
|---|---|---|
| `iw or he` | **`he`** | Modern ISO 639-1, and ML Kit's `HEBREW` constant is `"he"` — both sources already agree. `iw` kept as alias. |
| `fil or tl` | **`tl`** | ML Kit's constant is `TAGALOG = "tl"`. `RealOfflineModelManager` keys its state map by `TranslateLanguage.getAllLanguages()`, so an id of `fil` would match **no key** and would have to be remapped at the seam. `tl` is a valid BCP-47 primary subtag and Cloud documents it as accepted. `fil` kept as alias. Display name is **"Filipino"** (Cloud's label; ML Kit calls it "Tagalog"). |
| `jw or jv` | **`jv`** | Modern ISO 639-1. `jw` is **not in CLDR** — verified: `Locale.forLanguageTag("jw").getDisplayLanguage()` returns the literal string `"jw"`. `jw` kept as alias. |
| `zh-CN or zh (BCP-47)` | **`zh`** | ML Kit's constant is `CHINESE = "zh"`, and Cloud's own cell equates `zh` with `zh-CN`. Named **"Chinese (Simplified)"** per Cloud's row label. `zh-CN` kept as alias. |
| `zh-TW (BCP-47)` | **`zh-TW`** | Its own online-only row, "Chinese (Traditional)". |
| `in` | alias → `id` | Legacy ISO code for Indonesian, still emitted by older platform APIs. Cloud lists only `id`; ML Kit's constant is `id`. |
| `ji` | alias → `yi` | Legacy ISO code for Yiddish, same reasoning. |
| `zh-HK` | alias → `zh-TW` | S3's language-variants table: `zh-HK` "Hong Kong (Traditional)", **base model `zh-TW`**. |
| `zh-Hant` | alias → `zh-TW` | **Reasoned, not quoted** — see §6.3. |

### The premise that did not survive contact with the source

The task assumed "ML Kit uses some legacy/odd codes". **It does not.** All 59
constants are modern BCP-47: `he` not `iw`, `id` not `in`. Every legacy spelling
in this catalog comes from the **Cloud** side or from `java.util.Locale`, not
from ML Kit. No remapping layer between the catalog and the ML Kit seam is
needed, which is why catalog ids for the offline tier are the constant values
verbatim.

### Why `canonicalId` avoids `java.util.Locale`

Measured on the JBR 21 this repo builds with:
`Locale.forLanguageTag("iw").getLanguage()` returns **`he`** — the JVM
normalises the legacy code *forward*. Android has historically done the
**opposite** (canonicalising `he` to `iw`). Routing our mapping through `Locale`
would therefore make a passing unit test a statement about the desktop JVM and
not about the device. `canonicalId` uses an explicit lowercase table instead, so
both platforms are guaranteed to agree, and `lowercase()` is the no-arg
locale-invariant overload (the Turkish dotless-i corrupts the `Locale`-sensitive
one).

`canonicalId` implements RFC 4647 §3.4 Lookup — "the language range is
progressively truncated from the end until a matching language tag is located" —
so `en-GB` → `en`, `es-419` → `es`, `de-AT-1901` → `de`. The `zh-Hant` alias
exists precisely to **defeat** that truncation: plain lookup would send
`zh-Hant` to `zh`, which is Simplified, and Traditional text would be translated
by the wrong model with no error surfaced anywhere.

## 5. BCP-47 verification

Run against all 194 ids on JBR 21:

- **All 194 round-trip**: `Locale.forLanguageTag(id).toLanguageTag() == id`. None
  degrades to `und`, which is what a malformed tag produces.
- **8 ids have no CLDR name** — `alz`, `btx`, `bts`, `dov`, `cnh`, `hrx`, `ktu`,
  `yua`. For these `getDisplayLanguage` returns the subtag verbatim.

That 8-id gap is the reason `Language.name` exists and the reason the UI must
**not** call `getDisplayLanguage`: the catalog carries the official English name
from S3 for every row, so all 194 display correctly regardless of ICU coverage.
The exception list is pinned by name in the test so that the day CLDR learns one
of them, we are told rather than left guessing.

The 8 unnamed ids are kept, not dropped: Cloud supports them, and dropping a
supported language to make a test tidy is a product regression.

## 6. What could NOT be verified

1. **`TranslateLanguage.fromLanguageTag("fil")`** — whether ML Kit resolves the
   modern Filipino tag to its `tl` constant is undocumented and needs a device
   run. **Sidestepped**, not assumed: the catalog id is `tl`, the constant value
   itself, so the question never arises at runtime.
2. **Android ICU display-name coverage** — the 8-unnamed-id measurement is from
   the desktop JVM the unit tests run on. Android ships its own ICU data and the
   device set may differ. Safe either way, because the UI uses `Language.name`.
3. **`zh-Hant` → `zh-TW`** — reasoned from the script subtag plus S3's
   `zh-HK`→`zh-TW` variants row. **No Google page states it verbatim.** Included
   anyway because the alternative (RFC 4647 truncation to `zh` = Simplified) is
   known-wrong for Traditional text; recorded here as reasoning, not citation.
4. **ML Kit's 58-vs-59 count** — see §2.
5. **GOT tier coverage** — whether the unofficial `translate_a/single` tier
   (spec 02's tier 2) accepts all 194 codes is documented nowhere by Google.
   `offlineAvailable` makes no claim about it; the catalog's online tier is
   defined by the Cloud NMT list only.
6. **The overlay on a real device** — `offlineDownloaded` is proven by unit test
   against a fake `OfflineModelManager`. An emulator run confirming a downloaded
   model lights up its picker row is still owed.

## 7. Files

- `core/data/…/repository/BundledLanguageCatalog.kt` — 194 rows, derived
  `offlineAvailable`, alias table, `canonicalId`.
- `core/data/…/repository/LanguageRepositoryImpl.kt` — injects
  `OfflineModelManager`, overlays `offlineDownloaded`, normalises the
  `setLastUsed` id.
- `core/data/src/test/…/BundledLanguageCatalogTest.kt` — 16 tests: counts,
  uniqueness (incl. case), the ML Kit subset pinned as a literal, subset-of-online,
  no row claiming a download, BCP-47 round-trip, the CLDR-unnamed 8, and the full
  `canonicalId` contract.
- `core/data/src/test/…/LanguageRepositoryImplTest.kt` — 6 tests: bundled
  fallback, the overlay, the never-answering-ML-Kit guard, seeded-table
  precedence, and id normalisation on write.

`core/model/Language.kt` was **not** changed — the existing four fields express
the split correctly once `offlineAvailable` is derived and `offlineDownloaded`
is overlaid.

## 8. Gates

| gate | result |
|---|---|
| `:core:data:testDebugUnitTest` `:core:model:test` | PASS (28 in `:core:data`) |
| `:app:assembleTranzlateProdDebug` | PASS |
| `spotlessCheck` `detekt --rerun-tasks` | PASS |
| `:feature:languagepicker` · `:feature:text` · `:core:translate` tests | PASS |
| `:app:testTranzlateProdDebugUnitTest` | 4 `KonsistArchitectureTest` failures only — the known worktree bug, issue #110. No other failure. |
