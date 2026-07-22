# Feature Spec — Text Translation (v2, buildable)

> **Template status:** this is the reference shape for all ~16 feature specs. v1 scored 55%/not-buildable in adversarial review (47 gaps); v2 resolves every blocker by (a) leading with GT-equivalent behaviour, (b) inlining the resolved decisions, (c) delegating shared detail to the 5 foundation docs instead of hand-waving.
> **Status:** DRAFT v2 · uncommitted · 2026-07-21.
> **2026-07-22: C-2 amended — explicit Translate for all engines (issue #9).**

## Foundations this spec builds on (read as one contract)
| Concern | Source of truth | This spec does NOT redefine it |
|---------|-----------------|-------------------------------|
| Product decisions / defaults / north-star | [`00-foundations/DECISIONS.md`](00-foundations/DECISIONS.md) | D-0..D-4, defaults, engineering constants |
| Entities / prefs / usage keys | [`00-foundations/DATA_MODEL.md`](00-foundations/DATA_MODEL.md) | `Translation` entity, cache index, DataStore keys |
| Colors / type / spacing / motion | [`00-foundations/DESIGN_SYSTEM.md`](00-foundations/DESIGN_SYSTEM.md) | all token values (hex/sp/dp) |
| Copy (en/fil/pt-rBR) | [`00-foundations/STRINGS_text-translation.md`](00-foundations/STRINGS_text-translation.md) | every string key + translations |
| Tests + a11y | [`00-foundations/TEST_A11Y_CONTRACT_text.md`](00-foundations/TEST_A11Y_CONTRACT_text.md) | fake engine, golden outputs, testTags, per-control descriptions |
| Architecture | modes crux · FeatureAccess crux · UsagePolicy crux (design-debate docs) | orchestrator, gating, metering |

---

## 0. GT-equivalent behaviour (D-0 north star)
Behave like the **Google Translate Android app** text tab, then layer Tranzlate deltas:

**Match GT:** tap source/target chip → language picker; **⇄ swap** re-translates instantly; **✕ clear** empties input+result; auto source-detection with "%s (Detected)" label; result carries **Copy · TTS · fullscreen · ⭐ Save**; recent languages surface first in the picker. **Deviation (owner-approved 2026-07-22, C-2 amended):** GT's live-as-you-type trigger does **not** apply — every engine translates on the explicit **Translate** action (`tt_text_translate_btn`) and the result opens on its own screen (Compose transition keeps it feeling continuous).

**Tranzlate deltas (layer on, don't break GT UX):**
1. A **mode chip** (AUTO / Offline / Standard / Advanced AI) — GT has no engine choice; we do.
2. **Explicit Translate for ALL engines (C-2 amended 2026-07-22 — D-0 tension resolved):** no engine fires per keystroke or on debounce. Free (Offline, Standard, AUTO) and metered (Advanced AI) alike translate **only** on the explicit Translate action (`tt_text_translate_btn`; IME ⏎ mirrors it), so one quota unit = one intentional translation by construction. The one action is predictable across engines and keeps the editor a large, comfortable composing surface.
3. Subscription gating + ads per D-2/D-4.

---

## 1. User stories + acceptance criteria
| # | Story | Acceptance (Given/When/Then) — expected values in TEST_A11Y_CONTRACT |
|---|-------|----------------------------------------------------------------------|
| US-1 | translate typed text | Given EN→FR, when input="Good morning" and the user taps Translate (`tt_text_translate_btn`), then the fake-engine golden output renders in the result area and a `Translation` row is written (DATA_MODEL). |
| US-2 | change language | Given a result exists, when I change target via picker, then it **auto re-translates** (D-1) using cache if hit (no meter charge). |
| US-3 | auto-detect | Given source=Auto, then result header shows `language_detected` ("%1$s (Detected)") with the resolved id stored (never "auto"). |
| US-4 | swap | Given source≠Auto, when I tap ⇄, then langs swap **and** input↔result text swap + re-translate. Given source=Auto, ⇄ is disabled with `swap_disabled_auto` reason. |
| US-5 | pick mode | Given the mode chip, when I open+select, then it persists (`prefs.text_mode`) and applies next translate; metered status visible per US-6. |
| US-6 | see limits first | Given Advanced AI + free/plus, then a live "{used}/{limit} today" counter (D-2) + Premium chip is visible **before** translating; Premium hides it. |
| US-7 | act on result | Copy / TTS(play↔stop) / Reverse / ⭐Save / Add-to-Collection each execute with feedback (see §3 for Reverse vs Swap). |
| US-8 | recover from failure | Given engine failure, then inline error (`home_result_error_view_title`) + Retry; never a dead end or full-screen dialog. |
| US-9 | offline | Given offline + AUTO, uses offline engine if model present, else `offline_model_missing` guidance with a Download action. |
| US-10 | a11y | TalkBack: every control labeled (TEST_A11Y_CONTRACT), ≥48dp, state announced. |

## 2. Screen anatomy
**Compact** (tokens from DESIGN_SYSTEM; layout order fixed):
`TopAppBar(title) → LanguageBar([source ▾] ⇄ [target ▾]) → ModeChip + counter → InputCard(field, ✕, counter "0/500", [mic ↔ Translate]) → ResultCard(langs+detected, text, [Copy][TTS][Reverse][⭐][⋮]) on its own Result screen`.
- Input uses `bodyLarge`; result uses `headlineSmall`; spacing `md16` between blocks, `sm8` within (DESIGN_SYSTEM §4).
- **Explicit Translate action (`tt_text_translate_btn`) in EVERY mode** (C-2 amended 2026-07-22): disabled while the input is blank; the result opens on its own screen with a continuous Compose transition. No live/debounce-fired translation for any engine.

**Medium/Expanded:** `ListDetailPaneScaffold` — input pane | result pane. Pane split + min-widths per DESIGN_SYSTEM adaptive section. Never a stretched single column.

## 3. Components & interactions (deltas from foundations)

> **testTags follow DECISIONS C-1** — canonical prefix `tt_text_`. The bare names below map 1:1: `tt_text_source`, `tt_text_target`, `tt_text_swap`, `tt_text_mode_chip`, `tt_text_counter`, `tt_text_input`, `tt_text_charcount`, `tt_text_result`, `tt_text_copy`, `tt_text_tts`, `tt_text_reverse`, `tt_text_star`, `tt_text_more`, `tt_text_error_view`, `tt_text_retry`, `tt_text_translate_btn` (ALL modes — C-2 amended 2026-07-22; the former metered-only `tt_text_translate_action` is superseded), `tt_text_limit_sheet`.

| Element | Component (DESIGN_SYSTEM) | testTag | Spec note |
|---------|--------------------------|---------|-----------|
| Source/Target chip | `AssistChip`, ≥48dp | `lang_source`/`lang_target` | opens Language picker (separate spec) |
| Swap | `IconButton` | `btn_swap` | **Swap** = exchange the two languages (US-4). Disabled on Auto source. |
| Mode chip | `AssistChip`+menu | `chip_mode` | real ripple/role (fixes badge-disguise bug) |
| Counter | `Badge`/supporting text | `text_counter` | `usage_counter` string, hidden Premium |
| Input | `OutlinedTextField` multiline | `input_text` | ✕ trailing = clear; IME action = translate in every mode (mirrors `tt_text_translate_btn`, C-2) |
| Char counter | supporting text | `text_charcount` | "0/500"; inline `home_edit_dialog_reach_text_limit_*` at limit (rewritten copy, no "Translate Pro") |
| Result text | selectable `Text`, font-scalable | `text_result` | tap → fullscreen reader |
| Copy | `IconButton` | `btn_copy` | `home_result_option_copy` |
| TTS | `IconButton` play↔stop | `btn_tts` | |
| Reverse | menu item | `tt_text_reverse` | **Reverse (C-7)** = move result text → input, swap source↔target, re-translate. Post-condition: input == prior result, langs swapped, new result = reverse translation. (Swap = exchange languages only; Reverse = round-trip the output.) `home_result_option_reverse_translate` |
| ⭐ Save | `IconButton` toggle | `btn_star` | toggles `favourite` (D-3 star, thumbs removed) |
| More (⋮) | menu | `btn_more` | Add to Collection (`home_result_option_add_to_collection`) |
| Error | inline + Retry `Button` | `view_error`/`btn_retry` | not a Dialog |

## 4. State machine (deterministic — no open branches)
```
EMPTY ─type→ TYPING ─tapTranslate→ VALIDATING
VALIDATING ─gate/meter (§5)→ { blocked → LIMIT_SHEET | ok → TRANSLATING }
TRANSLATING ─success→ RESULT | ─fail→ ERROR ─Retry→ VALIDATING
RESULT/ERROR ─clear(✕)→ EMPTY
RESULT ─lang change→ VALIDATING (D-1 auto re-translate; cache-first)
```
- ALL engines: `TYPING→VALIDATING` fires **only** on the explicit Translate action (`tt_text_translate_btn` / IME ⏎) — no debounce transition in any mode (C-2 amended 2026-07-22).
- Cache hit (key per DECISIONS engineering constants) → `RESULT` immediately, **no meter charge**.

## 5. Behaviour: modes, gating, metering (exact)
- **Modes offered:** AUTO(default), Offline, Standard, Advanced AI. Default fresh user = **AUTO** (DECISIONS defaults). AUTO fallback = **offline→standard only** (free engines; **never** advanced/metered — C-10), fires on engine failure. If BOTH free engines fail: surface error / offline-missing guidance (§8), **no quota charge**.
- **Gating (FeatureAccess crux) + limits (D-2):** Advanced AI daily cap — **Free 20 · Plus 100 · Premium ∞** (RemoteConfig `limit_free`/`limit_plus`). Counter increments **once, on engine success only** (DECISIONS). Reset = device-local midnight (DATA_MODEL `usage.reset_epoch`).
- **At limit:** soft dismissible upgrade sheet; AUTO still works via free engines (never a hard block on the whole feature).
- **Ads (D-4):** interstitial after **every 2nd** completed translation, 90s gap, daily cap 12 (RemoteConfig-tunable). Never on Back/utility nav.

## 6. Data (see DATA_MODEL)
- Reads: language catalog, `prefs.source_lang/target_lang/text_mode`, entitlement (FeatureAccess), today's usage.
- Writes: successful translation → `Translation` row (with resolved `source_lang`, `engine`, `detected`); usage counter on metered success; last-used langs/mode.
- Process death: restore input text + langs + mode from prefs/saved-state (US-10 edge).

## 7. Non-functional / acceptance gates
- **a11y & tests:** governed entirely by TEST_A11Y_CONTRACT (fake engine golden outputs, testTags, per-control localized descriptions, focus order, live regions, ≥4.5:1, RTL, 200% scale). **pass/fail gate.**
- **Localization:** 0 hardcoded strings; all keys in STRINGS catalogue (en/fil/pt-rBR present or flagged NEEDS-TRANSLATION).
- **Adaptive:** Compact one-pane, Medium/Expanded ListDetail; landscape/multi-window reflow; tokens only, no raw dp.
- **Offline-first + performance:** translation starts within one frame of the Translate tap (no artificial delay); no cursor jump on recomposition.

## 8. Edge cases
empty/whitespace (no translate) · >500 chars: **hard-block the translate transition** (VALIDATING→TRANSLATING gated) + show inline `home_edit_dialog_reach_text_limit_*` copy, input **not truncated** · source==target · unsupported pair · single long word · emoji/RTL · rapid retype (edits alone never fire translation; cache serves repeated requests) · network drop mid-translate · offline model missing (Download CTA) · limit hit mid-session (AUTO continues) · process death restore · paste >limit.

## 9. Anti-requirements (do NOT carry)
❌ chat "What can I help with?" framing · ❌ rainbow gradient headline · ❌ NLP3.5 default · ❌ invisible metered status · ❌ silent truncation · ❌ dead text-limit AlertDialog · ❌ full-screen ErrorDialog for transient errors · ❌ drawer-only nav that HIDES peer tasks (D-5 hub model keeps peer modes on-canvas; drawer = secondary destinations only) · ❌ "Translate Pro" copy · ❌ Back-press ad · ❌ thumbs-as-save.

## 10. Deltas from v1 (what the review fixed)
Open questions **resolved** (D-1..D-4) · GT-equivalent behaviour now the spine · ~~live-translate replaces "always a button" (with metered exception)~~ *(v2 history — superseded 2026-07-22 by the C-2 amendment: explicit Translate for ALL engines, see §0)* · Reverse vs Swap disambiguated · entity/strings/tokens/tests **delegated to foundations** (no hand-waving) · cache-key + meter + reset now exact (DECISIONS).
