# Palette Catalog — Tranzlate (research-verified, 2026-07-21 · app palette re-decided 2026-07-22)

> **Shipped palette = [P9 GT Blue](#p9--gt-blue-google-1p-complete-48-role--app-palette-owner-final-2026-07-22-issue-15).** P8 (teal) is retired; P5 stays retired. Everything else in this file is preset material for the future theme picker (issue #7).

> Owner directive: "internet එකේ වඩාත්ම ජනප්‍රිය mobile light/dark palette එක use කරන්න + candidates ඔක්කොම save කරන්න — theme color-change feature එකට".
> Method: 4-lens web research (M3 official token DB · iOS HIG/UIKit · top-app teardowns · community consensus) + synthesis + 2 adversarial verifications (source spot-checks + WCAG math recomputed). Workflow `wf_d446a618-efd` (agents 7). සියලු hex අගයන් source-cited; contrast WCAG 2.x relative-luminance formula එකෙන් double-computed.
> **Theme-preset feature:** මේ catalog එකේ COMPLETE palettes = future "Theme color" picker presets (tracking issue #7). Token architecture එක නිසා preset එකක් = role-map swap එකක් — feature code වෙනස් නෑ.

---

## P9 — GT Blue (Google 1P, complete 48-role) ⭐ **APP PALETTE (owner-final 2026-07-22, issue #15)**

Owner ran **Google Translate v10.27** beside our build on the emulator and picked GT's own colours over the teal brand set. Verified result: **P9 = P1 verbatim + the 12 `*Fixed` roles** that P1 never listed. Every role Compose's `ColorScheme` exposes is now set explicitly in `core/designsystem/Color.kt` — nothing falls back to a Material default.

**Accent reference tones** (chromium `ui/color/ref_color_mixer.cc`, cross-checked against the m3.material.io 1P token DB):

| Tone | Primary | Secondary | Tertiary |
|---|---|---|---|
| 10 | `#041E49` | `#001D35` | `#072711` |
| 20 | `#062E6F` | `#003355` | `#0A3818` |
| 30 | `#0842A0` | `#004A77` | `#0F5223` |
| 40 | `#0B57D0` | `#00639B` | `#146C2E` |
| 80 | `#A8C7FA` | `#7FCFFF` | `#6DD58C` |
| 90 | `#D3E3FD` | `#C2E7FF` | `#C4EED0` |

**Light + dark (36 base roles):** identical to the **P1 table below** — accents, surfaces, neutrals, outline, inverse and the error set, hex for hex.

**The 12 `*Fixed` roles — IDENTICAL in BOTH schemes.** That is what "fixed" means: a colour that survives a light↔dark switch unchanged. Ladder verified in Compose `ColorLightTokens.kt`/`ColorDarkTokens.kt`: Fixed = tone90 · FixedDim = tone80 · onFixed = tone10 · onFixedVariant = tone30.

| Role | Value | Role | Value |
|---|---|---|---|
| `primaryFixed` | `#D3E3FD` | `onPrimaryFixed` | `#041E49` |
| `primaryFixedDim` | `#A8C7FA` | `onPrimaryFixedVariant` | `#0842A0` |
| `secondaryFixed` | `#C2E7FF` | `onSecondaryFixed` | `#001D35` |
| `secondaryFixedDim` | `#7FCFFF` | `onSecondaryFixedVariant` | `#004A77` |
| `tertiaryFixed` | `#C4EED0` | `onTertiaryFixed` | `#072711` |
| `tertiaryFixedDim` | `#6DD58C` | `onTertiaryFixedVariant` | `#0F5223` |

**Contrast — recomputed 2026-07-22 with the WCAG 2.x relative-luminance formula (issue #15). All pass AA; no pair below 4.5.**

| Pair | Light | Dark |
|---|---|---|
| `onPrimary` / `primary` | 6.39 ✓ | 7.50 ✓ |
| `onSurface` / `surface` | 15.67 ✓ | 14.47 ✓ |
| `onSurfaceVariant` / `surfaceVariant` | 7.28 ✓ | 5.51 ✓ |
| `onPrimaryContainer` / `primaryContainer` | 7.04 ✓ | 7.04 ✓ |
| `onSecondaryContainer` / `secondaryContainer` | 7.20 ✓ | 7.20 ✓ |
| `onTertiaryContainer` / `tertiaryContainer` | 7.32 ✓ | 7.32 ✓ |
| `onErrorContainer` / `errorContainer` | 7.17 ✓ | 7.17 ✓ |
| `primary` / `surface` (icon/label accent) | 6.07 ✓ | 10.80 ✓ |

Call-site pairs the stock-M3 swap introduced: `onSurfaceVariant`/`surface` 8.93 · 10.90 — `onSurface`/`surfaceContainerLowest` 16.48 · 15.03 — `onSurfaceVariant`/`surfaceContainerHigh` 7.68 · 8.42 — `primary`/`surfaceContainerLowest` 6.39 · 11.22. `*Fixed` pairs: lowest is `onTertiaryFixedVariant`/`tertiaryFixedDim` **5.13 ✓** (all 12 pass).

**Gradient:** none. The ambient-wash token is deleted (issue #15) — pages are flat `surface`, panels separate by a lighter surface step.

---

## P1 — Google Blue (GM3 "1P") — research winner; the base of the shipped P9 (was the NEUTRAL/SUPPORT source for the retired P8)

Google තමන්ගේම apps වල (Gmail/Drive/Chrome static baseline) ship කරන scheme එක. Blue = top apps + surveys දෙකෙන්ම dominant accent hue; surfaces = M3 tonal-neutral greys (M2 #121212 එකේ අනුප්‍රාප්තිකයා). Status: **COMPLETE** — Compose `lightColorScheme()`/`darkColorScheme()` වලට කෙලින්ම.

| Role | Light | Dark |
|---|---|---|
| primary | `#0B57D0` | `#A8C7FA` |
| onPrimary | `#FFFFFF` | `#062E6F` |
| primaryContainer | `#D3E3FD` | `#0842A0` |
| onPrimaryContainer | `#0842A0` | `#D3E3FD` |
| secondary | `#00639B` | `#7FCFFF` |
| onSecondary | `#FFFFFF` | `#003355` |
| secondaryContainer | `#C2E7FF` | `#004A77` |
| onSecondaryContainer | `#004A77` | `#C2E7FF` |
| tertiary | `#146C2E` | `#6DD58C` |
| onTertiary | `#FFFFFF` | `#0A3818` |
| tertiaryContainer | `#C4EED0` | `#0F5223` |
| onTertiaryContainer | `#0F5223` | `#C4EED0` |
| error | `#B3261E` | `#F2B8B5` |
| onError | `#FFFFFF` | `#601410` |
| errorContainer | `#F9DEDC` | `#8C1D18` |
| onErrorContainer | `#8C1D18` | `#F9DEDC` |
| background / surface | `#FAF9F8` | `#131314` |
| onBackground / onSurface | `#1F1F1F` | `#E3E3E3` |
| surfaceVariant | `#E1E3E1` | `#444746` |
| onSurfaceVariant | `#444746` | `#C4C7C5` |
| surfaceDim | `#DADADA` | `#131314` |
| surfaceBright | `#FAF9F8` | `#393939` |
| surfaceContainerLowest | `#FFFFFF` | `#0E0E0F` |
| surfaceContainerLow | `#F4F3F2` | `#1F1F1F` |
| surfaceContainer | `#EFEDED` | `#1F2020` |
| surfaceContainerHigh | `#E9E8E8` | `#2A2A2A` |
| surfaceContainerHighest | `#E3E3E3` | `#343535` |
| outline | `#747775` | `#8E918F` |
| outlineVariant | `#C4C7C5` | `#444746` |
| inverseSurface | `#303030` | `#E3E3E3` |
| inverseOnSurface | `#F2F2F2` | `#303030` |
| inversePrimary | `#A8C7FA` | `#0B57D0` |
| surfaceTint | = primary | = primary |
| scrim / shadow | `#000000` | `#000000` |

**Contrast (recomputed, verifier-confirmed):** 14/14 text pairs pass — light onPrimary/primary 6.39 · onSurface/surface 15.67 · dark onPrimary/primary 7.50 · onSurface/surface 14.47 · full list in workflow record. **Sources:** m3.material.io DSM token DB (1P Baseline context) · chromium `ref_color_mixer.cc` (`kColorRefPrimary40=#0B57D0`, `kColorRefPrimary80=#A8C7FA`) · Compose `ColorDarkTokens.kt` role ladder · material-web `_md-sys-color.scss`.
**Notes:** (1) Compose එකට `surfaceTint = primary` explicit දාන්න. (2) Live 1P DB surface-variant එකක් තියෙනවා (light surface `#FFFFFF` + blue-tinted ladder `#F8FAFD/#F0F4F9/#E9EEF6/#DDE3EA`; dark `#0E0E0E/#1B1B1B/#1E1F20/#282A2C/#333537`) — අපේ pick = Compose-token-structure grey ladder (fidelity to the shipped Compose defaults); blue-tinted variant P1b ලෙස note කර ඇත.

## P8 — Tranzlate Teal (cool-mono) — ❌ **RETIRED 2026-07-22 (issue #15)**

Was the app palette for a few hours on 2026-07-22 (issue #10). **Retired the same day** after the owner drove Google Translate on the emulator beside our build: the north star (D-0) is GT's behaviour AND its look, and a teal identity fights that. Superseded by **P9 (GT Blue)** above. Kept here as a preset candidate (issue #7) and as history — the teal hexes below must not appear in shipped code.

Original note: the teal accent kept from the brand set with all warm accents (coral/gold) removed — a single cool family, teal primary, blue support, P1 neutral greys. Every pair computed with the WCAG 2.x relative-luminance formula (13/13 pass).

| Role | Light | Dark |
|---|---|---|
| primary | `#1C7A97` | `#3FB6D4` |
| onPrimary | `#FFFFFF` (4.91:1 ✓) | `#00363F` (5.54:1 ✓) |
| primaryContainer | `#B8E7F5` | `#10586B` |
| onPrimaryContainer | `#002F3C` (10.73:1 ✓) | `#B8E7F5` (6.00:1 ✓) |
| secondary | `#00639B` | `#7FCFFF` |
| onSecondary | `#FFFFFF` (6.45:1 ✓) | `#003355` (7.65:1 ✓) |
| secondaryContainer | `#C2E7FF` | `#004A77` |
| onSecondaryContainer | `#004A77` (7.20:1 ✓) | `#C2E7FF` |
| tertiary | `#10586B` | `#8FD3E3` |
| onTertiary | `#FFFFFF` (7.98:1 ✓) | `#00363F` (7.87:1 ✓) |
| tertiaryContainer | `#CDE9F2` | `#0B4A5A` |
| onTertiaryContainer | `#002F3C` (11.23:1 ✓) | `#CDE9F2` (7.72:1 ✓) |
| error set | P1 values (`#B3261E`/`#F9DEDC`…) | P1 values (`#F2B8B5`/`#8C1D18`…) |
| surfaces / neutrals / outline / inverse | **P1 values verbatim** (`#FAF9F8`, `#1F1F1F`, container ladder, `#747775`…) | **P1 values verbatim** (`#131314`, `#E3E3E3`, `#0E0E0F/#1F1F1F/#1F2020/#2A2A2A/#343535`, `#8E918F`…) |
| surfaceTint | = primary | = primary |
| ambient gradient | teal→blue wash (`#1C7A97`→`#00639B` at low opacity over surface) | same family, deeper |

`primary` on `surface`: light 4.67:1 ✓ · dark 7.83:1 ✓ (icon/label accent use).
Provenance: teal tones from the original DESIGN_SYSTEM §1 brand set, blue/neutral/error from P1 (Google 1P, research-verified), tertiary tones derived in-family and contrast-verified here. The "ambient gradient" row is retired with the palette — the app has no gradient token at all (issue #15).

## P2 — Material 3 Baseline Purple (3P) — COMPLETE preset

M3 නිල baseline (docs/Compose defaults). Light: primary `#6750A4` · onPrimary `#FFFFFF` · pContainer `#EADDFF` · onPC `#4F378B` (current DB; legacy tokens `#21005D`) · secondary `#625B71`/sC `#E8DEF8` · tertiary `#7D5260`/tC `#FFD8E4` · error `#B3261E`/eC `#F9DEDC` · surface/bg `#FEF7FF` · onSurface `#1D1B20` · sVariant `#E7E0EC`/onSV `#49454F` · dim `#DED8E1` · containers `#FFFFFF/#F7F2FA/#F3EDF7/#ECE6F0/#E6E0E9` · outline `#79747E`/ov `#CAC4D0` · inverse `#322F35`/`#F5EFF7`/`#D0BCFF`.
Dark: primary `#D0BCFF`/onP `#381E72`/pC `#4F378B`/onPC `#EADDFF` · secondary `#CCC2DC`/onS `#332D41`/sC `#4A4458`/onSC `#E8DEF8` · tertiary `#EFB8C8`/onT `#492532`/tC `#633B48`/onTC `#FFD8E4` · error `#F2B8B5`/onE `#601410`/eC `#8C1D18`/onEC `#F9DEDC` · surface/bg `#141218` · onSurface `#E6E0E9` · sV `#49454F`/onSV `#CAC4D0` · bright `#3B383E` · containers `#0F0D13/#1D1B20/#211F26/#2B2930/#36343B` · outline `#938F99`/ov `#49454F` · inverse `#E6E0E9`/`#322F35`/`#6750A4`.
Sources: m3.material.io token DB + material-web + Compose `PaletteTokens.kt`.

## P3 — Classic Google Blue (accent variant of P1)

Accent swap only: light `#1A73E8` (kGoogleBlue600) / dark `#8AB4F8` (kGoogleBlue300) — 2018–2021 Gmail/Translate blue. ⚠ `#1A73E8` on white = **4.50:1 exactly** (AA zero-margin) — P1 (`#0B57D0`, 6.39) safer. Source: chromium `color_palette.h`.

## P4 — iOS System (reference; needs M3 role derivation before use)

systemBlue `#007AFF`/dark `#0A84FF` (accessible variants `#0040DD`/`#409CFF`) · light bg `#FFFFFF`, secondary `#F2F2F7` · dark bg `#000000`, elevated `#1C1C1E`/`#2C2C2E`/`#3A3A3C` · labels `#000000`/`#FFFFFF` + 60% secondaries. Source: developer.apple.com HIG/UIKit runtime values. Status: PARTIAL — M3 role map derive කළ යුතුයි.

## P5 — Tranzlate Teal/Coral (brand heritage) — ❌ RETIRED 2026-07-22

Full light+dark tables: [`docs/specs/00-foundations/DESIGN_SYSTEM.md` §1](../specs/00-foundations/DESIGN_SYSTEM.md) (teal `#1C7A97`/`#3FB6D4` + coral + gold, WCAG-checked). **Owner FINAL 2026-07-22: permanently retired** — design system එකෙන් ඉවත් කරන ලදී (theme-preset එකක් ලෙසවත් නෑ). **2026-07-22 පසුව:** owner ට teal එක ලස්සන බව තහවුරු වුණා — coral/gold පමණක් නරකයි. එබැවින් P5 ගේ teal tones **P8** එකට carry වුණා (coral/gold ස්ථිරව අයින්), issue #10 scope = P8 apply. **එදිනම (issue #15) owner Google Translate emulator එකේ අපේ build එක ළඟ තියලා බැලුවාට පස්සේ P8 ත් retire වුණා** — shipped palette දැන් **P9 (GT Blue)**.

## P6 — M2 Classic Dark (historic reference only)

`#121212` surfaces + `#BB86FC`/`#03DAC6` 200-tone accents + white 87/60/38% text — M2-era (superseded by M3 tonal neutrals; `#121212` M3 token DB එකේ නෑ). Preset එකක් ලෙස recommend නොකරයි.

## P7 — Top-app accent references (accent ideas only)

Spotify `#1DB954`/dark `#121212`–`#181818` · WhatsApp `#25D366`/dark `#111B21` · Instagram UI blue `#0095F6` · YouTube `#FF0000`/dark `#0F0F0F` · community "2026 UI-kit blue" `#2563EB` family. Measured dark-surface cluster: `#0F0F0F`–`#181818` (P1 dark `#131314` sits inside ✓).
