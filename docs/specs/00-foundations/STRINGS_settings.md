# String Catalogue Foundation — Settings feature (Appearance)

> Tranzlate · feature: **Settings** — Appearance section only (issue #30 / #17 A4)
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Source of truth: `feature/settings/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

C-3: this catalogue is the key authority. Every `en` value is written fresh here per Material UX
writing (concise, sentence case, no ending period on labels). No `fil` / `pt-rBR` value is guessed —
all are flagged **NEEDS-TRANSLATION** (C-12): the keys ship in `values/` now, the translated files are
queued, following the codebase convention of omitting an untranslated key (fallback to `en`) rather
than a blanket lint suppression.

---

## 1. Legend

| Mark | meaning |
|------|---------|
| **NEW** | key does not exist — created here |
| `NEEDS-TRANSLATION` | no faithful `fil` / `pt-rBR` value can be certified — a translator is needed (not guessed) |

---

## 2. Master string table

| Key | Status | Type | `en` | Args | Notes |
|-----|--------|------|------|------|-------|
| `settings_title` | NEW | string | `Settings` | — | top-bar title |
| `settings_appearance_header` | NEW | string | `Appearance` | — | section header, `primary`-coloured labelLarge |
| `settings_theme_label` | NEW | string | `Theme` | — | sub-group label above the three options |
| `settings_theme_system` | NEW | string | `System default` | — | radio option — `ThemeMode.SYSTEM` |
| `settings_theme_light` | NEW | string | `Light` | — | radio option — `ThemeMode.LIGHT` |
| `settings_theme_dark` | NEW | string | `Dark` | — | radio option — `ThemeMode.DARK` |
| `settings_dynamic_color_label` | NEW | string | `Dynamic colour` | — | switch row title |
| `settings_dynamic_color_supporting` | NEW | string | `Use colours from your wallpaper` | — | switch supporting line, API 31+ |
| `settings_dynamic_color_unavailable` | NEW | string | `Needs Android 12 or newer` | — | supporting line when the row is disabled (API < 31) — the reason, so it is never a dead control |

## 3. Accessibility strings (C-4)

| Key | Status | `en` | Args | Notes |
|-----|--------|------|------|-------|
| `cd_settings_back` | NEW | `Back` | — | top-bar navigation icon |

## 4. testTags (C-1 · `tt_settings_<control>`)

| Tag | Control |
|-----|---------|
| `tt_settings_back` | top-bar back |
| `tt_settings_theme_system` | System radio row |
| `tt_settings_theme_light` | Light radio row |
| `tt_settings_theme_dark` | Dark radio row |
| `tt_settings_dynamic_color` | dynamic-colour switch |

## 5. Data & Downloads sections (added 2026-08-01, issue #152 — C-3 "missing keys get ADDED to STRINGS")

These six keys shipped into `feature/settings/src/main/res/values/strings.xml` without a row here.
Nothing caught it, because until issue #152 nothing checked C-3 at all; `verifyStringKeyDocs` now
does. `en` values are transcribed **verbatim from the shipped resource**, not re-authored.

| Key | Status | Type | `en` | Args | Notes |
|-----|--------|------|------|------|-------|
| `settings_data_header` | SHIPPED | string | `Your data` | — | section header, matching the `Appearance` header style |
| `settings_history_label` | SHIPPED | string | `History & saved` | — | row title — one row for both lists, because they are one store |
| `settings_history_supporting` | SHIPPED | string | `Every translation, and the ones you starred` | — | supporting line; says what the row contains rather than what it does |
| `settings_downloads_header` | SHIPPED | string | `Downloads` | — | section header above the offline-language controls |
| `settings_mobile_data_label` | SHIPPED | string | `Always allow mobile data` | — | switch row title. "Always allow" is deliberate: OFF is the safe default, and the switch grants standing permission rather than turning a feature on |
| `settings_mobile_data_supporting` | SHIPPED | string | `Language packs are about 30 MB each. When off, you're asked before mobile data is used` | — | states the cost **and** the off-state behaviour, so nobody has to discover by being charged. ⚠ `about 30 MB` is the same unmeasured approximation as `offline_subtitle` in `STRINGS_language.md` — both need one real number |

> `settings_history_label` renders `&` as `&amp;` in the resource; the row above shows the value as
> the user sees it.

## 6. NEEDS-TRANSLATION queue (C-12)

All nine display strings + the CD strings above need `fil` and `pt-rBR`. Filed, not guessed.

> **Corrected 2026-08-01 (#152).** The paragraph that stood here described a state the module left
> behind: it said `values-fil/` and `values-pt-rBR/` carried only pre-existing keys, that the new
> Settings keys fell back to `en`, and that a per-string `tools:ignore` note guarded them. None of
> that is true today — all sixteen keys are present in all three locale files (verified by count),
> and there is no `tools:ignore` anywhere in `feature/settings/src/main/res/`. What is still open
> is narrower and worth stating plainly: the `fil` and `pt-rBR` wordings were authored in-project,
> not by a native speaker, so they are queued for review rather than missing.
