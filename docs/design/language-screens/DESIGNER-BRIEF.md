# Designer brief — Offline Translator, language screens

**Give this to Claude Design at the start of every language-screens task.**

> ## Status: rev 4 is the spec of record, and it holds ✅
>
> `language-screens-spec.html` is rev 4. Re-verified the same way — against the
> drawings, never the changelog: **zero user-facing percentages** (the only `%`
> in the file is the CSS keyframe driving the indeterminate bar), **zero per-row
> sizes**, every MB figure either aggregate ("110 MB used", "12 MB free") or the
> sanctioned **20–45 MB** range, which is now the only range in the file. Pause /
> resume / queue / Undo / version appear only in prose explaining their absence.
>
> Rev 4 delivers the empty state commissioned in §8 (**20f**) and adds an
> unrequested but welcome **ad layer** (§7 of the spec).
>
> **It also carries nine defects, three of them in the new sections** — the
> worst offers a download on two languages that can never have a pack. The full
> list, and the retraction of this review's own first pass, is in **§9**; the ad
> verdict in **§10**; the rev 5 correction list in **§11**. §7's older leftovers
> are closed.

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
  fallback, no-offline-voice, already-the-source, how it works. All have real
  triggers.
- ⚠️ **Correction — "detect needs a connection" (19i) has no trigger and is
  cancelled.** This line previously listed it as real. It is not: language
  detection runs **on-device** (`MlKitLanguageIdentifier.kt:3,22`), so the sheet
  can never fire and the `ONLINE ONLY` chip on the Detect row states something
  false. Owner ruling 2 (2026-08-01) settled it: Detect stays on-device, the
  chip is removed from the shipped picker, 19i is never built. Rev 4 still draws
  both, faithfully following the older wording above — the error is ours, not
  the designer's. Do not draw them again.

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

## 7. Rev 3 — what was left · ALL THREE NOW CLOSED

Kept for the record; each item's outcome is stamped on it. Nothing here blocks
building from rev 4.

### 7a. Two leftovers in the spec's own README (docs only) — ✅ fixed in rev 4

1. **"The three rules" still reads "Every row states offline status and pack
   size."** Rev 3's own change table removes per-row size — the rule outlived
   the decision. It should read *"Every row states its offline status."*
2. **The sanctioned range is written twice with different numbers** — "usually
   20–50 MB" in one paragraph and "usually 20–45 MB" in the change table and on
   the drawings. The drawings win; 20–45 MB is the figure we measured against.
   Please make the prose match.

### 7b. One engineering prerequisite for "Not used since April" — ✅ built (#134)

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

