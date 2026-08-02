# E-S1 — does the ML Kit model-store walk still find anything?

- **Issue:** #130 (language epic, rev.3 ruling risk **R8**)
- **Gates:** PR-15 (re-ruled 2026-08-01 — moved off PR-11, `docs/plan/issue-130-language-rev3.md` §"Re-ruling")
- **Run:** 2026-08-02, `emulator-5554` = `Resizable_Experimental`, API 37, 420dpi
- **Build:** `app-tranzlate-prod-debug.apk` (the PROD flavour — the fake one does not touch ML Kit)
- **Status:** ✅ **passed, both halves**

## What the experiment protects

The offline-library meter (U-5) prints a size. That size is the sum of the files
under ML Kit's on-device translate-model store, and **ML Kit has never documented
where that store is.** This project knows the path only because issue #90's
research (E3, 2026-07-30) measured it:
`noBackupFilesDir/com.google.mlkit.translate.models`.

If an ML Kit update renames or moves it, `StorageProbe.packsBytes()` returns
`null` on every device at once. The tempting thing to draw for `null` is `0 MB`,
which states as a fact that the user's packs occupy no space. That is risk R8.

E-S1 answers two questions and nothing else:

1. **Is the measured path still the path?** Download a real pack, walk it, and
   assert the sum is above zero.
2. **What happens when it is not?** Take the directory away and confirm the walk
   finds nothing rather than finding zero bytes.

## Method

Physical device present on the host (`REA_NX9`) was left untouched; everything
below is `-s emulator-5554`.

```
adb -s emulator-5554 install -r -g app/build/outputs/apk/tranzlateProd/debug/app-tranzlate-prod-debug.apk
adb -s emulator-5554 shell am start -n com.codeboxlk.tranzlate.offlinetranslator/com.codeboxlk.tranzlate.MainActivity
# Home → "Download languages" → Afrikaans ⬇
adb -s emulator-5554 shell run-as com.codeboxlk.tranzlate.offlinetranslator ls -laR ./no_backup/com.google.mlkit.translate.models
```

## Result 1 — the store is where research measured it

**Before the first download the directory did not exist at all**:

```
$ run-as <pkg> ls -la ./no_backup/
PersistedInstallation.…json          # and nothing else
```

Within ~25 seconds of tapping ⬇ on Afrikaans it appeared, and after the download
settled it held one pack:

```
no_backup/com.google.mlkit.translate.models/
├── af_en/
│   ├── merged_dict_af_en_25_both.bin            2 988 368
│   ├── merged_dict_af_en_25_from_af.bin        14 181 936
│   ├── merged_dict_af_en_25_from_en.bin        14 779 264
│   ├── merged_dict_af_en_25_update.bin                 64
│   ├── translate_afen/   (+ resources/)        ~5.6 MB over 11 files
│   └── translate_enaf/   (+ resources/)        ~5.6 MB over 11 files
└── temp/af_en/                                  (empty)
```

**Sum over regular files, recursively — exactly `packsBytesOf`'s semantics:**

| measure | value |
|---|---|
| regular files | **30** |
| bytes | **44 169 505** (42.1 MiB) |
| `du -sb` (includes directory blocks) | 44 202 273 |
| volume total (`df /data`) | 10 167 132 KiB ≈ 10 411 143 168 B |
| volume available | 8 448 928 KiB ≈ 8 651 702 272 B |

`packsBytes()` therefore returns **44 169 505 > 0**. The E3 path holds; ML Kit has
not renamed the store between 2026-07-30 and 2026-08-02.

## Result 2 — the degrade path

Simulating the rename the experiment exists to detect:

```
$ run-as <pkg> mv ./no_backup/com.google.mlkit.translate.models \
                  ./no_backup/com.google.mlkit.translate.models.v2
$ run-as <pkg> "[ -d ./no_backup/com.google.mlkit.translate.models ] && echo DIR_PRESENT || echo DIR_ABSENT"
DIR_ABSENT
```

