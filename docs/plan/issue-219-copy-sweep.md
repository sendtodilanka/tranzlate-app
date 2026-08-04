# Plan — wave 1c: one pack size, one verb, the third failure sentence

status: accepted
(accepted basis: `docs/plan/issue-130-rev5-completion.md` wave **1c**, which is
`status: accepted` and names these issues, this owner and this file set. This doc
adds the two decisions that plan left open — the pack-size figure and #230's
design call — and records what wave 1c does NOT close.)

Refs: #130 (the epic) · #256 (the completion plan) · **Fixes #219 #229 #243** ·
**Refs #230 #224**.
Tracker: `docs/plan/ROADMAP.md`. Design authority:
`docs/plan/issue-130-language-rev3-ruling.md`.

**PR: #262.**

## Scope, and the boundary that produced it

Wave 1c is *"copy and previews — one string sweep, one owner"*. File ownership was
declared before dispatch (rev5 plan, standing constraints) and is binding:

| Owned | Not owned |
|---|---|
| `feature/language/src/main/res/**` | `.claude/**` · `build-logic/**` · `.github/**` · `core/**` |
| `feature/settings/src/main/res/**` | any ViewModel |
| `docs/specs/00-foundations/STRINGS_*.md` | any other feature module |
| `@PreviewLightDark` bodies in `LanguagePickerScreen.kt` / `OfflineLanguagesScreen.kt` | |

**Two files were touched beyond that grant, and both are disclosed rather than
assumed:** `PackFailureSheets.kt` and `RemovePackSheets.kt` receive
`@PreviewLightDark` functions and nothing else — no production line, no signature,
no tag. They are in the same module, on neither prohibition, and no live sibling PR
owns them (#233 owns `.claude/hookify.*` and `.claude/agents/`; #257 owns
`.claude/hooks/*.sh` and `.claude/settings.json`). #243 cannot close without them,
because rule 7's letter names item-level composables and all four live there.

`feature/language/src/test/**` is touched for the two tests below. Not in the
grant, not in the prohibition, and a string change that breaks a test the author
may not fix is not a shippable change under rule 6.

---

## 1. #219 — the pack-size figure, and what it rests on

### The state before

Four user-facing strings state a pack size, in three wordings, and the two
`~30 MB` ones are contradicted by every measurement this project has.

| string | said |
|---|---|
| `feature/settings/…/strings.xml:25` `settings_mobile_data_supporting` | `about 30 MB each` |
| `feature/language/…/strings.xml:56` `lang_sheet_space_body` | `20–45 MB` |
| `feature/language/…/strings.xml:69` `lang_sheet_data_body` | `20–45 MB` |
| `feature/language/…/strings.xml:177` `offline_subtitle` | `~30MB each` |

The issue's own remedy: *"One number, defined once, referenced everywhere — and
derived from measurement rather than from an estimate that has drifted. Re-measure
across a spread of packs before picking the range; two samples is not a range."*

### The measurement — 58 packs, not two

Two samples is not a range, and this session did not need to settle for two. The
sizes of **every** ML Kit translate model are declared in the shipped artifact, in
the same file #224 already read for the model NAMES:

```
$ grep -n mlkitTranslate gradle/libs.versions.toml
21:mlkitTranslate = "17.0.3"

$ unzip -o -q ~/.gradle/caches/modules-2/files-2.1/com.google.mlkit/translate/\
17.0.3/7188883b661bc7a2c95f174fe2d47b04ff20f2e2/translate-17.0.3.aar \
  -d /tmp/aar && python3 - <<'PY'
import json, statistics
d = json.load(open('/tmp/aar/res/raw/translate_models_metadata.json'))
hi = d['PKG_HIGH']; MB = 1e6
sz = sorted(e['SZ'] for e in hi.values())
dl = sorted(e['DL_SZ'] for e in hi.values())
print('n', len(hi))
print('SZ    min %.1f  median %.1f  mean %.1f  max %.1f MB'
      % (sz[0]/MB, statistics.median(sz)/MB, statistics.mean(sz)/MB, sz[-1]/MB))
print('DL_SZ min %.1f  median %.1f  max %.1f MB'
      % (dl[0]/MB, statistics.median(dl)/MB, dl[-1]/MB))
print('in 40-50 MB:', sum(1 for s in sz if 40*MB <= s < 50*MB))
PY
n 58
SZ    min 39.5  median 43.7  mean 44.4  max 63.4 MB
DL_SZ min 31.3  median 34.4  max 48.0 MB
in 40-50 MB: 53
```

