# Research — issue #130 rev5 completion, Phase 2 audit record

Read-only research record (Mandatory Rule 4). This is the file the completion
plan names as the Phase-2 audit record (`docs/plan/issue-130-rev5-completion.md`
§"Phase 2 — the audit"). Each of the four audit passes writes here: **what was
run, against which commit, what it found, and an explicit line when a pass
returns clean** — because a clean pass that leaves no trace is indistinguishable
from one that was never run.

| Pass | What | Status |
|---|---|---|
| **1** | **`pr-review-toolkit:silent-failure-hunter` over the pack-download path** | **RECORDED BELOW (pass-1 remediation PR)** |
| 2 | `pr-review-toolkit:pr-test-analyzer` over every rev5 test | not in this record (other wave) |
| 3 | `pr-review-toolkit:type-design-analyzer` over rev5 model types | not in this record (other wave) |
| **4** | **frame-by-frame re-derivation of rev5 against the shipped screens — undeduped, per row** | **RECORDED BELOW (wave-1e)** |

---

# Pass 4 — frame-by-frame re-derivation (#183 / #184 / #203)

- **Run:** 2026-08-05, wave-1e (read-only analysis + one committed script).
- **Commit:** `ad41f78` (branch `main`).
- **Inputs:** `docs/design/language-screens/language-screens-spec.html` (9.05 MB,
  the rev5 export); measured device truth in
  `docs/research/issue-130-e-v1-voice-enumeration.md` (§V1, 68 offline voice ids)
  and `docs/research/issue-130-e-s1-storage-walk.md` (§E-S1b, first-run meter).
- **Tooling:** `python3` only. No app build, no Gradle, no emulator/adb — every
  device figure is CITED from the recorded research dumps above, not re-measured.
