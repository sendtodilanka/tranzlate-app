---
status: accepted   # owner directive 2026-07-29 ("Next karanna thiyena ewa tikath ohomama karanna")
issue: 74
title: Host the app drawer — Recents + destinations live
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #74

`DrawerContent`/`DrawerViewModel` (UI_SPEC §2.3) have been preview-only since the
shell phase. This hosts them:

- `ModalNavigationDrawer` wraps the nav display; Home's title row gains the menu
  button (`cd_text_menu` already ships ×3). Destinations: History/Saved →
  HistoryNavKey, Offline languages → LanguagesNavKey, Settings → SettingsNavKey —
  drawer closes first. Recents stay read-only rows (spec note).
- **SEARCH row removed for now** (recorded deviation): no search exists and a row
  that closes the drawer into nothing violates no-dead-end; it returns with the
  search feature.
- The Home Tools "History & saved" entry is REMOVED — its own issue-68 note said
  the drawer supersedes it (strings dropped ×3; lint guards).
- Device proof: menu → drawer (live Recents incl. the offline "À demain" row) →
  each destination navigates; screenshot.
