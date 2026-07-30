# Research — issue #90: what actually happens to an MLKit model download when the process dies

Read-only record (Rule 4). Experiments run 2026-07-30 on the Resizable AVD
(android-36.1, prod debug build), `pm clear` before the first run.

## Question

The spec (docs/specs/02) prescribes WorkManager for offline model downloads on
the assumption that a download dies with the app process. Is that true?

## What the docs say (checked first)

- developers.google.com/ml-kit/language/translation/android: models ~30MB,
  "only download them using Wi-Fi unless the user has specified otherwise"
  (`DownloadConditions.requireWifi()` shown). **Nothing** about process
  semantics, DownloadManager, WorkManager, or kill behaviour.
- m3/Android docs: no guidance specific to MLKit model lifecycle.

So the docs cannot answer it — device experiments below.

## Experiments (falsification log)

**E1 — kill 1.5s after starting a German download, watch app storage.**
`input tap` on the ⬇ control → `am force-stop` at ~1.5s → `run-as du -sk`
polled at 0s/25s/50s. Result: app storage frozen at 276KB, `pidof` dead, no
model dir ever appeared. *Naive reading: "download died with the process."*

**E2 — same, but with logcat.** Cleared logcat, tapped ⬇, captured before the
kill: `DownloadManager: Deleting /data/data/com.android.providers.downloads/
cache/de_en.zip via provider delete` — the transfer belongs to the **system
DownloadManager** (`com.android.providers.downloads`, its own process), not to
our app. The E1 reading is falsified as stated: only *our process's view* died.

**E3 — relaunch after the E2 kill, open the Offline screen, wait ~6s.**
`no_backup/com.google.mlkit.translate.models/de_en/` materialized at
**45.7MB** (translate_deen + translate_ende + resources) and the German row
rendered **"Delete German"** (= Downloaded). No user re-tap. MLKit finalized
the completed system-side transfer on the next process start when first
touched (our screen-entry `refreshDownloaded()` — #83 — was the touch).

## Verified conclusions

1. **The byte transfer survives process death.** It runs in the system
   DownloadManager's process.
2. **Finalization is lazy but automatic**: next app start + first MLKit touch
   moves/unpacks the model and it reads as downloaded. Our existing
   screen-entry refresh surfaces the corrected state with zero new code.
3. **What process death actually costs us:**
   - the in-memory transient `Downloading` spinner (already self-corrects on
     screen entry — the row shows store truth);
   - retry if the *system-side* transfer fails while our app is dead;
   - any user-visible progress/notification while away from the app;
   - finalization latency: the model occupies provider cache until the user
     next opens the app.
4. `DownloadConditions.Builder().build()` (RealOfflineModelManager.kt:61) sets
   **no wifi requirement** today — the docs recommend wifi-only for ~30MB
   models unless the user opts out. Design input, not a bug per se.

## Disconfirmation notes

- E1's "storage frozen" was real but measured the wrong process — E2/E3
  falsified the conclusion drawn from it. Kept as a caution: measure the
  system side too.
- Not yet tested (candidate follow-ups for the chosen design): behaviour when
  the system-side transfer FAILS while our app is dead (does MLKit re-enqueue
  on next touch, or surface an error?); airplane-mode mid-download with the
  app alive (our Failed → ↻ path, built in #83, covers the alive case).

## Consequence

"WorkManager owns the download" is NOT justified by continuation (the
platform already continues). Any WorkManager role must be argued on the
remaining gaps (3 above). → design debate per the owner's standing rule.

## Post-implementation experiment results (2026-07-30, resizable AVD, prod)

- **X2 (metered consent) — PASS, device-proven:** cellular-only active network →
  tapping ⬇ raised "Download French over mobile data?" with [Download once] /
  [Wait for Wi-Fi]. Wait → dialog closed, row stayed NotDownloaded (re-tappable,
  no spinner). Once → the download proceeded over cellular; the standing pref
  stayed false (the NEXT metered tap asks again — unit-pinned).
- **X1 (fail-while-dead — the worker decider) — NO WORKER, per the decision
  rule:** Spanish download started on wifi → app force-stopped at ~1s → wifi AND
  data disabled while dead → 30s wait → reopened still offline. Row read
  **NotDownloaded ⬇, re-tappable — no stuck ghost, no dead end.** The ruling's
  rule ("re-tappable → NO worker") closes the WorkManager question for v1;
  the persisted-marker fallback stays unbuilt.
- **Settings toggle** — "Downloads / Always allow mobile data" section renders
  and persists (unit-pinned round-trip).
- X3 (dedupe probe) not run — the same-tag guard is unit-pinned; logcat id
  comparison parked with X6 (requireWifi probe) for a future pass.
