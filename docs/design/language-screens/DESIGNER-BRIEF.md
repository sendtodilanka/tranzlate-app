# Designer brief — Offline Translator, language screens

**Give this to Claude Design at the start of every language-screens task.**

> ## Status: rev 3 of the spec is reconciled with this brief ✅
>
> Verified against the drawings, not just the changelog: **zero user-facing
> percentages** (the only `%` left in the file is the CSS keyframe that drives
> the indeterminate bar), **zero per-row sizes**, every MB figure either
> aggregate ("110 MB used", "12 MB free") or the sanctioned range, and
> pause / resume / queue / Undo / update appear **only in prose explaining why
> they were removed**. The one remaining "Cancel" is the tablet dialog's dismiss
> button, which is correct — it cancels the dialog, not a download.
>
> Two things still to tidy in the spec's own README, and one engineering
> prerequisite, are listed in §7. Everything else below still applies to future
> work.

This brief exists because the spec was first drawn without knowing what the
Android platform will and will not tell us at runtime. Nothing below is a matter
of taste — each item is a measured fact about the APIs, with the measurement
named, so you can design *around* the constraint instead of designing something
that has to be faked.

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

## 6. Both open questions are now answered — please design to these

### 1. There is NO free pack limit. Offline packs are unlimited and free.

**Sheet 19e is cut. Every "Free keeps N packs" line is cut.** Decided
2026-07-31 against a rule written before the evidence was gathered; the full
record is `docs/research/issue-120-free-pack-limit.md`.

The short version: Google Translate gives away unlimited free offline packs, so
charging for the third one prices below a free competitor in the one area this
app is named after. Our live app has never limited packs either, so a limit
would take something away from existing users — the Evernote path, not the
Dropbox one. And the packs cost us nothing to serve; the scarcity would be
visible to the user as artificial.

**But the guideline's underlying instinct was right, and it should be designed
— just not as a paywall.** ML Kit's own documentation says *"Avoid keeping too
many language models on the device at once."* That is a case for
**storage-hygiene UX** on the Manage packs page: a library meter, a nudge for
packs unused for months, an easy cleanup path. Please design that instead of
19e. It solves the real problem and asks nothing of the user's wallet.

Pro keeps selling what it already sells: cloud translation volume, the
higher-quality engine, and the extra modalities.

### 2. No pack sizes on rows. Downloaded rows say "On device".

Confirmed by the owner. Aggregate figures stay welcome — see §3. If a size range
is useful on the download confirm, one honest sentence ("packs are usually
20–45 MB") is the form it takes, never a per-language number.

---

*Sources for every claim above: `javap` on `translate-17.0.3.aar` and on
`android.jar` (API 37); the on-disk measurement in
`docs/research/issue-90-offline-download-lifecycle.md` §E3; ML Kit's published
language list; Cloud Translation's NMT list.*

---

## 7. Rev 3 — what is left

Three items, all small. Nothing here blocks building from rev 3.

### 7a. Two leftovers in the spec's own README (docs only)

1. **"The three rules" still reads "Every row states offline status and pack
   size."** Rev 3's own change table removes per-row size — the rule outlived
   the decision. It should read *"Every row states its offline status."*
2. **The sanctioned range is written twice with different numbers** — "usually
   20–50 MB" in one paragraph and "usually 20–45 MB" in the change table and on
   the drawings. The drawings win; 20–45 MB is the figure we measured against.
   Please make the prose match.

### 7b. One engineering prerequisite for "Not used since April" ⚠️

Rev 3's Manage packs nudge and the **20e Free up space** sheet both key off
per-pack last-used dates, correctly labelled in the spec as *"app-recorded
usage, not a platform figure"*. That framing is right, and the data is ours to
keep — but **the app does not record it in a usable form yet**.

What exists today is a *recents* list: the last **10** languages the user
selected, kept in preferences to drive the picker's Recent section. Two gaps for
this feature:

- It is capped at 10. A user with twelve packs would have two with no date at
  all — and those are exactly the stale ones the nudge is meant to surface.
- It records **selection**, not use. Close enough as a proxy, but it should be a
  deliberate choice rather than an accident of what the recents list happened to
  need.

The fix is small — keep a last-used entry for every language that has ever been
selected, bounded by the offline-capable set rather than by a top-N cap — but it
has to land **before** a screen can say "not used since April". Until it does, a
date on that row would be exactly the kind of invented figure this brief exists
to prevent. Tracked as its own issue.

### 7c. Build order

Rev 3 is much bigger than one change. The intended sequence, so nothing is drawn
that waits on something unbuilt:

1. **15a picker** — in review now (PR #121).
2. **16a Translate to** + the offline-voice speaker mark.
3. **17a–17d adaptive** — landscape two-pane, foldable two-leaf, tablet dialog.
4. **The ten sheets**, then **18a/18b first run**.
5. **20a–20e snackbars and Manage packs** — last, because 20b and 20e depend on
   7b.