- **`SZ`** = bytes on disk after install. **`DL_SZ`** = bytes transferred.
- Smallest on disk `be_en` **39.5 MB**; largest `en_ja` **63.4 MB**; 53 of 58
  between 40 and 50 MB. The three above 50 are `en_ko` 52.7, `en_ur` 52.9,
  `en_ja` 63.4.
- All 58 `PKG_HIGH` entries advertise `NMT` and all 58 match `X_en`/`en_X`.
  `PKG_LOW` is **not** a translation set — **0 of its 28 entries advertise `NMT`**
  (they are `OCR`/`TRANSLIT`), and one of them is `ar_de`, a pair with no English
  in it at all. It is excluded from every figure above.

**Why the manifest is trusted over an estimate, stated as a check rather than as a
preference.** `PKG_HIGH['af_en']['SZ']` is **44,169,505** bytes. The number this
project measured by walking ML Kit's model store on a device (E-S1,
`docs/research/issue-130-e-s1-storage-walk.md`, recorded at
`docs/plan/issue-130-language-rev3.md:438`) is **44,169,505** bytes. Byte-identical
— so `SZ` is the on-disk footprint and not a manifest guess. The second device
sample, `de_en` at 45.7 MB (#90 E3), sits beside a manifest `SZ` of 46.6 MB; that
measurement was a folder walk taken mid-lifecycle and the ~2% gap is not
reconciled here, only recorded. It changes no digit of the decision.

### The decision — `40–65 MB`, in all four strings

One figure, one meaning, four places. It covers **every** one of the 58 packs once
39.5 is rounded down to 40 and 63.4 rounded up to 65.

**Why the on-disk figure and not the download figure**, since they are genuinely
different quantities (40–65 vs 31–48):

1. Every one of the four sentences is a statement about **how big a language pack
   is**. None of them claims to state how many bytes cross the network — the
   mobile-data pair says *"Your plan may charge for it"* and *"you're asked before
   mobile data is used"*, which is the warning; the size is context. So the on-disk
   figure is literally true in all four.
2. The harms are asymmetric. Understating what a pack costs is the exact harm
   #219 was filed about — *"a user who freed 'about 30 MB' on the strength of
   `offline_subtitle` still cannot download"*. Overstating a data estimate costs a
   user nothing but a pleasant surprise.
3. Two figures in four places is the shape the issue exists to end. A reader who
   met `40–65 MB` on one screen and `30–50 MB` on another would be back where they
   started, and *"they are different quantities"* is a defence that lives in a plan
   doc and not in front of the user.

**Rejected: `40–50 MB`** (true of 53 of 58, tighter, more useful in the common
case). Rejected because the three packs it excludes include Japanese, and the user
it fails is the one freeing space on the strength of the sentence — harm (2).

**Rejected: `about 45 MB`** (median 43.7, mean 44.4). A single central figure reads
as more precision than a range and states nothing about the spread, and 19b's
sentence is arithmetic the user does with their own free-space number.

### What the figure does NOT fix — residual, recorded on #250

19b's sentence and the pre-flight that raises it still disagree about the number
that matters. `RealOfflineModelManager.kt:535` refuses below
`REQUIRED_FREE_BYTES = 150L * 1024 * 1024` (**157.3 MB**), so between 65 MB and
157 MB of free space the sheet says the user has enough and the app has already
said no.

The headroom is not arbitrary — a download holds the compressed file and the
unpacked pack at once, which for `en_ja` is 48.0 + 63.4 = **111.4 MB** peak — so
the constant is defensible and the *sentence* is what is incomplete. Deciding what
19b should say about that is a design call on a drawn frame, which this copy sweep
does not own. Recorded as a residual on **#250** (already the open design-call
issue for what the offline manager shows when a download is refused for space, held
for PR-23).

