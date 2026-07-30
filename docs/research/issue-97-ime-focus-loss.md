# Research — issue #97: landscape keyboard shows-then-hides (owner video, frame-by-frame)

Read-only record (Rule 4). Source: owner's real-device screen recording
(Screen_recording_20260730_234459.webm, 15.4s, 2400×1080@24fps), analysed
frame-by-frame with OpenCV (no ffmpeg on this machine; cv2 5.0 via pip;
bottom-45% luminance timeline located the keyboard events).

## Evidence

Six keyboard flashes in 15s (luminance dips at t = 1.79 · 3.88 · 6.71 ·
7.83 · 9.17 · 13.25 — the user retried repeatedly). The first, framed:

- **t≈1.75 (f0042):** composer entry morph mid-flight, cursor blinking in
  the field, full edit chrome.
- **t≈1.88 (f0045):** keyboard fully visible AND the #87 minimal-IME body
  already swapped in — chrome gone, counter+mic in the top-right minimal
  Row arrangement. (The swap happened DURING the IME slide-in.)
- **t≈2.10 (f0051):** keyboard gone; full chrome back (label/Paste/counter).

## Mechanism (code trace, confirmed against ComposerScreen.kt)

1. Field focuses on entry → IME requested → IME animates in.
2. `WindowInsets.isImeVisible` flips true mid-animation → `splitImeVisible`
   → `minimalIme = true`.
3. `ComposerEditBody` EARLY-RETURNS a different tree: the minimal branch's
   `ComposerField` is a DIFFERENT call site than the normal branch's — the
   focused node is disposed.
4. Compose clears focus on disposal → the InputConnection drops → **the IME
   dismisses itself**.
5. `isImeVisible` flips false → the normal branch swaps back, unfocused.
6. Every new tap repeats the loop. The keyboard can never stay up in
   `splitResultOnly`.

## What this retro-explains (previously misattributed)

- The "resizable-emulator landscape IME quirk" (#86: ImeTracker
  `onCancelled at PHASE_CLIENT_ON_CONTROLS_CHANGED`, `mInputShown=false`) —
  the SAME loop, cancelled a phase earlier. It was never the emulator: the
  emulator was faithfully reproducing OUR bug, and the "quirk" note shipped
  the bug past #87's verification. Lesson recorded: when a platform surface
  refuses an interaction only under one of our layout branches, suspect the
  branch before the platform.
- #92's "`input text` destabilises a freshly-opened composer" — same family
  (IME show request → branch swap → focus/state churn).

## Falsification for the fix

Whichever design lands: on the resizable AVD, phone-landscape composer tap
must show the IME AND KEEP IT UP (the old "quirk" disappearing is the
proof), typing must work, and phone-portrait + keyboard-down-chrome-return
(#86/#87 behaviours) must be unchanged.
