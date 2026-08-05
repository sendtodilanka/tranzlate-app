# Research — issue #293: what does Download on the picker's English row actually do?

status: read-only investigation · **result: characterised from the code path +
#224's on-device measurements + the AAR proof; NOT independently re-measured on a
device this session (reasoned absence — see the closing section).**
Refs #293
Sibling of #224 (`docs/research/issue-224-en-row-delete.md`, measured on
`emulator-5554`). Base: fresh worktree from `origin/main` @ `d08cfa5`.

## Why this record exists (rule 4)

The issue says the runtime harm is unmeasured, so a read-only record precedes the
fix, with every hypothesis paired with an experiment that would disprove it and no
unmeasured single hypothesis asserted above 70%. #293 is filed **S3/P3** — the same
mild severity #224 landed at — so, unlike #224 (filed provisional-worst-case S1/P1),
the question here is not "is this catastrophic" but "what exactly does the misleading
control do, and is the guard the right fix".

## Settled by reading the code (no device needed)

These are read off this worktree's `origin/main` base and are not in dispute.

1. **English is an offline-capable picker row.**
   `BundledLanguageCatalog` marks `en` `offlineAvailable = true` (it is one of the
   59 `offlineCapableIds`), so English appears in the picker as an offline-capable
   language, never as online-only.

2. **The picker's English row can be `Downloadable`, and that draws a real Download
   button.** `rowStateOf` (`LanguagePickerModel.kt:643-658`) resolves the resting
   state from five mutually exclusive facts; with no `Failed`/`Downloading` model
   state and `offlineDownloaded == false` and `offlineAvailable == true`, the result
   is `LanguageRowState.Downloadable`. `RowTrailing`
   (`LanguagePickerScreen.kt:1676-1688`) renders that as an `IconButton`
   (`tt_lang_download_${id}`, content-description `cd_text_lang_download` =
   "Download English for offline use") whose `onClick` is `onDownload("en")`.

3. **`offlineDownloaded` for English is not a catalog constant — it is overlaid from
   ML Kit's downloaded set, and the overlay's FIRST frame is empty.**
   `LanguageRepositoryImpl.languages()` (`:72-110`) sets
   `offlineDownloaded = modelStates[id] == OfflineModelState.Downloaded`, where the
   model-state source is `offlineModelManager.modelStates().onStart { emit(emptyMap()) }`
   (`:75`). `combine` emits as soon as every source has, and the model-state source
   emits `emptyMap()` immediately — so the **first** combined frame carries an empty
   map, `modelStates["en"]` is null, `offlineDownloaded` is false, and English
   resolves to `Downloadable`. The button is therefore drawn at least transiently on
   every picker open, until the real `getDownloadedModels()` result replaces the
   empty map. This is the concrete difference from #224, whose offline-manager rows
   showed English as `Downloaded` from first observation.

