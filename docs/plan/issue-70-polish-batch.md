---
status: accepted   # owner directive 2026-07-29 ("…yourself karala iwara karala thiyanna")
issue: 70
title: Polish batch — paste/swap dead-ends, at-limit announce, liveRegion sweep, C-3 parity
date: 2026-07-29
author: Claude (Opus 5)
---

# Plan — issue #70

The recorded small-fix backlog, closed in one batch:

1. **Paste** — empty clipboard guided ("clipboard is empty") instead of silent nothing.
2. **Swap with Detect** — never writes "auto" into TARGET: resolves through the shown
   result's `resolvedSourceLang`, else returns false and the UI guides ("translate once
   first"). Applies on Home AND the composer. Three VM tests pin it.
3. **At-limit announce (recorded TalkBack P0-3)** — the counter's description flips to
   "N-character limit reached" and gains a POLITE live region only at the cap.
4. **liveRegion sweep (C-4 promises)** — result text polite (both faces), error and
   limit surfaces assertive (all faces; the sheet already had it).
5. **C-3 parity** — feature/text fil+pt-rBR were 19/91 keys ("catalogue-verbatim only");
   full files now (verbatim 19 kept; 72 authored in-issue, flagged for native review —
   DoD gate 12), the `MissingTranslation` suppression REMOVED. settings/languagepicker/
   camera gain their missing locale dirs entirely.
