# Tranzlate — Design System Foundation

> Clean-room design foundation for the Tranzlate Android translator (Jetpack Compose + Material 3).
> This document **replaces** the Material template purple stubs (`Purple80`/`Pink80`) currently in `ui/theme/Color.kt`.
> **2026-07-22 (issue #15): palette = P9 "GT Blue (Google 1P)"** — owner-final, decided by running Google Translate v10.27 beside our build. **All 48 `ColorScheme` roles are set explicitly** (36 base + the 12 `*Fixed`); nothing falls back to a Material default. P8 teal is retired. Provenance + WCAG math: [`docs/design/PALETTES.md`](../../design/PALETTES.md) §P9.
> **Component-level contract: [`docs/design/UI_SPEC.md`](../../design/UI_SPEC.md).**
>
> **Scope:** static (baseline) palette + full M3 role tokens, type, spacing, shape, elevation, iconography, motion, and component notes.
> **Dynamic color:** on API 31+ the system may supply a Material You (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) palette; **this static palette is the guaranteed fallback** for API 24–30 and for users who disable dynamic color.

---

## 0. Contrast validation summary (P9 — recomputed 2026-07-22, issue #15)

Every pair below was computed with the WCAG 2.x relative-luminance formula
`(L1 + 0.05) / (L2 + 0.05)` against the shipped `Color.kt` values.
**All pass AA; the lowest text pair is 5.13.** Full list incl. the `*Fixed`
ladder: PALETTES.md §P9.

| Pair | Light | Dark |
|---|---|---|
| `onPrimary` / `primary` | 6.39 ✓ | 7.50 ✓ |
| `onSurface` / `surface` | 15.67 ✓ | 14.47 ✓ |
| `onSurfaceVariant` / `surfaceVariant` | 7.28 ✓ | 5.51 ✓ |
| `onPrimaryContainer` / `primaryContainer` | 7.04 ✓ | 7.04 ✓ |
| `onSecondaryContainer` / `secondaryContainer` | 7.20 ✓ | 7.20 ✓ |
| `onTertiaryContainer` / `tertiaryContainer` | 7.32 ✓ | 7.32 ✓ |
| `onErrorContainer` / `errorContainer` | 7.17 ✓ | 7.17 ✓ |
| `primary` / `surface` (icon/label accent use) | 6.07 ✓ | 10.80 ✓ |
| `onSurface` / `surfaceContainerLowest` (panel text) | 16.48 ✓ | 15.03 ✓ |
| `onSurfaceVariant` / `surfaceContainerHigh` (chip label) | 7.68 ✓ | 8.42 ✓ |

> **Design rule for `primary`:** `#0B57D0` (light) / `#A8C7FA` (dark) — Google's
> own reference tones, so our accent equals Google Translate's. **The dark theme
> never fills a large element with `primary`** (a near-white disc on `#131314`
> glares): the primary action fills with `primaryContainer` there — the rule
> lives in `PrimaryActionButton` (`:core:ui`), stated once.

---

## 1. Color role tokens (Material 3) — P9 "GT Blue (Google 1P)"

> The complete 48-role set, hex-for-hex with PALETTES.md §P9. §1.1/§1.2 list the 36 base roles per theme; §1.3 the 12 `*Fixed` roles, which are **the same in both themes**.

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
| `background` | `#FAF9F8` | = `surface` (P1) |
| `onBackground` | `#1F1F1F` | |
| `surface` | `#FAF9F8` | |
| `onSurface` | `#1F1F1F` | |
| `surfaceVariant` | `#E1E3E1` | |
| `onSurfaceVariant` | `#444746` | Secondary/label text on tonal areas |
| `surfaceDim` | `#DADADA` | |
| `surfaceBright` | `#FAF9F8` | |
| `surfaceContainerLowest` | `#FFFFFF` | |
| `surfaceContainerLow` | `#F4F3F2` | |
| `surfaceContainer` | `#EFEDED` | Default card/sheet base |
| `surfaceContainerHigh` | `#E9E8E8` | Chip / language-pill fill |
| `surfaceContainerHighest` | `#E3E3E3` | |
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
| `surfaceContainerLow` | `#1F1F1F` | |
| `surfaceContainer` | `#1F2020` | Default card/panel base (composer in dark) |
| `surfaceContainerHigh` | `#2A2A2A` | Chip / language-pill fill |
| `surfaceContainerHighest` | `#343535` | |
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
| Depth | **lightness steps only.** A panel that must lift off the page uses a lighter surface role — light `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer` (#1F2020). |
| Borders / shadows | avoid both on panels; a border or a drop shadow is a fallback for when no step is available, not the default. |
| Dividers | `outlineVariant` hairlines, where a step alone would not read. |

```kotlin
// :core:designsystem — provided by TranzlateTheme, resolved from the ACTIVE scheme
val panel = LocalFloatingSurface.current   // light #FFFFFF · dark #1F2020
```

---

## 3. Type scale

**Font family:** system default sans (`FontFamily.Default` → Roboto on most devices). No bundled font in the baseline.
**Brand display face slot:** if a brand face is later added, apply it **only** to `displayLarge`/`displayMedium`/`headlineLarge` (marketing/hero) via a `displayFontFamily` override; keep body/label on the system font for legibility and locale coverage (en, fil, pt-rBR).

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

**Navigation (amended 2026-07-22 — DECISIONS D-5)**
- Compact: HUB model — no bottom nav bar; peer modes live on the hub (composer + quick-action tiles), ☰ drawer = secondary destinations (push+scale motion per UI_SPEC §2.3).
- Medium/Expanded: `NavigationSuiteScaffold` adaptivity stays per C-13 (rail → permanent drawer). Never a bare `BottomNavigation`.
- Container `surfaceContainer`, Level 2. Selected item: filled icon `onSecondaryContainer` on a `secondaryContainer` pill indicator (shape `full`); unselected icon+label `onSurfaceVariant`. Label `labelMedium`.

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

**Rule:** no raw hex, `dp` text sizes, or magic paddings in Composables — everything resolves through a token. Dynamic color must always fall back to this static palette so API 24–30 renders the brand identity, not gray defaults.

---

## Adaptive & dimensions (C-13 — window size classes)

> Consumed via `rememberWindowInfo()`; never hardcode dp breakpoints in layout code.

| Class | Width | Navigation | Content |
|-------|-------|-----------|---------|
| Compact | < 600dp | bottom `NavigationBar` | single pane, content max-width 480dp centered |
| Medium | 600–840dp | `NavigationRail` | `ListDetailPaneScaffold` |
| Expanded | > 840dp | permanent `NavigationDrawer` | `ListDetailPaneScaffold`, wider |

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
