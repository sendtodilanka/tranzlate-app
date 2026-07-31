# Plan — issue #103: error card, animated shimmer, loading-flash fix

status: accepted
(accepted basis: direct owner instruction 2026-07-31 + owner sign-off on the
rendered design ("It seems good"). Timing policy came from a compressed
cross-model debate grounded in published UX guidance.)

## 1. Error view → a real card

The old app's error view was the right SHAPE (icon + title + message + a real
button in a tonal card); its implementation was not (manual light/dark colour
swapping that M3 roles already do). Rebuilt on roles: `errorContainer` /
`onErrorContainer` for the card, `error` / `onError` for the filled action so
it keeps contrast in both themes. Optional secondary exit ("Edit text") for
failures Retry cannot fix (EDGE_CASES no-dead-end).

## 2. Shimmer reads as animated

It always was an infinite sweep, but the owner never saw it: the loading state
lasted a few frames (see 3), and the highlight was faint. Contrast raised
(`Alpha.SHIMMER_HIGHLIGHT` 0.24 → 0.38, base 0.10 → 0.12) and the cycle
tightened (1200ms → 1000ms).

## 3. Loading flash — the debate ruling

Options weighed: (A) delay-then-minimum in the VM, (B) minimum only,
(C) UI-level smoothing, (D) asymmetric floor. **Ruled: D.**

- The synchronous `Translating` stays (the composer still opens straight into
  the shimmer — no empty frame).
- A **500ms minimum visible time** applies ONLY when the outcome is not a
  success. 500ms sits in the practitioner-tested 300–600ms minimum-display
  band and under Nielsen's 1s flow-of-thought limit.
- **A success is never delayed** — a cache hit (C-8) or an offline MLKit
  answer lands instantly. Holding back an answer the user could already have
  is untruthful.
- Shape: the floor timer runs ALONGSIDE the work inside `translateJob`
  (`launch { delay(FLOOR) }` + `join()` / `cancel()`), so it is real elapsed
  time in production and virtual time under test, and clear/retry cancels it
  with the work. No clock injection, no skew.
- a11y: the ≥500ms gap between the polite "translating" and the assertive
  failure announcement satisfies WCAG 2.2 SC 4.1.3 status-message pacing.

## 4. One shell for every card (owner follow-up)

Radius, interior padding, content gaps and type are now identical across the
result, loading and error cards — only the result's auto-sized body text
differs (owner's stated exception): extraLarge radius · 16dp interior on all
sides · 8dp between label/body/actions · `labelMedium` label line ·
`bodyLarge` body. The gap between the source card and whatever sits under it
went 16dp → **24dp** (`ResultCardGap`).

## 5. Landscape parity (owner follow-up)

The split read face gated on `Result || Translating`, so a failure fell to the
single-column path and rendered INSIDE the source card while a result rendered
beside it. Now every non-Idle outcome takes the right pane, and the landscape
error uses the same `ErrorCard` as portrait.

## Verification

Device (fake variant, dark theme): portrait error card, landscape error in the
right pane, shimmer mid-sweep — screenshots in the owner artifact. Unit tests
pin the floor (instant failure → shimmer at 400ms, error at 550ms; 900ms
failure → no extra delay; success → immediate). Previews per rule 7.
