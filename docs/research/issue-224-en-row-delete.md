# Research — issue #224: what does Delete on the English row actually do?

status: read-only investigation · **result: BRANCH A (Delete is a no-op) —
measured, with a control**
Refs #224
device: `emulator-5554` — AVD `Resizable_Experimental` (`sdk_gphone16k_arm64`,
has `com.google.android.gms` + `com.android.vending`, so ML Kit downloads run);
claimed exclusively via `.claude/device-claim` for the whole run
build: prod-debug (`tranzlateProdDebug`) from `fix/issue-224-en-row` @ base
`origin/main` 40af56a, APK md5 `680f41441a94cadce669d28600f036ac`, installed
2026-08-05 17:06:58 (build + install provenance recorded because two captures
were once compared across two different flavours — CLAUDE.md rule 12, third shape)

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

Every reading below was taken on `emulator-5554` while it was claimed. A
measurement without its command is not a measurement (rule 12), so the commands
are pasted with their output. `S=emulator-5554`,
`PKG=com.codeboxlk.tranzlate.offlinetranslator`, `ADB=$ANDROID_HOME/platform-tools/adb`.

### 1. First run — English is the ONE row that reports as downloaded (count = 1)

```
$ADB -s $S shell pm clear $PKG                       → Success
$ADB -s $S shell run-as $PKG ls no_backup/com.google.mlkit.translate.models/
  → ls: ... No such file or directory        # the model store dir is ABSENT on a fresh install (#94)
```

Yet on the Offline-translation screen, **English alone draws the Delete (trash)
icon; every other language draws Download** — screenshot `02-offline-firstrun.png`.
That is the count = 1 the issue predicted, shown directly: `getDownloadedModels()`
returns `en` before anything is downloaded, so `mergeModelStates` marks English
`Downloaded` and the row renders `tt_offline_delete`. The store directory not
existing while the count is 1 is the same paradox #94 recorded — the pivot is
reported present without a file of its own.

### 2. Download French, and prove `en → fr` offline (the baseline)

```
# tap Download French (content-desc "Download French"), poll the row:
t+3s:  content-desc="Stop downloading French"
t+12s: content-desc="Remove French"                  # downloaded
$ADB -s $S shell run-as $PKG ls no_backup/com.google.mlkit.translate.models/
  → en_fr   temp                                     # ONE dir for the pair — no standalone fr, no standalone en
```

Then go offline and translate:

```
$ADB -s $S shell cmd connectivity airplane-mode enable; svc wifi disable; svc data disable
$ADB -s $S shell ping -c1 -W1 8.8.8.8   → connect: Network is unreachable
dumpsys connectivity | grep -c NetworkAgentInfo{   → 0        # genuinely offline
# Text screen, en→fr, "Hello world":
result → "Bonjour monde"  (screenshot 03b-enfr-result.png, airplane icon in status bar)
```

Offline + a successful translation ⇒ the answer came from the on-device
`MlKitEngine`, which requires BOTH `en` and `fr` in `getDownloadedModels()`
(`MlKitEngine.kt:40`). So at this point `en` is in the set. Baseline established.

### 3. Delete English → nothing changes

```
# Offline-languages screen, English row control = content-desc "Remove English"
# tap it → confirm sheet:  text="Remove English?"  [Cancel] [Remove]
# tap Remove (confirmed 17:17:19)
English row AFTER confirm      → content-desc="Remove English"      # UNCHANGED
English row +7s (past any transient) → content-desc="Remove English"
run-as ls no_backup/.../models/ → en_fr  temp   (en_fr mtime still 17:13, the
                                                  translation's time — the 17:17
                                                  delete did not touch it)
```

Screenshot `08-after-delete-en.png`: English still shows the trash icon,
identical to first run.

Independent re-read through the OTHER caller of `getDownloadedModels()` — a
**fresh** offline translation with new text (forces a new `MlKitEngine.translate`,
so the `src !in downloaded` gate is re-evaluated):

```
# still offline (ping unreachable, 0 NetworkAgentInfo re-verified)
Text screen, en→fr, "Good morning"  → "Bonjour"        # SUCCESS, offline
```

