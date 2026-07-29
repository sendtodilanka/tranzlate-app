# Research — landscape 5a edit face: BasicTextField draws no text while the IME is up

> Read-only record (Rule 4). Root cause NOT yet proven — this logs the sharp repro and every
> falsified hypothesis so the fix session starts from evidence, not guesses.
> Date: 2026-07-29 · device: emulator-5554, Pixel-7-class AVD, API 36, landscape 915×412dp
> Branch: `feat/issue-56-adaptive` (WIP, un-PR'd)

## The defect

Screen 5a, **landscape**, edit face, IME visible: the input's text (and cursor) never draw.
Semantics are intact (`uiautomator` shows `text="Good morning"`, bounds ≈113px tall), the IME
is connected (suggestion strip reacts), the counter/label/Translate in the SAME card draw
normally, and `debug.layout` shows the field's inner text layout collapsed to a ~3px stripe
at the top of its box.

## The discriminator (measured, ink = dark pixels in the field zone)

| Condition | Field ink |
|---|---|
| Portrait, IME up — same build, same code path | **224** ✅ |
| Landscape, IME up — every variant below | **0** ❌ |
| **Landscape, IME dismissed (back), same edit face** | **331** ✅ |

→ Strictly `landscape ∧ IME visible`. The read face's plain `Text` (same card, landscape)
always draws — the failure is specific to the legacy `BasicTextField(value, onValueChange)`
inner text/cursor layer.

## Falsified hypotheses (each rebuilt + retested on device)

1. **Trailing `\n` scrolled the cursor to an empty line** — real for one capture (adb
   `KEYCODE_ENTER` inserts a newline; it does NOT fire `ImeAction.Send`), but text stays
   invisible after deleting it. Artifact, not cause.
2. **`weight(1f)` field collapsing** — replaced with wrap-content + `heightIn(min=56dp)`:
   still 0 ink.
3. **`hideTopRow` composition swap mid-IME-animation** — collapsed by layout
   (height-0 + `clipToBounds`, always composed) instead of `if`: still 0. (An earlier
   "ink 34" reading that implicated this was pill/counter ink caught by a too-wide scan —
   measurement error, corrected here.)
4. **Per-frame `WindowInsets.isImeVisible` read** (landscape-only via `&&` short-circuit) —
   removed entirely: still 0.
5. **sharedBounds/RemeasureToBounds entry morph** — re-entering the edit face from the read
   face (no nav transition, no morph) reproduces: still 0.
6. Tap-inside-field (forces cursor move / bring-into-view): does not recover.

## Best current theory (unproven)

Legacy CoreTextField's internal scroller: with a SHORT viewport (~40–90dp — only ever true
in landscape here) its bring-into-view/scroll offset puts the single text line fully above
the clip; IME dismissal re-lays-out and resets it. Portrait never engages the scroller
(viewport ≥300dp), which matches the portrait/landscape split exactly.

## Candidate fixes for the next session (in order)

1. **Migrate 5a's field to the state-based `BasicTextField(TextFieldState)`** (foundation
   1.7+ rewrite with a new scroll implementation) — needs a two-way sync with the
   SavedStateHandle-backed `input` flow; the likeliest real fix.
2. Minimal repro app to confirm the theory → if it holds, file upstream (Compose foundation
   1.11.4) with the repro.
3. Fallback UX if both stall: landscape edit face swaps `BasicTextField` for the plain-Text
   read look while typing happens through… (no — rejected: that's not an editor. Listed only
   to record that it was considered and dropped.)

## What already works on this branch (device-verified, matches the approved frames)

Landscape Home two-pane (frame 1) · landscape result face source|result split with auto-size
text (frame 3) · portrait unchanged (224-ink control + the full unit suite green). Tablet and
foldable AVD verification not yet run — blocked behind this defect only for the edit face.
