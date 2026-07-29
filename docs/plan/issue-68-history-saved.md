---
status: accepted   # owner directive 2026-07-29 ("…yourself karala iwara karala thiyanna")
issue: 68
title: History + Saved vertical — real list, star-to-save, tap-to-reopen
date: 2026-07-29
author: Claude (Opus 5)
---

# Plan — issue #68

The Room side has been ready since #54 (`history()`/`favourites()`/`setFavourite`,
favourite+created_at indices; drawer Recents already reads it). This lands the screen
and the write path.

- **HistoryViewModel**: the two flows → UI state; `toggleFavourite`. **HistoryScreen**:
  back row + History/Saved `SecondaryTabRow` + LazyColumn rows (pair label · source
  1-line · target 2-line · star) + EDGE_CASES empty states per tab.
- **Tap-to-reopen**: row → `TextViewModel.onHistoryPick` (input + language pair +
  `Result` state restored — the C-8 cache answers instantly on Retry) → Composer.
- **Composer star**: `onToggleFavourite` resolves the row via the C-8 tuple
  (auto-detect asks use the RESOLVED source, carried on `Result.resolvedSourceLang`
  and persisted); icon reflects `resultFavourite` (filled bookmark drawable added);
  unresolvable rows guide ("couldn't save") — no dead end.
- **Entry point (recorded deviation)**: the drawer isn't hosted yet (its own issue),
  so Home's Tools stack gains a History card; the drawer supersedes it when it lands.
- Strings ×3 · tests (HistoryViewModel · pick/favourite VM paths) · emulator proof
  (the real GOT translations from #62 appear in the list). Favourite is a DATA write →
  cross-model lens per Rule 5.
