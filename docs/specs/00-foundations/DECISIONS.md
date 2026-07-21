# Resolved Decisions & Defaults (single source of truth)

> Rule: **no feature spec may contain an open question that gates coded behaviour.** Decisions land here first; specs cite `D-n`.
> Status: 2026-07-21 · decisions D-0..D-4 confirmed by product owner in session.

## ⭐ D-0 — NORTH STAR: behave like the Google Translate Android app (governs everything)

The whole app's **behaviour must equal the Google Translate Android app.** GT is the behavioural gold standard for every equivalent surface:

| Tranzlate surface | Match GT's… |
|-------------------|-------------|
| Text | live translate **as you type** (debounced), tap-chip language change, swap, clear (✕), result with copy / TTS / fullscreen / star |
| Language picker | searchable list · Recent · All · "Detected" |
| Voice | tap mic → live speech → translate |
| Camera | point → instant on-image overlay (live) + capture/scan |
| Conversation (Dialog) | two-language auto-detect turn-taking |
| Offline | download language packs; full offline use |
| Saved / History | starred "Saved" + history list |

**Layering rule:** Tranzlate-specific additions — the 4 selectable engines (AUTO/Offline/Standard/Advanced-AI), subscription tiers, ads — **layer on top of** GT-equivalent core UX and must **not** break it.

**⚠️ Known tension to resolve per feature (flag, don't silently pick):** GT translates **live as you type** for its (free, unlimited) engine. Tranzlate's **Advanced AI (NLP3.5) is metered** (D-2) — live-translate-on-every-keystroke would burn quota. **Resolution rule:** live-translate applies to the **free/unlimited engines** (Offline, Standard, and AUTO when it resolves to a free engine); the **metered Advanced AI** uses **debounce-on-pause or an explicit "translate" affordance** so a paid quota unit is spent once per intentional translation, not per keystroke. (This supersedes v1's always-a-button design.)

> Every feature spec begins by stating **"GT-equivalent behaviour: …"** and only then its Tranzlate-specific deltas.

---


## Product decisions (confirmed)

| ID | Decision | Value |
|----|----------|-------|
| D-1 | Language change with existing result | **Auto re-translate** immediately (Google-Translate-style). Uses cache when available; no meter charge on cache hit. |
| D-2 | Tiering for metered Advanced AI (NLP3.5) | **Free = 20/day · Plus = 100/day · Premium = unlimited.** (Limits RemoteConfig-tunable: `limit_free`, `limit_plus`.) |
| D-3 | Save control on a result | **Star/bookmark icon** (toggles `favourite`). Thumbs removed (was feedback-ambiguous). |
| D-4 | Interstitial ad policy (free tier) | **Revenue-optimized, AdMob-compliant:** after every **N=2** completed translations, min gap **90s**, daily cap **12**. All three RemoteConfig-tunable (`ad_nth`, `ad_min_gap_s`, `ad_daily_cap`). Never on Back-press / utility navigation / task start. |

## Defaults table (fresh install)

| Item | Default | Key |
|------|---------|-----|
| Source language | `en` English | `prefs.source_lang` |
| Target language | `fr` French | `prefs.target_lang` |
| Translation mode | **AUTO** (never the metered mode) | `prefs.text_mode` |
| Theme | System | `prefs.theme` |
| Usage reset boundary | **Device-local midnight** (store last-reset epoch; reset when local date differs) | `usage.reset_epoch` |
| Text length limit | 500 chars (RemoteConfig `text_limit`) | — |

## Engineering constants

| Item | Value |
|------|-------|
| Meter charge point | Once, on **engine success only**; never on cache hit, start, or failure |
| Translate trigger | **live-translate (debounce) for free engines; explicit affordance for metered** (see C-2) |

---

## ⚖️ Cross-doc canonical conventions (the tie-breaker every doc defers to)

> **Why:** foundation docs were authored in parallel and contradicted each other (re-review found 13 conflicts). These are the single-authority resolutions. **Any doc that disagrees is wrong and must be edited to match.** Every future feature spec inherits these — no re-litigating per feature.

| # | Conflict | **Canonical resolution** |
|---|----------|--------------------------|
| C-1 | testTag namespace | **`tt_<feature>_<control>`** (e.g. `tt_text_input`, `tt_text_swap`, `tt_text_mode_chip`, `tt_text_result`, `tt_text_copy`, `tt_text_star`, `tt_text_error_view`). The TEST-contract set is authoritative; spec tables reference it verbatim. |
| C-2 | button vs live-translate | **Free engines (Offline/Standard/AUTO-resolved-free): live-translate, debounce 400ms, NO translate button.** Metered **Advanced AI only**: explicit `tt_text_translate_action` (or IME ⏎), debounce 1.2s. UI/E2E happy-path for free engines asserts **wait-for-result**, not a button tap. |
| C-3 | string-key authority | **`STRINGS_*.md` is the ONLY key authority.** contentDescriptions use its `cd_*` keys. TEST_A11Y references those keys — never invents `a11y_*`/`cd_*` of its own. Missing keys get ADDED to STRINGS. |
| C-4 | live-region strings | Canonical keys **in STRINGS**: `a11y_translating`="Translating…", `a11y_result_ready`="Translation ready", `a11y_error`="Translation failed", `a11y_limit_reached`="Daily Advanced-AI limit reached". |
| C-5 | char counter literal | **`"%1$d/%2$d"` → "12/500"** (no spaces). Key `text_char_counter`. Spec text and every test assertion use exactly `12/500`. |
| C-6 | metered counter | **used/limit**, key `text_metered_counter`="%1$d/%2$d today" (arg1=used, arg2=limit). Hidden for Premium. (`usage_counter` retired.) |
| C-7 | Reverse action | **Reverse = move result text → input, swap source↔target, re-translate.** Post-condition: `tt_text_input` == prior result text, languages swapped, new result = reverse translation. |
| C-8 | cache lookup | **No sha.** `Translation.source_text` is **stored normalized** (trim + collapse internal whitespace, case-preserved). Cache lookup = index `(source_text, source_lang, target_lang, engine)` on the normalized value. |
| C-9 | engine enums | Domain **`ModeId`** {AUTO, ML2_MINI, ML2_ONLINE, NLP35} = user selection. Persisted/resolved **`Engine`** {OFFLINE_MLKIT, ONLINE_GOOGLE, ONLINE_CLOUD_NLP}. Map: ML2_MINI→OFFLINE_MLKIT · ML2_ONLINE→ONLINE_GOOGLE · NLP35→ONLINE_CLOUD_NLP. `Translation.engine` + cache key use the resolved **`Engine`** form. |
| C-10 | AUTO fallback metering | **AUTO resolves among FREE engines only** (offline→standard). It **never** silently falls into metered NLP35, never charges quota, never needs the metered trigger. NLP35 is reached **only** by explicit Advanced-AI selection. |
| C-11 | at-limit UX | **Dismissible bottom-sheet upgrade overlay** (`tt_text_limit_sheet`); AUTO keeps working underneath. NOT a navigated paywall screen. |
| C-12 | fil/pt-rBR CDs | Keys defined now; missing translations are a **tracked content task** (`NEEDS-TRANSLATION` backlog), done before ship. Does not block spec buildability; DoD gate 12 = "keys present + translations queued". |
| C-13 | adaptive dimensions | Defined in **DESIGN_SYSTEM.md → Adaptive section** (added): Compact <600dp · Medium 600–840 · Expanded >840; ListDetail list-pane ≥360dp, detail-pane ≥400dp, split 40/60; content max-width 480dp. |