The walked path is gone while 44 MB of models are still on disk under the new
name — which is precisely the shape of an ML Kit rename. `packsBytesOf` returns
`null` for a path that `isDirectory` is false for (`StorageProbe.kt`), and the
meter routes `null` to `OfflineLibraryMeter.Unsized`, which reports free space
and draws no bar. The directory was moved back afterwards; the emulator is in the
state it was found in plus one Afrikaans pack.

## Three things this changed in PR-15

1. **A fresh install has no model store either.** On first run the directory does
   not exist, so `packsBytes()` returns `null` for the most ordinary reason there
   is. So **the COUNT decides first** — it comes from the catalogue and needs no
   disk.
   > ⚠️ **The conclusion drawn from this was wrong, and §E-S1b below corrects
   > it.** This paragraph originally went on to say that count-first keeps the
   > commonest state in the app — the first run — off the free-space line. It
   > does not. The run above never did a `pm clear`, so the first run was
   > reasoned about rather than measured, and the measurement says the count is
   > **1** there, not 0.
2. **A store that exists but sums to zero is also a degrade.** `null` is not the
   only way a rename shows up: ML Kit could keep the directory and move the
   models out of it. `0` while the catalogue says packs are installed cannot
   support "your packs take up no space", so it takes the same honest path.
3. **The bar's denominator is the whole volume.** One pack is 0.42% of this
   device's disk. That draws as very nearly nothing, and it is left that way —
   a minimum-width floor would draw a bar bigger than the fact it reports.

## What this experiment does NOT establish

- **One pack, one device, one ML Kit version.** It proves the path is right
  today; it cannot promise the next ML Kit release keeps it. That is why the
  degrade exists and why it is tested rather than reasoned about.
- **The measured size is not a per-pack size.** 42.1 MiB is af↔en *including*
  the shared English half. Per-pack byte counts remain impossible (designer-brief
  rule) and the meter only ever states the aggregate.
- **`Downloading…` was not sampled mid-flight.** The walk during a partial
  download would include `temp/`, which would over-report for a few seconds. Not
  a claim this PR makes, recorded here rather than left to be discovered.
  > ⚠️ **"For a few seconds" was wrong — see §E-S1c.** Nothing is documented to
  > clean `temp/` up, so an interrupted download's leftovers are counted for as
  > long as the install lives.

---

# E-S1b — what does a REAL first run show?

- **Run:** 2026-08-02, co-verify of PR #200, `emulator-5554`, same prod APK
- **Status:** ✅ ran — and **falsified a premise the PR's design rested on**

## Why it had to be run

E-S1 above concluded that a first run is the commonest state in the app and that
deciding on the pack count first is what keeps it off the free-space line. That
conclusion was never measured: **the original run never did a `pm clear`.** It
inferred the first-run state from "the store directory does not exist yet",
which is true, and stopped there.

## Method

```
adb -s emulator-5554 shell pm clear com.codeboxlk.tranzlate.offlinetranslator
adb -s emulator-5554 emu posture 2            # HALF_OPENED → the two-leaf layout
adb -s emulator-5554 shell am start -n com.codeboxlk.tranzlate.offlinetranslator/com.codeboxlk.tranzlate.MainActivity
# tap the source chip → the picker
adb -s emulator-5554 shell uiautomator dump /sdcard/d1.xml && adb pull …
```

## Result — the count is 1, and the first card is the free-space one

`pm clear` removed `no_backup/` entirely. On first launch it contained one file
and no model store:

```
$ ls -la /data/data/<pkg>/no_backup/
PersistedInstallation.….json        # and nothing else
```

The picker's first frame, from the dump:

| element | text |
|---|---|
| meter overline | `Offline library` |
| meter numeral | `1` |
| meter detail | `of 59 packs · 8.6 GB free` |
| top-bar counter | `1 of 59 on device` |
| English row | content-desc `English, on device`, badge `On device` |

**ML Kit reports the English pivot as downloaded before anything is
downloaded**, and while this app's store does not exist. `RealOfflineModelManager`
takes its truth from `RemoteModelManager.getDownloadedModels(TranslateRemoteModel)`
and seeds nothing itself, so the 1 comes from ML Kit.