---

## 2. #229 — one action, one verb, three locales

The 🗑's `contentDescription` said *Delete*; both sheets it opens say *Remove*.
Direction per the issue: the visible copy was reviewed and drawn, so the spoken
layer moves.

| locale | sheet confirm (`lang_sheet_remove_confirm`) | bin was | bin now |
|---|---|---|---|
| en | `Remove` | `Delete %1$s` | `Remove %1$s` |
| fil | `Alisin` | `Burahin ang %1$s` | `Alisin ang %1$s` |
| pt-rBR | `Remover` | `Excluir %1$s` | `Remover %1$s` |

All three had diverged, each in its own vocabulary, which is why a locale-blind fix
would have left two of them wrong.

**Not folded in: the `cd_text_lang_retry` / `offline_cd_retry` pair.** #229 says it
is *"worth fixing together"*, and `STRINGS_language.md` §9 already names it. It is
not fixed here for two reasons that are not effort: consolidating two keys into one
requires editing the `stringResource` call site in `OfflineLanguagesScreen.kt`,
which this PR owns only the previews of; and no issue owns that pair, so the fix
would land with nothing to close. Recorded as a residual on **#250** — the open
issue about that exact control on that exact screen, held for PR-23, which will be
editing it anyway.

§9's own quotation of `cd_text_lang_retry`'s value **was stale** — it still read
`Try downloading %1$s again`, the pre-PR-18 wording, two sections after §6
documents the change. Corrected here. This is precisely #220's blind spot:
`verifyStringKeyDocs` is resource→doc only and cannot see a doc-side value that has
drifted.

---

## 3. #230 — the saved-phrases line: 19f gains it (decision), and 19g keeps its frame

### The decision: option 1

19f gains the saved-phrases line, drawn only when the count is non-zero, exactly as
19g already draws it.

**Against `EDGE_CASES.md`.** §7's NO-DEAD-END rule governs **Error / Empty /
Blocked** states and requires a next move. Neither sheet is any of those — both are
confirmations before a destructive action — so §7 does not *compel* the line on
either sheet. It is not the argument, and pretending it is would be reading a rule
to say what was wanted. What §7 does establish is the standard the sheets are held
to: a user is never left to guess what an action costs them. §5's per-feature
availability checklist puts that question at the moment of the decision, which is
the sheet.

**The argument that actually settles it is in the two bodies.** 19f's body already
says: *"%1$s will need a connection to translate until you download it again."*
19g's body says the same thing in its own words. The saved line exists to answer
exactly that spectre — *your saved phrases still open without a connection*. So
19f raises the worry the line answers, in the same sentence, and then does not
answer it. The asymmetry is not "19g is higher-stakes"; it is one sheet asking a
question and only the other one replying.

And the fear is about the **language**, not about the target. A user removing
French with 12 saved French phrases has the identical question whether or not
French is what they are translating into. Verified in code: nothing on the delete
path can reach saved work — `OfflineLanguagesViewModel.confirmRemove` →
`OfflineModelManager.delete`, and saved rows live in Room's `translation` table;
`TextViewModel.onHistoryPick` replays `translation.targetText` with no engine call.
The effect on saved phrases is **nil in both cases**, which is why the reassurance
cannot honestly be reserved for one of them.

**Rejected: option 2** (reserve the line for 19g, and state the asymmetry as a
choice). Its only real argument is that a routine removal should not carry an extra
card — and the line is *already* gated on `savedCount > 0`
(`RemovePackSheets.kt:236`, `if (savedCount <= 0) return`). So the load falls only
on users who have saved phrases in that language, who are precisely the users with
the question. The gate answers the objection; withholding the line does not.

### Why #230 does not close in this PR

