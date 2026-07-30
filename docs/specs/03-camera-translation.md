# Camera Translation — DESIGN NOTE (DRAFT)

> **status: DRAFT — awaiting owner review (issue #78).** No code until this is
> `accepted` (CLAUDE.md: buildable = spec + foundations). Everything below is
> proposed, grounded in the D-0 north star (behave like Google Translate) and
> the shipped foundations; ප්‍රශ්න §7 එකේ owner-decisions විදිහට flag කරලා තියෙනවා.

## 1. The job

Signs, menus, labels — point the camera, read the translation. MVP subset item
(BUILD_ROADMAP §3). The screen ASKS the existing brains: recognized text goes
through `TranslateTextUseCase` (cache-first → waterfall → quota — nothing new).

## 2. Proposed UX (GT-shaped, freeze-first MVP)

```
[Camera viewfinder, full screen]
  top: ✕ close · flash toggle
  bottom: language pair chips (source=Detect default · target) — the SAME
          picker screens the text vertical uses
  center-bottom: ⬤ shutter ("Translate")
tap shutter → FREEZE the frame → MLKit Text Recognition v2 on-device
  → recognized blocks highlighted → full text translated as ONE ask
  → result sheet over the frozen frame (reuses the tonal result card idiom:
     translated text · copy · speak · star — the composer's own actions)
  → "Scan again" returns to live viewfinder
```

- **Freeze-first, not live AR overlay** for MVP: live word-replacement overlay
  (GT's fanciest mode) is a large rendering job with modest MVP value; the
  freeze flow covers the sign/menu job end-to-end. Live mode = v2 candidate.
- Recognized text lands in HISTORY exactly like typed text (same use case), so
  Recents/History/Saved work unchanged.
- Offline: recognition is on-device; translation follows the normal waterfall —
  with models downloaded the whole flow works in airplane mode.

## 3. Technical shape

- **CameraX** (`camera-camera2`, `camera-lifecycle`, `camera-view`) for the
  viewfinder + capture; **MLKit Text Recognition v2** (`text-recognition`
  latin + per-script packs as a follow-up decision, §7-Q3).
- New `:feature:camera` real vertical (module exists as placeholder).
  Recognition wrapper lives in the Translation brain's module boundary
  (`core/translate` — it is language work, same as Language ID).
- `TranslationOutcome`/trace/quota untouched — camera is a new INPUT, not a new
  engine (APP_STRUCTURE: screens ask, brains work).
- Permission: `CAMERA` runtime permission with the EDGE_CASES no-dead-end flow
  (denied → explainer + Settings deep-link; never a blank screen).

## 4. Availability + outcomes (EDGE_CASES discipline)

| Ask | Can I start? | What if it fails? |
|---|---|---|
| Open camera | permission granted? device has camera? | denied → explainer card + grant CTA / permanently denied → Settings deep-link |
| Recognize | frame captured | no text found → "No text found — get closer or steady the shot" + rescan |
| Translate | (use case's own gates) | the SAME error/limit faces the composer ships — trace-driven |

## 5. Tags + strings (skeleton — full C-3 table on acceptance)

`tt_camera_view` · `tt_camera_shutter` · `tt_camera_close` · `tt_camera_flash` ·
`tt_camera_rescan` · `tt_camera_result_sheet` · strings ×3 per C-3 with the
catalogue updated in the same PR.

## 6. Testing

Recognition wrapper behind a seam (fake returns golden blocks — same pattern as
`ModelStore`); VM state machine unit-tested; recognition itself device-verified
(emulator virtual scene has printed text). Fake variant: a golden image → fixed
recognized text, so Maestro/Compose tests stay hermetic.

## 7. Owner decisions needed (answer to accept)

- **Q1 — Freeze-first MVP ද?** (live AR overlay v2 — my recommendation: yes, freeze-first)
- **Q2 — result presentation**: bottom sheet over the frozen frame (proposed) vs
  navigate to the composer's read face?
- **Q3 — non-Latin scripts** (Chinese/Devanagari/Japanese/Korean packs add APK
  size): MVP = Latin only + "script not yet supported" guidance, packs later?
- **Q4 — gallery import** (translate a photo from storage): MVP or v2?