4. **A tap reaches ML Kit's real download call — there is no id guard on the path.**
   `onDownload("en")` → `LanguagePickerViewModel.download("en")` (`:494-505`) →
   `DownloadGate.requestDownload("en")` → on consent
   `OfflineModelManager.download("en")` → `RealOfflineModelManager` →
   `MlKitModelStore.download("en")` (`RealOfflineModelManager.kt:69-79`) →
   `manager.download(TranslateRemoteModel.Builder("en").build(), DownloadConditions…)`.
   `grep -rniE "pivot|isPivot" core/translate/src/main core/domain/src/main` returns
   only descriptive KDoc — nothing between the picker and ML Kit special-cases `en`
   (mirrors #224's finding on the delete path).

5. **There is no standalone `en` model to download.** `.claude/memory`'s
   `mlkit-english-is-in-every-model.md` records the AAR proof (re-verified for #224):
   `translate-<ver>.aar` → `res/raw/translate_models_metadata.json` key `PKG_HIGH` =
   **58 `X↔English` pairs, no `en` and no `en_en`.** Downloading "German" fetches
   `de_en` and English rides along. So `Builder("en")` names a model that has no pack
   of its own.

## The single unmeasured fact, and the two-sided prior (capped at 70%)

The one thing reading the source cannot settle: **what
`manager.download(Builder("en"))` does when `en` has no standalone pack.** Direction
is unmeasured, so no branch is asserted above 70% (rule 4). The evidence is
genuinely two-sided but leans strongly to Branch A by symmetry with #224:

- **Toward A (harmless no-op / immediate success).** #224 *measured* the mirror
  operation: `deleteDownloadedModel("en")` is a no-op — `en` never leaves
  `getDownloadedModels()`, on a fresh install with the model dir absent the count is
  still 1, and a French control proved the same confirm path really removes a real
  pack. An always-present pivot that a delete refuses to remove is most consistent
  with a download that finds it already present and returns success without
  transferring bytes. ML Kit's `download` checks the downloaded set first.
- **Toward B (a real, wasteful transfer or an error).** `Builder("en")` is a valid
  `TranslateRemoteModel` handle (`TranslateLanguage.ENGLISH == "en"`), and nothing in
  ML Kit's public contract documents `en` as exempt from `download`. A conceivable
  outcome is a spurious `Downloading` spinner that resolves to `Downloaded` (the
  state English is already in) having done nothing useful, or a background fetch of a
  base component.

Either branch converges on the **same UI truth** #224 reached: the pivot must not
present a control that acts on a pack that does not exist. Branch A (the strong
prior) additionally means the misleading control is *harmless* — no data loss, no
broken translation — which is why #293 is S3/P3, not an S1.

### Disconfirming experiments (for the day a device is genuinely idle)

- **Branch A is FALSE if**, after tapping Download on English (first frame, or on a
  device where `getDownloadedModels()` omits `en`), a real transfer occurs: the row
  sits in `Downloading` for a non-trivial time, or `no_backup/.../models/` gains
  bytes attributable to `en` alone, or the call throws. **A is supported if** the tap
  resolves to `Downloaded` effectively immediately with no new `en`-only bytes.
- **Guard correctness is independent of which branch wins:** with the fix in place,
  the English row offers no Download in any state, so the ML Kit call is never
  reached from the picker regardless.

## Why the fix does not depend on the measurement

The remedy is `isPivotLanguage(row.id)` in the render layer (guard by id, not
state), so whatever `download("en")` would have done, the picker never invokes it on
the pivot. The measurement would only sharpen the *severity narrative* (harmless vs
wasteful), not change the fix. #224 reached the same conclusion from the delete side
and shipped the guard; this is its sibling.

## Verdict

**A misleading Download control on the picker's English row, drawn at least on the
first frame of every picker open (empty model-state map) and permanently wherever
ML Kit does not report `en` as downloaded. Harmless in the strongly-favoured Branch
A (no standalone `en` pack; the mirror delete is a measured no-op), so S3/P3.** Fix:
strip the pack affordance on the pivot, keep the row selectable, and tell the same
"included with every language" story the offline manager tells — guarded by id.

## Not measured on a device this session — and why (rule 12, fourth shape)

`emulator-5554` (AVD `Resizable_Experimental`) — the exact device #224 measured on —
was booted. But two other agent worktrees were active this session
(`i1e-audit` running a rev5 frame-rederivation research task that may itself want the
device, and `i221-grep-b`), and `.claude/device-claim` did not exist — the precise
"no claim file, so the warning never fires" condition under which two agents have
driven one emulator before (CLAUDE.md rule 12; the #213 hook that had never fired).
Per the brief's "if unsure, do NOT boot/contend", I did not claim or drive it. The
decisive ML Kit facts this fix rests on were already measured on this same emulator
for #224 (English permanently in the downloaded set; the mirror delete a no-op; no
standalone `en` pack in the AAR), so the reasoned characterisation above stands in
for a re-measurement rather than replacing an unknown with a guess.

## Out-of-scope observations (for the orchestrator to file as issues if wanted — NOT fixed here)

1. **The empty-first-frame `Downloadable` flash is not English-specific.** Because
   `modelStates` starts empty (`LanguageRepositoryImpl.kt:75`), *every*
   already-downloaded language momentarily renders as `Downloadable` (a Download
   button) on picker open until the real set arrives, then flips to `Downloaded`.
   For non-pivot languages that is a brief wrong-affordance flicker, not a lie about
   a non-existent pack; it is a separate, lower-severity concern from #293 and is
   noted, not fixed, here.
