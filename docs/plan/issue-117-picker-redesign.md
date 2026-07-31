# Plan — issue #117: language picker, production redesign

status: accepted

**Accepted basis (2026-07-31).** The owner commissioned a picker design from
Claude Design ("Language Picker 15a", 412×892, light + dark) and supplied it as
a bundled export. I unpacked it and reviewed the full source. The owner then
approved this implementation contract verbatim: *"I agree with you go ahead and
implement Language Screen that suit to production level."*

The standing pre-implementation design debate is satisfied by the owner's own
design plus the four adjudicated rulings in §3 — the debate that remained was
never "what should it look like", it was "which parts of it can be built
truthfully", and that is settled below with evidence.

---

## 1. What ships

The picker becomes the production language screen: the full 194-language
catalog (landed separately on `feat/issue-117-catalog`), a permanent search
field, ISO-code avatars, honest per-row offline state, and an A–Z rail.

Base branch: `feat/issue-117-catalog` (194 rows, `offlineAvailable` derived,
`offlineDownloaded` overlaid live by `LanguageRepositoryImpl`). This PR is the
UI half and must not re-litigate the data half.

## 2. The design, as measured from its source

Read from the export's `__bundler/template` island — not eyeballed. Every value
below is a literal in that file, and every colour is already one of our tokens
(`core/designsystem/Color.kt`), so nothing is re-tokenised.

| Element | Spec |
|---|---|
| Top app bar | 64dp; back 48dp target; title 22sp/28sp `onSurface`; no trailing action |
| Search field | 48dp, radius 24dp, `surfaceContainerHigh`, leading 20dp icon, 15sp placeholder |
| List padding | `0 8dp 8dp` — rows inset 8dp from the screen edge |
| Row | 56dp plain / 60dp when it carries a supporting line; radius 28/30dp; inner padding `0 16dp 0 12dp`; gap 16dp |
| Avatar | 40dp circle, 13sp code, weight 500, letterSpacing .5 |
| Section header | 12sp, weight 500, letterSpacing .8, UPPERCASE, `onPrimaryContainer` (light) / `primary` (dark) |
| Selected row | `primaryContainer` fill; avatar `surfaceContainerLowest`; headline weight 500 `onPrimaryContainer`; trailing `check` 24dp |
| Downloaded row | avatar `primaryContainer`; supporting "On device · N MB"; trailing `cloud_done` 22dp `primary` |
| Downloadable row | avatar `surfaceContainerHigh`; trailing size text + `download` 22dp `primary` |
| Downloading row | `surfaceContainerLow` fill; progress + trailing 40dp `close` |
| A–Z rail | right edge, 18dp visual, top 196dp → bottom 26dp; active letter in a 16dp `primaryContainer` pill |
| Counter | right of the "All languages" header — "2 of 65 on device" |

