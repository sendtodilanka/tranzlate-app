---
status: accepted   # owner bug reports verbatim, 2026-07-30 chat (screenshots)
issue: 86
title: Adaptive polish — medium-portrait frame, IME-aware top row, minimal-IME editor
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #86

Owner-reported, reproduced on the **Resizable emulator** (owner-mandated tool;
`adb emu resize-display 0|1|2` = phone/unfolded/tablet):

1. **Medium-portrait frame**: top bars spanned full width while content centred
   at 600dp — Home's TopAppBar and the composer's top row join the SAME centred
   cap; Settings content gains the cap it never had. Every screen swept.
2. **Composer + IME (phone landscape)**: ~0dp typing space — while
   `splitResultOnly && imeVisible` the edit body drops to field + action only
   (label/counter/✕ return with the keyboard's dismissal) and the card's top
   padding tightens.
3. **No back after keyboard dismissal**: the hidden top row was gated on
   `isEditing` alone; now `isEditing && imeVisible` — back + pills return the
   moment the keyboard drops.

## Lens round (cross-model, PR #87)
- **OPEN-1 (fixed):** the minimal-IME body dropped the counter node — the only
  visible over-limit explanation AND the carrier of the recorded TalkBack P0-3
  cap-announce. Fix: ONE `CharCounter` composable, included by all three edit
  arrangements (normal / compactLandscape / minimalIme).
- **NOTE (fixed):** `isImeVisible` read narrowed to the short-landscape shape via
  short-circuit (`splitImeVisible`) so no other shape re-subscribes to per-frame
  IME inset invalidation (the issue-56 draw-failure suspect); stale comment
  rewritten to match.
- CI spotless: value-argument comment moved above the argument (HomeScreen).
