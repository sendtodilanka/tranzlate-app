# Plan — issue #88: M3 breakpoint margins replace the 600dp centred cap

status: accepted
(accepted basis: direct owner instruction 2026-07-30 — "reduce the side space and
show it like phone portrait with the standard margin"; owner asked for the M3
standard to be looked up and applied.)

## The verified standard (m3.material.io/foundations/layout/breakpoints)

| breakpoint | widths | rule |
|---|---|---|
| Compact | <600dp | margins **16dp** |
| Medium | 600–839dp (tablet portrait, unfolded foldable portrait) | **single pane recommended; margins 24dp** |
| Expanded | 840–1199dp | 1–2 panes; margins **24dp**; spacer 24dp |

M3 prescribes MARGINS for the pane, not a max-width cap. The related
readability rule ("keep text 40–60 characters per line") is about type styles,
not about boxing the whole pane. PR #87's 600dp cap anchored on the issue-56
token without re-deriving against the spec — the owner caught it.

## Change

One canonical margin reader in `:core:ui` (C-13 spirit — screens never pick
numbers themselves):

- `adaptiveScreenMargin()` → 16dp compact / 24dp medium+ (tokens
  `Dimensions.screenMarginCompact/screenMarginMedium`).
- `adaptiveMarginShim()` → `margin − 16dp` (0/8dp): for screens whose rows
  already carry the compact 16dp-based insets, the shim shifts the WHOLE sheet
  to the 24dp base on medium+ without rewriting every row (relative alignment
  preserved).

Per screen:

- **Home** — drop the `contentMaxWidth` cap trio (bar / language row / content
  column). Bar gets the shim (its own 16dp title inset lands the title exactly
  on the margin); language row + content column use the margin directly.
  (Home's Scaffold path only serves compact + medium portrait — expanded
  shapes early-return to the two-pane branch.)
- **Composer 5a** — `topRowCap` → shim, `cardWidth` cap → gone, card side
  padding → margin. Gated to `mediumWidth && !expandedWidth` exactly like the
  caps were: the landscape shapes were device-tuned in #56 and stay untouched.
- **Settings / History / Offline languages** — cap columns → `fillMaxWidth` +
  shim (their rows are 16dp-based).
- **Paywall** — 560dp cap → gone; its existing lg24 side padding already equals
  the medium margin.
- **Kept:** the 5a landscape pills-cluster cap (`constrainPills`,
  `contentMaxWidthMedium`) — owner-approved in #56; a control cluster is not
  pane content. Token doc rewritten to say only that.
- **Deleted:** `Dimensions.contentMaxWidth` (480dp, zero callers) +
  the per-screen `CONTENT_MAX_WIDTH` consts. DESIGN_SYSTEM.md token table
  updated.

## Follow-ups recorded (not this PR)

- Landscape/two-pane INTERNAL margins are 16dp from the #56 device-tuning;
  M3 expanded says 24dp. Align in an adaptive pass with fresh device frames.
- 40–60 cpl: full-width result text on 800dp may exceed 60cpl for long
  translations; revisit type ramp only if the owner flags readability.

## Verification

Resizable emulator (owner mandate): phone portrait (unchanged 16dp), tablet
portrait + unfolded portrait (full width, 24dp margins, bar/content aligned),
sanity on phone landscape (untouched shapes). Screenshot set for the owner
artifact. Full suite + `spotlessCheck detekt --rerun-tasks` + both APKs.

## Lens round (cross-model, PR #89)
- **OPEN-1 (fixed):** the 560dp paywall cap removal over-generalised to EXPANDED
  windows — three plan cards stretched across ~1232dp on tablet landscape
  (40–60cpl broken). Fix: `isExpanded` → `widthIn(max = 560.dp)` re-bound;
  compact + medium still FILL per the owner + M3.
- **Recorded (accepted for now):** Settings/History/Offline rows also span on
  expanded landscape (~1264dp). These are list rows (not card clusters); the
  owner's full-width direction stands until they flag the landscape look —
  revisit together with the two-pane internal-margin follow-up.
