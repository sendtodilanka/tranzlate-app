---
status: accepted   # owner instructions verbatim, 2026-07-30 chat
issue: 82
title: Offline manager — downloads survive navigation; trailing state control
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #82

1. **Manager-owned download scope.** `download()` currently suspends in the
   caller's scope: a nav pop cancels the coroutine mid-await and the transient
   `Downloading` can strand (no owner left to clear it; MLKit finishes underneath
   and the row lies until retry). The manager gets its own
   `SupervisorJob + Dispatchers.Default` scope: `download()` LAUNCHES internally
   and returns; `delete()` cancels the internal job (ownership rules unchanged,
   tests updated). Leaving + returning shows the live `Downloading` truthfully —
   the owner's exact scenario.
2. **Row = single trailing control** (old-app reference read, written fresh):
   name left; trailing ⬇ / circular-progress-with-stop / 🗑 / retry. The state
   sub-line goes; a11y stays on the controls (cd carries language + state).