Two independent readers (the manager's post-delete `refreshDownloaded`, and the
engine's own read) both still see `en` after `deleteDownloadedModel("en")`. The
exact result the brief asked for — `en → fr` in airplane mode after deleting
English — is **Success, `EngineResult.Success("Bonjour")`, no `AttemptCause`
failure at all.**

### 4. Control — the same confirm-path removes a real pack (closes the Cancel/Remove confound)

Tapping "Remove" and tapping "Cancel" both leave English showing "Remove English",
so the row alone cannot prove the confirm actually ran the delete. The confirm
path was therefore pointed at a pack that IS deletable, French, through the exact
same code (`confirmRemove()` → `modelManager.delete(id)`):

```
# French is the current target, so its sheet is the in-use variant:
#   "French is in use right now / … will need a connection until you download it again."
#   [Cancel] [Remove anyway]  — tap "Remove anyway" (confirmed 17:25:19)
French row AFTER   → content-desc="Download French"      # FLIPPED — fr left the set
English row AFTER  → content-desc="Remove English"       # still there, untouched
```

So the confirm path demonstrably removes a model from `getDownloadedModels()`
(French flipped to Download). Applied identically to English, twice, it changes
nothing. English's Delete is a **no-op**, not a mis-tap.

Contrast, confirming the engine gate fires when a real pack is gone:

```
# still offline, French now removed:
Text screen, en→fr, "Good afternoon" → "COULDN'T TRANSLATE — You're offline."
```

(Removing English left `en → fr` working; removing French broke it — the
opposite outcomes that separate the pivot from a real pack.)

A curiosity noted in passing, not load-bearing: after French was removed from the
set, its physical files were still on disk —
`en_fr/translate_enfr/…` and `translate_fren/…` intact at their original mtime.
ML Kit's downloaded-set registry is separate from the model files; the delete
updates the registry (which is what `getDownloadedModels()` and therefore our
whole app reads) without necessarily erasing the bytes.

### Recovery question — not applicable

Recovery ("re-download a pack, does English come back?") was defined as meaningful
only under Branch B. English was never removed, so there is nothing to recover.
N/A, by the branch that won.

## Verdict

**Branch A — Delete on the English row is a no-op. → S3/P3.** High confidence.

The 70% cap is for an UNMEASURED single hypothesis (rule 4); this is measured,
and measured with a control, so it is not asserted under the cap:

- After `deleteDownloadedModel("en")`, `getDownloadedModels()` still contains
  `en`, read two independent ways — the row control stayed `Remove English`, and
  a fresh offline `en → fr` still translated (`"Good morning" → "Bonjour"`).
- The confirm path is not inert: the identical code removed French
  (`Remove French` → `Download French`), so English staying put is the ML Kit
  behaviour, not a mis-tap.
- The on-disk `en_fr` pair was untouched by the English delete.

Branch B is disconfirmed on its own terms (I wrote: "Branch B is FALSE if `en`
remains in the set after the confirm — the row keeps its Delete icon and
`en → fr` still succeeds offline." Both held.). The third outcome I named —
`en` leaving the reported set but the pair files surviving — did not occur for
English either; `en` never left the set.

**What this means for the app and the user.** Tapping Delete on English does
nothing: the model cannot be removed, offline translation is never harmed, and
there is no data to lose or recover. The control is a lie of a milder kind than
#224 feared — it does not destroy anything, it just does not do what it says.
The Download side of the row is, on this branch, **unreachable in normal use**:
English never leaves the downloaded set, so the row never shows Download.

This drops #224 from its provisional worst-case S1/P1 to **S3/P3**, exactly as
the issue said it should if the no-op branch won. It should be re-labelled at fix
time.

## Fix direction (recommendation — implementation is the next brief, not this record)

Both branches always converged on the same UI truth: **the English/pivot row must
not offer Download or Delete controls that act on it.** Branch A narrows what the
fix needs:

- **No anti-delete guard is required, and no recovery path is required.** Those
  were Branch-B costs. Deleting `en` is harmless, so the fix is purely about not
  presenting a control that misleads.
- **The pivot must stop rendering an actionable Download/Delete control.** The
  cleanest seam is where the row's capability is decided —
  `OfflineLanguagesViewModel.rows` (which today filters on `offlineAvailable`
  only, `:105`) and/or the row's control `when` in `OfflineLanguagesScreen.kt`
  (`:241-317`). The design choice between *hiding English entirely* and *showing
  it as a non-actionable "included with every language" row* is a UX decision for
  the fix brief, not a measurement, so I am not making it here.
- **Mind the counter (C-11).** English is 1 of the 59 `offlineCapableIds`, and the
  first-run count of 1 IS English. If the fix hides English from the list, decide
  deliberately what happens to the "59" denominator and to that first-run count so
  the list and the counter do not disagree (the kind of drift `LanguagePickerModel`
  already reasons about at `:974-984`). This is a note for the fix brief, not a
  defect found here.

## Out-of-scope observations (for the orchestrator to file as issues if wanted — NOT fixed here)

1. **Offline error precedence.** With a needed pack missing AND the device
   offline, `en → fr` surfaced "You're offline" rather than anything about the
   missing model. The waterfall's online tiers fail with NETWORK and that cause
   appears to win over the ML Kit tier's `MODEL_NOT_DOWNLOADED`, so the user is
   told to "connect to the internet" when downloading the model would also fix it.
   Observed while testing the French contrast; unrelated to the English row.
2. **A second launcher icon in debug builds.** LeakCanary registers its own
   launcher activity, so the app resolves to the Android disambiguation screen and
   `monkey -c LAUNCHER` can open LeakCanary instead of the app. Debug-only, cosmetic;
   noted because it briefly misdirected this run (launch `…/MainActivity` explicitly).