Option 1 is a code change, and it is one line in a file this PR must not touch:

```
feature/language/…/OfflineLanguagesViewModel.kt:215
    savedCount = if (inUse) savedCountOf(id) else 0,
```

plus a `savedCount` parameter and a `supportingContent` on `RemovePackSheet`, plus
its previews and the render test. `OfflineLanguagesViewModel.kt` is *"any
ViewModel"* on the prohibition list. **Wave 1d owns `feature/language/**/kotlin`**
and is where this lands. `Refs: #230`, not `Fixes:`.

### The second question #230 asks: does 19g still earn a separate frame?

**Yes.** The issue reasons that with #226's false sentence gone, *"what 19g says
that 19f does not is one warning sentence"*. Counted against the shipped code that
is an undercount. After #226, and after 19f gains the saved line, the two sheets
differ in **four** of five visible elements:

| | 19f | 19g |
|---|---|---|
| title | `Remove %1$s?` | `%1$s is in use right now` |
| body | `Frees space on this device. …` | `It is your target language, and it stays your target. …` |
| primary action | `Remove` | `Remove anyway` |
| icon | `Icons.Outlined.Delete` | `Icons.Filled.Warning` |
| cancel | `Cancel` | `Cancel` (one shared key) |

A variant that changes its title, its body, its button label and its glyph is not a
variant. The differing primary label is the load-bearing one: *"Remove anyway"*
only reads correctly on a sheet that has given a reason to hesitate, and 19f gives
none. **19g keeps its frame**, and this is the answer #230 asked for in writing
rather than a silent merge.

---

## 4. #243 — the third failure sentence, and rule 7's letter

`downloadFailureCopy` (`DownloadFailure.kt:48`) produces three distinct sentences.
`STORAGE` and `NETWORK` were previewed on both screens; `UNKNOWN`
(`lang_pack_error_generic`, *"Download didn't finish. Try again."*) on neither —
so a third of the failure copy was invisible to the owner, who reviews UI from
previews. One omission, two screens, because PR-18 made them share one map.

Added:

| preview | file | state |
|---|---|---|
| `LanguageRowFailedGenericPreview` | `LanguagePickerScreen.kt` | `Failed(UNKNOWN)` |
| `OfflineRowStatesPreview` third failure row | `OfflineLanguagesScreen.kt` | `Failed(UNKNOWN)` |
| `RetryPillPreview` | `LanguagePickerScreen.kt` | the labelled pill, alone |
| `CauseCardPreview` ×2 | `PackFailureSheets.kt` | network cause / generic cause |
| `StorageBarCardPreview` | `PackFailureSheets.kt` | the used-against-free bar |
| `StorageLegendPreview` | `PackFailureSheets.kt` | the two swatches |
| `SavedPhrasesLinePreview` ×2 | `RemovePackSheets.kt` | many / one |

`WIFI_REQUIRED` gets none: it folds onto network by design and the fold is tested
(`DownloadFailureTest`). Comments fixed: `OfflineLanguagesScreen.kt`'s consent-sheet
note now covers 19f/19g as well, and `LanguagePickerContent`'s missing
`packFailure` preview gains the comment the consent case already had, so the two
omissions stop looking like different omissions.

**Not closed by this PR, and #243 says so itself:** both sheet files' previews
re-declare the sheet anatomy through `TranzlateSheetPreviewFrame` rather than
calling the real sheet, because `ModalBottomSheet` renders nothing in the preview
tooling. A change to a real sheet's arguments leaves the preview drawing the old
one. That needs a preview-scanner or a screenshot library this repo does not have —
#193 / #20.

---

## 5. #224 — verified still true, and not fixable inside this wave

Read against the code rather than assumed, both directions. **Still true.**

- `OfflineLanguagesViewModel.kt:105` filters the catalog on
  `Language::offlineAvailable` and nothing else; `BundledLanguageCatalog.kt:49`
  puts `"en"` in `offlineCapableIds`; `:316` derives `offlineAvailable` from it.
  English is listed.
