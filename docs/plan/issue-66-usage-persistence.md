---
status: accepted   # owner directive 2026-07-29 ("…yourself karala iwara karala thiyanna"); closes the recorded PR-59 in-process-counters TODO
issue: 66
title: Usage counters survive process death
date: 2026-07-29
author: Claude (Opus 5)
---

# Plan — issue #66

RealUsagePolicy's counters die with the process (PR-59 recorded TODO): kill+reopen
refills the FREE AI pool — a real metering hole now that the GCT tail spends money.

- `UsageDataSource` gains `usage.pro_ai_count` (DATA_MODEL rev: the fair-use pool is
  new in D-2 rev.2) and ONE atomic `writeUsage(free, pro, dayEpoch)` edit — a torn
  count/epoch pair must be impossible (same rule as the language-pair swap write).
- `core/usage` gets a `UsagePersistence` seam (load/save of the three facts);
  `DataStoreUsagePersistence` adapts the data source; binding lives in the prod
  TranslateModule (plan §6.1 — no Hilt in brains; the fake variant never constructs
  RealUsagePolicy so it needs no binding).
- `RealUsagePolicy`: lazy `ensureLoaded()` under the SAME mutex (no new races), and a
  `save` at the end of every mutation block (spend, refund, rollover-only). Load
  failure falls back to a fresh day (fail-open once, documented).
- Tests (in-memory fake persistence): counts survive a policy "restart" · rollover
  persists · refund persists · load happens once · every mutation saves · the 40-way
  race still admits exactly cap · existing 5 behaviours green.

High-risk (usage/limits) → cross-model lens per Rule 5.
