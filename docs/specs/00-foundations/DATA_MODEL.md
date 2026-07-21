# Data Model (typed — shared by all feature specs)

> Salvaged from proven schema v8 of the current app, **corrected** (adds engine provenance; renames for clarity). Room + DataStore.

## Entities (Room, db `tranzlate.db`)

### `Translation` (table `translation`)
| Column | Kotlin type | Notes |
|--------|------------|-------|
| `id` | `Long` | PK, autoGenerate |
| `source_lang` | `String` | BCP-47 id (e.g. `en`); **never** `"auto"` — store the *resolved* detected id |
| `source_text` | `String` | as entered (trimmed) |
| `target_lang` | `String` | BCP-47 id |
| `target_text` | `String` | translation output |
| `engine` | `String` | enum name: `OFFLINE_MLKIT` \| `ONLINE_GOOGLE` \| `ONLINE_CLOUD_NLP` (resolved engine, not "AUTO") |
| `detected` | `Boolean` | true if source came from auto-detect (drives "%s (Detected)" label) |
| `favourite` | `Boolean` | default false (D-3 star toggles) |
| `created_at` | `Long` | epoch millis (injectable clock) |

Indices: `(source_text, source_lang, target_lang, engine)` for cache lookup; `favourite`; `created_at DESC` for history paging.

> Legacy mapping (migration from old app if ever needed): `src_lang→source_lang, src→source_text, tgt_lang→target_lang, tgt→target_text, time→created_at`; `engine`/`detected` backfill = `ONLINE_GOOGLE`/false.

### `Language` (table `language`) — catalog (salvage 180+ seed)
`id: String (PK, BCP-47)` · `name: String` · `offline_available: Boolean` · `offline_downloaded: Boolean` · `last_used_at: Long?`

### Collections (secondary feature — spec later)
`collection(id PK, name UNIQUE)` · `collection_translation(collection_id FK, translation_id FK, PK both, CASCADE)` — **migration must dedup names before UNIQUE index** (audit fix).

## Preferences (DataStore)
| Key | Type | Default |
|-----|------|---------|
| `prefs.source_lang` | String | `en` |
| `prefs.target_lang` | String | `fr` |
| `prefs.text_mode` | String (ModeId) | `AUTO` |
| `prefs.theme` | Int | 0 (system) |

## Usage (DataStore)
| Key | Type | Notes |
|-----|------|-------|
| `usage.advanced_ai_count` | Int | today's metered count (all features share one NLP3.5 pool — **decision D-2 scope: per-account/day, not per-feature**) |
| `usage.reset_epoch` | Long | last reset; reset when device-local date(now) ≠ date(reset_epoch) |
| `usage.ads_shown_today` | Int | D-4 daily cap |
| `usage.ad_last_shown` | Long | D-4 min-gap |
| `usage.translations_since_ad` | Int | D-4 every-Nth |

## Entitlement (from FeatureAccess, not persisted here)
`Entitlement = Loading | Free | Paid(tier: PLUS | PREMIUM)` — gating always waits for a resolved (non-Loading) value.

---

## Engine enum + mapping (C-9) & cache normalization (C-8)

**Domain `ModeId`** (user selection): `AUTO · ML2_MINI · ML2_ONLINE · NLP35`
**Persisted/resolved `Engine`** (stored in `Translation.engine`, used in cache key):
| ModeId | resolves to Engine |
|--------|--------------------|
| ML2_MINI | `OFFLINE_MLKIT` |
| ML2_ONLINE | `ONLINE_GOOGLE` |
| NLP35 | `ONLINE_CLOUD_NLP` |
| AUTO | resolves among **free** engines only → `OFFLINE_MLKIT` or `ONLINE_GOOGLE` (never `ONLINE_CLOUD_NLP` — C-10) |

**Cache (C-8):** `Translation.source_text` is **stored normalized** = `trim + collapse internal whitespace, case-preserved`. Lookup = index `(source_text, source_lang, target_lang, engine)` on that normalized value. **No sha, no separate cache_key column.**