- `OfflineLanguagesScreen.kt:245` draws the download button and `:284` the 🗑,
  from a `when (row.state)` that never reads `row.id`.
- `grep -rniE "pivot|isPivot|protected|canDelete|locked" feature/language/src/main/kotlin/`
  → **5 hits, all KDoc prose, 0 guards.**
  `grep -rnE '"en"' feature/language/src/main/kotlin/` → 3 hits, none a filter
  (`FALLBACK_SOURCE_LANG`, two preview fixtures).

**Three of the issue's four line citations are stale and one no longer exists** —
`delete(id)` was split into `requestRemove`/`confirmRemove` by PR-19. Re-derived
line numbers are posted to the issue so wave 1d does not chase them.

**Not fixed here, for two reasons that are not effort.** The issue itself forbids
it: *"Do not 'fix' this by hiding the row until the measurement says which branch
is real — the two branches want different fixes"*, and rule 4 owes a research
record first (`docs/research/issue-224-*` does not exist). And every file the fix
touches — the ViewModel, the screen's production `when`, `BundledLanguageCatalog`
in `core/data` — is on this wave's prohibition list. **Wave 1d.** `Refs: #224`.

Two tests currently depend on English being an ordinary pack and will need
rewriting when it is fixed, named here so the fix is not costed as smaller than it
is: `OfflineLanguagesViewModelTest.kt:366-374` (`requestRemove("en")` asserted to
proceed as an ordinary 19f removal) and `BundledLanguageCatalogTest.kt:51-52`
(`offlineCapableIds` asserted equal to `ML_KIT_TAGS`, which contains `"en"`).

---

## 6. Mutation register — decided BEFORE the tests were written

Rule 11, third cause. Written to a scratchpad file before the first edit, run
afterwards; each reddened the test it was aimed at.

| # | Mutation | Test that must go red |
|---|---|---|
| M1 | `offline_subtitle` back to `~30MB each` | `the pack size is one figure, stated once` |
| M2 | `lang_sheet_data_body` to `20–45 MB` while the other two carry the new figure | same — this is the historical state #219 was filed about, and a test comparing each string to a hardcoded literal survives it wrongly |
| M3 | `offline_cd_delete` back to `Delete %1$s` | `the bin's spoken verb is the sheet's verb` (en, fil, pt-BR) |
| M4 | `lang_sheet_remove_confirm` to `Delete` — the wrong-direction fix | same. This is why the assertion is agreement and not `isEqualTo("Remove %1$s")`: a literal assertion survives M4 |
| M5 | drop the locale qualifier so the fil/pt-BR cases silently read English | the guard assertion inside each locale case (`lang_sheet_remove_confirm` == `Alisin` / `Remover`) |

**M5 is the register's own #242 guard.** A locale test whose qualifier does not take
effect reads the English strings and passes, which is a test that cannot fail. The
guard assertion makes the qualifier itself the thing under test.

**No mutation is declared for #243, and none is invented.** The rule 7 gate is
file-level presence only (#193, and #243's own closing section), so a deleted
preview reddens nothing. The deliverable is the previews; the verification is
`preflight` compiling them and the owner reading them.

### Declared limit of the size test

`settings_mobile_data_supporting` lives in `feature/settings` and a
`feature/language` Robolectric test cannot resolve another module's `R`. The test
covers **3 of the 4** strings. The fourth is covered by enumeration and by
`STRINGS_settings.md` alone. Stated in the test's KDoc as well, because a limit
recorded only in a plan doc is a limit nobody reads at the call site.

---

## What would prove this plan wrong

- If ML Kit 17.0.3's `SZ` is not what lands on disk, `40–65 MB` is wrong in all
  four places. The disconfirmation was run and failed to disconfirm: `af_en`'s
  declared `SZ` and the E-S1 device walk agree to the byte.
- If a future ML Kit bump changes the distribution, the figure goes stale silently
  — nothing in the build reads the manifest. The test pins that the four strings
  **agree**, never that they are right. Named so it is not mistaken for more.
