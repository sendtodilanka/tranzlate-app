# UI Spec — approved design contract (2026-07-22 · §1/§3 reset by issue #15 · §2.1 reset by issue #42)

> Owner-approved after 7 design rounds (Claude Design project "Tranzlate UI v2"). **This file is the ground truth for implementation** — the Claude Design chat is not. Palette = [`PALETTES.md` P9 "GT Blue"](PALETTES.md); reference apps = [`reference-apps/`](reference-apps/README.md).
> Behaviour rules still come from `docs/specs/` (DECISIONS, EDGE_CASES, TEST_A11Y) — with the C-2 amendment (issue #9): **explicit Translate action for ALL engines, result on its own screen.**
> **2026-07-22 — issue #15 reset:** owner drove Google Translate v10.27 beside our build. The gradient, the dotted-ring buttons and the bespoke component set are gone; the app is **flat + stock Material 3 + GT's palette**. §1 and §3 below are rewritten; the language picker (§2.6) becomes a full screen.
> **2026-07-26 — issue #42 / PR #43 reset (DECISIONS D-5 rev.3):** the owner's Claude Design export **"Offline Translator M3"** is now the Home contract. **The bottom navigation bar and the modal drawer are both removed**; Home is a **card stack** that reaches every destination itself. §2.1 is rewritten, §2.2 folds into it as the input card, §2.3 is retired, and §1 gains the typeface/icon rows the export settled. Shipped: `feature/text/.../HomeScreen.kt`, `app/.../navigation/TranzlateApp.kt`. *(The design export itself is not yet checked in — see the note under §2.1.)*

## 1. Global visual language

| Aspect | Rule |
|---|---|
| **Components** | **Stock Material 3 first.** If M3 ships the component, use it as-is — `IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `AssistChip`, `CenterAlignedTopAppBar`, `Card`, `TextButton`, `ListItem`, `Scaffold`. A `:core:ui` wrapper needs a rule to carry (see DESIGN_SYSTEM §9); a re-colour is not a rule. |
| Background | Flat `surface`. **No gradient anywhere** — not on pages, cards, icons or text (the ambient-wash token is deleted). |
| Elevated panels | Cards = a **lighter surface step** — light `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer` — read at the call site through `LocalFloatingSurface`. **Separation by lightness first**; `outlineVariant` hairlines only where no step exists. **Amended 2026-07-26 (issue #42):** the approved design does carry a **1dp shadow** (`Elevation.level1`) on the Home cards — the lightness step alone did not lift #FFFFFF off a #F8FAFD page. "No shadow" now means *no decorative* shadow, not zero elevation. |
| Accent discipline | **Exactly one saturated element per screen** (the primary action). Everything else neutral or soft tonal. **Home reading (2026-07-26):** the saturated element is the mic ⇄ Translate action; the 2×2 tool grid's icon circles are *tonal* containers (`primaryContainer` / `secondaryContainer` / `tertiaryContainer` / `surfaceContainerHigh`), not accents, so the rule holds. |
| **Primary action** | `FilledIconButton` — **light: `primary` fill · dark: `primaryContainer` fill** (in dark, `primary` is a near-white blue that glares on `#131314`; GT uses the deep container tone). The rule lives in `PrimaryActionButton` (`:core:ui`), once. ⚠ Home's own action currently fills with `primary` in **both** themes (`HomeScreen.kt` builds the mic/Translate slot inline rather than through `PrimaryActionButton`, so the dark-theme rule is not applied there — tracked as FIX_QUEUE C11. |
| Icon buttons | Plain M3 `IconButton` — no container, no ring, no bespoke detail. 48dp target, 24dp glyph. *(Canvas quick-action circles were removed in issue #26; the bottom-nav tabs that replaced them were themselves removed in issue #42 — Conversation/Camera are tool cards on Home now.)* |
| Type | **Roboto Flex, bundled** (issue #42) — `core/designsystem/src/main/res/font/roboto_flex.ttf`, declared as one `FontVariation` axis instance per weight (Normal + Medium) so `FontWeight.Medium` actually renders Medium. One neutral sans at every level incl. result; hierarchy by **size + weight** only. Non-Latin scripts fall through to the platform's Noto (Sinhala result renders in the same visual family). *(Supersedes "system default sans, no bundled font".)* |
| Icons | **Material Symbols Rounded**, shipped as **17 vector drawables** in `core/designsystem/src/main/res/drawable/ic_*.xml` and drawn with `painterResource` — not `material-icons-extended`, and not the ~14.7 MB variable icon font. New Home glyphs are added the same way. *(Legacy surfaces — Result, `:core:ui`, the retired drawer — still import `material-icons-extended`; removing that dependency is FIX_QUEUE batch 🔵 E.)* |
| Shape / rhythm | One radius scale (chips/buttons `full`, cards `md12`–`lg16`, input card + error card `xl28`); 8dp vertical rhythm; no decorative shadows. |

## 2. Screens

### 2.1 Home — the card stack (rewritten 2026-07-26 · issue #42 / PR #43 · DECISIONS D-5 rev.3)

**There is no bottom navigation bar, no drawer and no FAB.** Home *is* the navigation: a scrolling stack of cards, each of which is the door to one destination. The shell around it is a bare `NavDisplay` (`TranzlateApp.kt`) — no `NavigationSuiteScaffold`, no `NavigationSuiteType` switching, nothing to hide on secondary screens because there is nothing to hide.

> **Source:** the owner's Claude Design export "Offline Translator M3", measured on a 412dp frame. The export itself is **not in the repo** — `docs/design/claude-design/` still holds the 2026-07-22 round-5 cards, and `HomeScreen.kt`'s header cites a `docs/design/OFFLINE_TRANSLATOR_M3.md` that does not exist. Until it is checked in, **this section plus the shipped `HomeScreen.kt` are the contract.**

**Fixed (does not scroll) — the `topBar` slot holds both:**
1. **Top app bar** — stock `TopAppBar`, transparent container over the page `surface`. Title **"Translate"** (`titleLarge`, start-aligned — not centred). Actions: a **Pro chip** (`primaryContainer` pill, 24dp, `ic_workspace_premium` + "Pro"; a soft upsell, never a blocker) then a **settings `IconButton`** (`ic_settings`, `onSurfaceVariant`) → Settings.
2. **Language pill row** — `[source ▾]` · circular **swap** button · `[target ▾]`, full width, both pills `surfaceContainerHigh` with a `full` corner and a trailing `ic_arrow_drop_down`. **Pinned:** it lives in the topBar slot, so it never scrolls away. Tapping either pill opens the full-screen picker (§2.6). **Swap is disabled when the source is "Detect language"** — there is nothing to move into the target slot (EDGE_CASES: say why, never act wrongly).

**Scrolling stack, in order:**
3. **Input card** — the composer, folded in here (see §2.2). `xl28` corners, `LocalFloatingSurface` fill, `Elevation.level1`.
4. **"Tools"** label — `labelLarge` on `onSurfaceVariant`.
5. **2×2 tonal tool grid** — four equal cards, each a floating-surface card holding a **tonal icon circle** + title + one-line subtitle:
   | Card | Icon | Tonal circle | Goes to |
   |---|---|---|---|
   | **Offline mode** | `ic_cloud_done` | `primaryContainer` | Offline-languages screen |
   | **Voice** | `ic_record_voice_over` | `secondaryContainer` | guided message (voice vertical not built) |
   | **Camera** | `ic_photo_camera` | `tertiaryContainer` | Camera screen |
   | **Conversation** | `ic_forum` | `surfaceContainerHigh` | Chat (coming-soon placeholder) |
6. **"Download languages" list row** — full-width card: `primaryContainer` icon circle (`ic_download_for_offline`) · headline + supporting line · trailing `ic_chevron_right` → Offline-languages screen.
7. **Phrasebook / Quotes mini cards** — two half-width shortcut cards (icon in `primary` + `labelLarge`); both answer with a guided message until their verticals land.
8. **"Natural phrasing" banner** — a `secondaryContainer` tonal banner with `ic_auto_awesome`, a **NEW badge** (`primary`/`onPrimary`, `labelSmall`), a supporting line and a chevron. The AI teaser; soft, dismissible in spirit, never a blocker.

**Rules that survive the rewrite**
- **No dead ends (EDGE_CASES):** every card that has no destination yet shows a guided snackbar — `home_guided_pro`, `text_guided_voice`, `home_guided_phrasebook`, `home_guided_quotes`, `home_guided_phrasing`.
- **First run:** zero history — the stack must feel complete on its own (no empty-state apology).
- **No overscroll stretch:** Home provides `LocalOverscrollFactory = null`; the approved design has no stretch/glow at the scroll ends. Scroll and fling are otherwise untouched.
- **No system-bar contrast scrim:** `window.isNavigationBarContrastEnforced = false` (API 29+) — without it the platform tints the navigation bar (#16171C over a #131314 page) and the strip reads as a different colour from the stack. *(This reverses `docs/plan/issue-17-core-shell-theme.md` T-7 — see the note there.)*
- **Insets:** the topBar column takes `WindowInsets.safeDrawing.only(Horizontal)` itself, because `Scaffold` does not inset topBar content and the pinned language row would otherwise sit under a landscape display cutout.
- **Medium/Expanded are not designed yet** — the same stack renders at every width (D-5 rev.3 open item).

### 2.2 Input card (the composer, inside the Home stack)
Rationale unchanged: the control row carries a counter plus the action, so it needs full width; one consistent card shape avoids layout jumping.
- **Built from stock parts** (issue #15): `Surface` (`LocalFloatingSurface`, `xl28`, `Elevation.level1`) + `BasicTextField` + an action slot.
- **Structure:** multi-line text area on top (placeholder "Enter text", `headlineSmall`) → **action row beneath**: char counter at the start, the primary action at the end. The **language chips moved out** of this card into the pinned row (§2.1) — that is the design's change, and it is why the chips now carry a dropdown caret.
- **Primary action — a real morph, not a disable:** 🎤 **mic** (a circular `primary` surface, `tt_text_mic`) while the field is blank → a filled **Translate** button (`ic_translate` + "Translate", `tt_text_translate_btn`) as soon as there is text. **The two are different nodes** — when the input is blank the translate affordance does not exist at all, so a tap on the slot cannot start a translation. IME ⏎ (`ImeAction.Send`) mirrors the button (C-2).
- **Char counter** `12 / 500` (C-5 format, **spaced** since PR #43). Over the limit it swaps to the inline `text_over_char_limit` copy in `error` and the Translate button disables — the input is never truncated.
- **Growth:** `heightIn(min = Dimensions.composerInputMinHeight)` (56dp); the whole stack scrolls, so the card grows with content instead of scrolling internally.

### 2.3 Navigation drawer — ❌ RETIRED (2026-07-26, issue #42 · D-5 rev.3)
~~The drawer is secondary navigation — the primary Home/Chat/Camera switch lives in the bottom nav (§2.1). Claude-app structure: wordmark → sections **History · Saved · Offline languages · Settings · Help · About** → **RECENTS** list → account row pinned bottom.~~

**The approved design has no drawer.** Settings is reached from the top-bar icon, offline languages from the tool card and the download row, Camera and Conversation from their tool cards. **History · Saved · Help · About · Recents · the account row currently have no entry point** — they must be re-homed (top-bar overflow, a Settings section, or new cards) when their verticals land; do not re-introduce a drawer to solve it without a new decision record.
*Code state:* `DrawerContent.kt` / `DrawerViewModel.kt` / `TopLevelDestination.kt` are still in `:app` but are **no longer referenced** by `TranzlateApp.kt` — dead code queued for removal.

### 2.4 Result screen (separate reading surface — no composer)
- Top: `CenterAlignedTopAppBar` (transparent) — back · bookmark · ⋯, all plain `IconButton`s.
- Source block: `ENGLISH` label · source text · phonetic (`/ həˈloʊ /`) · speaker + copy (top-right).
- Divider (neutral hairline).
- Target block: `සිංහල` label + **engine badge** — a small `AssistChip` (`⚡ Offline · instant`). *Not* a `SuggestionChip(enabled = false)*: a disabled chip alpha-dims its label under the 4.5:1 floor (contract §2.5). Tapping it explains the engine rather than doing nothing.
- **Result text large in `primary`** · transliteration · actions: reverse · speaker · copy · 👍 · 👎.
- **Follow-up chips:** `✦ Formal` · `Explain` · `Examples` (the AI-enhancement slot — issue #8).
- Home → Result feels continuous (shared-element / container transform).

### 2.5 States (EDGE_CASES — every one designed, no dead ends)
Translating (shimmer in the result area — kept custom, M3 has no skeleton) · error as an **`errorContainer` `Card` with a `TextButton` bottom-end** (`xl28` corners, assertive live region — never a dialog) · offline/engine chip · daily-limit **dismissible bottom sheet** (Upgrade / Not now, C-11) · over-char-limit inline copy · empty field (action disabled with reason).

### 2.6 Language picker (full screen — issue #15)
Replaces the interim bottom sheet: a sheet cannot hold ~100 languages plus search, and GT uses a screen.
- `Scaffold` + `CenterAlignedTopAppBar`: back · title ("Translate from" / "Translate to") · **search action** (guided message until the search vertical lands).
- **"Detect language" row first — source side only** (a target cannot be detected).
- Section header **"All languages" in `primary`** (`labelLarge`).
- Plain `ListItem` rows, **no dividers**, transparent containers; the current choice reads in `primary` with a trailing check AND `selected` semantics.
- Selecting writes the pref and pops back. Rows carry `tt_lang_row_<id>`.

## 3. Token mapping (implementation)
`page` = flat `surface` (no overlay) · `panel/card` = `surfaceContainerLowest` (light) / `surfaceContainer` (dark), via `LocalFloatingSurface` · `chip` = `surfaceContainerHigh` tonal, selected = `primaryContainer` · `primary action` = `primary`/`onPrimary` in LIGHT, `primaryContainer`/`onPrimaryContainer` in DARK · `divider` = `outlineVariant` · `result text` = `primary` · `secondary text` = `onSurfaceVariant` · `error card` = `errorContainer`/`onErrorContainer`. All values from PALETTES.md **P9**.

**Added 2026-07-26 (issue #42) — Home card stack:** `language pill` + `swap` = `surfaceContainerHigh`/`onSurface` · `card shadow` = `Elevation.level1` on every floating card  — on the input card, the four tool cards, the download row, both mini cards and the swap button, but **not** the Natural-phrasing banner (the export gives it none) · `tool-grid icon circle` = one tonal container each (`primaryContainer` · `secondaryContainer` · `tertiaryContainer` · `surfaceContainerHigh`) with its matching `on*` tint · `list-row icon circle` = `primaryContainer`/`onPrimaryContainer` · `mini-card icon` = `primary` · `AI banner` = `secondaryContainer`/`onSecondaryContainer` with a `primary`/`onPrimary` NEW badge · `Pro chip` = `primaryContainer`/`onPrimaryContainer`. The **neutral surface roles themselves changed** in the same PR — the cool-tinted 1P ramp; exact hexes in DESIGN_SYSTEM §1 and PALETTES §P9.
**Measurement tokens (DECISIONS C-14):** spacing → `LocalSpacing`, fixed sizes → `Dimensions`, corners → `MaterialTheme.shapes`, elevation → `Elevation`, text → the type scale. Every screen complies as of PR #44 — Home was migrated off its private literal block.

## 4. Not yet designed (later rounds / can be built from rules)
Advanced-AI variant of the input card (mode chip `✦ Advanced AI ▾` + `15/20 today` counter — **the mode chip has no home in the rev.3 top bar; it needs one**) · mode-picker sheet · picker search + Recent section · History & Saved · Settings + offline languages (6 row states) · paywall · camera. Build these from the rules above + their feature specs.

**Opened by the rev.3 reset (2026-07-26) — needs a design answer, not an implementation guess:**
- **Entry points lost with the drawer:** History · Saved · Help · About · Recents · the account/tier row (§2.3).
- **The mode chip** (`tt_text_mode_chip`, `cd_text_mode_chip`, `text_mode_automatic`) — no longer on Home; the engine choice must surface somewhere before the metered path ships (C-2/D-2/US-6 all assume it is visible).
- **Clear (✕) / new-translation** — removed from Home with the old top bar (`cd_text_clear`).
- **Medium / Expanded layout** for the card stack (D-5 rev.3 open item; C-13 nav column on hold).