> **Outcome (2026-08-01, PR #134 · issue #122).** Built, and built stricter than
> asked: an uncapped Room table `language_usage(lang_id, role, last_used_at)`,
> stamped on **translation success**, never on selection. The brief called
> selection "close enough as a proxy"; the debate rejected that — a language you
> picked and immediately abandoned is not a language you used, and the whole
> point of the nudge is to find packs nobody uses. A test pins the distinction,
> so "not used since April" is now a figure the app can actually source.

### 7c. Build order — ✅ superseded by the ruled 28-PR plan

The sketch below was right in shape and is now replaced by the real thing:
`docs/plan/issue-130-language-rev3.md`, which is also the live progress tracker.
Phases there follow the same order — picker, 16a, adaptive, sheets and first
run, then Manage packs last because it depends on 7b.

1. **15a picker** — ✅ shipped (PR #121).
2. **16a Translate to** + the offline-voice speaker mark.
3. **17a–17d adaptive** — landscape two-pane, foldable two-leaf, tablet dialog.
4. **The ten sheets**, then **18a/18b first run**.
5. **20a–20f snackbars and Manage packs** — last, because 20b and 20e depend on
   7b.

---

## 8. Commissioned: the Manage packs EMPTY state — ✅ DELIVERED as 20f (rev 4)

Rev 3 draws Manage packs populated ("5 of 59 packs"), downloading, failed and
dated — but never with ZERO packs, which is what every fresh install sees on
first open. That frame is now commissioned. Requirements: same 412×892 light +
dark treatment as 20b; the storage card degrades honestly (no packs = no "used
by packs" figure); guidance must not dead-end — the way forward is downloading
a first pack (the picker's download affordance, or the first-run suggestions
pattern from 18a); no invented figures, per the rest of this brief. The rev3
"no packs yet" block from 18a is the obvious vocabulary to reuse.

> **Outcome — 20f, light and dark, accepted as drawn.** Every requirement met,
> and the honesty test passed on the hardest part: the storage card. With no
> packs there is no "used by packs" figure to state, so the card states free
> space alone, the packs legend reads **"none yet"**, and the sanctioned range
> carries the expectation instead ("They are free and unlimited — a pack is
> usually 20–45 MB"). The `of 59` counter, the hygiene nudge and the dated rows
> are all absent, because with nothing downloaded there is nothing to count or
> call stale — three separate places the frame chose to say less rather than
> fake more. The 18a vocabulary is reused as suggested, and the way forward is
> the suggestion list plus **Browse all languages**, so the page cannot
> dead-end. Builds in **PR-23**.

---

## 9. Rev 4 review (2026-08-01) — rev 4 is the spec of record, with 9 defects to fix

**Read this section before drawing rev 5.** The first pass of this review was
too generous, and an independent adversarial lens took it apart. What follows is
the corrected version, with the retraction stated plainly.

### What the first pass got wrong — the method, not just the conclusion

1. **"26 frames scanned" was not the coverage it sounded like.** There are 26
   `data-screen-label` attributes, but the labels stop before a 117 KB stretch of
   the document: **all ten sheets, all five snackbars, 20c, 20e and 21c carry no
   label at all**. Enumerating labels enumerates about 26 of some 60 drawings.
   The whole-document greps for banned figures did cover them; the frame-by-frame
   read did not.
2. **The scan de-duplicated repeated lines**, which is precisely how a row-level
   contradiction hides — `download` appearing once in a frame looks identical to
   `download` appearing on a row that must never carry it. Defect A below was
   invisible for exactly that reason. **Never dedupe while scanning a design
   export.**
3. **"The state table holds across every one" is RETRACTED.** It does not. Three
   frames contradict it, and the same paragraph then admitted 20f is an exception
   — a claim arguing with itself two sentences later.
4. **Two "fixed in rev 4" items were fixes to `README.md`, not to the spec.**
   That is what §7a asked for and it is what was delivered — but the spec's own
   "THE THREE RULES" paragraph is byte-identical between rev 3 and rev 4, and
   `20–50 MB` never appeared in the spec at all. Say which file changed.

### Defects to fix in rev 5 — verified, ordered by severity

**A. 21b offers a download on two languages that can never have a pack.**
The new ad frame draws `AZ / Azerbaijani / download`, `EU / Basque / download`.
Checked against our own catalog (`BundledLanguageCatalog.offlineCapableIds`):
`az` and `eu` are **not** in the offline-capable set, and 15a and 16a correctly
draw both as `ONLINE ONLY`. (`be` Belarusian, drawn `download` in the same
frame, **is** capable — that row is right.) This is the exact defect class this
brief exists to prevent, and it arrived in the newest section.

**B. 21b re-writes the running state four more ways.** `ES / Spanish / On device
· in use` on **Translate from**, when Spanish is the target everywhere else; the
recents header reads `On device` instead of `Recent`; Afrikaans — the selected
source in every other From frame — is missing; and the list header reads
`59 can be offline`, which is 18a's *zero-pack* header on a screen that has five.

**C. 19m is drawn on a state the document does not have.** `swap_horiz /
Spanish is already the source`. Spanish is the **target** (16a: "Recently used
as target"), and 19g says so explicitly: "It is your target language." The
source in every From frame is Afrikaans.

**D. 20f and 18a claim different platform signals for the same suggestion.**
20f: `FR / French / From your keyboards`. 18a and 18b: `FR / French / Common
where you are`. One fresh install, one French row, two different sources.

**E. The running state is internally impossible.** Every populated frame draws
`12 MB free` **and** `AR / Arabic / Downloading…`. Sheet 19b defines that exact
condition as a hard stop: "There is 12 MB free on this device. A language pack
usually needs 20–45 MB." → `Not enough space`. Pick a free-space figure that
permits the download the same frames show in flight.

**F. 19j has no reachable trigger — the same defect as 19i.** The sheet is
`Amharic has no offline voice`, and its caption says it opens "by tapping the
speaker mark". Amharic appears 20 times in the document and never carries
`volume_up`. A mark that is only drawn when offline voice exists cannot be the
way to report that it does not.

**G. The failed row breaks two of the three rules.** In 15a, 16a and 18a the
Hindi row is drawn as `cloud_off / Hindi / Did not download · connection lost /
Retry` — **no ISO-code avatar**, and placed immediately after Azerbaijani, out
of A–Z order. 21c refuses inline ads for precisely this reason: "position means
letter". If the placement is a drawing convenience, say so in the caption;
otherwise the failed row needs its avatar and its alphabetical slot.

**H. The speaker mark is drawn on rows with no pack.** `AR / Arabic /
volume_up / downloading`, `BN / Bengali / volume_up / download`, `BG / Bulgarian
/ volume_up / download` (17a, 17b, 17d). The explainer says the mark denotes
"packs that can also read the translation aloud offline", but `Voice.is
NetworkConnectionRequired` reports the installed **TTS voice**, which is
independent of the translate pack. Either the mark means "offline voice exists"
(then the explainer is wrong) or it means "this pack reads aloud offline" (then
it cannot appear on an undownloaded row). Needs one answer before 16a is built.

**I. `workspace_premium` — the Pro glyph — still marks the "packs are free and
unlimited" card** in 15b light/dark and 17b from/to. The caption's stale "Pro
ceiling" wording was caught; the icon saying the same thing inside the drawing
was not. Its `Learn more` also has no declared destination.

### Stale captions — copy only, every drawing correct

1. 15a: "pack state **and size** on every row" — the rows say `On device`.
2. 15b: "the **Pro ceiling** stated once at the bottom" — the frame says
   "Packs are free and unlimited".
3. 18a: suggestions carry "the size on the button" — the buttons say `Get`
   (section 3's intro repeats it: "sizes sit on the buttons").

All three were equally stale in rev 3 and this brief signed rev 3 off as
reconciled. A caption is part of the spec an engineer reads.

### One conflict that is ours, not the designer's

Rev 4 draws the Detect row's `ONLINE ONLY` chip and sheet **19i**. Both are
cancelled by owner ruling 2 — detection is on-device
(`MlKitLanguageIdentifier.kt:3,22`, re-verified). The designer followed §3 of
this brief, which listed that sheet as having a real trigger. §3 is corrected
above; **PR-27** strips the chip and 19i is never built.

### Two corrections to this brief's own earlier claims

- The rev 3 status block said "the one remaining Cancel is the tablet dialog's
  dismiss button". There are **eight** — four tablet dismisses and four in
  19f/19g. Sheet dismisses reading "Cancel" are fine; §2's prohibition is about
  the ✕ on a **downloading row**, which correctly reads "Stop download and
  remove". The count was simply wrong.
- §1 still points at "the `ONLINE ONLY` chip (the one drawn on the Detect row)"
  as the exemplar for the 135 online-only languages. That chip is being removed.
  Point it at any ordinary online-only row instead.

### What is genuinely good, and stands

20f as delivered (§8), every honesty check that did pass — no user-facing
percentages, no per-row sizes, no invented durations, indeterminate progress,
"Stop download and remove" — and the whole shape of the ad layer's reasoning.
**Rev 4 remains the spec of record.** None of A–I requires a re-design; they are
corrections to frames that already exist.

## 10. The ad layer (spec §7) — engineering verdict

Unrequested and genuinely good: two slots for the whole feature (an anchored
adaptive banner on the picker and Manage packs, one native card at the tail of
the Manage packs list), a documented refusal list, and states for every way a
slot can fail to fill. It reasons the way this brief asks — a slot that cannot
fill **leaves no hole**, never a house placeholder, never an empty box labelled
`AD`. That is the same honesty rule the figures follow.

**Not folded into #130.** The ad layer's drawings are accepted as the reference,
but the build is a separate epic — **issue #139** — for three reasons that are
facts about this repo, not preferences:

1. The `:ads` and `:consent` library modules **do not exist yet** — they are
   scheduled with the four brains, not the language screens.
2. The spec's own step 1 is the **UMP consent flow**, which is a privacy-gate
   change to app startup, not a language-screens change.
3. Its **owner decision 1 — "does Pro remove ads?" — is unsettled**, and every
   frame in §7 assumes the answer is yes. Building to an assumed answer is
   exactly the failure this brief exists to prevent.

**Three internal contradictions in §7 — fix before anyone builds from it.**
They matter more than they look, because this section is written as an
implementation guide and would be followed literally:

1. **The code sample contradicts the rule it sits under.** The guide's states
   table says a download in flight means "no new requests and no refresh — but a
   slot already filled **stays**, see 21a, where Arabic is downloading and the
   banner is still there." The sample then gates the whole slot on one boolean:
   `showAds = !isPro and canRequestAds and isOnline and !isDownloading` — which
   tears the filled banner out mid-download, exactly what 21a shows not
   happening. Two flags are needed: one to *compose* the slot, one to *request*.
2. **The ad unit ids disagree between the drawings and the table.** Drawings
   label `picker_banner` / `managepacks_native`; the inventory specifies
   `translator_picker_banner`, `translator_managepacks_banner`,
   `translator_managepacks_native`. One naming, please — these become real
   AdMob unit ids.
3. **The native card omits a required asset and draws an unreliable one.** The
   rules list "required assets: headline, icon, CTA"; the drawn card shows
   `{headline}`, `{advertiser} · {store}`, `{body}`, `{callToAction}` and **no
   `{icon}`**. `{store}` exists only on app-install ads and is absent on content
   ads, with no fallback drawn. The section that states "the screen may not
   state anything the app cannot know" breaks that rule inside its own mock.

**What we adopt now, before any ad code exists:** the picker and Manage packs
layouts must not have to be re-laid-out to host a slot later. Today they are
hardcoded to bare-screen values — `LanguagePickerScreen.kt:370` sets the list's
`bottom = spacing.sm8`, and the A–Z rail is `padding(vertical = spacing.lg24)`
(same file, ~line 405). Keeping both **parameterised** is a one-line habit in
the #130 PRs that saves a rework when the slot arrives.

**Carried to the owner as decisions, not designed here:** whether Pro removes
ads (changes what existing subscribers were sold); the first-run grace period;
whether the picker — the app's most task-focused screen — carries a banner at
all; and app-open/interstitial ads, which the spec itself recommends leaving
unbuilt for this release. Our recommendations match the spec's on all four.

---

## 11. Commissioned: rev 5 — corrections only, no new screens

Rev 4 stands as the spec of record. Rev 5 is a correction pass over frames that
already exist. Nothing here needs a new design; everything here is a frame
disagreeing with another frame, or with what the app can actually do.

**Fix in the drawings** — §9 carries the evidence for each:

1. **21b** — `AZ / Azerbaijani` and `EU / Basque` must read `ONLINE ONLY`, not
   `download`. Neither language has an offline pack and never will. (`BE /
   Belarusian / download` in the same frame is correct — leave it.)
2. **21b** — restore the running state: Spanish is the **target**, so the From
   screen's selected source is Afrikaans; the pinned section is `Recent`, not
   `On device`; and the list header is `5 of 59 packs on device`, not 18a's
   zero-pack `59 can be offline`.
3. **19m** — Spanish is the target, so "Spanish is already the source" cannot
   happen. Redraw it on a language the document actually has as the source.
4. **20f** — French's suggestion reason must match 18a: `Common where you are`.
5. **The running state's free space** — `12 MB free` makes the in-flight Arabic
   download impossible by sheet 19b's own rule. Choose a figure that permits it,
   and keep 19b's tight-space number inside 19b.
6. **19j** — give it a trigger that can fire, or cut it. A speaker mark drawn
   only where offline voice exists cannot be the way to report its absence.
7. **The failed row** (Hindi, in 15a/16a/18a) — give it its ISO avatar and its
   alphabetical position, or state in the caption that the placement is a
   drawing convenience.
8. **The speaker mark** on `AR`, `BN`, `BG` in 17a/17b/17d — decide what the
   mark means (offline voice exists **vs** this pack reads aloud offline) and
   make the explainer and the rows agree. See §9-H for why the platform answer
   is the first one.
9. **`workspace_premium`** on the "packs are free and unlimited" card (15b,
   17b) — the Pro glyph contradicts the no-ceiling ruling. Use a neutral
   information icon, and give its `Learn more` a destination or drop the link.
10. **Detect** — remove the `ONLINE ONLY` chip from the Detect row everywhere,
    and cut sheet **19i** entirely. Detection runs on-device; the sheet has no
    trigger. (This one is our correction, not a designer error — see §3.)

**Fix in the captions** — the drawings are right, the words are stale:

11. 15a: drop "and size" from "pack state and size on every row".
12. 15b: drop "the Pro ceiling stated once at the bottom" — the frame says
    packs are free and unlimited.
13. 18a: the suggestion buttons say `Get`, not a size — in the frame caption and
    in section 3's intro.

**Fix in §7, the ad guide** — see §10 for the reasoning:

14. Split the one `showAds` boolean into compose-the-slot and request-an-ad, so
    a download in flight stops new requests without tearing out a filled banner.
15. One naming for the ad unit ids — the drawings and the inventory table
    currently disagree.
16. The native card mock must draw its required `{icon}`, and either drop
    `{store}` or draw the fallback for content ads, which do not carry it.

**One structural request, not a defect:** please label every drawing with
`data-screen-label`. Ten sheets, five snackbars, 20c, 20e and 21c currently
carry none, so any reviewer enumerating frames silently skips them — which is
exactly what happened in the first pass of the rev 4 review.
