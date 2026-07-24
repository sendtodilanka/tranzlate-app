# Resolved Decisions & Defaults (single source of truth)

> Rule: **no feature spec may contain an open question that gates coded behaviour.** Decisions land here first; specs cite `D-n`.
> Status: 2026-07-21 · decisions D-0..D-4 confirmed by product owner in session; D-5 added 2026-07-22 (design phase).
> **2026-07-22: C-2 amended — explicit Translate for all engines (issue #9).**
> **2026-07-24: D-5 revised → D-5 rev.2 — persistent bottom-nav IA on Compact (Home/Chat/Camera), owner (issue #26).**

## ⭐ D-0 — NORTH STAR: behave like the Google Translate Android app (governs everything)

The whole app's **behaviour must equal the Google Translate Android app.** GT is the behavioural gold standard for every equivalent surface:

| Tranzlate surface | Match GT's… |
|-------------------|-------------|
| Text | typed-text translation with tap-chip language change, swap, clear (✕), result with copy / TTS / fullscreen / star. **Trigger deviates from GT by owner decision (2026-07-22): explicit Translate action, not live-as-you-type — see the resolved-tension note below + C-2.** |
| Language picker | searchable list · Recent · All · "Detected" |
| Voice | tap mic → live speech → translate |
| Camera | point → instant on-image overlay (live) + capture/scan |
| Conversation (Dialog) | two-language auto-detect turn-taking |
| Offline | download language packs; full offline use |
| Saved / History | starred "Saved" + history list |

**Layering rule:** Tranzlate-specific additions — the 4 selectable engines (AUTO/Offline/Standard/Advanced-AI), subscription tiers, ads — **layer on top of** GT-equivalent core UX and must **not** break it.

**✅ Tension RESOLVED (owner decision 2026-07-22, issue #9):** GT translates **live as you type** for its (free, unlimited) engine; Tranzlate's **Advanced AI (NLP3.5) is metered** (D-2), so per-keystroke translation would burn quota. The earlier split rule (live-translate for free engines, debounce/affordance for metered) is **superseded**. Resolution: **every engine — free and metered — uses an explicit Translate action (`tt_text_translate_btn`); the result opens on its own screen** (a Compose transition makes it feel continuous). This is an **intentional, owner-approved deviation from GT's live-typing**, for three reasons: (1) **one predictable action across all engines** — no mode-dependent behaviour switch; (2) a **large, comfortable editor** — the input screen is for composing, the result screen for reading; (3) **zero per-keystroke quota risk** — one quota unit = one intentional translation, by construction. See C-2 for the binding convention.

> Every feature spec begins by stating **"GT-equivalent behaviour: …"** and only then its Tranzlate-specific deltas.

---


## Product decisions (confirmed)

| ID | Decision | Value |
|----|----------|-------|
| D-1 | Language change with existing result | **Auto re-translate** immediately (Google-Translate-style). Uses cache when available; no meter charge on cache hit. |
| D-2 | Tiering for metered Advanced AI (NLP3.5) | **Free = 20/day · Plus = 100/day · Premium = unlimited.** (Limits RemoteConfig-tunable: `limit_free`, `limit_plus`.) |
| D-3 | Save control on a result | **Star/bookmark icon** (toggles `favourite`). Thumbs removed (was feedback-ambiguous). |
| D-4 | Interstitial ad policy (free tier) | **Revenue-optimized, AdMob-compliant:** after every **N=2** completed translations, min gap **90s**, daily cap **12**. All three RemoteConfig-tunable (`ad_nth`, `ad_min_gap_s`, `ad_daily_cap`). Never on Back-press / utility navigation / task start. |
| D-5 rev.2 | Navigation model — **revised** (owner, issue #26, 2026-07-24) | **Compact = persistent bottom `NavigationBar` (Home / Chat / Camera) via `NavigationSuiteScaffold`.** Home = text translation · Chat = conversation (**deferred to v2** — the tab shows a coming-soon placeholder) · Camera. The ☰ top-bar drawer holds SECONDARY destinations only (History, Saved, Offline languages, Settings, Help, About) + Recents + account — it is never the primary navigation. **Medium = rail · Expanded = permanent drawer** (C-13 adaptive nav via `NavigationSuiteScaffold`, + ListDetail); History/Settings stay drawer-secondary. **The bar/rail/drawer shows ONLY on the top-level tabs** — a secondary/detail destination (Settings, Result, LanguagePicker, History) uses `NavigationSuiteType.None`, so it gets the full height (GT-style detail flow, no bottom nav). This is a **deliberate, owner-approved deviation from GT** (which has no phone bottom bar) — treat it like the recorded explicit-Translate deviation (D-0 / C-2), not an error. Contract: `docs/design/UI_SPEC.md`. |

> **D-5 (original, 2026-07-22) — SUPERSEDED by D-5 rev.2 above (issue #26, 2026-07-24); kept for the paper trail:**
> ~~Compact = hub model, **no bottom nav bar.** Peer translation MODES stay one tap away ON the hub itself: Text = the always-visible composer · Voice = the composer mic · Camera + Conversation = canvas quick-action tiles. The ☰ drawer holds SECONDARY destinations (Search, History, Saved, Offline languages, Settings) + Recents + account. History reclassified as secondary. Medium/Expanded keep C-13 adaptive nav.~~ *(The hub model — and its canvas quick-action tiles instead of a bottom bar — is reversed by rev.2; Chat replaces the deferred Conversation tile as a v2-placeholder tab.)*

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
| Translate trigger | **Explicit Translate action (`tt_text_translate_btn`) for ALL engines — no debounce-fired translation** (amended 2026-07-22, see C-2) |

---

## ⚖️ Cross-doc canonical conventions (the tie-breaker every doc defers to)

> **Why:** foundation docs were authored in parallel and contradicted each other (re-review found 13 conflicts). These are the single-authority resolutions. **Any doc that disagrees is wrong and must be edited to match.** Every future feature spec inherits these — no re-litigating per feature.

| # | Conflict | **Canonical resolution** |
|---|----------|--------------------------|
| C-1 | testTag namespace | **`tt_<feature>_<control>`** (e.g. `tt_text_input`, `tt_text_swap`, `tt_text_mode_chip`, `tt_text_result`, `tt_text_copy`, `tt_text_star`, `tt_text_error_view`). The TEST-contract set is authoritative; spec tables reference it verbatim. |
| C-2 | Translate trigger (amended 2026-07-22, issue #9) | **EVERY engine (free and metered) fires translation only on the explicit Translate action `tt_text_translate_btn`** (IME ⏎ mirrors it). **No debounce-fired translation in any mode.** The result opens on its own screen (Compose transition keeps it feeling continuous). Meter charge unchanged: **once, on engine success only** (see Engineering constants). UI/E2E happy-path in ALL modes asserts a **tap on `tt_text_translate_btn`**. *(Supersedes the earlier free=live-translate / metered=`tt_text_translate_action` split.)* |
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