## What this means for the design

- `downloaded` is **never 0** on hardware with Play Services, so
  `OfflineLibraryMeter.Empty` — the "nothing downloaded" card — is close to
  unreachable there, and the export's `from · foldable first run` frame does not
  match the device.
- The first card a new user sees is **`Unsized`**: count plus free space.
- **The drawn output is right; the reasoning behind it was not.** "1 of 59 packs
  · 8.6 GB free" is true, agrees with the English row beside it, and is the
  number someone about to download a pack actually wants. What was false is the
  claim that this state is rare.
- The precedence is **kept**, on a reason that survives the correction: a store
  can outlive the packs deleted from it, so bytes-first would print a size under
  a count of nought.
- The tempting alternative repair — treat a `null` walk as "nothing downloaded"
  — is refused. It would print "1 of 59 packs · nothing downloaded", a card
  contradicting the row three inches from it.

Pinned by `OfflineLibraryMeterTest.a first run reports free space because the
pivot pack already counts` and `…zero packs stays empty even when the store still
holds bytes`.

---

# E-S1c — how long does `temp/` debris inflate the size?

- **Run:** 2026-08-02, co-verify of PR #200, `emulator-5554`
- **Status:** ✅ ran — **"a few seconds" was wrong; it is unbounded**

## Method

One real model file from the af↔en pack, copied into the scratch directory the
way an interrupted download leaves one behind:

```
cp .../af_en/merged_dict_af_en_25_from_en.bin  .../temp/af_en/
# 14,779,264 bytes
```

## Result

The card moved **44 MB → 59 MB** — a ~34% overstatement — while the catalogue
still correctly read **"2 of 59 packs"**. Nothing removes those bytes: ML Kit
documents no cleanup of the scratch area, and the walk had no notion of "belongs
to a complete pack", so the inflation lasts as long as the install.

## The fix, and its limit

`packsBytesOf` no longer descends into the store-root scratch directory
(`MLKIT_SCRATCH_DIR = "temp"`, `core/common/StorageProbe.kt`). Only that one: a
`temp` folder nested inside a pack directory is the pack's own layout, and a
plain file called `temp` at the root is a file.

The limit, stated rather than implied: ML Kit documents neither the store's name
nor the scratch directory's, so this exclusion is pinned to an observed layout in
exactly the way the store path is. A future rename of the scratch directory
starts the over-report again — silently, because there is a degrade for "no
number" and none for "a number that is too big". It remains the better side of
the trade: the card's claim is a completed pack's size, and scratch files are not
part of it.

Pinned by `StorageProbeWalkTest.an interrupted download's temp debris is not
counted as pack bytes` and `…only the store-root scratch dir is skipped`.

---

# The fold postures this AVD can and cannot reach

Recorded here because PR-15 stated TABLETOP as "unit-tested only", which reads as
untried. It is not reachable on the mandated AVD at all.

```
$ grep posture ~/.android/avd/Resizable_Experimental.avd/config.ini
hw.sensor.hinge_angles_posture_definitions=0-30, 30-150, 150-180
hw.sensor.posture_list=1, 2, 3

$ adb -s emulator-5554 shell cmd device_state print-states
CLOSED(0) · HALF_OPENED(1) · OPENED(2) · REAR_DISPLAY_MODE(3)

$ adb -s emulator-5554 emu posture 4
KO: Failed to set posture
$ adb -s emulator-5554 emu posture 5
KO: Failed to set posture
```

Three postures are declared and the emulator refuses every identifier outside
them. One further observation, recorded without a conclusion drawn from it:
holding HALF_OPENED and forcing `user_rotation 1` put the picker back to a single
pane at 700dp of width — comfortably over the 608dp two-leaf minimum — so
whatever the window reported there, it was not `BOOK`. That is consistent with
TABLETOP and equally consistent with the folding feature simply not surviving the
rotation, and nothing available on this AVD distinguishes the two. TABLETOP's
single-pane behaviour therefore stays unit-tested (`PickerArrangementTest`), and
that is a ceiling of the tooling rather than a gap in the verification.
