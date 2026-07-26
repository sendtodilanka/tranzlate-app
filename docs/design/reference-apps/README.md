# Reference apps — taste anchors (owner recordings, 2026-07-22)

The owner's chosen UI/UX references for the Tranzlate identity (design round 5). NOT to be cloned —
the *energy* and specific patterns below are what we adopt; the identity must remain Tranzlate's own.

## Gemini app (gemini-home-dark.jpg · gemini-home-light-gradient.jpg · gemini-drawer-light.jpg)
- **Ambient gradient background** — light: white → pale blue wash toward the bottom; dark: near-black → deep tinted glow. Soft, atmospheric, no hard edges. Owner: "colors/gradient supiri".
- Sparkle/star mark above a large light-weight centered greeting; dotted-ring circular icon buttons ("dot dot pattern").
- Top bar: ☰ hamburger (left, circular tonal) · model chip centered ("Pro Extended ▾") · new/edit action right.
- Floating pill composer at the bottom: `+` · placeholder · mic · filled action circle.
- Model picker = floating rounded menu with check-marked current model + a nested option row.
- Drawer: title + ✕, tonal "New chat" pill at top, icon list, grouped sections ("Notebooks", "Recents"), account row pinned at the bottom (avatar, name, tier).

## Claude app (claude-home-dark-serif.jpg · claude-drawer-dark.jpg)
- **Serif personality greeting** ("Afternoon, Dilanka") with a small brand starburst above — warm, editorial, instantly distinctive. Owner-approved personality direction.
- Composer as a rounded CARD (not a pill): text row on top, controls row below (`+`, model chip "Sonnet 5 Max", mic, filled send circle) — matches Tranzlate's own bottom-cluster heritage.
- **Side drawer**: serif wordmark, outline-icon section list (Chats/Projects/Artifacts/Code/…), "Starred" + "Recents" groups, floating "+ New chat" white pill, account avatar bottom-left.

## What Tranzlate adopts (round-5 locked — with later corrections)
Gemini's hamburger/mode-chip top bar · Claude's drawer structure + card composer · explicit Translate button for all engines + separate result screen (issue #9 amends C-2).

> **⚠ Superseded by issue #15 (2026-07-22):** the **ambient gradient, dotted texture, serif greeting and teal→blue P8 hues are retired.** The app is now **flat + stock Material 3 + GT-Blue** (PALETTES **P9**), one neutral sans (no serif). The energy anchors above stay; the specific gradient/serif/teal cues do not.
> **Added by issue #26 (2026-07-24):** a persistent bottom nav (Home / Chat / Camera) on phone — see the sibling-translator anchor below.
> **⚠ Superseded by issue #42 / PR #43 (2026-07-26):** that bottom nav is **removed** (D-5 rev.3). The taste anchors on this page are now historical context; **the live Home contract is the owner's own Claude Design export** — see below.

## Current Home contract — Claude Design "Offline Translator M3" (owner export, 2026-07-26)
The owner designed Home directly in Claude Design and exported it; that export, not a third-party app, is the anchor for the shipped card stack: pinned top bar (Translate · Pro chip · Settings) + pinned language pill row → input card whose mic becomes Translate → "Tools" 2×2 tonal grid → Download-languages row → Phrasebook/Quotes mini cards → Natural-phrasing banner. **Roboto Flex** and **Material Symbols Rounded** come from the same export.
⚠ The export file itself is **not checked into this repo** (`docs/design/claude-design/` still holds the 2026-07-22 round-5 cards, and `HomeScreen.kt` cites a `docs/design/OFFLINE_TRANSLATOR_M3.md` that does not exist). Until it lands, **`docs/design/UI_SPEC.md` §2.1 + the shipped `HomeScreen.kt` are the contract.**

## Sibling translator — Lingo French (structural anchor, issue #26) — ❌ SUPERSEDED 2026-07-26
~~The owner's sibling Play translator **Lingo French** uses a **persistent bottom navigation bar** — the structural anchor for Tranzlate's **D-5 rev.2** phone IA (Home / Chat / Camera on a bottom `NavigationBar`). We adopt the bottom-nav *structure* only, not its branding. A deliberate deviation from Google Translate's no-phone-bottom-bar hub.~~
**The bottom-nav borrowing is reversed** (D-5 rev.3). What survives from the FT look is the *idea* that a translator home can be a stack of purposeful cards rather than an empty canvas — the design export answers that with its own composition. Kept here as the paper trail for why the bar existed for two days.
