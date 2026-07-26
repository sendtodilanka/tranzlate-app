---
status: accepted   # owner-decided live in the 2026-07-22 Q&A session (flat · GT-identical palette · stock M3 · full-screen picker)
issue: 15
title: UI reset — flat design, GT-identical 48-role palette, stock Material 3 components, full-screen language picker
date: 2026-07-22
author: Claude (Opus 4.8) · evidence = Google Translate v10.27 driven on an emulator + Google's own published palette sources
---

> **⚠️ Superseded IA note (2026-07-24, issue #26 → DECISIONS D-5 rev.2):** this doc's Home **"tonal quick actions"** (the Conversation/Camera canvas tiles — the component-map row and build step 3) are **reversed**. Those peers became **bottom-nav tabs** (Home / Chat / Camera), not canvas tiles.
>
> **⚠️ Superseded AGAIN (2026-07-26, issue #42 / PR #43 → DECISIONS D-5 rev.3):** the bottom nav is gone too — the peers are now **tool cards on Home's card stack** (UI_SPEC §2.1), which is nearer this doc's original tonal-quick-action instinct than rev.2 was. Two more of this plan's outcomes moved with it: **§1's neutral surfaces** (7 roles now take the 1P blue-tinted values — PALETTES "P9 neutral amendment"), and the §2 rows for **top-bar icons / quick actions** (Material Symbols Rounded drawables now, not `material-icons-extended`). **Still standing exactly as written:** the flat design, the GT-Blue accents + error + the 12 `*Fixed` roles, stock-M3-first, and the full-screen language picker. §5's risk 2 ("`surfaceContainerLowest` on a `#FAF9F8` page is a subtle step — verify on device") **materialised**: the answer shipped was a 1dp shadow on the cards plus the cooler `#F8FAFD` page.

# Plan — issue #15

> Owner ran Google Translate on the emulator and compared it with our build component-by-component. Outcome: drop the gradient identity, adopt GT's exact colours with the COMPLETE M3 role set, and use stock Material 3 components everywhere M3 has one.

## 1. Palette — "GT Blue (Google 1P)", all 48 roles

**Accents** — Google's own reference tones, verified verbatim from Chromium `ui/color/ref_color_mixer.cc` (Google's published source) and cross-checked against the m3.material.io 1P token DB:

| Tone | Primary | Secondary | Tertiary |
|---|---|---|---|
| 10 | `#041E49` | `#001D35` | `#072711` |
| 20 | `#062E6F` | `#003355` | `#0A3818` |
| 30 | `#0842A0` | `#004A77` | `#0F5223` |
| 40 | `#0B57D0` | `#00639B` | `#146C2E` |
| 80 | `#A8C7FA` | `#7FCFFF` | `#6DD58C` |
| 90 | `#D3E3FD` | `#C2E7FF` | `#C4EED0` |

**Light scheme:** primary `#0B57D0` · onPrimary `#FFFFFF` · primaryContainer `#D3E3FD` · onPrimaryContainer `#0842A0` · secondary `#00639B` · onSecondary `#FFFFFF` · secondaryContainer `#C2E7FF` · onSecondaryContainer `#004A77` · tertiary `#146C2E` · onTertiary `#FFFFFF` · tertiaryContainer `#C4EED0` · onTertiaryContainer `#0F5223` · error `#B3261E` · onError `#FFFFFF` · errorContainer `#F9DEDC` · onErrorContainer `#8C1D18` · background/surface `#FAF9F8` · onBackground/onSurface `#1F1F1F` · surfaceVariant `#E1E3E1` · onSurfaceVariant `#444746` · surfaceDim `#DADADA` · surfaceBright `#FAF9F8` · containers `#FFFFFF`/`#F4F3F2`/`#EFEDED`/`#E9E8E8`/`#E3E3E3` · outline `#747775` · outlineVariant `#C4C7C5` · inverseSurface `#303030` · inverseOnSurface `#F2F2F2` · inversePrimary `#A8C7FA` · surfaceTint = primary · scrim `#000000`.

**Dark scheme:** primary `#A8C7FA` · onPrimary `#062E6F` · primaryContainer `#0842A0` · onPrimaryContainer `#D3E3FD` · secondary `#7FCFFF` · onSecondary `#003355` · secondaryContainer `#004A77` · onSecondaryContainer `#C2E7FF` · tertiary `#6DD58C` · onTertiary `#0A3818` · tertiaryContainer `#0F5223` · onTertiaryContainer `#C4EED0` · error `#F2B8B5` · onError `#601410` · errorContainer `#8C1D18` · onErrorContainer `#F9DEDC` · background/surface `#131314` · onBackground/onSurface `#E3E3E3` · surfaceVariant `#444746` · onSurfaceVariant `#C4C7C5` · surfaceDim `#131314` · surfaceBright `#393939` · containers `#0E0E0F`/`#1F1F1F`/`#1F2020`/`#2A2A2A`/`#343535` · outline `#8E918F` · outlineVariant `#444746` · inverseSurface `#E3E3E3` · inverseOnSurface `#303030` · inversePrimary `#0B57D0` · surfaceTint = primary · scrim `#000000`.

**The 12 previously-missing `*Fixed` roles — identical in BOTH schemes** (mapping verified in Compose `ColorLightTokens.kt`/`ColorDarkTokens.kt`: Fixed=tone90 · FixedDim=tone80 · onFixed=tone10 · onFixedVariant=tone30):
`primaryFixed #D3E3FD` · `primaryFixedDim #A8C7FA` · `onPrimaryFixed #041E49` · `onPrimaryFixedVariant #0842A0` · `secondaryFixed #C2E7FF` · `secondaryFixedDim #7FCFFF` · `onSecondaryFixed #001D35` · `onSecondaryFixedVariant #004A77` · `tertiaryFixed #C4EED0` · `tertiaryFixedDim #6DD58C` · `onTertiaryFixed #072711` · `onTertiaryFixedVariant #0F5223`.

Neutrals/surfaces/error keep the researched 1P-baseline greys (PALETTES.md P1). Teal (P8) is retired; the ambient gradient token is deleted.

## 2. Component mapping — measured from GT v10.27 on-device

| Element | GT's treatment | Ours becomes |
|---|---|---|
| Page | flat, no gradient | `surface`, no gradient |
| Top-bar icons | plain icons, no container | `IconButton` (delete `DottedRingIconButton`) |
| Elevated panel | lighter step, no border/shadow | light `surfaceContainerLowest` / dark `surfaceContainer` |
| Language chips | filled, lighter-than-page, large radius | `AssistChip`-family / tonal `Button` |
| Swap | plain icon | `IconButton` |
| Primary action | large filled circle | `FilledIconButton`; **light `primary` · dark `primaryContainer`** |
| Quick actions | tonal circle + label beneath | `FilledTonalIconButton` + label |
| Result text | accent-coloured | `primary` (unchanged) |
| Error | `errorContainer` card + text action, no dialog | `Card(errorContainer)` + `TextButton` |
| List section header | primary-coloured label | `Text` labelLarge in `primary` |
| List rows | plain, no dividers, trailing icon | `ListItem` |
| Language picker | full screen + top-bar search | full-screen destination (spec D-E2 Screen A) |
| Loading | — (M3 has no skeleton component) | **keep custom `ShimmerResult`** |

## 3. Work
1. `:core:designsystem` — 48-role schemes; delete `AmbientGradient.kt`; keep Motion/Dimensions/Alpha (drop wash alphas).
2. `:core:ui` — swap to stock M3 per the table; `ComposerCard` rebuilt from stock parts; `AmbientBackground` deleted; previews updated.
3. `:feature:text` — Home (flat, tonal quick actions), Result (plain icon actions, error card), **new full-screen `LanguagePickerScreen`** (search action, "Detect language" row, primary section headers, plain rows) replacing the bottom sheet; nav wired in `:app`.
4. Docs — PALETTES.md (GT Blue = app palette, P8 retired), DESIGN_SYSTEM §1/§2, UI_SPEC §1/§2.3/§3.
5. Tests/previews green; emulator screenshots light+dark for the PR.

## 4. Non-goals
Settings screen + dynamic-colour/theme toggles (next step) · brains/engines · camera · paywall · history screen.

## 5. Risks
- Sweeping component swap touches every screen → rely on Konsist/Detekt/tests + emulator verification.
- `surfaceContainerLowest` (light) is pure white; on a `#FAF9F8` page the step is subtle — verify on device, fall back to `surfaceContainerLow` if the panel disappears.