**Deliberate deviation, recorded:** the row pill sits 8dp from the screen edge,
not on our 16dp screen-margin line (#88/#92). That is correct for this pattern —
the margin rule governs *content*, and the design keeps content on 20dp+ while
letting the pill bleed outward, which is how M3 draws selectable list containers.
The 16dp rule still governs the app bar and the search field.

## 3. The four rulings — where the design outruns the platform

The design is buildable except where it displays data Google does not give us.
Inventing those numbers is precisely the class of defect this release has spent
its whole review cycle removing, so each one is resolved toward the truth.

### R1 — the 42% progress bar becomes indeterminate

`RemoteModelManager.download()` returns `Task<Void>`. There is no progress
callback, no byte count, no percentage. Recorded as verified in `CLAUDE.md` and
in `docs/research/issue-90-offline-download-lifecycle.md`.

→ Ship an **indeterminate** `LinearProgressIndicator`. No percentage text. The
row says "Downloading…", which is the whole of what we know.

### R2 — the ✕ is delete-to-cancel, and says so

There is no cancel API either. Issue #90's accepted ruling already settled this:
delete-to-cancel, job-ownership scoped (#83).

→ Keep the trailing ✕ exactly where the design puts it; it invokes the existing
delete path. The a11y label is "Stop download and remove" — never "Cancel",
which would promise an interruption we cannot perform.

### R3 — MB is shown only where it is real

No ML Kit API returns a per-language model size before download. The one figure
we ever measured is a de↔en **pair** at 45.7 MB on disk (#90 research).

→ **Downloaded rows** show the real on-disk size, measured from the models
directory. **Not-yet-downloaded rows show no size** — the trailing edge carries
the `download` affordance alone. A single honest sentence about typical size
belongs in the download confirmation, not on 194 rows.

If a future ML Kit release exposes sizes, the row already has the slot.

### R4 — the fourth state the design has no row for

135 of our 194 languages cannot be downloaded at all (online-only). The design
draws downloaded / downloading / downloadable and stops.

→ Reuse the design's own `ONLINE ONLY` chip — the one it puts on "Detect
language" — as the trailing content for online-only rows. Same visual language,
nothing invented.

**Row state matrix (all five, each with a preview):**

| State | Avatar | Trailing | Supporting |
|---|---|---|---|
| Selected | `surfaceContainerLowest` | `check` | on-device line if downloaded |
| Downloaded | `primaryContainer` | `cloud_done` | "On device · N MB" |
| Downloading | `surfaceContainerHigh` | `close` (delete) | indeterminate bar |
| Downloadable | `surfaceContainerHigh` | `download` | — |
| Online only | `surfaceContainerHigh` | `ONLINE ONLY` chip | — |
| Failed | `surfaceContainerHigh` | `refresh` | cause line (#90 §4) |

Failed is not in the design but is non-negotiable: #90 shipped per-row failure
guidance and EDGE_CASES forbids a dead end. It reuses the downloadable shape.

## 4. Corrections to strings in the design

- "Search 65 languages" → the real count, via `pluralStringResource`. Our
  catalog is 194.
- "2 of 65 on device" → downloaded count over the **offline-capable** count
  (59), not over 194 — "12 of 194 on device" would imply the other 182 are
  downloadable, which is false.

## 5. Accessibility (blocking, contract C-4 / TEST_A11Y)

The old app's screen had none of this: the device UI dump showed
`content-desc=""` on every node. That is the bar we are clearing.

- Every row: `Role.RadioButton` + `selected` semantics + a content description
  naming the language AND its state.
- The avatar is decorative — `contentDescription = null`. The code is not read
  aloud; the language name already carries it.
- A–Z rail: 18dp **visual**, 48dp **touch target**; each letter labelled;
  the rail is `hidden` from TalkBack traversal in favour of the list itself.
- Search field: labelled, IME action Search, clear button when non-empty.
- Empty search result is not a dead end: it names the query and offers to clear.

## 6. Testing (Rule 6)

- Row-state mapping: a unit test per state in the matrix above, including that a
  not-downloaded row exposes **no** size.
- Search predicate: display name, endonym and code; diacritic-insensitive;
  already covered on `feat/issue-117-picker-ux`, harvested here.
- Counter arithmetic: denominator is the offline-capable count, not the catalog
  size.
- Rule 7: `@PreviewLightDark` for the screen and for **every** row state.

## 7. Explicitly out of scope

Camera/conversation (#112) · the offline-manager screen in Settings (it keeps
its own surface; only the shared state source is common) · any change to the
catalog itself (that is the base branch) · a per-pair offline verdict in the
composer (real, and worth its own issue — the picker's tick describes the
MODEL, and the composer must eventually describe the PAIR).

## 8. Open risk

`LanguageRepositoryImpl` overlays live download state at read time; the A–Z rail
plus 194 rows plus a state overlay must not cause recomposition storms. Measure
with a scroll on the resizable AVD before the PR is opened; if it janks, the
overlay moves to a keyed map computed once per state emission.
