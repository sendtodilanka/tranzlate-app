# Research — issue #224: what does Delete on the English row actually do?

status: read-only investigation · **MEASUREMENT PENDING** (this section is written
BEFORE any device is touched, per rule 4)
Refs #224
device: TBD — one of `Resizable_Experimental` / `Tranzlate_API24/28/29`, claimed
exclusively via `.claude/device-claim`
build: TBD — prod-debug APK, md5 + install time recorded at measurement
(recorded because two captures were once compared across two different flavours
— CLAUDE.md rule 12, third shape)

## Why this record exists

Rule 4: an unknown root cause gets a read-only research record before a fix, with
every hypothesis paired with an experiment that would prove it wrong, and
single-hypothesis confidence capped at 70% until measured.

#224 is filed as **S1/P1 provisional, set to the worse branch**. Its own text
says the two possible effects want *different fixes*, so hiding the row before
knowing which effect is real would be a speculative fix. The one thing that must
be measured is small and exact: **when the user confirms Delete on the English
row, does ML Kit's `deleteDownloadedModel("en")` remove English from the
downloaded set, or not?** Everything else follows from that single fact.

## What is already settled by reading the source (no device needed)

These are read off the code on this branch's base (`origin/main` @ 40af56a) and
are not in dispute; they are what makes the row reachable and what makes the
outcome matter.

1. **English is listed as an offline row.**
   `BundledLanguageCatalog.offlineCapableIds` contains `"en"`
   (`core/data/.../BundledLanguageCatalog.kt:49`), so `Language.offlineAvailable`
   is `true` for English (derived at `:316`). `OfflineLanguagesViewModel.rows`
   filters the catalog on `offlineAvailable` **only**
   (`OfflineLanguagesViewModel.kt:105`), so English appears.

2. **On first run the English row shows Delete, not Download.**
   A row's control is chosen from its `OfflineModelState`
   (`OfflineLanguagesScreen.kt:241-317`). The state comes from
   `mergeModelStates` (`OfflineModelStates.kt:15-17`): a tag is `Downloaded`
   iff it is in the downloaded set, else `NotDownloaded`. The downloaded set is
   `getDownloadedModels().map { it.language }`
   (`RealOfflineModelManager.kt:62-67`; same call in `MlKitEngine.kt:64-71`).
   Issue #94 and `LanguagePickerModel.kt:974-984` both record that ML Kit reports
   the English pivot as on-device **before anything is downloaded** — a first-run
   downloaded count of **1**. So on a fresh install `en` is in the downloaded set,
   English's state is `Downloaded`, and the row draws the **Delete** icon
   (`tt_offline_delete`, `OfflineLanguagesScreen.kt:284`). The "Download" control
   is therefore only reachable on English *if* something first removes `en` from
   the set — which is exactly Branch B below.

