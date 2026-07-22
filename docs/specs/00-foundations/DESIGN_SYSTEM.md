# Tranzlate — Design System Foundation

> Clean-room design foundation for the Tranzlate Android translator (Jetpack Compose + Material 3).
> This document **replaces** the Material template purple stubs (`Purple80`/`Pink80`) currently in `ui/theme/Color.kt`.
> **2026-07-22 (issue #10): palette = P8 "Tranzlate Teal (cool-mono)"** — owner-final. Teal primary kept from the brand set; blue support + neutrals + error from Google 1P (P1); **all warm accents (coral / gold) permanently removed.** Provenance + WCAG math: [`docs/design/PALETTES.md`](../../design/PALETTES.md) §P8.
> **Component-level contract: [`docs/design/UI_SPEC.md`](../../design/UI_SPEC.md).**
>
> **Scope:** static (baseline) palette + full M3 role tokens, type, spacing, shape, elevation, iconography, motion, and component notes.
> **Dynamic color:** on API 31+ the system may supply a Material You (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) palette; **this static palette is the guaranteed fallback** for API 24–30 and for users who disable dynamic color.

---

## 0. Contrast validation summary (P8 — precomputed in PALETTES.md)

Every pair below was computed with the WCAG 2.x relative-luminance formula
`(L1 + 0.05) / (L2 + 0.05)` during the P8 verification (PALETTES.md §P8 — **13/13 pass**).
Values are copied verbatim from that record.

| Pair | Theme | Ratio | Verdict |
|---|---|---|---|
| `onPrimary` `#FFFFFF` on `primary` `#1C7A97` | light | 4.91:1 | ✓ |
| `onPrimary` `#00363F` on `primary` `#3FB6D4` | dark | 5.54:1 | ✓ |
| `onPrimaryContainer` `#002F3C` on `primaryContainer` `#B8E7F5` | light | 10.73:1 | ✓ |
| `onPrimaryContainer` `#B8E7F5` on `primaryContainer` `#10586B` | dark | 6.00:1 | ✓ |
| `onSecondary` `#FFFFFF` on `secondary` `#00639B` | light | 6.45:1 | ✓ |
| `onSecondary` `#003355` on `secondary` `#7FCFFF` | dark | 7.65:1 | ✓ |
| `onSecondaryContainer` `#004A77` on `secondaryContainer` `#C2E7FF` | light | 7.20:1 | ✓ |
| `onTertiary` `#FFFFFF` on `tertiary` `#10586B` | light | 7.98:1 | ✓ |
| `onTertiary` `#00363F` on `tertiary` `#8FD3E3` | dark | 7.87:1 | ✓ |
| `onTertiaryContainer` `#002F3C` on `tertiaryContainer` `#CDE9F2` | light | 11.23:1 | ✓ |
| `onTertiaryContainer` `#CDE9F2` on `tertiaryContainer` `#0B4A5A` | dark | 7.72:1 | ✓ |
| `primary` `#1C7A97` on `surface` `#FAF9F8` (icon/label accent use) | light | 4.67:1 | ✓ |
| `primary` `#3FB6D4` on `surface` `#131314` (icon/label accent use) | dark | 7.83:1 | ✓ |

> Surfaces, neutrals, outline, inverse neutrals and the error set are **P1 values verbatim** and inherit P1's independently verified ratios (PALETTES.md §P1 — 14/14 pass; e.g. `onSurface`/`surface` light 15.67:1 · dark 14.47:1).

> **Design rule for `primary`:** the brand teal `#1C7A97` (light) / `#3FB6D4` (dark) is **kept** from the original brand set (owner 2026-07-22: the teal stays; only the warm coral/gold companions were the problem). All support/neutral/error tones come from P1 (Google 1P, research-verified). **No coral, no gold, no violet, no warm hue anywhere** (P8 rule).

---

## 1. Color role tokens (Material 3) — P8 "Tranzlate Teal (cool-mono)"

> Accents (primary/secondary/tertiary families) from the P8 table; surfaces, neutrals, outline, inverse neutrals and the error set are **P1 (Google 1P) values verbatim** — see PALETTES.md §P8/§P1.

### 1.1 Light scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#1C7A97` | Brand teal (kept), AA-checked |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#B8E7F5` | Light teal fill (chips, tonal buttons) |
| `onPrimaryContainer` | `#002F3C` | |
| `secondary` | `#00639B` | Support blue (P1 secondary) |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#C2E7FF` | Light blue fill |
| `onSecondaryContainer` | `#004A77` | |
| `tertiary` | `#10586B` | Deep teal, in-family (P8-derived) |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#CDE9F2` | |
| `onTertiaryContainer` | `#002F3C` | |
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
| `surfaceContainerHigh` | `#E9E8E8` | |
| `surfaceContainerHighest` | `#E3E3E3` | |
| `surfaceTint` | `#1C7A97` | = `primary` (tonal elevation tint) |
| `inverseSurface` | `#303030` | |
| `inverseOnSurface` | `#F2F2F2` | |
| `inversePrimary` | `#3FB6D4` | = dark `primary` (P8 teal family) |
| `outline` | `#747775` | Borders, dividers with emphasis |
| `outlineVariant` | `#C4C7C5` | Low-emphasis dividers |
| `error` | `#B3261E` | P1 error set |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#F9DEDC` | |
| `onErrorContainer` | `#8C1D18` | |
| `scrim` | `#000000` | |

### 1.2 Dark scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#3FB6D4` | Bright brand teal (kept) |
| `onPrimary` | `#00363F` | |
| `primaryContainer` | `#10586B` | |
| `onPrimaryContainer` | `#B8E7F5` | |
| `secondary` | `#7FCFFF` | Support blue (P1 secondary) |
| `onSecondary` | `#003355` | |
| `secondaryContainer` | `#004A77` | |
| `onSecondaryContainer` | `#C2E7FF` | |
| `tertiary` | `#8FD3E3` | Soft teal, in-family (P8-derived) |
| `onTertiary` | `#00363F` | |
| `tertiaryContainer` | `#0B4A5A` | |
| `onTertiaryContainer` | `#CDE9F2` | |
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
| `surfaceContainer` | `#1F2020` | Default card/sheet base |
| `surfaceContainerHigh` | `#2A2A2A` | |
| `surfaceContainerHighest` | `#343535` | |
| `surfaceTint` | `#3FB6D4` | = `primary` (tonal elevation tint) |
| `inverseSurface` | `#E3E3E3` | |
| `inverseOnSurface` | `#303030` | |
| `inversePrimary` | `#1C7A97` | = light `primary` (P8 teal family) |
| `outline` | `#8E918F` | |
| `outlineVariant` | `#444746` | |
| `error` | `#F2B8B5` | P1 error set |
| `onError` | `#601410` | |
| `errorContainer` | `#8C1D18` | |
| `onErrorContainer` | `#F9DEDC` | |
| `scrim` | `#000000` | |

---

## 2. Ambient wash (gradient token) — per UI_SPEC.md §1

The old teal→coral brand gradient is **retired with the warm accents** (2026-07-22, issue #10). The gradient token is now the **ambient wash**: a soft, low-opacity teal→blue tint laid over `surface`, strengthening toward the bottom of the page — light theme reads near-white → pale teal, dark theme `#131314` → a deep teal glow. No hard edges.

| Property | Light | Dark |
|---|---|---|
| `washStart` (teal = `primary`) | `#1C7A97` | `#3FB6D4` |
| `washEnd` (blue = `secondary` family) | `#00639B` | `#00639B` |
| Application | low-opacity radial/vertical gradient **over `surface`** | same family, deeper |

**Usage rules (gradient discipline):**
- **Page background + at most ONE signature element per screen.** Nothing else carries the gradient.
- **Never behind body text** — text always sits on a solid surface step.
- **Never on dividers, borders, icons, or text** (dividers use `outlineVariant`; icons use solid roles).
- Floating surfaces (composer, tiles, drawer sheet, cards) sit as a **lighter step over the wash** — light `surfaceContainerLowest`, dark `surfaceContainer`/`High`; separation by lightness, not outlines.

```kotlin
// :core:designsystem — provided by TranzlateTheme per theme
val ambient = LocalAmbientGradient.current   // start = teal, end = blue
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
- **Color:** icons inherit `onSurfaceVariant` for inactive, `primary` (or `onSecondaryContainer` inside a selected pill) for active. Decorative brand marks may use the gradient; functional icons never do.
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

**NavigationBar / NavigationSuiteScaffold**
- Use `NavigationSuiteScaffold` (adaptive: bar → rail → drawer per window size class). Never a bare `BottomNavigation`.
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

**2. Non-M3 tokens** (spacing, gradient, motion, dimensions) — expose via CompositionLocals, provided inside the theme:

```kotlin
CompositionLocalProvider(
    LocalSpacing provides Spacing(),
    LocalAmbientGradient provides if (darkTheme) DarkAmbientGradient else LightAmbientGradient,
) {
    MaterialTheme(colorScheme = colorScheme, typography = TranzlateTypography, shapes = TranzlateShapes, content = content)
}
```

**3. Access at call sites:**
- Color → `MaterialTheme.colorScheme.primary`
- Type → `MaterialTheme.typography.bodyLarge`
- Shape → `MaterialTheme.shapes.medium`
- Spacing → `LocalSpacing.current.md16`
- Ambient wash → `LocalAmbientGradient.current` (§2 usage rules)
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
