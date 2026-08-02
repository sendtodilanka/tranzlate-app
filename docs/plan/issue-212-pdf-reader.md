---
issue: 212
slug: pdf-reader
type: plan
status: accepted
severity: medium
priority: p2
owner: dilanka
risk: medium
---

# Plan — issue #212: PDF Reader feature vertical

status: accepted
(accepted basis: owner-mandated design debate, 3 differently-primed architects →
adversarial judge → verifier who re-ran 22 file:line citations against the live
repo. Two enumeration claims made during the debate were REFUTED by that
verifier and are corrected below — see §Risks R5.)

## Context

The Home Tools card currently labelled "Offline mode" becomes the entry point to
a new `PdfScreen`: pick a PDF, read it, tap a word for its meaning, translate a
page. No code exists yet. Two facts, verified against shipped artifacts rather
than docs, constrain the offline story:

- ML Kit translation ships **58 models, every one an English↔X pair**. There is
  no standalone English model. 58 partners + English = the 59 offline languages
  the app already lists.
- A live measurement across 49 targets found **42 return dictionary data, 7 do
  not**. The dictionary panel is a per-language capability; where none exists the
  design shows a plain translation, not an empty panel.

Phase order (owner-agreed): 1 reader only · 2 word lookup sheet · 3 reflowed
reader · 4 OCR for scanned PDFs · 5 deferred GT-style overlay. Mixed documents
translate the text layer and state plainly that image text was skipped. Mixed
scripts on one page are out of scope for v1. Monetisation/quota deferred by the
owner. Worldwide, no country- or language-specific tailoring.

## Options debated

| # | Question | Options | Ruling |
|---|---|---|---|
| 1 | Viewer | `androidx.pdf` 1.0.0-alpha19 · framework `PdfRenderer` · 3rd-party | framework |
| 2 | Modules | extraction inside `:core:translate` · own home | own home |
| 3 | Script detection | scored 5-way waterfall · ordered escalation | ordered escalation |
| 4 | Reflow | plan now · gate on measurement | gate |
| 5 | Language controls | keep swap for parity · drop swap | drop swap |
| 6 | Large docs | prefetch · page-at-a-time | page-at-a-time |

## Decision

### D1 — Viewer: framework `PdfRenderer`, alpha rejected

Alpha rejected on API churn (unstable surface, sole consumer, unpaid maintenance
on a critical path), on the minSdk 24 → 28 cost imposed on five features that do
not need it, and on having no fallback if it is abandoned pre-1.0. Third-party
viewers rejected — the mature ones are AGPL/commercial dual-licence and the
project has no licence-review process. The alpha re-opens exactly once, if
**E2′** shows no framework page-text-with-bounds below API 28; even then it is
confined to `:core:document` behind an interface.

**No-dead-end:** hiding the Home card below API 28 is FORBIDDEN. The card is
visible on every supported device. Capability gaps are stated inside `PdfScreen`
in the existing `Blocked` vocabulary with a present-tense action, while
open/render/page/zoom still work. Availability is decided per capability, never
by deleting the entry point.

