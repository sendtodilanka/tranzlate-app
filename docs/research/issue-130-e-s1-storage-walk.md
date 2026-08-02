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

1. **A fresh install has no model store either.** This is the finding that
   mattered most, and it was not anticipated: on first run the directory does not
   exist, so `packsBytes()` returns `null` for the most ordinary reason there is.
   A meter that branched on the bytes first would degrade the commonest state in
   the app to a free-space number, on the one screen where the user has nothing
   to be told about. So **the COUNT decides first** — it comes from the catalogue
   and needs no disk — and only a device with packs installed can reach the
   degrade. `OfflineLibraryMeterTest.a fresh install says nothing downloaded`
   pins it.
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
