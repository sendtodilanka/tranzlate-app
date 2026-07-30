---
status: accepted   # owner directive 2026-07-29 ("Next karanna thiyena ewa tikath ohomama karanna")
issue: 72
title: Offline languages manager (Screen B) — the MLKit tier becomes real
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #72

Spec 02 §3/§4.3/§5.2 + D-E2 Screen B, with the VERIFIED MLKit limits honoured
(no progress %, no true cancel — indeterminate + delete-to-cancel).

- **RealOfflineModelManager** (core/translate): `getDownloadedModels` truth +
  in-memory transient overrides (Downloading/Deleting/Failed(cause)); states =
  capable-set map (capable = `TranslateLanguage.getAllLanguages()`); download via
  `RemoteModelManager.download(model, conditions)`, delete doubles as cancel.
  The pure merge (capable × downloaded × transient → map) is extracted and
  unit-tested; the MLKit calls stay thin. WorkManager resilience = recorded
  follow-up (in-process awaits this batch; sizes not exposed by the API — the
  "~30MB" hint is a static string, deviation recorded).
- **OfflineLanguagesViewModel/Screen** (feature/languagepicker): rows = catalog ∩
  capable, name + state control (⬇ / spinner+Stop / ✓+🗑 / retry); empty/edge copy
  per EDGE_CASES; strings ×3. Nav entry swaps the placeholder.
- androidTestProd module gains the missing OfflineModelManager provider (graph
  parity with prod once the screen injects it).
- **Device acceptance:** download en+fr on the fold → airplane mode → translate →
  `OFFLINE_MLKIT` row in history (the rebuild's first OFFLINE translation), then
  N4's non-EN observation recorded honestly.
