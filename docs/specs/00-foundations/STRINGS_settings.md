# String Catalogue Foundation — Settings feature (Appearance)

> Tranzlate · feature: **Settings** — Appearance section only (issue #30 / #17 A4)
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Source of truth: `feature/settings/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

C-3: this catalogue is the key authority. Every `en` value is written fresh here per Material UX
writing (concise, sentence case, no ending period on labels). No `fil` / `pt-rBR` value is guessed —
all are flagged **NEEDS-TRANSLATION** (C-12): the keys ship in `values/` now, the translated files are
queued, and lint is told to expect them per string rather than blanket-suppressed.

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
| `cd_settings_theme_group` | NEW | `Theme` | — | `selectableGroup` label; each row's own text is its accessible name, state via `Role.RadioButton` + `selected` |
| `cd_settings_dynamic_color` | NEW | `Dynamic colour` | — | switch contentDescription; on/off exposed by `Role.Switch` + `toggleable` state, not baked into the label |

## 4. testTags (C-1 · `tt_settings_<control>`)

| Tag | Control |
|-----|---------|
| `tt_settings_back` | top-bar back |
| `tt_settings_theme_system` | System radio row |
| `tt_settings_theme_light` | Light radio row |
| `tt_settings_theme_dark` | Dark radio row |
| `tt_settings_dynamic_color` | dynamic-colour switch |

## 5. NEEDS-TRANSLATION queue (C-12)

All nine display strings + the CD strings above need `fil` and `pt-rBR`. Filed, not guessed. Until a
translator provides them, `values-fil/` and `values-pt-rBR/` carry only the keys that already existed;
the new Settings keys fall back to `en` and are marked with a per-string `tools:ignore` note in the
default file rather than a blanket suppression.
