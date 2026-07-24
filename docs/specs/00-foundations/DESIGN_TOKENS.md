# Design Tokens (shared — every feature spec builds from these)

> Status: **v1 — engineering-complete, brand-seed PROPOSED.** The seed colour needs one product-owner confirmation (§1); everything else is decided. Spacing/alpha values derived from the Phase-4 token plan (design decisions, not Tranzlate code).
> **⚠ 2026-07-22 (issue #10): §1 Colour is SUPERSEDED** by [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) §1 (**P8 "Tranzlate Teal"** role tables) + [`docs/design/PALETTES.md`](../../design/PALETTES.md). The seed-generation strategy and the proposed `#1E88A8` seed below are historical — the shipped palette is the explicit P8 role table, not a seed derivation. The non-colour sections (§2–§8) remain in force.

## 1. Colour — Material 3 roles, seed-generated

- **Strategy:** M3 dynamic color (Android 12+) **on by default**; brand scheme below for < 12 / dynamic-off.
- **Proposed brand seed:** `#1E88A8` (teal-blue drawn from the app logo's blue tone; gradient partner pink `#F06292` reserved for the logo only — **never** as text/decoration gradients). ⚠ CONFIRM with brand owner.
- Scheme = standard M3 tonal generation from seed (light + dark). Tokens used by name only in specs: `primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, surface, surfaceContainerHigh, onSurface, onSurfaceVariant, outline, error, onError, errorContainer`.
- **Rules:** contrast ≥ 4.5:1 body / 3:1 large text (verify both themes); disabled = M3 38% pattern, never alpha-only below floor; semantic colours (error) never reused decoratively; **no hardcoded hex in feature code — roles only.**

## 2. Typography (M3 scale, default type — no custom face v1)
`displaySmall 36/44` · `headlineSmall 24/32` · `titleLarge 22/28` · `titleMedium 16/24 w500` · `bodyLarge 16/24` · `bodyMedium 14/20` · `labelLarge 14/20 w500` · `labelMedium 12/16 w500`. Result text: `headlineSmall`, user-scalable 0.85×–1.6× (persisted). Never `sp` hardcodes in composables — roles only.

## 3. Spacing scale (from the Phase-4 token plan)
`none 0 · xs 4 · small 8 · medium 12 · default 16 · large 24 · xl 32 · xxl 40 · huge 48 · massive 56` (dp). Screen edge padding = `default`. Card inner = `default`. Between stacked cards = `medium`.

## 4. Shape & elevation
Cards `RoundedCornerShape(16dp)` · sheets top `28dp` · chips/buttons M3 defaults · elevation: resting card 0 (outlined/tonal), sheet 1, dialog 3.

## 5. Component minimums (a11y gate)
Touch target **≥48×48dp** always · icon buttons 48dp box / 24dp icon · chips height ≥32dp with ≥48dp touch expansion · buttons height 56dp (primary) / 40dp (text).

## 6. Motion
State/content transitions `fadeThrough 300ms` · sheet `standardDecelerate 250ms` · loading appears only if op > 100ms (skeleton, not spinner-block) · respect `prefers-reduced-motion` (disable non-essential).

## 7. Adaptive breakpoints (window size classes — never dp checks in code)
| Class | Nav | Text feature layout |
|-------|-----|--------------------|
| Compact | `NavigationBar` (bottom) | single column |
| Medium | `NavigationRail` | single column, content max-width 600dp centered |
| Expanded | permanent drawer | `ListDetailPaneScaffold`: input pane 40% (min 360dp) / result 60% |

> **Note (issue #26):** the **Compact = `NavigationBar` (bottom)** row was the pre-existing target here — it was never touched by the 2026-07-22 hub amendment, and **DECISIONS D-5 rev.2** now confirms it (Home/Chat/Camera). The Medium (rail) / Expanded (permanent drawer) rows stand unchanged.

## 8. Iconography
Material Symbols (