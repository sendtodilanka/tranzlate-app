---
status: accepted   # owner instructions verbatim, 2026-07-30 chat
issue: 80
title: IA correction — no drawer; History gets chips, swipe actions, Settings entry
date: 2026-07-30
author: Claude (Opus 5)
---

# Plan — issue #80

Owner corrections applied verbatim:

1. **Drawer removed for good** — the approved design never had one (the old shell
   smoke asserted exactly this; wave-2's hosting was my design-delta and reverts
   fully): host unwired, Home menu button gone, `DrawerContent`/`DrawerViewModel`
   + their strings/tests DELETED, smoke test's original no-drawer assertion
   restored with its KDoc.
2. **History**: the tabs become **All / Saved filter chips**; rows get **swipe
   actions** — leading swipe toggles Saved, trailing swipe DELETES with an Undo
   snackbar (new `delete` DAO/repo path; Undo re-inserts the same row content).
   Row tap → 5a result face stays as shipped.
   *(Amended by **issue #179**: "re-inserts" was only true while the row's C-8
   tuple stayed free. A plain insert is `IGNORE`-on-conflict, so once the tuple
   had been retaken Undo silently restored nothing. Undo now goes through
   `TranslationRepository.restore`, which merges the star and the earlier
   `created_at` onto the occupying row instead.)*
3. **Entry point**: SETTINGS gains a History row (Offline languages already has
   its Home entries). `tt_home_menu` leaves the tag doc; delete hygiene per the
   KSP lesson (grep + clean-module builds).
