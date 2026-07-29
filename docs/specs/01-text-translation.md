# Feature Spec — Text Translation (v2, buildable)

> **Template status:** this is the reference shape for all ~16 feature specs. v1 scored 55%/not-buildable in adversarial review (47 gaps); v2 resolves every blocker by (a) leading with GT-equivalent behaviour, (b) inlining the resolved decisions, (c) delegating shared detail to the 5 foundation docs instead of hand-waving.
> **Status:** DRAFT v2 · uncommitted · 2026-07-21.
> **2026-07-22: C-2 amended — explicit Translate for all engines (issue #9).**
> **2026-07-26: Home's shape is reset by DECISIONS D-5 rev.3** (issue #42 / PR #43) — Home is the approved design's **card stack**, with **no bottom nav, no drawer and no FAB**, and the language chips move out of the input card into a pinned row. §2 and §9 below are updated; the behaviour spine (§0/§1/§4/§5) is unchanged. C-5's counter literal is now **spaced** (`12 / 500`).

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
1. ~~A **mode chip** (AUTO / Offline / Standard / Advanced AI)~~ — **withdrawn (C-10 rev.2, issue #50): like GT, the user never picks an engine; the waterfall decides.**
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

## 2. Screen anatomy (**updated 2026-07-26 — D-5 rev.3 card stack**; full contract: UI_SPEC §2.1/§2.2)
**Compact** — pinned header, then a scrolling stack:
`TopAppBar("Translate", [Pro chip][⚙]) → LanguageRow([source ▾] ⇄ [target ▾])` **pinned (topBar slot, does not scroll)**
`→ InputPreviewCard(placeholder, [mic]) → "Tools" → 2×2 tool grid → Download-languages row → Phrasebook/Quotes → Natural-phrasing banner`
`→ Composer 5a(back + language pills, source field, [counter | mic ⇄ Translate])` on its own destination.

**Screen 5a (updated 2026-07-28 — issue #46).** Home's input card is a **preview only**: placeholder + voice button, nothing editable and no counter. Both controls open 5a, which is the one surface for typing, the result and re-editing — it **replaced the former Result destination**.
- **Edit face:** source label + `✕` clear (the Paste chip's mirror — Paste when empty, `✕` once there is text), the field, then `counter | mic ⇄ Translate`.
- **Read face:** the source text stays in place and stays tappable (tapping returns to editing); the translation lands beneath it in a **tonal result card** — `primaryContainer`, target language in CAPITALS, then speak · copy at the left with bookmark at the right edge. The keyboard drops and the counter and mic/Translate are gone: those exist **only while editing** (owner decision).
- **No engine badge anywhere** — the engine waterfall is invisible; user-facing engine selection is deferred.
- Leaving 5a for Home discards the draft; a trip to the language picker and back preserves it.
- Input placeholder + text use `headlineSmall`; result uses `headlineSmall`; supporting lines `bodyMedium`/`labelMedium`. Measurements resolve through tokens (C-14) — satisfied by every screen since PR #44.
- **Explicit Translate action (`tt_text_translate_btn`) in EVERY mode** (C-2 amended 2026-07-22): the result opens on its own screen with a continuous Compose transition. No live/debounce-fired translation for any engine.
- **The action slot is a morph, not a disable:** blank input renders `tt_text_mic` and **no** `tt_text_translate_btn` node at all; typing swaps it for the Translate button. Over the char limit the button stays present but disabled with the inline reason.
- ~~`ModeChip + counter` between the language bar and the input card~~ — **the mode chip has no home in rev.3** (the top bar carries only the Pro chip + Settings). US-5/US-6 cannot be satisfied until it is re-sited; see UI_SPEC §4.
- ~~`✕` clear inside the input card~~ — removed with the old top bar.

**Medium/Expanded:** the target is still `ListDetailPaneScaffold` — input pane | result pane, split + min-widths per DESIGN_SYSTEM adaptive section. ⚠ **Not implemented and not designed for the card stack** (D-5 rev.3 open item): the same stack currently renders at every width.

## 3. Components & interactions (deltas from foundations)

> **testTags follow DECISIONS C-1** — canonical prefix `tt_text_`. The bare names below map 1:1: `tt_text_source`, `tt_text_target`, `tt_text_swap`, `tt_text_mode_chip`, `tt_text_counter`, `tt_text_input`, `tt_text_charcount`, `tt_text_result`, `tt_text_copy`, `tt_text_tts`, `tt_text_reverse`, `tt_text_star`, `tt_text_more`, `tt_text_error_view`, `tt_text_retry`, `tt_text_translate_btn` (ALL modes — C-2 amended 2026-07-22; the former metered-only `tt_text_translate_action` is superseded), `tt_text_limit_sheet`.
>
> **Home surface tags (added 2026-07-26, PR #43 — C-1 clarified: `<feature>` = surface, not module):** `tt_text_card` (the preview card) · `tt_home_input_preview` (the placeholder) · `tt_home_mic` · `tt_home_settings` · `tt_home_pro` · `tt_home_tool_offline` / `_voice` / `_camera` / `_conversation` · `tt_home_row_download` · `tt_home_phrasebook` · `tt_home_quotes` · `tt_home_phrasing`. **Retired from Home:** `tt_text_menu`, `tt_text_clear`, `tt_text_mode_chip` (no drawer, no clear icon, no mode chip in rev.3) and the shell's `tt_app_nav_*` / `tt_app_drawer*`.
>
> **Composer 5a tags (added 2026-07-28, issue #46):** `tt_composer_card` · `tt_composer_back` · `tt_composer_clear` · `tt_composer_paste` · `tt_composer_source` · `tt_text_input` · `tt_text_counter` · `tt_text_mic` · `tt_text_translate_btn` · `tt_text_result_card` · `tt_text_result` · `tt_text_copy` · `tt_text_speak` · `tt_text_star` · `tt_text_error` · `tt_text_retry`. `tt_text_mic` / `tt_text_translate_btn` / `tt_text_counter` moved from Home to 5a with the input itself. **Never rendered (their screen is gone):** `tt_text_reverse`, `tt_text_mode_chip`, `tt_text_more`, `tt_text_tts`, `tt_text_charcount`, `tt_text_error_view`, `tt_text_limit_sheet` — the row-1 list above is the historical catalogue, not the live surface. Their strings are still in the catalogue pending a C-3 reconciliation pass.

| Element | Component (DESIGN_SYSTEM) | testTag | Spec note |
|---------|--------------------------|---------|-----------|
| Source/Target chip | `AssistChip`, ≥48dp | `lang_source`/`lang_target` | opens Language picker (separate spec) |
| Swap | `IconButton` | `btn_swap` | **Swap** = exchange the two languages (US-4). Disabled on Auto source. |
| Mode chip | `AssistChip`+menu | `chip_mode` | real ripple/role (fixes badge-disguise bug) |
| Counter | `Badge`/supporting text | `text_counter` | `usage_counter` string, hidden Premium |
| Input | `OutlinedTextField` multiline | `input_text` | ✕ trailing = clear; IME action = translate in every mode (mirrors `tt_text_translate_btn`, C-2) |
| Char counter | supporting text | `text_charcount` | **"0 / 500"** (C-5, spaced since PR #43); inline `text_over_char_limit` at limit (rewritten copy, no "Translate Pro") |
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
- **Modes (C-10 rev.2, 2026-07-29, issue #50):** the user never picks an engine — mode UI is deferred entirely. AUTO = the whole waterfall **MLKit → GOT → GCT**, with the **GCT tail quota-gated** by the FREE 5/day AI pool (unlimited on PRO). Quota spent + free engines failed → guided outcome (§8) via the C-11 sheet, **never a silent charge**. Cache hits charge nothing (C-8).
- **Gating (FeatureAccess crux) + limits (D-2 rev.2, issue #50):** Advanced AI daily cap — **FREE 5 · PRO unlimited (hidden fair-use cap)** (RemoteConfig `limit_free_ai`/`limit_pro_fair_use`; BUSINESS_MODEL.md). Counter increments **once, on engine success only** (DECISIONS). Reset = device-local midnight (DATA_MODEL `usage.reset_epoch`).
- **At limit:** soft dismissible upgrade sheet; AUTO still works via free engines (never a hard block on the whole feature).
- **Ads (D-4):** interstitial after **every 2nd** completed translation, 90s gap, daily cap 12 (RemoteConfig-tunable). Never on Back/utility nav.

## 6. Data (see DATA_MODEL)
- Reads: language catalog, `prefs.source_lang/target_lang/text_mode`, entitlement (FeatureAccess), today's usage.
- Writes: successful translation → `Translation` row (with resolved `source_lang`, `engine`, `detected`); usage counter on metered success; last-used langs/mode.
- Process death: restore input text + langs + mode from prefs/saved-state (US-10 edge).

## 7. Non-functional / acceptance gates
- **a11y & tests:** governed entirely by TEST_A11Y_CONTRACT (fake engine golden outputs, testTags, per-control localized descriptions, focus order, live regions, ≥4.5:1, RTL, 200% scale). **pass/fail gate.**
- **Localization:** 0 hardcoded strings; all keys in STRINGS catalogue (en/fil/pt-rBR present or flagged NEEDS-TRANSLATION).
- **Adaptive:** Compact one-pane, Medium/Expanded ListDetail; landscape/multi-window reflow; **tokens only, no raw dp (DECISIONS C-14 — satisfied since PR #44)** — `HomeScreen.kt` was migrated off its private literal block in PR #44 and now carries zero raw `dp`/`sp`. ⚠ The Medium/Expanded half of this gate is currently unmet (D-5 rev.3 open item).
- **Offline-first + performance:** translation starts within one frame of the Translate tap (no artificial delay); no cursor jump on recomposition.

## 8. Edge cases
empty/whitespace (no translate) · >500 chars: **hard-block the translate transition** (VALIDATING→TRANSLATING gated) + show inline `home_edit_dialog_reach_text_limit_*` copy, input **not truncated** · source==target · unsupported pair · single long word · emoji/RTL · rapid retype (edits alone never fire translation; cache serves repeated requests) · network drop mid-translate · offline model missing (Download CTA) · limit hit mid-session (AUTO continues) · process death restore · paste >limit.

## 9. Anti-requirements (do NOT carry)
❌ chat "What can I help with?" framing · ❌ rainbow gradient headline · ❌ NLP3.5 default · ❌ invisible metered status · ❌ silent truncation · ❌ dead text-limit AlertDialog · ❌ full-screen ErrorDialog for transient errors · ❌ **nav that HIDES peer tasks** (D-5 rev.3: Home's card stack keeps Offline/Voice/Camera/Conversation visible on the first screen; ~~D-5 rev.2's persistent bottom nav~~ is itself retired — do not re-add a bar or a drawer without a new decision record) · ❌ **a private `dp`/`sp` ladder in a screen file** (C-14) · ❌ "Translate Pro" copy · ❌ Back-press ad · ❌ thumbs-as-save.

## 10. Deltas from v1 (what the review fixed)
Open questions **resolved** (D-1..D-4) · GT-equivalent behaviour now the spine · ~~live-translate replaces "always a button" (with metered exception)~~ *(v2 history — superseded 2026-07-22 by the C-2 amendment: explicit Translate for ALL engines, see §0)* · Reverse vs Swap disambiguated · entity/strings/tokens/tests **delegated to foundations** (no hand-waving) · cache-key + meter + reset now exact (DECISIONS).