**The Home card is RENAMED over "Offline mode".** The debate ruled "added, not
renamed, *unless the same PR ships an Offline entry elsewhere*" and cited
`SettingsScreen.kt:135-136` for Home being Offline's "deliberate sole entry".
Co-verify (PR #225) refuted the citation and the premise, and both corrections
are adopted here:

- The comment says the **opposite**: *"History lives HERE — the design has no
  drawer, and Offline languages already has its **Home entries**."* Plural, and
  it is the reason History was routed to Settings **because** Home already has
  redundant Offline access — not a claim of exclusivity.
- The debate's own escape condition is therefore **already satisfied**:
  `HomeScreen.kt:446` (the "Download languages" `ListRowCard`) calls the same
  `onOpenLanguages` as the tool card at `:400`, so the offline manager keeps a
  Home entry after the rename. The rename is the owner's decision and it stands.

Enumerated below rather than asserted, because the "2 found" this paragraph
originally carried was the count for **one symbol in code only**, and the rename
touches four reference classes. See §Risks R5.

### D2 — Module graph

```
Ring 3  :core:document   open Uri · page count · render page → bitmap ·
                         page text + bounds.  No translation type, no Compose.
Ring 3  :core:ocr        PEER of :core:translate. Recogniser selection,
                         script detection, image → text.
Ring 4  :feature:pdf     asks only. deps: :core:document, :core:translate,
                         :feature:language (picker), :core:ocr (phase 4).
```

Extraction does **not** live in the Translation brain: the brain is text-in /
text-out; reading bytes out of a container format is a different job with a
different failure vocabulary. Tests follow repo precedent — Robolectric render
tests in `feature/pdf/src/test/` (cf. `feature/language/.../PickerRowRenderTest.kt`),
instrumented end-to-end in `app/src/androidTestProd/` (cf.
`TextTranslationScreenTest.kt:35`). This repo has **zero** instrumented tests in
any feature module.

### D3 — Script detection: ordered escalation

Scored waterfall rejected: "volume × confidence" has no cross-recogniser
calibration — a Latin recogniser fed Devanagari emits confident garbage in
volume — and it pays for five model loads per document.

1. Run Latin first.
2. Accept iff the existing ML Kit language identifier returns non-null **AND**
   the identified script matches the recogniser that produced the text. The
   identifier's below-threshold-returns-nothing behaviour is the guard.
3. On rejection, escalate to the next recogniser.
4. Full five-deep escalation runs **once**, on the first non-blank sampled page,
   max 3 blank skips.
5. Advance to another sample page only on a **structural zero** (every
   recogniser returned zero text units) — never on ink coverage, which misfires
   both ways on scans (paper grain reads as ink; a full-page figure reads as ink).

States: no network with unbundled models → `Blocked(models unavailable)` + retry,
NOT "unsupported" (different facts, different states). All-undetermined →
`Blocked(OCR_UNSUPPORTED)` with a present-tense recovery action. **The "offer the
camera path" promise is deleted** — `feature/camera/.../CameraScreen.kt` is 45
lines whose own KDoc calls it a scaffold placeholder.

Consent, stated as two separate claims: (a) the `DownloadGate` consent/metered
layer is generic over an opaque `id: String` (`DownloadGate.kt:79-121`) and
reuses cleanly; (b) the transfer/progress adapter behind it wraps
`RemoteModelManager.download()` specifically (`RealOfflineModelManager.kt:65-75`)
and does **not** automatically reuse. Time cost and first-run download size are
**not estimated here** — E3 measures them before phase 4 is sized in either
direction.

### D4 — Reflow: not plannable, gated

Phase 3 enters this plan as `blocked-on-E2′`: no paragraph algorithm, no
estimate, no acceptance criteria until granularity is measured. Decision rules
are fixed **before** the run (mutate-first): block-level → proceed as scoped;
line-level → add a line-joining sub-task; word-level → **re-scope, not
re-estimate**; must hold on ≥5 of 6 layout files or treat as word-level.

### D5 — Language controls

One **target pill** in the app bar. **No swap** — a document's language is a
property of the file; swap would mean "translate my translation back", a
different operation, not the composer's mirror. C-7 governs composer swap and
does not reach here. A **persistent low-emphasis non-pill source label**, always
visible; tapping it opens the **existing** `feature/language` picker in source
mode — no new surface, and no app-bar overflow menu (repo-wide grep for
`DropdownMenu`/`MoreVert`: zero hits, so that pattern would be invented for one
screen). An explicit override is **sticky for the document**; a later page
disagreeing raises the label's attention state and never silently changes the
language. New state `AlreadyInTargetLanguage` (detected source == target): the
bottom translate action renders disabled/relabelled with an inline reason rather
than repainting identical text, and re-enables reactively without a remount.
Translate action is a bottom-**docked bar** (not a FAB), verified at
Medium/Expanded and landscape per C-13. Every state ships `@PreviewLightDark`
per rule 7.

### D6 — Large documents

- **Memory:** one bitmap for the visible page + a small LRU whose budget is
  derived from viewport size at runtime and invalidated on fold/rotation/config
  change. Never a fixed count; never the whole document.
- **Quota:** pull-based, per visible page. No whole-document prefetch — 4 pages
  read costs 4 pages.
- **Cache key:** content-derived (content hash + page index + engine + target
  language). **Never the SAF Uri** — it both misses across sessions and collides,
  because provider document ids are reusable.
- **Cancellation:** tri-state Idle / Running / Cancelled. Cancel restores the
  previous original text (never blanks the page); an in-flight page is dropped
  without partial paint; pages never started are never charged; Cancelled is a
  real UI state with a way forward.
- Flagged, not silently inherited: if E3 shows OCR artifacts use dynamic
  delivery, that path may have no mid-install cancellation, which collides with
  this section.

## Decisive test

**E2′ — granularity + API floor (run first; the only run that answers D1 and D4
together).** One throwaway instrumented test in a scratch `:core:document`
opens 9 committed asset PDFs — single-column export, two-column paper,
justified+hyphenated, table-heavy, RTL Arabic, image-only negative control,
password-protected, cloud-provider Uri, one reopened after process death — and
dumps every text unit's string and rect to CSV. API 24 / 28 / 33 / latest, plus
`Tranzlate_Resizable` including a fold event. Reports units-per-page,
chars-per-unit, internal-space fraction, rect clustering, emission order, and the
lowest API exposing page text with rectangles at all. Decision rules per D4.

**E3 — OCR artifact spike (second).** Per text-recognition artifact, record the
Maven **group first** (`com.google.mlkit` vs `com.google.android.gms:play-services-mlkit-*`
— `gradle/libs.versions.toml:125-126` shows this project already uses the
standalone group for translate and language-id; group membership decides delivery
mechanism), then APK/AAB size delta, first-call behaviour online on a fresh
install, and first-call behaviour in airplane mode. Also settles the unverified
"GMS-less device" claim raised in debate, which is a line item here — **not** an
issue filed on an assumption.

**E1′ — English pivot (parallel, **filed as #224**, does not block this plan).**

Narrowed after filing: the *translation gate* question the debate inherited is
**already answered in this repo, twice**, and neither record was read before the
issue was scoped — `docs/research/issue-94-en-pivot-check.md` (experiment,
2026-07-30, Resizable AVD, wifi+data off) recorded fr→de translating offline with
only `de_en` + `en_fr` on disk, and `LanguagePickerModel.kt:976-977` records a
`pm clear` run on `emulator-5554` (2026-08-02) measuring a first-run downloaded
count of **1**. `MlKitEngine.kt:39-42` is therefore **not** rejecting English→X.
What survives is narrower and still unmeasured: the row's **controls**.

`pm clear` → launch → tap Delete on the English row → read the downloaded set →
run en→fr in airplane mode, record the `AttemptCause` → then download any pack
and re-check whether English returns (recoverability is part of the answer, not
an assumption). Reachability is already
settled in source: `OfflineLanguagesViewModel.kt:62-69` filters only on
`offlineAvailable`; `OfflineLanguagesScreen.kt:243-247` renders an unconditional
delete button; `OfflineLanguagesViewModel.kt:115-116` calls delete
unconditionally — on a row whose own KDoc (`LanguagePickerModel.kt:976-977`)
documents English as reported on-device before anything is downloaded, and
`RealOfflineModelManager.kt:280-310` republishes ML Kit's answer unconditionally
(so the outcome is ML Kit's behaviour, not our stale state). Both outcomes are
shipped-app defects: a no-op Delete (a control that visibly does nothing) or a
real delete (`en` leaves the set → `MODEL_NOT_DOWNLOADED` for every pair, with no
standalone English model to restore).

## Risks

- **R1 (key) — E2′ returns word-level.** Reflow stops being a phase with a
  joining step and becomes paragraph reconstruction (word ordering, column
  detection, hyphenation, RTL). That is an owner re-scope decision, not a
  schedule slip absorbed quietly.
- **R2 — OCR cost is unknown until E3.** Phase 4 carries no estimate. If the
  recognisers are bundled, the cost is install size and `DownloadGate` is not
  involved at all; if dynamic, cancellation semantics collide with D6.
- **R3 — no camera fallback exists.** `Blocked(OCR_UNSUPPORTED)` must ship a
  recovery action that exists **today**. A camera hand-off becomes possible only
  when `:feature:camera` is more than its placeholder — that is that vertical's
  issue, not this one.
- **R4 — U1 is a live shipped-app gap** reachable from Settings by any user
  today, independent of this feature. Own issue, opened before this plan lands.
- **R5 — enumeration hygiene.** Two claims made during this debate were refuted
  by the verifier: a "14 call sites" figure for `tt_home_tool_offline` (real
  count: **2**) and a grep whose `overflow` term matched `TextOverflow`
  throughout the codebase (the conclusion "no overflow menu exists" survives on
  the `DropdownMenu`/`MoreVert` grep alone). Every count in this document was
  re-run. Any number added later is re-grepped before it appears in a PR body,
  per rule 11.

  **The corrected figure was itself incomplete, twice.** A co-verify lens re-ran
  both greps literally and found each returns MORE than claimed — and the cause
  is this document: once committed, the prose describing a search becomes a hit
  for that search. So a raw hit count is the wrong unit. The rename is
  enumerated **by reference class**, each re-run against the tree with this plan
  excluded:

  | class | count | where |
  |---|---|---|
  | Kotlin (`feature/text/.../HomeScreen.kt`) | **5 lines, 2 sites** | `:396` `:397` `:401` the live card · **`:920` `:921` the `@PreviewLightDark` sample**, which carries its own `tt_preview_tool_offline` and no search for the tag would have found |
  | string resources | **6 lines, 3 locales** | `values/strings.xml:37,42` · `values-fil:32,33` · `values-pt-rBR:32,33` |
  | test | **1** | `NavShellSmokeTest.kt:40` |
  | documents | **8** | `DECISIONS.md:77` (C-1 tag registry, **authoritative**) · `STRINGS_text-translation.md:242` (string registry, **authoritative**) · `specs/01-text-translation.md:69` · `docs/design/UI_SPEC.md:40` (design table, plain prose) · **`docs/CLEAN_ROOM_UX_REQUIREMENTS.md:13,32,77`** (the navigation contract — names the card three times in live prose) · `VerifyStringKeyDocsTask.kt:42-43` (KDoc example) · `launch-blockers.md:87` · `launch-readiness.md:172` |

  **The table was 7 on the first correction, and co-verify found the eighth** —
  `CLEAN_ROOM_UX_REQUIREMENTS.md`, the navigation contract, which names the card
  three times in live prose. Two search bugs hid it, and both are worth naming
  because they are the same shape as the ones already listed above:

  - a pathspec bug — `git grep -- 'docs/**/*.md'` does **not** match files sitting
    directly in `docs/`, so a sweep that reads as exhaustive silently skipped the
    top level. It returned 6 files where `docs/*.md` returns 7.
  - a substring bug — the human phrase `Offline mode` is a **prefix of
    `Offline model`**, so a plain grep also matches `CLAUDE.md:83`
    ("Offline model download") and `docs/audit/…:132` ("Offline models"), neither
    of which is the card. This is exactly the `overflow` → `TextOverflow`
    failure the debate's own verifier caught, recurring on a different token.
    `\b` is not the fix here — see **#221**, where `git grep -E` was found to
    match nothing for `\b`; the working form was `grep -rnE "Offline mode([^l]|$)"`.

  Two consequences the "2" hid. **`STRINGS_text-translation.md:242` is a build
  gate, not a note** — `VerifyStringKeyDocsTask` checks resource keys against
  those docs, so renaming the key without the doc row fails the build rather
  than merely going stale. Co-verify **ran** that gate rather than reading it:
  renaming the key in `strings.xml` with the catalogue untouched fails
  `./gradlew verifyStringKeyDocs` with *"1 of 216 shipped keys appear in no
  STRINGS_*.md catalogue"*. And the **preview** at `HomeScreen.kt:920-921` renders
  the same strings under a different tag, so a rename that misses it ships a
  preview contradicting the screen, which is exactly what rule 7's previews exist
  to prevent.

  The second search — `git grep -n "onOpenLanguages"`, by destination rather than
  by symbol — is what independently clears the rename: **two** `onClick =
  onOpenLanguages` sites in code, `HomeScreen.kt:400` (the tool card) and `:446`
  (the download row). Neither search could have found both facts, and neither
  found the string key, the locales, the design table or the STRINGS gate. Three
  searches were needed and two were named. **The rule that failed here is not
  "re-grep" — it was followed — it is "enumerate by reference class, not by
  symbol".**
- **R7 — D3's accept-condition names a mapping that does not exist** (co-verify,
  PR #225). The rule is *"accept iff the identifier returns non-null AND the
  identified script matches the recogniser"*. But `MlKitLanguageIdentifier.kt:21-25`
  returns a **BCP-47 language tag** or `null` — never a script. Comparing `"hi"`
  against the Devanagari recogniser needs a language→script-family table, and a
  repo-wide search finds none. This is the same shape of gap D4 is explicit
  about and this ruling was not: **flagged open, owed before phase 4 starts**,
  with its acceptance criteria written before the table is. It does not affect
  phases 1-3.
- **R6 — edge cases owed by every phase, from the corpus above:**
  password-protected PDFs, Uri grant lifetime after process death,
  cloud-provider Uris with no seekable descriptor, fold/rotation invalidating the
  bitmap budget, cancel restoring rather than blanking, and RTL interaction
  between the source label and the docked bar verified in the **same** RTL
  preview, not two separate ones.
