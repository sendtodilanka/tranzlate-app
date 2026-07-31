# Plan — issue #100: @PreviewLightDark coverage (owner mandatory rule)

status: accepted
(accepted basis: direct owner instruction 2026-07-31 — "Preview liyana eka
mandatory rule ekak widiyata CLAUDE.md and memory update karaganna". Debate
scaled to a convention decision: preview-only code, zero runtime behaviour,
and the house style already existed — the decision was WHAT must be covered,
not how to architect it.)

## The rule (now CLAUDE.md mandatory rule 7)

Every composable ships `@PreviewLightDark` — screens AND every custom item
built from standard M3 parts (rows, row action buttons, cards, chips,
dialogs, counters, banners). ONE preview per meaningful STATE. Same file,
`private`, `TranzlateTheme { … }` (+ `Surface` for items), literal fake data,
never DI. Named `<Composable><State>Preview`.

## Coverage added

- **Offline languages** (owner's named example): screen · loading/empty face ·
  metered-consent dialog · `OfflineRowStatesPreview` = all five row states in
  one column (⬇ / spinner+stop / 🗑 / deleting / ↻ with the STORAGE and
  NETWORK cause lines) — the row's trailing action button in every state.
- **History**: screen (All/Saved chips) · empty face · `HistoryRow` starred +
  unstarred.
- **Camera** placeholder screen.
- **Home items**: `LanguageRow` (normal + Detect/swap-disabled) ·
  `InputPreviewCard` + `TokenProChip` · tool cards, list row, mini cards and
  the phrasing banner.
- **Composer items**: `CharCounter` at 0 / at-cap / over-limit ·
  `EditAction` mic / Translate / over-limit-disabled.
- **Settings items**: section header, subgroup label, theme rows (selected +
  unselected), dynamic-colour row, mobile-data row.
- **Paywall items**: benefit rows + plan cards (normal + selected wide
  Yearly).

Screens that already had previews keep them (Home, Composer, LanguagePicker,
Settings, Paywall, and the five `:core:ui` components).

## Not covered (nothing to render)

`Theme.kt`, `WindowInfo.kt`, `AdaptiveMargin.kt`, `AdaptiveLayout.kt`,
`LanguageNames.kt` — no UI output.

## Verification

Compile probes per module + full suite + `spotlessCheck detekt --rerun-tasks`
+ both APKs. Previews are compile-verified only: rendering them is an Android
Studio action, and no Paparazzi/screenshot harness exists yet (issue #20 is
the open JVM screenshot-harness item — recorded, not this PR).