3. **Delete is unconditional — there is no pivot guard anywhere.**
   Tapping 🗑 → `onRequestRemove(id)` → `requestRemove("en")` opens the confirm
   sheet; confirming → `confirmRemove()` → `modelManager.delete("en")`
   (`OfflineLanguagesViewModel.kt:284-288`) → `store.delete("en")` →
   `manager.deleteDownloadedModel(TranslateRemoteModel.Builder("en").build())`
   (`RealOfflineModelManager.kt:419-468`, `:81-83`). Nothing between the row and
   ML Kit special-cases `en`:
   `grep -rniE "pivot|isPivot|protected|canDelete|locked" feature/language/src/main/kotlin/`
   returns only the descriptive KDoc, no guard (confirmed in #224).

4. **If `en` leaves the downloaded set, our OWN engine refuses the translation —
   we do not even reach ML Kit.** `MlKitEngine.translate` gates on
   `if (src !in downloaded || tgt !in downloaded) return MODEL_NOT_DOWNLOADED`
   (`MlKitEngine.kt:39-42`). The app's default pair is `en → fr`
   (`app/build.gradle.kts:75-76`). So Branch B does not depend on any deep ML Kit
   behaviour to break translation: the moment `getDownloadedModels()` stops
   returning `en`, our gate fails every pair that has English on either side,
   including the default one, at our own code.

The single unmeasured fact is what `deleteDownloadedModel("en")` does to the
downloaded set. There is no standalone `en` pack on disk (issue #94 verified the
AAR: 58 models, every one an `X_en`/`en_X` pair, no `en`), yet ML Kit's accounting
reports `en` as present. Whether ML Kit lets you delete that accounting entry is
the experiment.

## The decisive observable

Both hypotheses make **opposite predictions about one thing**: the contents of
`getDownloadedModels()` immediately after `deleteDownloadedModel("en")`. That set
is observable two ways without any private ML Kit API, and both will be recorded:

- **Through the app's own state** — the English row's trailing control. `Delete`
  still showing ⇒ `en` still in the set; the control flipping to `Download`
  (`tt_offline_download`) ⇒ `en` removed.
- **Through a translation attempt** — `en → fr` in airplane mode. `Success` ⇒
  `en` still in the set; `MODEL_NOT_DOWNLOADED` ⇒ `en` removed (our gate at
  `MlKitEngine.kt:40`).

## Hypotheses and the experiments that would disprove them

### Branch A — Delete is a no-op (→ S3)

`deleteDownloadedModel("en")` removes nothing ML Kit recognises. After the
confirm, `getDownloadedModels()` still contains `en`; the English row stays
`Downloaded` (Delete icon unchanged); `en → fr` still translates offline. The
control is misleading but harmless — and the Download side of the row is never
even reached, because `en` never leaves the set. Under this branch #224 drops to
S3/P3 (a control that does nothing), as the issue itself says.

**Disconfirming experiment.** With French downloaded and `en → fr` proven offline,
confirm Delete on the English row; re-read the downloaded set (row control + a
fresh `en → fr` attempt in airplane mode). **Branch A is FALSE if** `en`
disappears from the set — the row flips to `Download`, or `en → fr` returns
`MODEL_NOT_DOWNLOADED`.

### Branch B — Delete really removes the pivot (→ S1)

`deleteDownloadedModel("en")` succeeds in removing `en` from ML Kit's downloaded
set. After the confirm, `getDownloadedModels()` no longer contains `en`; the
English row flips to `NotDownloaded` (Download icon); and every pair with English
on either side — including the default `en → fr` — returns
`MODEL_NOT_DOWNLOADED` at `MlKitEngine.kt:40`, so **all offline translation
through the default pair stops**. This is the S1 case.

**Disconfirming experiment.** Same steps. **Branch B is FALSE if** `en` remains
in the set after the confirm — the row keeps its Delete icon and `en → fr` still
succeeds offline.

### A third outcome worth naming, so it is not mistaken for A

If ML Kit removes `en` from `getDownloadedModels()` but the physical `en_fr` pair
files remain on disk, ML Kit *itself* might still be able to translate `en → fr`.
This does **not** rescue the app: our engine gates on the reported set
(`MlKitEngine.kt:40`), so `en → fr` breaks at OUR code regardless. This outcome
is therefore a sub-case of Branch B for severity purposes (offline translation
stops), and it only changes the **recovery** story — which is why recovery is
measured separately below.

### Recovery (only meaningful under Branch B)

If Branch B is real: after English is removed, download any partner pack again
(which fetches an `X_en` bundle) and re-check whether `en` returns to the set and
whether `en → fr` translates again. This decides whether the fix needs only a row
guard, or a guard **plus** a recovery path.

## Prior, stated as a prior and not as a finding

Direction unmeasured, so capped at 70% (rule 4). The source gives a weak, genuinely
two-sided prior:

- Leaning toward **A**: ML Kit reports `en` as downloaded even with **zero** packs
  on disk, which is the behaviour of an always-present base/pivot rather than of a
  normal deletable model; an always-present base is one a delete might refuse or
  no-op.
- Leaning toward **B**: `TranslateRemoteModel.Builder("en")` is a valid model
  handle and `deleteDownloadedModel` is documented as deleting the model for the
  handle it is given; nothing in the public contract says `en` is exempt.

Neither is strong. The measurement decides. No confidence above 70% is asserted
for either branch until the section below is filled.

## Measurement

TO BE FILLED after the device experiment — pasted commands and their output, the
English-row screenshot, the exact `AttemptCause`, and the recovery result.

## Verdict

TO BE FILLED — which branch is real, the evidence, the confidence, and the fix
direction that follows.
