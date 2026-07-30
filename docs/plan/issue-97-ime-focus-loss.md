# Plan — issue #97: landscape keyboard show-then-hide (debate-ruled fix)

status: accepted
(accepted basis: owner bug report — real-device video, frame-by-frame
analysed; owner standing rules: debate before implementation. Compressed
cross-model debate: movableContentOf vs single-call-site restructure vs
drop-IME-gating → ruling = single call site.)

## Root cause (research: docs/research/issue-97-ime-focus-loss.md)

`ComposerEditBody` rendered `ComposerField` at TWO source positions and the
`minimalIme` early-return switched between them WHILE the IME animated in
(`isImeVisible` flips mid-slide) → the focused node was disposed → Compose
cleared focus → the InputConnection dropped → the IME dismissed itself →
loop. The keyboard could never stay up in `splitResultOnly`. This same loop
WAS the "resizable-emulator landscape IME quirk" (#86) and #92's
`input text` destabilisation — misattributed to the emulator twice.

## The ruling — single call site (movableContentOf rejected: 4+ volatile
params invite stale-capture bugs; drop-IME-gating rejected: re-opens #86)

The field renders at exactly ONE source position inside a STABLE parent Row;
`minimalIme` / `compactLandscape` toggle only sibling chrome and modifier
VALUES (never the field's group identity):
top chrome row (compact ∧ ¬minimal) → stable Row [field + (minimal: counter
+ action)] → Paste (¬minimal ∧ empty) → bottom row (¬compact ∧ ¬minimal).

## Verification (resizable AVD, prod — all green 2026-07-31)

- Phone landscape (914×411dp): tap → **IME SHOWS AND STAYS** (mInputShown
  true at +4s and +7s — first time ever on this rig; the "quirk" was ours),
  minimal body live (i97-minimal-ime-stays.png), typing works while up
  ("Good morning" — also kills the #92 symptom), keyboard-down returns
  back + pills with the draft kept (#86 intact).
- Portrait: normal chrome, typed "Portrait ok", counter 11/500 ✓ unchanged.
- Tablet two-pane: renders unchanged (incidental capture during setup).
- Full suite + clean-run style + prod APK ✓.

## Lessons recorded

When a platform surface refuses an interaction only under one of our layout
branches, suspect the branch before the platform. An "emulator quirk" note
must carry a disconfirmation experiment before it ships past verification.
