# UI Spec — approved design contract (2026-07-22)

> Owner-approved after 7 design rounds (Claude Design project "Tranzlate UI v2"). **This file is the ground truth for implementation** — the Claude Design chat is not. Palette = [`PALETTES.md` P8](PALETTES.md); reference apps = [`reference-apps/`](reference-apps/README.md).
> Behaviour rules still come from `docs/specs/` (DECISIONS, EDGE_CASES, TEST_A11Y) — with the C-2 amendment (issue #9): **explicit Translate action for ALL engines, result on its own screen.**

## 1. Global visual language

| Aspect | Rule |
|---|---|
| Background | `surface` + **ambient wash**: a soft `primary`-tinted radial/vertical gradient strengthening toward the bottom (low opacity). Light: near-white → pale teal. Dark: `#131314` → deep teal glow. No hard edges. |
| Floating surfaces | Composer, tiles, drawer sheet, cards = a **lighter step** over the wash — light `surfaceContainerLowest` (#FFFFFF), dark `surfaceContainer`/`High`. **Separation by lightness, not outlines**; hairlines only where no step exists. |
| Accent discipline | **Exactly one saturated `primary` element per screen** (the primary action). Everything else neutral or soft tonal (`primaryContainer`-tinted). |
| Icon buttons | Circular, tonal fill, **dotted-ring detail** as our signature; 48dp target, 24dp glyph. |
| Type | One neutral sans (Roboto/system class) at every level incl. greeting + result; hierarchy by **size + weight** only. Non-Latin scripts pair to Noto (Sinhala result must render in the same visual family). |
| Shape / rhythm | One radius scale (chips/buttons `full`, cards `md12`–`lg16`, sheets `xl28`); 8dp vertical rhythm; ambient, barely-there shadows. |
| Gradient discipline | Ambient wash + at most ONE signature element. Never on dividers, borders, icons or text. |

## 2. Screens

### 2.1 Home hub
- **Top bar:** ☰ hamburger (opens drawer) · centered **mode chip** `✦ Automatic ▾` · right = new/clear icon. No bottom nav bar (hub model — official decision record: DECISIONS.md **D-5**; Medium/Expanded keep C-13 rail/drawer).
- **Canvas** (the band between top bar and composer, content **vertically centred**, re-centres when the IME opens): brand sparkle → greeting (time-aware, e.g. "Afternoon, *Dilanka*" — name in `primary`) → subtitle "What would you like to translate?" → **compact quick-action tiles** (Conversation · Camera; ≈64–72dp tall, icon chip + label + sub-label) in a wrapping grid that must scale to 5–6 tiles and still look intentional with 2.
- **First run:** zero history — the canvas must feel complete (no empty-state apology). Recents live in the drawer.

### 2.2 Composer (always a CARD — owner decision A, 2026-07-22)
Rationale: our control row carries two language chips + swap + action, so it needs full width; a Gemini-style inline pill is not possible. One consistent shape avoids layout jumping.
- **Structure:** multi-line text area on top (placeholder "Enter text") → **control row beneath, full width**: `[source chip ▾]` `⇄` `[target chip ▾]` + primary action at the end.
- **Primary action:** 🎤 mic when the field is empty → **Translate (➜)** as soon as there is text.
- **Growth:** grows with content to a max height (~40% of the viewport), then scrolls internally. Sits **directly above the IME**.
- **Char counter** `12/500` (C-5 format) in the control row / under the text area.
- **Typing behaviour:** the canvas content (greeting + tiles) **hides as soon as the first character is typed**; it returns when the field is completely empty.

### 2.3 Navigation drawer
Claude-app structure: wordmark (sparkle + "Tranzlate") → sections **Search · History · Saved · Offline languages · Settings** (outline icons) → **RECENTS** list (source line + translation line) → account row pinned bottom (avatar, name, email, tier chip "Free"). **No "+ New translation" pill.**
**Motion:** drawer slides in from the left while the main screen is **pushed right, scaled down, corner-rounded and dimmed** — one continuous, gesture-driven motion (predictive-back friendly).

### 2.4 Result screen (separate reading surface — no composer)
- Top: back · bookmark · ⋯
- Source block: `ENGLISH` label · source text · phonetic (`/ həˈloʊ /`) · speaker + copy (top-right).
- Divider (neutral hairline).
- Target block: `සිංහල` label + **engine badge** (`⏷ Offline · instant`) · **result text large in `primary`** · transliteration · actions: speaker · copy · 👍 · 👎.
- **Follow-up chips:** `✦ Formal` · `Explain` · `Examples` (the AI-enhancement slot — issue #8).
- Home → Result feels continuous (shared-element / container transform).

### 2.5 States (EDGE_CASES — every one designed, no dead ends)
Translating (shimmer in the result area) · network error + inline **Retry** · offline/engine badge · daily-limit **dismissible bottom sheet** (Upgrade / Not now, C-11) · over-char-limit inline copy · empty field (action disabled with reason).

## 3. Token mapping (implementation)
`page` = `surface` + gradient overlay · `card` = `surfaceContainerLowest` (light) / `surfaceContainer` (dark) · `chip` = `surfaceContainerHigh` tonal, selected = `primaryContainer` · `primary action` = `primary`/`onPrimary` · `divider` = `outlineVariant` · `result text` = `primary` · `secondary text` = `onSurfaceVariant`. All values from PALETTES.md **P8**.

## 4. Not yet designed (later rounds / can be built from rules)
Advanced-AI variant of the composer (mode chip `✦ Advanced AI ▾` + `15/20 today` counter) · mode-picker sheet · language picker · History & Saved · Settings + offline languages (6 row states) · paywall · camera. Build these from the rules above + their feature specs.
