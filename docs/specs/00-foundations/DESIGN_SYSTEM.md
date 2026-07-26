# Tranzlate — Design System Foundation

> Clean-room design foundation for the Tranzlate Android translator (Jetpack Compose + Material 3).
> This document **replaces** the Material template purple stubs (`Purple80`/`Pink80`) currently in `ui/theme/Color.kt`.
> **2026-07-22 (issue #15): palette = P9 "GT Blue (Google 1P)"** — owner-final, decided by running Google Translate v10.27 beside our build. **All 48 `ColorScheme` roles are set explicitly** (36 base + the 12 `*Fixed`); nothing falls back to a Material default. P8 teal is retired. Provenance + WCAG math: [`docs/design/PALETTES.md`](../../design/PALETTES.md) §P9.
> **2026-07-26 (issue #42 / PR #43): three amendments from the approved Claude Design export "Offline Translator M3"** — (a) the **neutral surface ramp** moves to the 1P *blue-tinted* variant for 7 roles (§0 contrast recomputed, §1.1/§1.2 updated, provenance PALETTES §P1 note "P1b"); (b) **§3 type = Roboto Flex, bundled**; (c) **§7 icons = Material Symbols Rounded as bundled vector drawables**. Accents, error and the 12 `*Fixed` roles are untouched. §9 Navigation is rewritten for **D-5 rev.3** (no bottom bar, no drawer).
> **Component-level contract: [`docs/design/UI_SPEC.md`](../../design/UI_SPEC.md).**
>
> **Scope:** static (baseline) palette + full M3 role tokens, type, spacing, shape, elevation, iconography, motion, and component notes.
> **Dynamic color:** on API 31+ the system may supply a Material You (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) palette; **this static palette is the guaranteed fallback** for API 24–30 and for users who disable dynamic color.

---

## 0. Contrast validation summary (P9 — recomputed 2026-07-22, issue #15 · neutral rows re-recomputed 2026-07-26, issue #42)

Every pair below was computed with the WCAG 2.x relative-luminance formula
`(L1 + 0.05) / (L2 + 0.05)` against the shipped `Color.kt` values.
**All pass AA; the lowest text pair is 5.13** (`onTertiaryFixedVariant`/`tertiaryFixedDim`, unchanged).
Full list incl. the `*Fixed` ladder: PALETTES.md §P9.

Rows marked **↺** were recomputed on 2026-07-26 because the neutral ramp changed
(§1.1/§1.2); the previous value is shown struck. **Every one still passes AA**, and
the light page pairs got *slightly better* — the tinted surfaces are marginally
darker than the warm greys they replaced.

| Pair | Light | Dark |
|---|---|---|
| `onPrimary` / `primary` | 6.39 ✓ | 7.50 ✓ |
| `onSurface` / `surface` ↺ | ~~15.67~~ **15.76** ✓ | 14.47 ✓ |
| `onSurfaceVariant` / `surfaceVariant` | 7.28 ✓ | 5.51 ✓ |
| `onPrimaryContainer` / `primaryContainer` | 7.04 ✓ | 7.04 ✓ |
| `onSecondaryContainer` / `secondaryContainer` | 7.20 ✓ | 7.20 ✓ |
| `onTertiaryContainer` / `tertiaryContainer` | 7.32 ✓ | 7.32 ✓ |
| `onErrorContainer` / `errorContainer` | 7.17 ✓ | 7.17 ✓ |
| `primary` / `surface` (icon/label accent use) ↺ | ~~6.07~~ **6.11** ✓ | 10.80 ✓ |
| `onSurface` / `surfaceContainerLowest` (panel text) | 16.48 ✓ | 15.03 ✓ |
| `onSurfaceVariant` / `surfaceContainerHigh` (pill label) ↺ | ~~7.68~~ **8.06** ✓ | ~~8.42~~ **8.45** ✓ |
| `onSurfaceVariant` / `surface` (supporting text) ↺ | ~~8.93~~ **8.98** ✓ | 10.90 ✓ |
| `onSurface` / `surfaceContainerHigh` (pill text) ↺ | **14.15** ✓ | **11.22** ✓ |
| `onSurface` / `surfaceContainerLow` ↺ | **14.92** ✓ | 12.84 ✓ |
| `onSurface` / `surfaceContainer` (card fill) ↺ | 14.13 ✓ | **12.86** ✓ |
| `primary` / `surfaceContainer` (mini-card icon) ↺ | 5.48 ✓ | **9.60** ✓ |
| `onSurfaceVariant` / `surfaceContainerLow` ↺ | **8.51** ✓ | 9.67 ✓ |

> **Design rule for `primary`:** `#0B57D0` (light) / `#A8C7FA` (dark) — Google's
> own reference tones, so our accent equals Google Translate's. **The dark theme
> never fills a large element with `primary`** (a near-white disc on `#131314`
> glares): the primary action fills with `primaryContainer` there — the rule
> lives in `PrimaryActionButton` (`:core:ui`), stated once.

---

## 1. Color role tokens (Material 3) — P9 "GT Blue (Google 1P)"

> The complete 48-role set, hex-for-hex with PALETTES.md §P9. §1.1/§1.2 list the 36 base roles per theme; §1.3 the 12 `*Fixed` roles, which are **the same in both themes**.
>
> **Neutral amendment — 2026-07-26 (issue #42 / PR #43).** The approved design draws the cool, blue-tinted 1P neutrals rather than the warm grey ladder. **Seven roles moved** (marked ↺ below); they are exactly the values PALETTES.md §P1 already recorded as the live-1P "blue-tinted variant **P1b**", so this is an adoption of an already-researched set, not a new invention. Everything else — accents, error, outline, inverse, the 12 `*Fixed` — is unchanged, and `surfaceContainer`/`surfaceContainerHighest` deliberately stay on the grey ladder in light (the design does not use them on Home). `android:windowBackground` in `app/src/main/res/values/colors.xml` tracks light `surface` and was moved with it.

### 1.1 Light scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#0B57D0` | Google reference primary40 |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#D3E3FD` | primary90 — tonal fill (chips, tonal buttons) |
| `onPrimaryContainer` | `#0842A0` | primary30 |
| `secondary` | `#00639B` | secondary40 |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#C2E7FF` | secondary90 |
| `onSecondaryContainer` | `#004A77` | secondary30 |
| `tertiary` | `#146C2E` | tertiary40 (green) |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#C4EED0` | tertiary90 |
| `onTertiaryContainer` | `#0F5223` | tertiary30 |
| `background` ↺ | `#F8FAFD` | = `surface` (1P blue-tinted, P1b) — was `#FAF9F8` |
| `onBackground` | `#1F1F1F` | |
| `surface` ↺ | `#F8FAFD` | Page background — was `#FAF9F8`. Mirrored in `values/colors.xml` `window_background` |
| `onSurface` | `#1F1F1F` | |
| `surfaceVariant` | `#E1E3E1` | |
| `onSurfaceVariant` | `#444746` | Secondary/label text on tonal areas |
| `surfaceDim` | `#DADADA` | |
| `surfaceBright` ↺ | `#F8FAFD` | = `surface` — was `#FAF9F8` |
| `surfaceContainerLowest` | `#FFFFFF` | Floating card fill in light (`LocalFloatingSurface`) |
| `surfaceContainerLow` ↺ | `#F0F4F9` | was `#F4F3F2` |
| `surfaceContainer` | `#EFEDED` | Default card/sheet base — **unchanged** (grey ladder; Home does not use it in light) |
| `surfaceContainerHigh` ↺ | `#E9EEF6` | Chip / language-pill / swap fill — was `#E9E8E8` |
| `surfaceContainerHighest` | `#E3E3E3` | **unchanged** |
| `surfaceTint` | `#0B57D0` | = `primary` (tonal elevation tint) |
| `inverseSurface` | `#303030` | |
| `inverseOnSurface` | `#F2F2F2` | |
| `inversePrimary` | `#A8C7FA` | = dark `primary` (primary80) |
| `outline` | `#747775` | Borders, dividers with emphasis |
| `outlineVariant` | `#C4C7C5` | Low-emphasis dividers |
| `error` | `#B3261E` | 1P error set |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#F9DEDC` | Error card container |
| `onErrorContainer` | `#8C1D18` | |
| `scrim` | `#000000` | |

### 1.2 Dark scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#A8C7FA` | primary80 — accent for text/icons, NOT large fills |
| `onPrimary` | `#062E6F` | primary20 |
| `primaryContainer` | `#0842A0` | primary30 — the primary ACTION fill in dark |
| `onPrimaryContainer` | `#D3E3FD` | primary90 |
| `secondary` | `#7FCFFF` | secondary80 |
| `onSecondary` | `#003355` | secondary20 |
| `secondaryContainer` | `#004A77` | secondary30 |
| `onSecondaryContainer` | `#C2E7FF` | secondary90 |
| `tertiary` | `#6DD58C` | tertiary80 (green) |
| `onTertiary` | `#0A3818` | tertiary20 |
| `tertiaryContainer` | `#0F5223` | tertiary30 |
| `onTertiaryContainer` | `#C4EED0` | tertiary90 |
| `background` | `#131314` | = `surface` (P1) |
| `onBackground` | `#E3E3E3` | |
| `surface` | `#131314` | |
| `onSurface` | `#E3E3E3` | |
| `surfaceVariant` | `#444746` | |
| `onSurfaceVariant` | `#C4C7C5` | |
| `surfaceDim` | `#131314` | |
| `surfaceBright` | `#393939` | |
| `surfaceContainerLowest` | `#0E0E0F` | |
| `surfaceContainerLow` | `#1F1F1F` | **unchanged** |
| `surfaceContainer` ↺ | `#1E1F20` | Floating card fill in dark (`LocalFloatingSurface`) — was `#1F2020` |
| `surfaceContainerHigh` ↺ | `#282A2C` | Chip / language-pill / swap fill — was `#2A2A2A` |
| `surfaceContainerHighest` | `#343535` | **unchanged** |
| `surfaceTint` | `#A8C7FA` | = `primary` (tonal elevation tint) |
| `inverseSurface` | `#E3E3E3` | |
| `inverseOnSurface` | `#303030` | |
| `inversePrimary` | `#0B57D0` | = light `primary` (primary40) |
| `outline` | `#8E918F` | |
| `outlineVariant` | `#444746` | |
| `error` | `#F2B8B5` | 1P error set |
| `onError` | `#601410` | |
| `errorContainer` | `#8C1D18` | Error card container |
| `onErrorContainer` | `#F9DEDC` | |
| `scrim` | `#000000` | |

### 1.3 The 12 `*Fixed` roles — identical in BOTH schemes

A "fixed" role is one that must survive a light↔dark switch unchanged, so the
same value is passed to `lightColorScheme()` and `darkColorScheme()`. Ladder
(verified against Compose `ColorLightTokens`/`ColorDarkTokens`):
Fixed = tone90 · FixedDim = tone80 · onFixed = tone10 · onFixedVariant = tone30.

| Role | HEX | Role | HEX |
|---|---|---|---|
| `primaryFixed` | `#D3E3FD` | `onPrimaryFixed` | `#041E49` |
| `primaryFixedDim` | `#A8C7FA` | `onPrimaryFixedVariant` | `#0842A0` |
| `secondaryFixed` | `#C2E7FF` | `onSecondaryFixed` | `#001D35` |
| `secondaryFixedDim` | `#7FCFFF` | `onSecondaryFixedVariant` | `#004A77` |
| `tertiaryFixed` | `#C4EED0` | `onTertiaryFixed` | `#072711` |
| `tertiaryFixedDim` | `#6DD58C` | `onTertiaryFixedVariant` | `#0F5223` |

---

## 2. No gradient — flat surfaces (issue #15)

**The app has no gradient token.** `AmbientGradient` / `LocalAmbientGradient`
are deleted. Google Translate paints flat pages, and side by side the wash only
muddied ours.

| Rule | |
|---|---|
| Page background | solid `surface`. Nothing paints a gradient — not a page, not a card, not a "signature element". |
| Depth | **lightness steps first.** A panel that must lift off the page uses a lighter surface role — light `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer` (#1E1F20). |
| Borders / shadows | avoid borders on panels. **Amended 2026-07-26 (issue #42):** the approved Home cards carry `Elevation.level1` (1dp) *on top of* the lightness step — #FFFFFF on a #F8FAFD page is a 2-point step and did not read on device. A 1dp shadow is now the sanctioned card treatment; anything above `level1` still needs a reason. |
| Dividers | `outlineVariant` hairlines, where a step alone would not read. |

```kotlin
// :core:designsystem — provided by TranzlateTheme, resolved from the ACTIVE scheme
val panel = LocalFloatingSurface.current   // light #FFFFFF · dark #1E1F20
```

---

## 3. Type scale

**Font family (amended 2026-07-26, issue #42 / PR #43): `Roboto Flex`, bundled.**
`core/designsystem/src/main/res/font/roboto_flex.ttf` — the approved design specifies Roboto Flex, and it differs from the device's Roboto in proportion, so it ships with the app rather than being assumed.
**How it is declared (this detail is load-bearing):** the family is built from **one `Font` per weight with an explicit `FontVariation` axis instance**, not a single variable-font entry:

```kotlin
private fun robotoFlex(weight: FontWeight) =
    Font(R.font.roboto_flex, weight = weight,
         variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val RobotoFlex = FontFamily(robotoFlex(FontWeight.Normal), robotoFlex(FontWeight.Medium))
```

A single-entry family bakes to weight 400, and every `FontWeight.Medium` role below would silently render Regular — Compose only synthesises weight from `W600` upward, so `W500` gets no synthesis and the matcher has nothing else to choose. Adding a weight to the scale means adding its axis instance here.
`variationSettings` needs **API 26+**; on 24–25 the font falls back to its default instance (the same regular face we would otherwise have had). Non-Latin scripts still fall through to the platform's Noto.
*(Supersedes "system default sans, no bundled font in the baseline". The brand-display-face slot below is unused — Roboto Flex covers every role.)*

| M3 role | Size (sp) | Line height (sp) | Weight | Letter spacing (sp) | Usage |
|---|---|---|---|---|---|
| `displayLarge` | 57 | 64 | 400 | −0.25 | Splash / onboarding hero |
| `displayMedium` | 45 | 52 | 400 | 0 | Large empty-state |
| `displaySmall` | 36 | 44 | 400 | 0 | Section hero |
| `headlineLarge` | 32 | 40 | 400 | 0 | Screen title (large) |
| `headlineMedium` | 28 | 36 | 400 | 0 | Home headline |
| `headlineSmall` | 24 | 32 | 400 | 0 | Translation result text |
| `titleLarge` | 22 | 28 | 500 | 0 | Top app bar title |
| `titleMedium` | 16 | 24 | 500 | +0.15 | Card / list header |
| `titleSmall` | 14 | 20 | 500 | +0.10 | Dense list header |
| `bodyLarge` | 16 | 24 | 400 | +0.5 | Primary body / input text |
| `bodyMedium` | 14 | 20 | 400 | +0.25 | Secondary body |
| `bodySmall` | 12 | 16 | 400 | +0.4 | Captions, timestamps |
| `labelLarge` | 14 | 20 | 500 | +0.1 | Button label |
| `labelMedium` | 12 | 16 | 500 | +0.5 | Chip / nav label |
| `labelSmall` | 11 | 16 | 500 | +0.5 | Overline, badges |

> All text sizes in **sp** (respect user font scaling). Never hardcode text `18.sp` in Composables — map to a role (e.g. `FeaturesCard` → `titleMedium`).

---

## 4. Spacing scale

Named `dp` tokens exposed via `LocalSpacing` (CompositionLocal). No raw `dp` padding in layout code.

| Token | dp | Typical use |
|---|---|---|
| `none` | 0 | Reset |
| `xxs2` | 2 | Icon–label gap, hairline insets |
| `xs4` | 4 | Chip internal, dense rows |
| `sm8` | 8 | Between related items |
| `md16` | 16 | **Default** screen/content padding |
| `lg24` | 24 | Section separation |
| `xl32` | 32 | Large block separation |
| `xxl48` | 48 | Hero / empty-state vertical rhythm |

```kotlin
val spacing = LocalSpacing.current
Modifier.padding(horizontal = spacing.md16, vertical = spacing.sm8)
```

---

## 5. Shape / corner tokens

Mapped onto `MaterialTheme.shapes` plus named extras.

| Token | Radius (dp) | Applied to |
|---|---|---|
| `none` | 0 | Full-bleed images |
| `extraSmall` (`xs4`) | 4 | Snackbar, small chips |
| `small` (`sm8`) | 8 | Text fields, small cards |
| `medium` (`md12`) | 12 | **Default** cards, list items |
| `large` (`lg16`) | 16 | Bottom sheets (top corners), dialogs, hero cards |
| `extraLarge` (`xl28`) | 28 | Large modal sheets, FAB-adjacent surfaces |
| `full` | 50% | Pills, AssistChip, avatar, `NavigationBar` indicator |

---

## 6. Elevation / tonal levels

Material 3 uses **tonal elevation** (surface color shift via `surfaceTint`) in addition to shadow. Use tonal for containers; reserve shadow for floating/scrolled states.

| Level | Shadow dp | Tonal surface role | Used by |
|---|---|---|---|
| Level 0 | 0 | `surface` | Page background, flat cards |
| Level 1 | 1 | `surfaceContainerLow` | Resting Card, `NavigationBar` |
| Level 2 | 3 | `surfaceContainer` | Elevated Card, top app bar (scrolled) |
| Level 3 | 6 | `surfaceContainerHigh` | Menus, `AssistChip` (elevated), FAB |
| Level 4 | 8 | `surfaceContainerHigh` | Navigation drawer |
| Level 5 | 12 | `surfaceContainerHighest` | Modal bottom sheet, dialog |

> In dark theme prefer tonal steps over heavy shadows (shadows read poorly on dark surfaces).

---

## 7. Iconography

- **Icon set:** Material Symbols (Rounded style, to match the soft brand geometry).
- **How they ship (settled 2026-07-26, issue #42 / PR #43):** as **vector drawables checked into `core/designsystem/src/main/res/drawable/ic_*.xml`**, drawn with `painterResource(DsR.drawable.ic_*)`. **Not** `material-icons-extended` (10,820 classes in the dex for the ~25 we use — FIX_QUEUE batch 🔵 E) and **not** the ~14.7 MB variable icon font. Adding a glyph = exporting one more `ic_*.xml` from Material Symbols Rounded at the axes below.
- **Shipped set (17):** `ic_arrow_back` · `ic_arrow_drop_down` · `ic_auto_awesome` · `ic_chevron_right` · `ic_close` · `ic_cloud_done` · `ic_download_for_offline` · `ic_format_quote` · `ic_forum` · `ic_menu_book` · `ic_mic` · `ic_photo_camera` · `ic_record_voice_over` · `ic_settings` · `ic_swap_horiz` · `ic_translate` · `ic_workspace_premium`.
- **Migration state:** Home is fully on the drawable set. `:core:ui` (`ResultBlock`) and `:app` (`ComingSoonScreen`, plus the retired drawer files) still import `material-icons-extended` — that dependency comes out with batch 🔵 E, and no NEW code may add an `Icons.*` import.
- **Default grid:** `24.dp` optical size; touch target ≥ `48.dp`.
- **Axes:** `weight 400`, `grade 0`, `opticalSize 24`, `fill 0` by default.
- **Filled (`fill 1`) vs outlined (`fill 0`):**
  - Outlined = inactive / unselected / default affordance.
  - Filled = **active/selected** state only — selected `NavigationBar` item, active favourite star, playing TTS, current translation model.
- **Color:** icons inherit `onSurfaceVariant` for inactive, `primary` (or `onSecondaryContainer` inside a selected pill) for active. Solid roles only — no icon carries a gradient (there is none, §2).
- **Sizes:** `20.dp` dense inline (chips), `24.dp` standard, `40.dp` avatars/feature glyphs.

---

## 8. Motion tokens

Durations and easing follow the M3 motion system. Expose as a `Motion` object.

**Durations (ms):**

| Token | ms | Use |
|---|---|---|
| `short1` | 50 | Icon state flip, ripple start |
| `short2` | 100 | Small selection, checkbox |
| `short3` | 150 | Chip/toggle state |
| `short4` | 200 | Standard state change, nav item |
| `medium1` | 250 | Card expand, small enter |
| `medium2` | 300 | **Default** screen content transition |
| `medium3` | 350 | Bottom sheet enter |
| `medium4` | 400 | Large container transform |
| `long1` | 450 | Full-screen shared-element/hero |
| `long2` | 500 | Splash → home hand-off |

**Easing (cubic-bezier):**

| Token | Curve | Use |
|---|---|---|
| `standard` | `(0.2, 0.0, 0.0, 1.0)` | Most on-screen state changes |
| `standardDecelerate` | `(0.0, 0.0, 0.0, 1.0)` | **Enter** (elements arriving) |
| `standardAccelerate` | `(0.3, 0.0, 1.0, 1.0)` | **Exit** (elements leaving) |
| `emphasized` | `(0.2, 0.0, 0.0, 1.0)` w/ emphasized spec | Hero / expressive transitions |
| `emphasizedDecelerate` | `(0.05, 0.7, 0.1, 1.0)` | Sheet/dialog enter |
| `emphasizedAccelerate` | `(0.3, 0.0, 0.8, 0.15)` | Sheet/dialog dismiss |

Guidance: **enter** with decelerate (`medium2`), **exit** with accelerate (`short4`), **state** toggles at `short3`–`short4` with `standard`.

---

## 9. Component style notes

> **Stock-M3-first (issue #15, BINDING).** If Material 3 ships the component, we
> use it — `IconButton`, `FilledIconButton`, `FilledTonalIconButton`,
> `AssistChip`, `SuggestionChip`, `CenterAlignedTopAppBar`, `Card`, `TextButton`,
> `ListItem`, `Scaffold`. A wrapper in `:core:ui` is justified only when it
> carries a RULE or a state a call site would otherwise repeat — today:
> `PrimaryActionButton` (the dark-theme fill rule), `QuickActionButton` (circle +
> caption + the a11y merge), `ErrorCard` (assertive live region + testTags),
> `ComposerCard` (the §2.2 layout), `ResultBlock` (the §2.4 layout) and
> `ShimmerResult` (M3 has no skeleton). A wrapper that only re-colours a stock
> component is NOT justified — set the colours at the call site.

**Button (filled / primary CTA)**
- Container `primary`, label `onPrimary`, `labelLarge`.
- Shape `full` (pill), height 40dp min, min touch 48dp; content padding `horizontal = lg24`, `vertical = sm8`.
- Tonal variant: container `secondaryContainer` / label `onSecondaryContainer`. Text/outlined variants: label `primary`, outline `outline`.

**AssistChip**
- Shape `sm8` (or `full` for filter pills), height 32dp, elevation Level 0 (flat) or Level 3 (elevated).
- Container `surfaceContainerLow`/transparent with `outlineVariant` border; label `onSurface` (`labelLarge`), leading icon 18dp `onSurfaceVariant`.
- Selected filter chip → container `secondaryContainer`, label `onSecondaryContainer`, filled check icon.

**Card**
- Default: `surfaceContainer`, shape `md12`, Level 1. Padding `md16`.
- Elevated (results, feature cards): Level 2. Outlined variant: `surface` + `outlineVariant` 1dp border, elevation Level 0.

**OutlinedTextField** (translation input)
- Shape `sm8`; unfocused border `outline`, focused border `primary` 2dp; label/placeholder `onSurfaceVariant`; input text `bodyLarge`/`onSurface`; cursor `primary`.
- Error: border/label `error`, supporting text `error`. Container transparent (or `surfaceContainerLowest`).

**Navigation (DECISIONS D-5 rev.3, issue #42 / PR #43, 2026-07-26 — rewritten)**
- **There is no navigation component.** No bottom `NavigationBar`, no `NavigationSuiteScaffold`, no `NavigationRail`, no `ModalNavigationDrawer`, no FAB. The shell is a bare `NavDisplay`; **Home's card stack is the navigation** (UI_SPEC §2.1). A screen that needs a new destination adds a card or a top-bar action — it does not add a bar.
- Adaptive: the Adaptive table below still governs *dimensions* (C-13), but its **navigation column is on hold** — rev.3 was designed phone-first and renders the same stack at every width. Wide-window IA needs its own design round before any rail/drawer claim returns.
- ~~Compact: bottom `NavigationBar` (Home / Chat / Camera) via `NavigationSuiteScaffold`. The ☰ drawer holds secondary destinations only, push+scale motion per UI_SPEC §2.3. Medium/Expanded: `NavigationSuiteScaffold` adaptivity per C-13 (rail → permanent drawer). Container `surfaceContainer`, Level 2; selected item filled icon `onSecondaryContainer` on a `secondaryContainer` pill indicator, unselected `onSurfaceVariant`, label `labelMedium`.~~ *(D-5 rev.2 styling, kept for the paper trail — reinstate only with a new decision record.)*

**BottomSheet (modal)**
- Container `surface`/`surfaceContainerLow`, top corners `xl28` (or `lg16` for compact sheets), Level 5, `scrim` at 32% behind.
- Drag handle `onSurfaceVariant` at 40% alpha, centered, `sm8` top padding. Enter `medium3` + `emphasizedDecelerate`; dismiss `short4` + `emphasizedAccelerate`.

---

## 10. How to consume (Compose)

**1. Color schemes** — define `lightColorScheme(...)` / `darkColorScheme(...)` in `ui/theme/Color.kt` from §1, then in `TranzlateTheme`:

```kotlin
val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicEnabled
val colorScheme = when {
    useDynamic && darkTheme -> dynamicDarkColorScheme(context)
    useDynamic              -> dynamicLightColorScheme(context)
    darkTheme               -> TranzlateDarkColors   // static fallback (API 24–30)
    else                    -> TranzlateLightColors
}
```

**2. Non-M3 tokens** (spacing, panel step, motion, dimensions) — expose via CompositionLocals, provided inside the theme:

```kotlin
CompositionLocalProvider(
    LocalSpacing provides Spacing(),
    // resolved from the ACTIVE scheme, so it follows dynamic color too
    LocalFloatingSurface provides
        if (darkTheme) colorScheme.surfaceContainer else colorScheme.surfaceContainerLowest,
) {
    MaterialTheme(colorScheme = colorScheme, typography = TranzlateTypography, shapes = TranzlateShapes, content = content)
}
```

**3. Access at call sites:**
- Color → `MaterialTheme.colorScheme.primary`
- Type → `MaterialTheme.typography.bodyLarge`
- Shape → `MaterialTheme.shapes.medium`
- Spacing → `LocalSpacing.current.md16`
- Panel step → `LocalFloatingSurface.current` (§2 — never branch on `isSystemInDarkTheme()` at a call site to pick a panel colour)
- Motion → `Motion.medium2`, `Motion.standardDecelerate`

**Rule (promoted to a binding convention 2026-07-26 — DECISIONS C-14):** no raw hex, `sp` text sizes, or magic `dp` in Composables — **every measurement resolves through a token**: spacing → `LocalSpacing`, fixed sizes → `Dimensions`, corners → `MaterialTheme.shapes` / `TranzlateShapeFull`, elevation → `Elevation.level*`, text → `MaterialTheme.typography`. **When a design's measured value falls off our scale, the token wins** — snap to the nearest token, and if the gap is real, amend §4/§5/§6 once so every screen inherits it. A private `val Foo = 20.dp` ladder inside a feature file is the anti-pattern this rule exists to stop.
⚠ **Tracked exception:** `feature/text/.../HomeScreen.kt` still carries such a block (`ScreenMargin`, `SectionGap`, `CardRadius`, `InputCardRadius`, `PillHeight`, `CircleIconSize`, `ActionSize`, `CardShadow`) plus inline `dp` and one `15.sp`, documented in-file as "the design's own numbers". It is the only sanctioned exception, and it is a migration item — not a precedent.
Dynamic color must always fall back to this static palette so API 24–30 renders the brand identity, not gray defaults.

---

## Adaptive & dimensions (C-13 — window size classes)

> Consumed via `rememberWindowInfo()`; never hardcode dp breakpoints in layout code.

> ⚠ **Navigation column ON HOLD (2026-07-26, D-5 rev.3).** The approved design has no navigation component at any width; the same card stack renders everywhere. The **Content** column and the dimension values below still stand. Struck entries are the rev.2 target, kept for when a wide-window design lands.

| Class | Width | Navigation | Content |
|-------|-------|-----------|---------|
| Compact | < 600dp | **none** — Home's card stack *is* the navigation | single pane, content max-width 480dp centered |
| Medium | 600–840dp | ~~`NavigationRail`~~ — not designed yet | `ListDetailPaneScaffold` |
| Expanded | > 840dp | ~~permanent `NavigationDrawer`~~ — not designed yet | `ListDetailPaneScaffold`, wider |

**ListDetailPaneScaffold panes:** list/input pane **min 360dp**; detail/result pane **min 400dp**; default split **40 / 60** (list : detail). Below the combined min → collapse to single-pane with navigation.

**Fixed dimension tokens** (`Dimensions.kt`): `touchTargetMin=48dp` · `iconSm=20dp` `iconMd=24dp` `iconLg=32dp` · `borderThin=1dp` `borderThick=2dp` · `contentMaxWidth=480dp` · `sheetPeek=56dp` · `fabSize=56dp`.

## Alpha tokens (state opacity)

| Token | Value | Use |
|-------|-------|-----|
| `disabled` | 0.38f | disabled controls (e.g. Swap on Auto source) |
| `dragHandle` | 0.40f | bottom-sheet grabber |
| `scrim` | 0.32f | modal scrim |
| `hover` | 0.08f · `focus` 0.12f · `pressed` 0.12f | interaction states (M3 defaults) |

## Badge / counter style
Metered counter = M3 `Badge` (or supporting `labelSmall` on `onSurfaceVariant`); never alpha-only dimming to signal "over limit" — use lock icon + readable-contrast text (a11y C-5/§0 rule).
