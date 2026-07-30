---
status: accepted   # owner instructions verbatim, 2026-07-30 chat
issue: 84
title: Result actions — C-7 reverse + TTS speak with play/stop state
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #84

1. **Reverse (C-7, GT/FT behaviour observed — FT builds
   `TranslateRequest(text=translatedText, source=oldTarget, target=oldSource)`):**
   the RESULT becomes the input, the pair swaps (auto-detect reverses through the
   RESOLVED source), languages persist, re-translates. The pre-existing
   auto-unsafe `onReverse` (would write "auto" into TARGET) is replaced. Golden
   G12 row added per the §1.2 add-a-row rule.
2. **TTS speak:** `ResultSpeaker` seam (Android `TextToSpeech` adapter; old app's
   SpeechHelper studied as behaviour reference, written fresh) — play ⇄ stop icon
   from a `speaking` StateFlow, stop/replay anytime, dismissal stops audio,
   engine/language-unavailable guides.
