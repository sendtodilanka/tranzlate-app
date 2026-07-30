# Plan — issue #90: download lifecycle v1 (debate-ruled shape)

status: accepted
(accepted basis: owner standing rule 2026-07-30 — mandatory pre-implementation
design debate, implement per its outcome; full autonomy while away. Debate ran
2026-07-30: 3 differently-primed designers → adversarial judge (cross-model,
Opus) → my verifier pass confirmed all five of the judge's load-bearing code
claims against source.)

## Research base

docs/research/issue-90-offline-download-lifecycle.md — device-verified: the
byte transfer lives in the SYSTEM DownloadManager and survives our process
death; MLKit auto-finalizes lazily on next process start + touch; today's
`DownloadConditions.Builder().build()` sets no wifi requirement; cold-started
rows can only read NotDownloaded/Downloaded (transient is in-memory) so no
post-death ghost exists.

## The ruling (implementation contract)

**"Gate metered downloads in our own connectivity check with a per-tap consent
dialog plus a settings override; ship with no WorkManager and no
requireWifi(); only the fail-while-dead experiment can add a durable worker."**

### Ships in v1

1. **Metered gate, in-app (NOT requireWifi):** before starting a download the
   ViewModel reads a metered snapshot. Unmetered → download. Metered +
   "allow mobile data" OFF → ONE consent dialog: "Download over mobile data?
   (~30 MB)" — [Download once] proceeds this download only; [Wait for Wi-Fi]
   leaves the row NotDownloaded (no spinner, no dead end). Metered + pref ON →
   download without asking. Rationale: a metered download is CONSENT, not an
   availability block; and `requireWifi()` mid-download behaviour is untested
   (silent-hang risk) — our own gate is deterministic.
2. **Settings override:** "Downloads" section, "Always allow mobile data"
   toggle (default OFF = wifi-only default, per MLKit docs' recommendation).
   Persisted at `prefs.allow_mobile_data`; the DEFAULT comes from
   `AppConfig.defaultAllowMobileData` (white-label flavor value).
3. **Storage pre-flight:** a `StorageProbe` seam (StatFs over the models dir)
   checked in the manager before enqueue; free space below the budget →
   `Failed(STORAGE)` — store never called, no partial download. Budget:
   150 MB (observed de↔en pair = 45.7 MB on disk; ×3 headroom, documented).
4. **Failure guidance on the row (EDGE_CASES no-dead-end):** a Failed row
   shows one small cause line under the name (STORAGE: free-up-space text;
   NETWORK: check-connection text; UNKNOWN: generic) — without it, ↻ on a
   full disk re-fails with no explanation, a dead end. This is an ERROR line,
   not the always-on sub-line the owner removed in #82.
5. **Unchanged:** manager-owned scope + job-ownership delete-to-cancel (#83),
   `activeDownloads` same-tag dedupe, Failed→↻, lazy auto-finalize +
   screen-entry refresh, the 5-state trailing control.

### Explicit rejects (judge — do NOT build)

WorkManager in v1 · `requireWifi()` in v1 · any progress/ongoing notification
(no real % exists — verified) · eager finalization service · cross-process
spinner restore · routing metered consent through the unused
`Availability`/`BlockedReason` types · a persisted "requested" marker unless
X1 proves a dead-end.

### Contingent experiments (run on-device after implementation)

- **X2 (v1 gate):** metered device (wifi off, data on): tap ⬇ → dialog; once
  → downloads; wait → row stays ⬇; toggle ON → no dialog.
- **X1 (the ONLY worker-justifier):** start download (wifi), force-stop the
  app, break the network while dead, reopen. Row NotDownloaded/re-tappable
  or self-healed → NO worker, record and close. A stuck ghost blocking
  re-tap → build the pre-specified minimum (persisted marker +
  retry-on-next-open); WorkManager only if that cannot clear it.
- **X3 (probe):** double-download dedupe via logcat DownloadManager ids.
- **X4:** storage pre-flight is unit-tested; simulating a full disk on the
  emulator is not attempted (honest gap, StatFs path asserted in tests).
- **X6 (v2 only):** requireWifi mid-download drop probe — future tail
  enforcement question, not v1.

## Touch list

- `core/common/ConnectivityMonitor` + real impl: add `isMetered(): Boolean`
  snapshot; `core/testing` fake gains a `metered` var.
- `core/datastore/TranzlatePreferencesDataSource`: `prefs.allow_mobile_data`
  (accessor takes the config default). DATA_MODEL.md prefs table row.
- `core/config/AppConfig`: `defaultAllowMobileData: Boolean`; app
  `AppConfigModule` + flavor BuildConfig field (tranzlate = false).
- `core/translate/RealOfflineModelManager`: `StorageProbe` seam + pre-flight.
- `feature/languagepicker`: VM gate (pendingConsent StateFlow +
  downloadAnyway/waitForWifi) + dialog + Failed cause line; strings ×3.
- `feature/settings`: Downloads section + toggle; strings ×3.
- Tests: VM gate matrix, manager storage pre-flight, prefs round-trip,
  settings toggle; C-3 parity rides the MissingTranslation lint gate.

## Risk register (judge)

- Wifi→mobile handoff can spend ≤~30 MB on mobile (no requireWifi tail
  enforcement): bounded, X6 gates future adoption.
- X1 may reveal a real dead-end: the minimal-marker path is pre-specified;
  worker stays last resort.
- `isActiveNetworkMetered` may misread VPN/hotspot: the dialog fires on any
  metered read and the toggle is the explicit override.

## Outcome (2026-07-30)

X2 device-proven (dialog both paths + cellular download); **X1 ran and ruled
OUT the worker** — kill + offline-while-dead leaves a re-tappable ⬇ row, no
ghost (research doc, post-implementation section). v1 shipped exactly the
ruling: gate + dialog + toggle + storage pre-flight + row error lines; no
WorkManager, no requireWifi, no notification.
