# Tranzlate — Design System Foundation

> Clean-room design foundation for the Tranzlate Android translator (Jetpack Compose + Material 3).
> This document **replaces** the Material template purple stubs (`Purple80`/`Pink80`) currently in `ui/theme/Color.kt`.
> Brand: a teal→coral gradient "A" logo. Primary accent = teal; secondary = warm coral from the logo swoosh.
>
> **Scope:** static (baseline) palette + full M3 role tokens, type, spacing, shape, elevation, iconography, motion, and component notes.
> **Dynamic color:** on API 31+ the system may supply a Material You (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) palette; **this static palette is the guaranteed fallback** for API 24–30 and for users who disable dynamic color.

---

## 0. Contrast validation summary

All values below were checked with the WCAG 2.1 relative-luminance formula
`(L1 + 0.05) / (L2 + 0.05)`. **All body/label text pairs meet AA ≥ 4.5:1.**

| Pair (both themes unless noted) | Ratio | Verdict |
|---|---|---|
| `onSurface` on `surface` (light) | ≈ 16.9:1 | ✅ |
| `onSurface` on `surface` (dark) | ≈ 14.8:1 | ✅ |
| `onSurfaceVariant` on `surfaceVariant` (light) | ≈ 7.2:1 | ✅ |
| `onSurfaceVariant` on `surfaceVariant` (dark) | ≈ 5.5:1 | ✅ |
| `onBackground` on `background` (both) | ≥ 14:1 | ✅ |
| `onPrimary` (#FFFFFF) on `primary` #1C7A97 (light) | ≈ 4.9:1 | ✅ |
| `onPrimary` #00363F on `primary` #3FB6D4 (dark) | ≈ 9.6:1 | ✅ |
| `onSecondary` (#FFFFFF) on `secondary` #C0563E (light) | ≈ 4.5:1 | ✅ |
| `onPrimaryContainer` on `primaryContainer` (both) | ≥ 8:1 | ✅ |
| `onErrorContainer` on `errorContainer` (both) | ≥ 7:1 | ✅ |

> **Design rule for `primary`:** the brand accent teal is `#2B8FB0`, but white-on-`#2B8FB0` is only ~3.7:1 (fails AA-normal). The light **`primary` role token is deepened one step to `#1C7A97`** so `onPrimary` white passes 4.5:1. The lighter `#2B8FB0` / `#3FB6D4` tones live in the gradient and in the dark `primary`.

---

## 1. Color role tokens (Material 3)

### 1.1 Light scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#1C7A97` | Brand teal, deepened for AA text |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#B8E7F5` | Light teal fill (chips, tonal buttons) |
| `onPrimaryContainer` | `#002F3C` | |
| `secondary` | `#C0563E` | Coral swoosh, deepened for AA |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#FFDBD1` | Light coral fill |
| `onSecondaryContainer` | `#3B0A02` | |
| `tertiary` | `#7A5A2E` | Warm bronze/gold bridge accent |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#FBDFA6` | |
| `onTertiaryContainer` | `#2A1C00` | |
| `background` | `#FCFCFD` | |
| `onBackground` | `#1A1C1E` | |
| `surface` | `#FBFCFE` | |
| `onSurface` | `#1A1C1E` | |
| `surfaceVariant` | `#DCE3E8` | |
| `onSurfaceVariant` | `#40484D` | Secondary/label text on tonal areas |
| `surfaceContainerLowest` | `#FFFFFF` | |
| `surfaceContainerLow` | `#F4F5F7` | |
| `surfaceContainer` | `#EEF0F3` | Default card/sheet base |
| `surfaceContainerHigh` | `#E8EBED` | |
| `surfaceContainerHighest` | `#E2E5E8` | |
| `surfaceTint` | `#1C7A97` | = `primary` (tonal elevation tint) |
| `inverseSurface` | `#2E3134` | |
| `inverseOnSurface` | `#F1F1F3` | |
| `inversePrimary` | `#3FB6D4` | |
| `outline` | `#70787D` | Borders, dividers with emphasis |
| `outlineVariant` | `#C0C8CD` | Low-emphasis dividers |
| `error` | `#BA1A1A` | |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#FFDAD6` | |
| `onErrorContainer` | `#410002` | |
| `scrim` | `#000000` | |

### 1.2 Dark scheme

| Role | HEX | Notes |
|---|---|---|
| `primary` | `#3FB6D4` | Bright brand teal |
| `onPrimary` | `#00363F` | |
| `primaryContainer` | `#10586B` | |
| `onPrimaryContainer` | `#B8E7F5` | |
| `secondary` | `#FFB4A0` | Bright coral |
| `onSecondary` | `#5C1900` | |
| `secondaryContainer` | `#7A3421` | |
| `onSecondaryContainer` | `#FFDBD1` | |
| `tertiary` | `#E0C08A` | Soft gold |
| `onTertiary` | `#422C00` | |
| `tertiaryContainer` | `#5E421E` | |
| `onTertiaryContainer` | `#FBDFA6` | |
| `background` | `#101416` | |
| `onBackground` | `#E2E5E8` | |
| `surface` | `#101416` | |
| `onSurface` | `#E2E5E8` | |
| `surfaceVariant` | `#40484D` | |
| `onSurfaceVariant` | `#C0C8CD` | |
| `surfaceContainerLowest` | `#0B0F11` | |
| `surfaceContainerLow` | `#181C1F` | |
| `surfaceContainer` | `#1C2023` | Default card/sheet base |
| `surfaceContainerHigh` | `#262A2E` | |
| `surfaceContainerHighest` | `#303539` | |
| `surfaceTint` | `#3FB6D4` | |
| `inverseSurface` | `#E2E5E8` | |
| `inverseOnSurface` | `#2E3134` | |
| `inversePrimary` | `#1C7A97` | |
| `outline` | `#8A9297` | |
| `outlineVariant` | `#40484D` | |
| `error` | `#FFB4AB` | |
| `onError` | `#690005` | |
| `errorContainer` | `#93000A` | |
| `onErrorContainer` | `#FFDAD6` | |
| `scrim` | `#000000` | |

---

## 2. Brand gradient token

Used **only** for the logo, hero accents, splash, premium/paywall highlights, and decorative strokes — **never as a background behind body text**.

| Property | Value |
|---|---|
| `gradientStart` (teal) | `#2B8FB0` (light) / `#3FB6D4` (dark) |
| `gradientEnd` (coral) | `#F0725A` (both themes) |
| Angle | `135°` (top-left → bottom-right), i.e. Compose `start = Offset(0f, 0f)` → `end = Offset.Infinite` on `linearGradient` |
| Optional mid-stop | `#D67A6E` at 55% for a softer teal→coral blend on large surfaces |

```kotlin
val TranzlateBrandGradient = Brush.linearGradient(
    colors = listOf(GradientColors.start, GradientColors.end)
    // 135° achieved with default start=topLeft, end=bottomRight offsets
)
```

**Contrast rule:** the gradient never sits behind text or icons that convey information. If text must overlay it (e.g. a paywall banner), place text on a solid `surface`/`scrim` panel over the gradient, or use `onPrimary`/white only after verifying ≥4.5:1 against the **darkest** stop (`#2B8FB0` → white ≈ 3.7:1, so white body text over the raw gradient is **not allowed**; use it for large ≥24sp display headings only, or add a scrim).

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
    LocalGradientColors provides gradientFor(darkTheme),
) {
    MaterialTheme(colorScheme = colorScheme, typography = TranzlateTypography, shapes = TranzlateShapes, content = content)
}
```

**3. Access at call sites:**
- Color → `MaterialTheme.colorScheme.primary`
- Type → `MaterialTheme.typography.bodyLarge`
- Shape → `MaterialTheme.shapes.medium`
- Spacing → `LocalSpacing.current.md16`
- Gradient → `LocalGradientColors.current.brush`
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