- **Committed artifact:** `docs/design/language-screens/tools/enumerate-frames.py`
  (the structural frame enumerator #184 asked for, with its mutation self-test).

## 0. The encoding, and the trap it set

The frame markup is not in the raw HTML. It lives inside a single
`<script type="__bundler/template">` block, HTML-entity-escaped around a JSON
string literal. Decoding is **extract the block → `html.unescape()` →
`json.loads()`**, which yields 511 KB of real markup.

A raw `grep` of the 9 MB file is what produced the false "54": it finds **0**
`class="dv-opt"` frame spans (they are written `class=\"dv-opt\"` inside the JSON
string) but **55** `data-screen-label` hits — 54 attributes plus one prose
`<code>data-screen-label</code>` mention. The issue's quoted selector
`<div class="dv-opt" id="20c">` does not match the raw bytes verbatim; it matches
only after decoding. Verified against the decoded bytes before enumerating
(rule 12).

## A. #184 — true frame count is **58**, not 54 (undercount of 4)

### The structural signal (label-independent)

Every drawing is a wrapper `<div>` identified by **either** (a) a monospace
caption div as its first child (`font:500 10px/1 ui-monospace,Menlo,monospace…`,
e.g. `LIGHT` / `DARK` / `TO · LANDSCAPE`) **or** (b) a `data-screen-label`
attribute on the wrapper tag. The union of the two is the complete frame set, and
signal (a) alone already catches every *unlabelled* frame — the property #184
needs.

### Two independent enumerations (rule 12), plus one that disagreed and why

| Method | Result | Note |
|---|---|---|
| Caption-anchored walk (56 captions) ∪ caption-less labelled 21c (2) | **58** | committed script |
| Per-`dv-opt` reconciliation of caption-count vs label-count | **58** | every group symmetric except 20c(2,0), 20e(2,0), 21c(0,2) |
| Direct-child-of-drawings-row heuristic | 53 — **rejected** | miscounts: 20a nests its 10 drawings in sub-columns, so it counts columns not drawings. Recorded because it is the tempting wrong method. |

Reconciliation (all figures from the decoded markup at `ad41f78`):

- **28** `dv-opt` groups (id-level), of which **2 are empty** placeholders with no
  drawings at all: `19i`, `19j`.
- **58** drawings total = **54 labelled** + **4 unlabelled**.
- The 54 label attributes = 50 distinct strings (`light`/`dark` are reused across
  groups) on 52 caption drawings + 2 caption-less `21c` "refused placement"
  drawings.
- The **4 unlabelled** drawings are exactly: **`20c` LIGHT, `20c` DARK, `20e`
  LIGHT, `20e` DARK** — the pack-actions sheet and the free-up-space sheet, light
  and dark each. `20c` and `20e` are confirmed present among the label-less set.
- Incidental: 26 drawings render the full `AndroidDevice` phone frame; the other
  32 are sheets / snackbars / adaptive fragments. The 26 is the origin of the
  docs' "up from 26".

So a label count reports **54** and silently omits the four `20c`/`20e`
drawings. `20c` and `20e` are named in the sentence claiming they were labelled
and are precisely the two that were not.

### The committed script + its mutation test (decided before writing, rule 11)

`docs/design/language-screens/tools/enumerate-frames.py`. Mutation, fixed before
the script existed: *remove one `data-screen-label` from a frame that has one
(`15a picker light`); the STRUCTURAL enumerator must still find it via its
caption; a label enumerator loses it.* `--self-test` output at `ad41f78`:

```
STRUCTURAL FRAME ENUMERATION -- language-screens rev5 spec
dv-opt groups (id-level)        : 28
  empty groups (no drawings)    : 2  ['19i', '19j']
TRUE FRAME (drawing) COUNT      : 58
  carrying a data-screen-label  : 54
  carrying NO label             : 4
label-attribute count (claimed) : 54
Frames carrying NO data-screen-label:
    20c / LIGHT
    20c / DARK
    20e / LIGHT
    20e / DARK
20c present among label-less : True
20e present among label-less : True

MUTATION TEST -- remove data-screen-label '15a picker light'
  [PASS] structural total unchanged (58 -> 58)
  [PASS] label-less frames grew by one (4 -> 5)
  [PASS] mutated frame still enumerated, now label-less
  [PASS] label-only total dropped (54 -> 53)
  [PASS] mutated frame ABSENT from the label-only set (this is the bug it hides)
RESULT: PASS -- structural survives label removal; label-only does not
```

### The doc lines asserting "54" (cited, NOT edited — owner ruling pending)

- `docs/design/language-screens/DESIGNER-BRIEF.md:9` — "**26 labels became 54**,
  so the ten sheets, the five snackbars, 20c, 20e and 21c can no longer be skipped
  by a reviewer enumerating frames."
- `docs/design/language-screens/DESIGNER-BRIEF.md:15` — "…across all **54 frames**.
  **Zero contradictions**…"
- `docs/design/language-screens/README.md:29` — "…including the eight sheets, the
  five snackbars, 20c, 20e and 21c (**54 in total**, up from 26), so enumerating
  labels enumerates the whole document."
- `docs/plan/issue-130-language-rev3.md:749-750` — "…cross-checked against
  `BundledLanguageCatalog.offlineCapableIds` across all **54 frames** — zero
  contradictions."

Corroboration that the enumeration was loose: `DESIGNER-BRIEF.md:9` and `:524`
say "ten sheets" while `README.md:29` says "eight sheets" — the three docs do not
even agree with each other on the sheet count. And `DESIGNER-BRIEF.md:524` (the
original request TO the designer) correctly lists `20c`/`20e` as carrying no
label, while `:9` (the summary) claims they were labelled.

## B. #183 — per-row speaker mark, all adaptive frames

### Method

Frame span isolated by its caption; rows segmented by the **avatar 2-letter code**
(`font-weight:500;letter-spacing:.5px`), which is the language id and is present
in every row across the portrait / landscape / foldable / tablet layouts (the
name-div font size varies by layout — 16 px portrait, 14.5 px landscape — so
anchoring on the name would silently drop the landscape frames, and did in a
first attempt). Per row, `volume_up` in the segment `[avatar_i, avatar_{i+1})` is
row `i`'s mark — the per-row parse the issue demands, never a flattened scan.

**Truth (E-V1, `issue-130-e-v1-voice-enumeration.md:58`, 68 offline voice ids):**
`es`✅ `en`✅ `ar`✅ `sq`✅ · **`af` absent** ❌.

### Validation against the issue's independently-taken numbers (rule 12)

My parser reproduces all three totals the issue stated by hand — this is the
second independent search agreeing with the first:

| Frame | issue says | parser | match |
|---|---|---|---|
| 16a portrait | 3 `volume_up` | 3 (2 row-marks + 1 non-row header speaker) | ✅ |
| to · landscape | 5 | 5 (4 row-marks + 1 non-row legend speaker) | ✅ |
| from · landscape | 0 | 0 | ✅ |

The "3rd" mark in 16a is a **non-language-row** element — a green avatar whose
emblem is itself a `volume_up` glyph — so the honest row-mark count is 2. The
issue's frame total conflated it; per-row it does not touch the es/en/af/ar rows.
**The same non-row artifact recurs in `to · landscape`** — caught by the #296
co-verify, not this pass's first draft. A *"Speaker marks languages the device can
speak offline"* **legend** sits in document order between the Afrikaans and
Albanian rows, and its `volume_up` glyph was miscounted as an Afrikaans row-mark.
Afrikaans's own row draws only `cloud_done` — confirmed across all 26 `af`-row
occurrences in the decoded spec, none draws `volume_up`. So `to · landscape` is
**4** row-marks, not 5, and carries **no** false mark.

### Per-frame totals (11 adaptive drawings)

| Frame | role | rows | row-marks | marked ids |
|---|---|---|---|---|
| 16a portrait · light | target | 12 | 2 | es, en |
| 16a portrait · dark | target | 12 | 2 | es, en |
| to · landscape | target | 17 | 4 | en, ar, bn, bg |
| to · foldable | target | 19 | 5 | es, en, ar, bn, bg |
| to · tablet portrait | target | 10 | 2 | es, en |
| to · tablet landscape | target | 17 | 4 | en, ar, bn, bg |
| from · landscape | source | 17 | 0 | — |
| from · foldable | source | 19 | 0 | — |
| from · tablet portrait | source | 10 | 0 | — |
| from · tablet landscape | source | 17 | 0 | — |
| from · foldable first run | source | 17 | 0 | — |

### Cross-frame disagreement matrix (target frames; `M` = marked, `.` = blank, `-` = row not in frame)

| id | voice? | 16a port | to·land | to·fold | to·tab-port | to·tab-land | verdict |
|---|---|---|---|---|---|---|---|
| es | yes | M | . | M | M | . | **DISAGREE** — 2 of 5 wrongly blank |
| en | yes | M | M | M | M | M | consistent, correct |
| af | **NO** | . | . | . | . | . | consistent — correctly unmarked (the to·land `volume_up` is the Speaker legend, not af's row; cf. the 16a exclusion above) |
| ar | yes | . | M | M | . | M | **DISAGREE** — 2 of 5 wrongly blank |
| sq | yes | . | . | . | . | . | consistent (cast omission — not a contradiction) |
| bn | yes | - | M | M | - | M | consistent where present |
| bg | yes | - | M | M | - | M | consistent where present |

### Counts

- **Rows drawn inconsistently across the target frames: 2 — `es`, `ar`.**
  (The issue named three — `es`/`af`/`ar` — from two frames; extended to all five
  target frames, `af` drops out: it is correctly blank in every frame, and the
  apparent `to · landscape` mark is a non-row legend glyph, not a row-mark. `es`
  and `ar` are each blank in 2 of 5 frames that should carry them.)
- **Affirmatively-false marks (mark on a no-voice id): 0.** The #296 co-verify
  caught the earlier "1 — `af` in `to · landscape`": that `volume_up` is the same
  non-row Speaker-legend artifact already excluded for 16a above — Afrikaans's row
  draws only `cloud_done` (confirmed across all 26 `af`-row occurrences). Every
  real disagreement is a voice-capable row left blank (omission), which the issue
  classes as a cast choice rather than a contradiction.
- **Source frames marking anything: 0 of 5.** All source pickers are blank, which
  is correct — see the code note in Part D.

## C. #203 — the first-run meter draws a state the device cannot produce

- **Frame** (`from · foldable first run`, dv-opt `18b`), decoded verbatim: the meter is
  overline `Offline library`, numeral **`0`**, detail **`of 59 packs · nothing
  downloaded`** — an explicit **0-count** in the same 28px slot the device fills with
  `1`. (The **`No packs yet`** text is a *separate* element — the suggestion-list
  overline, the owner's intentional §8 first-run design, out of scope; it is NOT the
  meter's text. Corrected per the #203 re-verify — three independent DOM parses.)
- **Device** (E-S1b, `issue-130-e-s1-storage-walk.md:167-178`, `pm clear` on
  `emulator-5554`, 2026-08-02): meter numeral **`1`**, detail
  **`of 59 packs · 8.6 GB free`**, top bar `1 of 59 on device`, English row
  content-desc `English, on device`, badge `On device`. The `Unsized` state.

The frame depicts a state a Play-Services device **cannot produce**: ML Kit
reports the English pivot as on-device from first launch, so the count is never 0.
Confirmed clean — the frame and the device genuinely disagree, exactly as #203
states.

## D. The rule-13 verdict — reference DRAWING, or rev5 DESIGN?

The decisive question for every finding: does it change **what Phase 3 must
build**, or only correct a **reference drawing** while the shipped app is already
right?

### #183 — reference-only. The app derives marks from the device, not the frames.

- `LanguageRepositoryImpl.kt:101-109` builds every row with
  `hasOfflineVoice = language.id in voiceIds`, where `voiceIds` is
  `offlineVoices.offlineVoiceLanguageIds()` — the device's enumerated TTS voices
  (`OfflineVoiceCatalog.kt`). Comment, line 104-105: *"an id the device cannot
  speak simply carries no mark."*
- `LanguagePickerModel.kt:615`:
  `fun LanguagePickerRow.showsVoiceMark(role) = role == LanguageRole.TARGET && hasOfflineVoice`
  — *"only a target picker shows it"* (`:587`). This **exactly** matches the
  frames' own split: every `from ·` (source) frame draws 0 marks, matching the
  app; every `to ·` (target) frame draws them.
- Therefore the shipped app marks `es`/`en`/`ar`/`sq` and not `af`, in target
  pickers only, **regardless of what any frame draws.** The `to ·` frames'
  disagreements (es/ar) are inconsistencies in the
  *reference art*; they never reach the built screen because the mark set is
  computed from device truth, not copied from a drawing.
- **Verdict: reference-drawing correction. Not design-invalidating. PR-13…PR-16
  are unaffected** — their instruction is "mark = `hasOfflineVoice` from the
  device, target picker only," which is already the code, and they must ignore the
  frames' illustrative cast.

### #203 — reference-only. The app already draws "1 of 59 packs" and the code says so.

- `MlKitModelStore.downloadedTags()` (`RealOfflineModelManager.kt:62-67`) reads
  `RemoteModelManager.getDownloadedModels(TranslateRemoteModel)` and seeds
  nothing; the initial `emptySet()` is replaced by ML Kit's real answer. The `1`
  is Play Services', not this app's bookkeeping. `BundledLanguageCatalog.kt:317`
  seeds `offlineDownloaded = false` per row, overlaid with per-device truth.
- `LanguagePickerModel.kt:974-984` (the meter model KDoc) states it outright:
  *"the first card a new user sees is therefore [Unsized] — '1 of 59 packs · 8.6
  GB free' — and the export's `from · foldable first run` frame, which draws '0 ·
  nothing downloaded', is not what the device does."* Pinned by
  `OfflineLibraryMeterTest` ("a first run reports free space because the pivot
  pack already counts").
- **Verdict: reference-drawing correction. Not design-invalidating.** The code
  was already corrected (PR-200); only the drawing remains wrong. PR-21 (and any
  meter-drawing PR) builds `Unsized` on first run, which is the current behaviour.

### #184 — reference/process-only. A miscount of the reference set; no app behaviour.

- The "54" is a count of drawings in the reference export and a claim about a
  review method. Nothing in `feature/language` or `core/*` reads it. The true
  count is 58; the enumeration must be structural (now committed).
- **Verdict: reference + process correction. Not design-invalidating.** It gates
  the *reviewability* of PR-24 (builds 20c) and PR-25 (builds 20e) — both need
  those two frames to be findable — but it changes no rev5 design decision.

### Overall

**All three findings are reference-drawing / reference-count corrections. None
changes what Phase 3 must build.** The rev5 *design* is intact; the shipped app is
already on the correct side of every one of these three. **Therefore Phase 3's PR
list (PR-20…PR-28) is NOT stale on account of #183, #184 or #203, and the #256
ruling does not reopen for them.** (This addresses the plan's explicit worry that
#203 and #184 might be "frames that may not describe a producible state" — they
are not producible, but the app never tried to produce them, so the *code* is the
authority and it is right.)

## E. Owner decisions still needed (evidence produced; ruling is the owner's)

- **#183 — pick the correction policy for the `to ·` frames.** Options: (a)
  rev6-redraw all target frames to one consistent, device-true cast (mark
  `es`/`en`/`ar` where present, never `af`); or (b) declare **16a portrait
  authoritative** and annotate the landscape/foldable/tablet frames as
  illustrative. Either way the concrete fixes are: add `es` to `to · landscape` and
  `to · tablet landscape`, add `ar` to `16a portrait` and `to · tablet portrait`,
  and state whether `sq` (and the other capable-but-blank rows) are a deliberate
  partial cast. Not pre-empted here (#183 is an owner-decision fork).
- **#184 — approve the structural enumerator as the inventory and correct the
  four "54" doc lines** (cited in Part A) to **58**, with the correction stated
  rather than silently replaced. Decide separately whether to also add
  `data-screen-label` to `20c`/`20e` or to retire the label-as-inventory idea in
  favour of the committed script. Docs left unedited pending this ruling.
- **#203 — redraw-to-1-pack vs deliberate-empty-library caveat.** Either redraw
  `from · foldable first run`'s meter as `1 · of 59 packs · <free space>` with a
  caption noting the pivot is present before any download, or keep an empty-library
  frame but label it as the no-Play-Services / fake-flavour case, since that is the
  only device that can produce it. Owner-decision fork; not pre-empted.

## F. What this pass did NOT do

- Did not edit the design frames, the spec, or the three "54" docs (owner forks on
  #183/#203; #184's doc correction is the owner's to approve).
- Did not run passes 1-3 (silent-failure-hunter, pr-test-analyzer,
  type-design-analyzer) — those are other waves and are not recorded here.
- Did not re-measure device state; every device figure is cited from the E-V1 and
  E-S1b records at the lines named above.

---

# Pass 1 — silent-failure-hunter (the pack-download path)

- **Run:** 2026-08-06, phase-2 pass 1 (read-only analysis; the two defects
  remediated in the same PR that records this).
- **Commit audited:** `960cd03` (branch `main` tip — `Merge #317`, PR-24 landed).
- **Scope:** the offline-model download path end to end — the app-shell observer
  (`app/.../navigation/DownloadEventsViewModel.kt`), the manager
  (`core/translate/.../RealOfflineModelManager.kt`), the gate
  (`core/domain/.../DownloadGate.kt`), and the two screen ViewModels' refusal
  handling — i.e. the code merged by **PR-20…PR-24**. PR-25 (20e "Free up space")
  and PR-26 (20d list-detail) are not yet merged; their deltas are to be
  re-checked in the **final** pass, not here.
- **Method:** trace every place a synchronous pre-flight can refuse or a binder
  read can throw, and ask two questions — *does a refusal reach the user, and can
  a throw reach `Thread.defaultUncaughtExceptionHandler`?*

## Finding 1 — #314 (S2): the app-shell snackbar actions swallowed a refusal

**FIXED in this PR.** `DownloadEventsViewModel.onRetry` (`:106`),
`onDownloadAgain` (`:98`) and `onConsentOnce` (`:116`) each called
`modelManager.download()` / `downloadGate.requestDownload()` /
`downloadGate.downloadConsented()` and **discarded** the returned
`DownloadAttempt`. When the tap is refused before enqueue — `Refused(NETWORK)`
because still offline (`RealOfflineModelManager.kt:395-399`), `Refused(STORAGE)`
because the disk is still full (`:425-429`) — the manager writes a value-EQUAL
`Failed` map (a `data class`, so the conflating `modelStates()` does not re-emit)
and fires **no** `PackEvent`. So the snackbar's Retry / Download again / Download
now was a **silent no-op** behind an enabled control — the exact #234/#250
dead-end, but on the app-shell surface, which the two screen ViewModels had
already fixed on theirs.

The shell was **the one caller that ignored the attempt** — confirmed by reading
all three methods against `OfflineLanguagesViewModel.reportOutcome`/`refusals` and
`LanguagePickerViewModel`, which already capture it. Remediation mirrors that
seam: a `Channel<String>` (`refusals`) carrying the pack tag, fed by a
`reportOutcome` that sends only on `Refused`; the app shell (`TranzlateApp.kt`)
collects it into a message-only snackbar on the existing app-shell `SnackbarHost`.

**One deviation, stated because the brief named a specific string.** The two
management screens draw the CAUSE-specific line
(`downloadFailureCopy(cause).rowLine` → *"Not enough space…"* / *"No
connection…"*). That resolver is **`internal` to `:feature:language`**, so the
`:app` shell cannot call it, and re-spelling the cause→string map in `:app` is the
third copy the rev3 ruling REJECT §7.8 and `DownloadFailureSourceTest` forbid. The
shell therefore shows the language-named generic *"Couldn't download X"* (the
public `packSnackbarMessage(PackSnackbarKind.FAILED)` seam) — visible feedback,
which is the S2. Raising it to the cause-specific line needs a one-line visibility
widening of `downloadFailureCopy` in `:feature:language`, out of this PR's
ownership (a second agent held that module); flagged for the owner to route.

## Finding 2 — #319 (S3, crash-class): an unguarded connectivity binder read

**FIXED in this PR.** `RealOfflineModelManager.download()` at `:395` did a bare
`if (!connectivity.isOnline())`. `isOnline()` is a synchronous binder IPC into
`ConnectivityManager`, and it runs on the **caller's** coroutine — outside the
`try/catch` that wraps the launched transfer — with the tap dispatched on a bare
`viewModelScope`. A binder throw there (`DeadObjectException`, a
Play-Services/ConnectivityManager crash) went straight to
`Thread.defaultUncaughtExceptionHandler`: the app vanishing on a download tap.
This is the **#238 crash class**, and the fix is the one already applied twice
next to it — the free-space probe immediately below (`:415-424`) and `DownloadGate`
(`isOnline()`/`isMetered()`, #248/#280) both guard the identical shape.
Remediation wraps the read in the SAME idiom: rethrow `CancellationException`,
treat any other `Throwable` as *unreadable → proceed* (never refuse on unknown,
matching the free-space probe's degrade contract), so a genuinely offline transfer
still lands `Failed(NETWORK)` through the bounded wait's own path.

## Finding 3 — observability (acceptable as-is, recorded not fixed)

`LanguagePickerViewModel.stampSafely` and `OfflineLanguagesViewModel.savedCountOf`
each swallow a `Throwable` without logging. Judged **acceptable**: both are
best-effort side jobs whose failure is correctly non-fatal (a missing usage stamp;
a saved-count that falls back to 0 rather than blocking a pack removal), and both
already rethrow `CancellationException` first. The only improvement available is a
`logError` for field diagnosis — an **optional future**, not a defect. Both files
are in `:feature:language` and out of this PR's scope regardless.

## Areas audited CLEAN (the pack-download error handling is otherwise disciplined)

Verified by reading each against its source at `960cd03`:

- **The launched transfer** (`RealOfflineModelManager.kt:434-465`) rethrows
  `CancellationException` first (`:448`), catches `Exception` for the async
  failure, and only the owning job publishes an outcome.
- **`bounded()`** (`:131-140`) converts a `TimeoutCancellationException` to an
  `IOException` on purpose, so a timeout lands in the failure path and is NOT
  misread as a user Stop — a subtlety a naive `withTimeout` would get wrong.
- **`refreshDownloaded()`** (`:577-590`) and **the refresh worker** (`:298-314`)
  both rethrow `CancellationException` and keep the last-known truth on any other
  failure; the worker's `Throwable` catch is deliberate and documented (it is the
  sole reader of the request channel).
- **The free-space probe** (`:415-424`) — the sibling of Finding 2 — was already
  `Throwable`-guarded with proceed-on-unknown (#238).
- **`DownloadGate.requestDownload`** (`:112-147`) guards BOTH the standing-pref
  read and the `isOnline()`/`isMetered()` reads, rethrowing `CancellationException`
  and asking-not-assuming on any other throw (#248/#280).
- **The delete path** (`:499-555`) mirrors the download path's ownership +
  cancellation discipline.
- **The U-1 `packEvents`** channel is `replay = 0`, drop-oldest, `tryEmit` — a
  backgrounded app loses notices and reads truth from the state map on return.

## Out-of-scope, same class — recommend a follow-up issue (NOT fixed here)

`RealTranslator.kt:178` has a **structurally identical** unguarded
`if (!connectivity.isOnline())` pre-flight in the translation waterfall. It is the
same #238/#319 read; whether it can crash depends on its caller's handler, which
this pass did not trace. It is a different file, outside this PR's ownership, so it
is recorded here for a **#238-class follow-up review**, not fixed.
