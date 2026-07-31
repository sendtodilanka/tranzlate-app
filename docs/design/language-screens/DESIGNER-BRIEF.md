# Designer brief — Offline Translator, language screens

**Give this to Claude Design at the start of every language-screens task.**

The "Complete Language Screen design guideline" is excellent and most of it is
being built. This brief exists because that spec was drawn without knowing what
the Android platform will and will not tell us at runtime. Nothing below is a
matter of taste — each item is a measured fact about the APIs, with the
measurement named, so you can design *around* the constraint instead of
designing something that has to be faked.

**The governing rule for this product: the screen may not state anything the app
cannot know.** We removed a "2 updates ready" badge from this app in the same
week for exactly this reason. A number we cannot source is not a placeholder —
it is a lie that ships.

---

## 1. The real numbers (the spec says 65 — that is not our library)

| | Count | What it means |
|---|---|---|
| Total languages in the picker | **194** | everything the app can translate |
| Of those, downloadable as offline packs | **59** | ML Kit's on-device set |
| Of those, **online-only, no pack exists** | **135** | can never be downloaded, ever |

Two consequences for the design:

- Every "of 65" figure becomes a real one. On-device counters count against
  **59**, never 194 — "12 of 194 on device" would tell the user the other 182
  are downloadable, and they are not.
- **The picker is not a pack chooser.** Two thirds of its rows have no pack at
  all. The spec has no row state for that; we use the spec's own `ONLINE ONLY`
  chip (the one drawn on the Detect row) as the trailing content for those 135.
  If you have a better idea for that state, it is worth drawing.

## 2. What the platform will NOT give us — do not design these

Measured with `javap` against the shipped library `translate-17.0.3.aar`. The
entire public surface of a downloadable pack is:

```
TranslateRemoteModel:
    getLanguage()  getModelNameForBackend()  getUniqueModelNameForPersist()
```

That is all of it. Therefore:

| Drawn in the spec | Why it cannot ship | Design instead |
|---|---|---|
| **Pack size on every row** — "24 MB", "38 MB" | No size API. Worse, the on-disk store is keyed by **pair**, not language: one measured folder `de_en` was 45.7 MB and those bytes belong to German *and* English. Any per-language figure is either invented or double-counted. | Downloaded rows say **"On device"**. A single honest sentence about typical size can live on the download confirm — see §4. |
| **"42%"**, **"61% of 38 MB downloaded · 15 MB left"** | The download call returns a bare completion signal with no progress reporting at all. | **Indeterminate** progress. The row says "Downloading…" — that is the whole of what we know. |
| **"Pause anytime from Manage packs"** | No pause API. | Do not offer pause. The only real control is remove. |
| **"Move to front"** / a visible queue | We do not run a queue; downloads are deduped by language and handed to the system. | Do not draw queue management. |
| **"resuming carries on from there"** | No resume control. The system may or may not resume internally; we cannot observe it, so we must not promise it. | A failed download offers **retry**, not resume. Do not claim kept progress. |
| **"takes about 40 seconds"** | Unknowable — depends on the network. | Say nothing about duration. |
| **"Version 4.2 → 4.4 · released 2 days ago"**, update-available, update-required | ML Kit exposes **no pack version and no changelog**. There is no update concept to surface. | **Drop sheets 19k and 19l entirely.** They have no trigger that can ever fire. |

**The "Cancel" wording is also wrong** — there is no cancel. The ✕ on a
downloading row deletes the pack. It is labelled "Stop download and remove", and
must never say "Cancel", which promises an interruption we cannot perform.

## 3. What the platform WILL give us — these are good, keep going

- **Offline voice speaker mark** (16a, 19j). Android exposes
  `Voice.isNetworkConnectionRequired()`, so we genuinely know which languages
  read aloud without a connection. Verified present. Build it.
- **Aggregate storage, yes — per-pack, no.** This is the important nuance: we
  can walk the models folder, so **"110 MB used"** on the library meter is
  truthful, and free-space figures are truthful. It is only the *per-language*
  breakdown that has no source. Design meters and totals freely.
- **"3 saved phrases use Spanish"** (19g) — we can count saved translations by
  language. Real.
- **Suggestions from the user's keyboards and locale** (18a) — real, and a good
  first-run idea.
- **Everything adaptive** (17a–17d) — landscape two-pane, foldable two-leaf,
  tablet-as-dialog. All buildable. The tablet-dialog decision in particular is
  right and we intend to follow it.
- Most of the sheets: mobile data, no space, remove, remove-in-use, offline
  fallback, detect-needs-connection, no-offline-voice, already-the-source, how
  it works. All have real triggers.

## 4. Decisions already made with the owner — please keep them

1. **No flags.** An ISO-code avatar, never a country flag. (A flag is a country;
   `pt` is mostly Brazil, `ar` spans 25 countries, Esperanto has no country.)
2. Search is a **permanent field**, never an icon.
3. Progress is **indeterminate**; the ✕ removes.
4. Rows never show a size they cannot source; downloaded rows say "On device".
5. A **failed** row states its cause and offers retry — no bare retry that
   re-fails silently.
6. The row states that exist are exactly six: **selected · downloaded ·
   downloading · downloadable · online-only · failed.**

## 5. Still to design — and what to watch for in each

### Snackbars

- **"Download started"** — fine, build it.
- **"Pack removed · Undo"** — ⚠️ **Undo is not instant here.** The pack is gone
  from disk; undoing means downloading it again, which takes minutes and needs a
  connection. A snackbar Undo implies a free reversal. Either drop Undo, or
  label it so it reads as "download it again" — and then it must respect the
  mobile-data consent gate like any other download. Please design that
  explicitly rather than leaving Undo generic.
- **"Update finished"** — ⚠️ no trigger exists (see §2, no version API). Please
  drop it.

### Manage packs page

This is the page the sheets keep pointing at, and it is where the depth belongs.
Notes for it:

- It already exists in the app in an early form (a Settings screen listing
  offline languages), so this design will replace a real screen, not fill a void.
- It may show **aggregate** storage — total used, free space, per-device totals.
  Not per-pack sizes.
- Its row states are the same six as the picker. Reuse, do not invent a seventh.
- It is the correct home for **removing** packs and for **retrying failures**;
  the picker deliberately keeps only download and select.
- No pause, no queue, no update section.

## 6. Open questions for the owner — please do not design an answer

Two things in the guideline are business decisions that do not exist in the app
today. They are the owner's to make, and until made, nothing should be drawn
that assumes them:

1. **"Free keeps 2 packs on the device. Pro downloads all 65."** Our paid tier
   currently gates *translation volume*, not pack count. A pack-count limit is a
   new rule with real consequences, and sheet 19e is built entirely on it.
   *(Also: the guideline contradicts itself — frame 15b says 2 packs, frame 17b
   says 3.)*
2. **Whether pack sizes matter enough** to be worth measuring all 59 by hand and
   shipping a table that would go stale. The current answer is no; the row says
   "On device" and the download confirm carries one honest range.

---

*Sources for every claim above: `javap` on `translate-17.0.3.aar` and on
`android.jar` (API 37); the on-disk measurement in
`docs/research/issue-90-offline-download-lifecycle.md` §E3; ML Kit's published
language list; Cloud Translation's NMT list.*
