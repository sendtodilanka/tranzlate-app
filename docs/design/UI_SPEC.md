# UI Spec — approved design contract (2026-07-22 · §1/§3 reset by issue #15)

> Owner-approved after 7 design rounds (Claude Design project "Tranzlate UI v2"). **This file is the ground truth for implementation** — the Claude Design chat is not. Palette = [`PALETTES.md` P9 "GT Blue"](PALETTES.md); reference apps = [`reference-apps/`](reference-apps/README.md).
> Behaviour rules still come from `docs/specs/` (DECISIONS, EDGE_CASES, TEST_A11Y) — with the C-2 amendment (issue #9): **explicit Translate action for ALL engines, result on its own screen.**
> **2026-07-22 — issue #15 reset:** owner drove Google Translate v10.27 beside our build. The gradient, the dotted-ring buttons and the bespoke component set are gone; the app is **flat + stock Material 3 + GT's palette**. §1 and §3 below are rewritten; §2.3 is unchanged; the language picker (§2.6) becomes a full screen.

## 1. Global visual language

| Aspect | Rule |
|---|---|
| **Components** | **Stock Material 3 first.** If M3 ships the component, use it as-is — `IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `AssistChip`, `CenterAlignedTopAppBar`, `Card`, `TextButton`, `ListItem`, `Scaffold`. A `:core:ui` wrapper needs a rule to carry (see DESIGN_SYSTEM §9); a re-colour is not a rule. |
| Background | Flat `surface`. **No gradient anywhere** — not on pages, cards, icons or text (the ambient-wash token is deleted). |
| Elevated panels | Composer, drawer sheet, cards = a **lighter surface step** — light `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer`. **Separation by lightness**, no border and no shadow; `outlineVariant` hairlines only where no step exists. |
| Accent discipline | **Exactly one saturated element per screen** (the primary action). Everything else neutral or soft tonal. |
| **Primary action** | `FilledIconButton` — **light: `primary` fill · dark: `primaryContainer` fill** (in dark, `primary` is a near-white blue that glares on `#131314`; GT uses the deep container tone). The rule lives in `PrimaryActionButton` (`:core:ui`), once. |
| Icon buttons | Plain M3 `IconButton` — no container, no ring, no bespoke detail. 48dp target, 24dp glyph. *(Canvas quick-action circles were removed — Conversation/Camera are bottom-nav tabs now, issue #26.)* |
| Type | One neutral sans (Roboto/system class) at every level incl. greeting + result; hierarchy by **size + weight** only. Non-Latin scripts pair to Noto (Sinhala result must render in the same visual family). |
| Shape / rhythm | One radius scale (chips/buttons `full`, cards `md12`–`lg16`, composer + error card `xl28`); 8dp vertical rhythm; no decorative shadows. |

## 2. Screens

### 2.1 Home (text) — the first bottom-nav destination
- **Bottom nav:** a persistent M3 `NavigationBar` (Compact) with **Home · Chat · Camera** (Chat = conversation, a v2 coming-soon placeholder). Medium = rail, Expanded = permanent drawer — all via `NavigationSuiteScaffold` (official decision record: DECISIONS.md **D-5 rev.2**; C-13 adaptive dims). This bottom bar is a deliberate, owner-approved deviation from GT's no-phone-bottom-bar hub. **The bar shows only on the top-level tabs** — secondary/detail screens (Settings §2.6, Result §2.4, LanguagePicker, History) drop the bar/rail/drawer via `NavigationSuiteType.None` for full height.
- **Top bar:** stock `CenterAlignedTopAppBar`, transparent container over the page `surface` — ☰ hamburger (opens the secondary drawer) · centered **mode chip** `✦ Automatic ▾` (an `AssistChip`) · right = new/clear icon.
- **Canvas** (the band between top bar and composer, content **vertically centred**, re-centres when the IME opens): brand sparkle → greeting (time-aware, e.g. "Afternoon, *Dilanka*" — name in `primary`) → subtitle "What would you like to translate?". **The Conversation/Camera quick-action tiles are removed — they are bottom-nav tabs now** (issue #26); the canvas is just greeting + subtitle above the composer.
- **First run:** zero history — the canvas must feel complete (no empty-state apology). Recents live in the drawer.

### 2.2 Composer (always a CARD — owner decision A, 2026-07-22)
Rationale: our control row carries two language chips + swap + action, so it needs full width; a Gemini-style inline pill is not possible. One consistent shape avoids layout jumping.
- **Built from stock parts** (issue #15): `Surface` (panel step, `xl28`, no shadow) + `BasicTextField` + two `AssistChip`s + `IconButton` + `PrimaryActionButton`.
- **Structure:** multi-line text area on top (placeholder "Enter text") → **control row beneath, full width**: `[source chip]` `⇄` `[target chip]` + primary action at the end. **No dropdown caret on the language chips** — GT has none; the pill shape plus "Source language, …" already say tappable.
- **Swap is disabled when the source is "Detect language"** — there is nothing to move into the target slot (EDGE_CASES: say why, never act wrongly).
- **Primary action:** 🎤 mic when the field is empty → **Translate (➜)** as soon as there is text.
- **Growth:** grows with content to a max height (~40% of the viewport), then scrolls internally. Sits **directly above the IME**.
- **Char counter** `12/500` (C-5 format) in the control row / under the text area.
- **Typing behaviour:** the canvas content (greeting + subtitle) **hides as soon as the first character is typed**; it returns when the field is completely empty.

### 2.3 Navigation drawer (SECONDARY destinations only)
The drawer is secondary navigation — the primary Home/Chat/Camera switch lives in the bottom nav (§2.1). Claude-app structure: wordmark (sparkle + "Tranzlate") → sections **History · Saved · Offline languages · Settings · Help · About** (outline icons) → **RECENTS** list (source line + translation line) → account row pinned bottom (avatar, name, email, tier chip "Free"). **No "+ New translation" pill.** *(Search was never built — dropped from the drawer, issue #26.)*
**Motion:** drawer slides in from the left while the main screen is **pushed right, scaled down, corner-rounded and dimmed** — one continuous, gesture-driven motion (predictive-back friendly).

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

## 4. Not yet designed (later rounds / can be built from rules)
Advanced-AI variant of the composer (mode chip `✦ Advanced AI ▾` + `15/20 today` counter) · mode-picker sheet · picker search + Recent section · History & Saved · Settings + offline languages (6 row states) · paywall · camera. Build these from the rules above + their feature specs.
