---
status: accepted   # owner approved the v3 mockups: "Approved. Let's test with emulator" (2026-07-29)
issue: 56
title: Adaptive Home + 5a — landscape, tablet, foldable (D-5 rev.3 open item closed)
date: 2026-07-29
author: Claude (Opus 5) · design artifact = claude.ai/code/artifact/d88cb851… (7 frames, v3)
---

# Plan — issue #56

> D-5 rev.3 shipped phone-portrait-first and left wide-window IA explicitly open. The owner approved seven mockups (phone landscape ×3 · tablet ×2 · foldable ×2) built from the shipped design's own tokens. This plan implements them.

## 1. The layout rule (from the approved frames)

| Window | Home | 5a |
|---|---|---|
| **COMPACT width** (phone portrait) | unchanged — the shipped card stack | unchanged — face-switching composer |
| **MEDIUM width** (tablet portrait ~600–839dp) | same stack, content max-width centered (C-13 single-column rule) | same composer, card max-width centered |
| **EXPANDED width** (phone landscape · tablet landscape · foldable unfolded) | **two-pane**: left = translate zone (bar + pills + preview card), right = Tools stack; 24dp edge margins, panes fill (owner correction v2) | see below |
| EXPANDED + **height COMPACT** (phone landscape) | as above | face-switching stays: edit = full-width field (Compose has no IME extract mode — verified); **result face = split** source card \| tonal card |
| EXPANDED + height ≥ MEDIUM (tablet/foldable) | as above | **permanent two-pane**: left editable input pane (counter + mic⇄Translate), right result pane (shimmer/result/error; label-only when idle) — spec-01's ListDetail intent |
| **Separating vertical hinge** (foldable book posture) | split snaps to 50/50 with a hinge gutter — content never under the hinge | same |

**Result text auto-sizes** (owner Q1 decision, v3): the tonal card stays full-height; short results render at display size, long results at body size — GT's own behaviour (D-0). Portrait phone keeps the shipped fixed size.

**One approved-mockup deviation, recorded:** frame 2 drew 40dp landscape pills; implementation keeps **48dp** — `Dimensions.touchTargetMin` is an a11y floor and C-14 says the token wins. Visual difference is 8dp of pill height only.

## 2. Implementation shape

- `currentWindowAdaptiveInfo()` (material3-adaptive 1.2.0, already catalog-pinned) read per screen — width/height classes + posture (hinge list). No new state holders; pure layout branching.
- `HomeScreen`: extract the existing sections (bar, pills, preview card, tools stack) into internal composables; `HomeContent` branches COMPACT/MEDIUM/EXPANDED. Zero visual change at COMPACT.
- `ComposerScreen`: extract edit-face internals; add the two-pane arrangement; `isEditing` becomes irrelevant on permanent-two-pane (both panes live; Translate never switches face there). Requirements A–H semantics unchanged (back still clears; picker round trip still preserves).
- Foldable: `windowPosture.separatingVerticalHingeBounds` non-empty → 50/50 + gutter.

## 3. Acceptance (owner: "test with emulator")

Per form factor, on device: Home renders the approved arrangement · 5a edit → Translate → result matches the frame · back/clear semantics unchanged · portrait phone byte-identical behaviour. Emulators: `Tranzlate_Play` rotated (landscape) · new `Tranzlate_Fold` (pixel_fold) · new `Tranzlate_Tablet` (pixel_tablet), all on the android-36.1 image, booted one at a time (memory rule). Screenshots delivered to the owner via the review artifact.

Gates: full unit suite · fake+prod compile · detekt/spotless · CI. (Compose previews for the new arrangements ride along.)
