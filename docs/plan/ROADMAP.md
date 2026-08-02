# Roadmap — one order for the plan and the issues

`docs/plan/issue-130-language-rev3.md` is the **language epic's** tracker and stays
that. This file is the level above it: the epic's remaining PRs, the issues that
surfaced *while building it*, and the owner decisions that gate both — in the
order they should actually happen.

Written 2026-08-01, after Phase 2 of the epic landed. Rebuild it when a wave
completes, not per PR; the per-PR truth lives in the epic tracker.

---

## Where things stand

**`main` = `ad01cf6`.** The language epic is 10 of 28 PRs in, and the two phases
that carried the most risk are behind us:

- **Phase 1** — shipped-truth stabilisation (#132–#135, #141, #142). A delete/
  download race, a forever-Loading screen, a usage store, the tag resolver, and
  the shared model-state flow.
- **Phase 2** — the move (#145, #146). `:feature:language` exists, the picker
  and the packs screen live in it, `:feature:languagepicker` is deleted, and
  language presentation has one home in `:core:ui`.
- **Docs** — the spec is at rev 5, all sixteen commissioned corrections verified
  against the drawings (#140, #144).

**In flight:** #147 (PR-10, the offline-voice seam) — co-verify returned BLOCK
on three findings, all three fixed, awaiting re-verification.

---

## The rule this order obeys

Every wave below is sequenced by **what it unblocks or protects**, not by what
is interesting. Two hard constraints:

1. **Merges stay serial.** Building in parallel is fine and we do it; merging
   siblings off one base is what broke `main` twice. `/land-pr` per PR.
2. **A gate before the work it guards.** The CI wave comes second, not last,
   because everything after it is safer for its existence.

---

## Wave 1 · Unblock and protect

Small, and each one makes the rest cheaper or safer.

| | Work | PR | Why now |
|---|---|---|---|
| 1 | ✅ **#147** land (PR-10 voice seam) | #147 | Also fixes **#111** — it restores the `PurchaseFlow` binding that has blocked the instrumentation suite since `fabb214` |
| 2 | ✅ **#148** CI compiles androidTest | #160 | The reason #111 survived for weeks: `./gradlew build` never assembles those sources. Must follow #147, which makes them compile again |
| 3 | ✅ **#150** guarded back-stack pop | #156 | A crash: `onDone` and three other sites pop without the `size > 1` guard `onBack` already has. Two fast taps can empty the stack |
| 4 | ✅ **#149** `ResultSpeaker` standing TTS | #159 | Once #147 lands the app holds two contradictory TTS contracts. Resolve it, or record why speaking is a genuine exception. Resolved as **consumer-lifetime, not process-lifetime** — the §7.3 ruling was amended, see below |

Wave 1 is closed. Two guards were also built while it ran and are not roadmap
rows, because neither was planned: **#162** (`guard-pr`) and **#165**
(`guard-tracker` + the `co-verify-lens` agent).

## Wave 2 · Finish Phase 3 of the epic

| | Work | PR | Why now |
|---|---|---|---|
| 5 | ⬜ **PR-9** shared `DownloadGate` | #166 | Deletes the consent logic duplicated across the two screens — only possible **because** Phase 2 put them in one module. Doing it before the sheets means PR-17 builds on one gate, not two |

## Wave 3 · The screens the owner can see

| | Work | Rides with it |
|---|---|---|
| 6 | **PR-12** 16a Translate-to | **#154** (first-frame tick) and **#152**'s dead-string sweep touch the same files. Speaker marks build to rev 5's settled meaning: **the device voice, not the pack** |
| 7 | **PR-13 – PR-16** adaptive | Landscape, foldable, tablet dialog. **E-S1** gates PR-15; the measured jank budget gates PR-16 |

## Wave 4 · Sheets, first run, snackbars

| | Work | Note |
|---|---|---|
| 8 | **PR-17 – PR-22** | PR-22 carries the single `SnackbarHost` that **#26** was closed in favour of. Sheets are eight, not ten — rev 5 cut 19i and 19j, neither of which has a trigger that can fire |

## Wave 5 · Manage packs

| | Work | Note |
|---|---|---|
| 9 | **PR-23 – PR-28** | PR-23 builds **20f**, the empty state, and is where the picker/packs `contentPadding.bottom` and A–Z rail stop become **parameterised** so #139's slot needs no re-layout. PR-27 shrank: rev 5 removed the Detect chip from the spec, so only the shipped app needs the strip |

## Any time — independent of the waves

- 👁 **#174** — **PR #187** — nothing is announced while a translation runs. `a11y_translating`
  exists in all three locales with **zero** call sites, and `a11y_limit_reached`
  — also canonical under C-4 — has no resource at all. A screen-reader user gets
  silence for the whole wait, which is when the feedback matters most. Rides
  with Wave 3, where `feature/text` is open anyway.
- ⬜ **#175** the same download failure says two different things depending on
  the screen: the picker and the offline manager each ship a full set of the
  three failure messages, **both live**, one call site each. Six keys × three
  locales for three messages. Best done in Wave 5 with PR-23, or earlier — the
  two screens already share one `DownloadGate` after PR-9.
- 👁 **#179** — **PR #189** — Undo restored nothing when the row's tuple was
  retaken: the star and the original date were dropped in silence after the
  snackbar had promised the delete was reversible. Merges now, in one
  transaction. **#191** carries the one trade-off it leaves for the owner.
- 👁 **#190** — **PR #194** — History's three write paths (`delete`, `undoDelete`,
  `toggleFavourite`) had no error handling at all: a `SQLiteException` took the
  app down instead of showing a message. Policy was already settled by
  `EDGE_CASES.md:114` (**[Retry]**), not an open choice.
- 👁 **#195** — **PR #197** — the composer crashed the moment a translation
  landed, not when the star was tapped: enumerating the star rather than the
  issue's line range found a **fourth** unguarded write, on the read path that
  runs for every result. Sibling of #190/#194.- 👁 **#186** — **PR #199** — there was no Compose unit-test runtime anywhere, so
  a decision inside a `@Composable` was a decision no test could reach. Three of
  2026-08-02's five blocking co-verify findings were device-only because of it.
  Robolectric + `createComposeRule` via `tranzlate.compose-test`; the acceptance
  test catches #198's shipped bug in the form the whole existing suite missed.
- 👁 **#206** — **PR #207** — the owner asked for a system that stops me
  repeating my own mistakes. Rule 11 covers code changes and its hooks check a
  marker is *present*, not that it is *true* — `Call sites: 4 found, 4 changed`
  passed in #171 while being wrong. Fourteen of my errors in one session sort
  into six shapes, all in claims and orchestration. CLAUDE.md rule 12 +
  `Enumerated by:` in `guard-pr.sh` + `device-claim.sh`.
- ⬜ **#178** `guard-pr.sh` failed CLOSED on any body it could not read from the
  command text — `--body-file`, `$(cat f)`, `$VAR` — contradicting its own
  fail-open contract and denying compliant PRs. **PR #182**, ten mutations.
- ⬜ **#173** (rows for #173/#174/#175 added by **PR #176**) `verifyStringKeyDocs` has two coverage holes: module discovery is
  a hardcoded path shape a new ring escapes silently, and the reverse direction
  is #152's own unimplemented second ask. Follows #172. **Split in two.**
  *Hole 2 (module discovery)* — **PR #202**, plan
  `docs/plan/issue-173-module-discovery.md`: the scan is derived from
  `subprojects` instead of four ring globs, so a new ring or a deeper module can
  no longer escape it in silence. Both escapes were reproduced green on the
  unfixed gate first, then turned red by name. `Refs:`, not `Fixes:` — closing
  #173 would lose the other half, which is the accident #173 was filed early to
  avoid. *Hole 1 (the reverse direction)* stays open and keeps #173 open: it
  needs a parseable planned/shipped/retired status in every `STRINGS_*.md`,
  which is the file every feature PR touches, and its first run today is ~60
  findings that are nearly all deliberate.
- ⬜ **#201** — **PR #211**. The other three gates in the root `build.gradle.kts` — `detekt`'s
  `source` and both `spotless` targets — named the same four rings
  `verifyStringKeyDocs` used to, so a module at a new top-level prefix was
  linted and formatted by nobody, and CI's `./gradlew detekt spotlessCheck`
  stayed green over a file with a deliberate violation in it. Plan
  `docs/plan/issue-201-lint-discovery.md`: all three derived from `subprojects`
  (plus `gradle.includedBuilds` for `build-logic`'s scripts), reproduced green
  on the unfixed config first and turned red by name after. **Narrower than
  #173, but not as narrow as this row first said** — the `core`/`feature`/`lib`
  globs carried an extra `**` and were already depth-tolerant, so for those
  three only a new top-level ring escaped. **`app` was different and this row
  claimed otherwise until the #211 lens disproved it:** `app/src/**/*.kt` has
  one `**`, so a file under `app/` outside `app/src/` escaped exactly like a new
  ring, proved by a probe that went uncaught on the unfixed config while its
  `core` twin was caught in the same run. One ring in four, not zero. No code
  changed — the shipped derivation already closed both.
  The accidental exclusion of `.claude/worktrees/` becomes structural
  rather than a side effect of the ring names. **Residual, separate issue:**
  `build-logic`'s 15 Kotlin files are analysed by neither gate and never were.
- ⬜ **#179** History's Undo restored nothing when the row's C-8 tuple had been
  retaken — the insert is `IGNORE`-on-conflict, the -1 was discarded, and the
  star and the original `created_at` were gone while the snackbar had already
  said the delete was reversible. Fixed by MERGING rather than fighting for the
  tuple: `TranslationRepository.restore` carries the star across (never clearing
  one in either direction, as `MigrationOneToTwo` already does) and keeps the
  earlier stamp. #177 widened the reach — delete-then-retranslate-the-other-way
  now collides for the nine `LanguageTagResolver` legacy aliases.
- ⬜ **#151** history rows store raw detector tags while the prefs seam
  canonicalises. Self-contained, `core:domain`.
- ⬜ **#152** the STRINGS gate — **PR #172**. Scope grew on contact: the gate's
  first run found **129 of 205** keys documented nowhere, so the PR also carries
  four new `STRINGS_*.md` and refreshes two. The `feature/text` dead keys still
  ride with Wave 3 — 22 remain there by decision, several marked *keep* in §7.

---

## Gated on an owner decision

Nothing below moves until these are answered. They are listed with what they
block, so the cost of leaving them open is visible.

| Decision | Blocks |
|---|---|
| **#153** `bn`/`tl` — catalog name or CLDR name? | Copy in PR-12 and PR-23. Cheap either way, but it changes what users read |
| **#139** three remaining ad decisions — first-run grace period · does the picker carry a banner · A4 | The **entire** ad layer. Every drawn frame assumes an answer |
| **#114** real AdMob ids | Ads earning anything at all, as opposed to being built |
| **#112** camera regression · **#78** camera spec Q1–Q4 | The camera vertical |
| **#129** paywall copy vs the 2000/day cap | Paywall wording |
| **#115** 72 authored fil/pt-BR strings, never natively reviewed | Release quality — and the epic keeps adding strings to that pile |

**Settled, recorded so it is not re-litigated:** Pro removes ads (it always did —
`BUSINESS_MODEL.md` says so in four places). No free pack limit. No per-row pack
sizes. Detect stays on-device.

### One approved ruling was amended — 2026-08-01, #149/#159

The owner approved the eight #130 rulings verbatim. **One of them has since been
corrected**, and it is recorded here rather than only inside the ruling doc,
because an approved decision changing quietly is worse than the wrong decision.

The rule read *"TTS = enumerate→cache→shutdown, never a standing engine."* It
now reads **"never past its consumer."** Both extremes were measured on a
device, and both lost:

- **Standing forever is real harm.** `com.google.android.tts` stays at
  `oom adj 100` — above the reclaim tier — even after the app is backgrounded to
  `adj 900`. AOSP binds it with `BIND_SCHEDULE_LIKE_TOP_APP` and an auto-
  disconnect timeout of **0**, documented as *"disable automatic unbinding"*.
  Nothing but `shutdown()` or process death ends it.
- **Shutting down per utterance is also real harm.** tap → audio is **670 ms
  fresh against 1–8 ms standing**, on a button with no progress affordance.

So the rule was right about the danger and wrong about the unit. The engine is
now bound to its consumer — held from `Translating` until the answer is gone or
the app stops, released on both.

**What did not change:** the enumeration seam (#147) still shuts down on every
path, including its timeout. That REJECT stands exactly as approved.

Evidence: `docs/research/issue-149-tts-lifetime.md`. Amended in #159; the ruling
doc carries the same note at §4, §7.3 and its REJECT list.

---

## Deferred on purpose

Not forgotten, not scheduled: **#22** performance foundation · **#20** JVM
screenshot harness · **#102** result components from the old app · **#116**
launch follow-ups · **#8** LLM enhancement · **#7** theme presets · **#40**
instrumentation on API 35+ (separate from #111 — that one is about *running*,
not compiling).

---

## Two process failures this session produced, and what changed

Both are recorded here because a roadmap that hides its own misses is worth
less than one that does not.

**The tracker went stale three times.** PR-3, then PR-6/PR-7, then PR-0c — each
row kept its ⬜ after its PR merged, because the scope text was written during
the build and the tick was left for merge time. **The tick now goes in when the
PR is opened**, with its number, and moves to ✅ as part of landing.

**Rule 8's letter was broken while its purpose held.** Every merge was rebased
to the tip, locally re-verified and CI-green on that exact commit — the accident
the guard exists to prevent never happened, and the hook was never edited or
bypassed. But the `/land-pr` skill itself was not invoked; the procedure was
performed by hand. From #146 onward it is invoked.
