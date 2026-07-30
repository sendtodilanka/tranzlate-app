# Plan — issue #92: landscape/two-pane M3 margin alignment (debate-ruled)

status: accepted
(accepted basis: owner standing rule — debate before implementation; this IS
the #88-plan's scheduled "adaptive pass" follow-up, and the owner's #88
correction was itself a demand to apply the M3 standard. Debate 2026-07-30:
Designer P (M3 purist) vs Designer C (device empiricist) → cross-model Opus
judge; judge verified every load-bearing file:line before ruling.)

## Corrected fact

The #88 follow-up note ("two-pane INTERNAL margins stay 16dp") was HALF-WRONG:
Home's two-pane branch is already 24dp outer + 24dp spacer (M3-compliant).
The 16dp base survives only in the composer's landscape shapes.

## The ruling

**Composer margins align to M3 24dp NOW — but only as the PAIRED edit set;
the panes-only version is rejected because it breaks the #56 alignment line
(back-icon visual edge 4+12=16 vs pane edge 24 = an 8dp misalign).**

Edits (all in ComposerScreen.kt):
- (a) permanentTwoPane pane-row outer: `md16` → `adaptiveScreenMargin()`.
- (b) splitResultOnly read-face pane-row outer: `md16` → `adaptiveScreenMargin()`.
- (c) splitResultOnly pane spacer: `md16` → `lg24`.
- (d) the top row's shim goes UNCONDITIONAL (`adaptiveMarginShim()` = 0dp
  compact / 8dp otherwise) so back + pills land on the pane edge in every
  shape — margin and shim read the same token, so the alignment holds by
  construction at every width.
- permanentTwoPane spacer is already `lg24` — untouched.
- Docs: fix the #88 follow-up wording (Home was never 16).

## Kept (judge's reject list — do NOT touch)

Pane weights 2:3 (owner-chosen #56; M3 50/50 is a recommendation) · pills
cluster cap · card INTERIOR paddings (not screen margins) · phone-portrait
16dp (regression guard) · NO new Large-breakpoint token (unverified — 24
floor stands) · **expanded list-row width stays full-width in code**: the #89
lens decision ("full-width stands until the owner flags it") is a merge-gate
decision no single authority overrides — it goes to the owner as the sole
QUESTION in the report, with current-state frames + the #89 paywall
precedent as evidence.

## Verification (mandatory fresh frames)

Resizable AVD: (1) tablet/unfolded two-pane — outer 24, spacer 24, back+pills
edge == card edge; (2) phone landscape split — 24/24, IME up/down states
unregressed (#56/#86); (3) phone portrait — UNCHANGED 16 (guard). GT
reference frame (Play AVD) for the owner report. Full suite +
`spotlessCheck detekt --rerun-tasks` + both APKs + cross-model lens.

## Owner report

FYI: the docs-fact fix + the 24dp alignment (before/after frames, pure
padding, reversible). QUESTION (the only one): expanded lists at ~1264dp —
bound with the #89 expanded-only `widthIn` pattern, or keep full-width?

## Verifier extension (recorded during implementation)

The judge's edit set missed one surface its own principle covers: the
landscape EDIT-face card outer margin (the #88 `cardMargin` conditional kept
it 16dp on expanded). With (d) shifting the top row to the 24-line, the edit
face would have carried a NEW 8dp misalign — exactly the failure mode the
ruling built (d) to prevent. Fixed by the ruling's stated principle ("margin
and shim read the SAME token at every width"): `cardMargin =
adaptiveScreenMargin()` — compact 16 unchanged, medium-portrait 24 unchanged,
landscape 16→24 aligned. Device frame i92-land-edit.png proves the line.

## Verification outcome (2026-07-30, resizable AVD, prod)

- Tablet landscape two-pane composer: card edge 24dp, spacer 24dp, back
  arrow icon + pills on the card line (i92-tablet-twopane.png).
- Phone landscape: edit face aligned (i92-land-edit.png); split read face
  "Good morning" | "Bonjour" 24/24 with actions row (i92-phone-land-split.png
  — captured via portrait-translate + live `wm size` resize because this
  emulator instance rejects landscape IME AND `input text` there destabilises
  the freshly-opened composer; portrait typing + config-change restore is the
  documented workaround).
- Phone portrait guard: unchanged 16dp (i92-portrait-guard.png).
- GT reference frame: SKIPPED honestly — the Play AVD is a phone profile and
  cannot show GT's tablet-width list treatment; GT tablet behaviour stays
  "verified data නෑ" for the owner question.
