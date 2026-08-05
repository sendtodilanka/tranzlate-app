# String Catalogue Foundation — Language feature (`:feature:language`)

> Tranzlate · feature: **Language picker** (issues #15 / #117) + **Offline languages manager** (#82)
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Resources: `feature/language/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

C-3 makes this file the key authority for every string `:feature:language` ships. It was written
**after** the resources, not before: PR-6 (#130) moved the picker into this module and PRs #82/#117
built the offline manager, and none of them added a catalogue — which is the gap issue #152 closes.
Every `en` value below is transcribed **verbatim from the shipped resource** and every `fil` /
`pt-rBR` value already exists in the module's locale files; nothing here is invented copy. Where a
value is a guess dressed as a fact, it says so.

The `verifyStringKeyDocs` Gradle task now fails the build if a key ships without a row here.

---

## 1. Legend

| Mark | meaning |
|------|---------|
| **SHIPPED** | key exists in `values/` **and** in both locale files — this is the state of every row below |
| `%1$s` etc. | positional format argument, order is part of the contract |

---

## 2. Language picker — screen chrome

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_sheet_source_title` | string | `Translate from` | — | picker opened for the source side |
| `text_lang_sheet_target_title` | string | `Translate to` | — | picker opened for the target side |
| `text_lang_recent_header` | string | `Recent` | — | list section header — 15a, role-neutral because that section is served the merged source∪target view |
| `text_lang_target_recent_header` | string | `Recently used as target` | — | 16a's header. Its section is served TARGET recents only, so the header can be checked against its rows; empty → the whole section is **absent**, never a header over nothing |
| `text_lang_all_header` | string | `All languages` | — | list section header |
| `text_lang_voice_legend` | string | `Speaker marks languages this device can also speak offline — a voice, installed separately from the translate pack` | — | 16a, said **once** above the list, never per row. Drawn only when at least one visible row carries the mark — on a device with no installed voices it would explain an absence (ruling §7.6, no dead affordances) |
| `text_lang_show_all` | string | `Show all languages` | — | expands past the recents block |
| `text_lang_search_hint` | plurals | `Search %1$d language` / `Search %1$d languages` | count | ⚠ the count is the **real catalogue size**, never the design export's hardcoded 65 |

## 3. Language picker — per-row state

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_on_device` | string | `On device` | — | row is downloaded, size not yet measured |
| `text_lang_on_device_size` | string | `On device · %1$s` | size label | used only once a real size exists — no invented MB |
| `text_lang_online_only` | string | `Online only` | — | language has no offline model |
| `text_lang_downloading` | string | `Downloading…` | — | ML Kit reports no progress, so no `%` is shown |
| `text_lang_on_device_count` | plurals | `%1$d of %2$d on device` | on-device, offline-capable | ⚠ the denominator is **offline-capable** languages, not the full catalogue row count |

### 3.1 Offline-library meter (17b foldable two-leaf · U-5 · issue #130 PR-15)

Drawn at the foot of the shortcut leaf, and **only** on a folded window — 17a's
272 × 412 landscape pane has no room for it and the export draws none there.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_library_title` | string | `Offline library` | — | card overline |
| `text_lang_library_downloaded` | string | `%1$d` | packs on device | the big numeral, its own key so locales with their own digits can shape it |
| `text_lang_library_used` | plurals | `of %1$d packs · %2$s used` | offline-capable, size label | the measured walk of ML Kit's model store succeeded |
| `text_lang_library_free` | plurals | `of %1$d packs · %2$s free` | offline-capable, size label | the line for **"the size is not knowable"**: the store directory is absent, renamed or empty while packs are counted. `0 MB used` would be a claim about the disk that nothing checked (risk R8). It is **also the first card a new user sees** — a `pm clear` on `emulator-5554` read `1 of 59 packs · 8.6 GB free`, because ML Kit counts the English pivot as on device before anything is downloaded and this app's store does not exist yet (E-S1b, `docs/research/issue-130-e-s1-storage-walk.md`). Not a rare degrade |
| `text_lang_library_none` | plurals | `of %1$d packs · nothing downloaded` | offline-capable | outranks both of the above whenever the count is zero, however the walk answers: a store can outlive the packs deleted from it, and a size printed under a count of nought is a size for something the user does not have. Close to unreachable on Play-Services hardware for the E-S1b reason above; kept because a zero count still needs a truthful card |

### 3.2 Tablet dialog host (17c/17d · issue #130 PR-16)

The picker on a tablet is a card over the screen that asked for it, not a
destination. Two of these three keys exist only because of that: a card has a
Close cross where a screen has a back arrow, and a docked action bar where a
screen has none.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_dialog_manage_packs` | string | `Manage packs` | — | the docked action, as drawn in all four tablet frames. It opens the SAME destination the Home row does, and owner ruling 5 relabels that row **"Language packs"** in PR-23 — the two are deliberately left un-unified here rather than silently changed in one place, because the relabel is PR-23's across three locales |
| `lang_dialog_cancel` | string | `Cancel` | — | close the card, change nothing. The engineering brief §9 already ruled these four tablet dismisses read "Cancel" correctly; the prohibition on the word applies to the ✕ on a **downloading row**, which stops a download and removes it |
| `lang_dialog_close` | string | `Close` | — | content description for the card's leading cross. Not `cd_lang_back`: that key says "Back", which promises a return to somewhere, and the card returns to a screen that never left |

## 4. Language picker — empty, loading and failure states (EDGE_CASES, no dead ends)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_loading` | string | `Loading languages…` | — | first paint |
| `text_lang_no_results` | string | `No languages match “%1$s”` | query | names the query back |
| `text_lang_no_results_body` | string | `Check the spelling, or try the language's own name.` | — | the way out, not just the bad news |

### 4.1 Failed-download copy — ONE set, every surface (#175, #130 PR-18)

Owned by `DownloadFailure.kt`, which is the only `when` over `OfflineModelFailure` in the
feature. It is read by the picker's failed row, the offline manager's failed row and (through
the same map) by sheets 19d/19b. `NETWORK` and `WIFI_REQUIRED` share one line deliberately —
the app cannot queue for Wi-Fi (E-W1 never ran, REJECT §7.8), so a separate `WIFI_REQUIRED`
sentence would have to promise a wait that never happens.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_pack_error_network` | string | `No connection. Reconnect and try again.` | — | download outcome — also `WIFI_REQUIRED` |
| `lang_pack_error_storage` | string | `Not enough space. Free some up and try again.` | — | download outcome; the same refusal 19b explains at length |
| `lang_pack_error_generic` | string | `Download didn't finish. Try again.` | — | download outcome, cause unreported by ML Kit |

These three replaced SIX (§8). The wording is the picker's on all three, and the reason is per
message rather than per set: each of its sentences states the fact and then the way out, which
is EDGE_CASES §7's requirement in one line. #175's own recommendation was to keep the
`offline_*` NAMES and take "the offline manager's" generic wording; **both halves are deviated
from, on purpose.** `Something went wrong — retry` names no fact, and `offline_*` is Screen B's
namespace on a key that is now nobody's screen — `lang_*` is this module's shared prefix since
PR-17's sheet-19a set.

## 5. Mobile-data consent — sheet 19a (ONE set, both screens)

Owned by `MobileDataSheet.kt` and raised by the picker AND the offline manager. It replaced two
identical sets behind two dialogs (`text_lang_data_dialog_*`, `offline_data_dialog_*` — §8).

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_sheet_data_title` | string | `Download over mobile data?` | — | **No language name**, and that is the drawing (spec rev 5, 19a). The checkbox below can make the answer STANDING, so a title naming one language would misdescribe what unticking it does; the row that raised the sheet is behind it either way |
| `lang_sheet_data_body` | string | `A language pack is usually 40–65 MB. Your plan may charge for it.` | — | **ONE of the four strings that state the pack size (#219), and all four now carry the same figure** — this, `lang_sheet_space_body` (§5.3), `offline_subtitle` (§7) and `settings_mobile_data_supporting` (`STRINGS_settings.md`). **40–65 MB is the on-disk size (`SZ`) of all 58 translate models declared in `res/raw/translate_models_metadata.json` inside `translate-17.0.3.aar`** — min 39.5 MB (`be_en`), max 63.4 MB (`en_ja`), median 43.7, 53 of 58 between 40 and 50. Not an estimate and not the two samples the issue refused: `af_en`'s declared `SZ` is 44,169,505 bytes and the E-S1 device walk measured 44,169,505 bytes, so `SZ` is the real footprint. The DOWNLOAD is smaller (`DL_SZ` 31.3–48.0 MB) and none of the four sentences claims to state it. Derivation with the command: `docs/plan/issue-219-copy-sweep.md` §1 |
| `lang_sheet_data_always_ask` | string | `Always ask before using mobile data` | — | the standing preference, **inverted**: ticked = keep asking = `allowMobileData` is false. The Settings row says the same bit the other way round (`settings_mobile_data_label`, "Always allow mobile data") |
| `lang_sheet_data_download` | string | `Download now` | — | the filled action (the likely intent, spec §5) |
| `lang_sheet_data_not_now` | string | `Not now` | — | the text action. The export draws **"Wait for Wi-Fi"**; nothing in this app queues a download for Wi-Fi, experiment **E-W1** has never been run, and the rev3 ruling's REJECT §7.8 refuses that word until it has. This is the owner's pre-approved interim (ruling 8) |

### 5.1 Sheet 19a — testTags (C-1)

`tt_lang_sheet_data` (root) · `tt_lang_sheet_data_download` · `tt_lang_sheet_data_not_now` ·
`tt_lang_sheet_data_always_ask` (the whole toggleable ROW, which is the 48dp target and the one
semantics node — the `Checkbox` inside it is read-only and announces nothing of its own).

## 5.2 Failed download — sheet 19d "Interrupted" (#130 PR-18)

Raised by the picker when a download **this screen asked for** ends in a failure that is not
about space. Owned by `PackFailureSheets.kt`.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_sheet_failed_title` | string | `%1$s did not download` | language name | cause-free — the title is the same sentence whatever stopped it |
| `lang_sheet_failed_body_network` | string | `The connection dropped before the pack was ready, so nothing is on the device yet.` | — | as drawn. **The frame's own caption says "progress is kept" and the frame's body says the opposite; the body ships.** `DESIGNER-BRIEF.md:73` — "we must not promise it… Do not claim kept progress" — and `README.md:73`. An interrupted download does leave debris in ML Kit's scratch directory (E-S1c), but debris is not a pack: the sentence is about the pack |
| `lang_sheet_failed_cause_network` | string | `Cause: connection lost. Retrying starts the download again.` | — | the tonal cause card. It states what Retry does, because there is no resume to offer |
| `lang_sheet_failed_body_generic` | string | `The download stopped before the pack was ready, so nothing is on the device yet.` | — | ⚠ **not drawn.** The frame is written for one cause; ML Kit's other failures report none, and reusing the connection sentence for them would invent a reason |
| `lang_sheet_failed_cause_generic` | string | `Cause: not reported. Retrying starts the download again.` | — | ⚠ not drawn, same reason |
| `lang_sheet_failed_close` | string | `Close` | — | the text action, error ink (spec §5: error is reserved for loss and stopping, and this is the label that leaves the loss standing) |
| `lang_sheet_failed_retry` | string | `Retry` | — | the filled action — and the SAME key the failed row's pill spells, because they are one action in two places |

### 5.2.1 Sheet 19d — testTags (C-1)

`tt_lang_sheet_failed` (root) · `tt_lang_sheet_failed_cause` (the tonal cause card, one merged
semantics node) · `tt_lang_sheet_failed_close` · `tt_lang_sheet_failed_retry`.

## 5.3 No space — sheet 19b (#130 PR-18)

Raised by the pre-flight in `RealOfflineModelManager.download`, which refuses before enqueueing
anything when free space is under `REQUIRED_FREE_BYTES` (150 MB). The rev3 ruling settled that
this constant IS 19b's trigger.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_sheet_space_title` | string | `Not enough space` | — | no language name: the pre-flight refuses every pack equally |
| `lang_sheet_space_body` | string | `There is %1$s free on this device. A language pack usually needs 40–65 MB.` | free space | the free-space figure is read at the moment of refusal, never the drawn `12 MB`. The pack figure is the shared one — see `lang_sheet_data_body` (§5.2) for where it comes from. ⚠ **It is not the threshold that raised this sheet.** `REQUIRED_FREE_BYTES` is 150 MiB (157.3 MB), because a download holds the compressed file and the unpacked pack at once — 111.4 MB peak for `en_ja`. So between 65 MB and 157 MB free, this sentence reads as though the user has enough. What 19b should say about that is a design call, residual on **#250** |
| `lang_sheet_space_used` | string | `Other apps and system` | — | the bar's FILL. Used-against-free on one volume, never packs-against-device: at 110 MB the library cannot be plotted against a whole device without misstating one of the two figures (`docs/design/language-screens/README.md:15`) |
| `lang_sheet_space_free` | string | `%1$s free` | free space | the bar's track |
| `lang_sheet_space_manage` | string | `Manage packs` | — | the sole action. The drawn second action, `Free up space`, opens 20e — **PR-25** — so it is omitted rather than wired to nothing (rev3 ruling, PR-18 row). A third wording of "Manage packs" is not introduced: `lang_dialog_manage_packs` is the tablet card's docked action and owner ruling 5 relabels the Home row in PR-23, so this key joins that relabel rather than pre-empting it |

### 5.3.1 Sheet 19b — testTags (C-1)

`tt_lang_sheet_space` (root) · `tt_lang_sheet_space_bar` (the storage bar, semantics cleared —
the body already says "There is 12 MB free" in words) · `tt_lang_sheet_space_manage`.
## 5.4 Remove a pack — sheets 19f + 19g (#130 PR-19)

The 🗑 on the offline manager deleted on the tap until this PR; it now asks. Owned by
`RemovePackSheets.kt`, raised by the offline manager.

**Two of these values do not say what the export draws, and that is the PR.** The drawn 19g reads
*"It is your target language. Removing it switches the target to English."* and *"They stay saved
and will need a connection to reopen."* Both are false about this app, checked in code rather than
argued:

- **Nothing switches.** The language selection has four production writers —
  `LanguagePickerViewModel.select` and `TextViewModel`'s three `setLanguagePair` calls — and the
  remove path is none of them. `RealOfflineModelManager` is built from a `ModelStore` and a
  `StorageProbe`; it cannot reach a preference. Removing a pack takes away OFFLINE capability and
  nothing else, and the language keeps translating through the AUTO waterfall's online tiers.
  This also makes rev3 ruling 3 (owner-approved: *the device language if catalog-capable, else
  `en`*) an answer to a question the app never asks — **no fallback is implemented**.
- **Reopening a saved phrase needs no connection.** `TextViewModel.onHistoryPick` puts
  `translation.targetText` — the stored answer — straight into the result state with no engine
  call, and Retry short-circuits on `TranslationRepository.cachedAny`, a database read.

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `lang_sheet_remove_title` | string | `Remove %1$s?` | language name | 19f, as drawn. The verb matches the button's, which is the export's own caption for the frame — and neither is "Delete" |
| `lang_sheet_remove_body` | string | `Frees space on this device. %1$s will need a connection to translate until you download it again.` | language name | 19f, as drawn, and true: `RealTranslator.waterfall` falls from ML Kit to GOT to GCT, and names this case in its own trace ("MLKit: fr not downloaded · GOT: offline") |
| `lang_sheet_remove_confirm` | string | `Remove` | — | the error-filled action (spec §5 reserves error for loss and stopping) |
| `lang_sheet_remove_cancel` | string | `Cancel` | — | **shared by both sheets** — one word, one key. The engineering brief §9 already ruled a sheet dismiss reading "Cancel" is correct; the prohibition is on the ✕ of a downloading row, which stops a download and removes it |
| `lang_sheet_remove_inuse_title` | string | `%1$s is in use right now` | language name | 19g, as drawn |
| `lang_sheet_remove_inuse_body` | string | `It is your target language, and it stays your target. Translations into %1$s will need a connection until you download it again.` | language name | **NOT as drawn** — see above. What 19g adds over 19f is immediacy: this is not a capability the user might miss one day, it is the next translation they make |
| `lang_sheet_remove_inuse_confirm` | string | `Remove anyway` | — | as drawn. The word still reads correctly: the sheet does state a reason to hesitate |
| `lang_sheet_remove_inuse_saved` | plurals | `%1$d saved phrase uses %2$s. It stays saved and still opens without a connection.` / `%1$d saved phrases use %2$s. They stay saved and still open without a connection.` | count, language name | first sentence as drawn and true — saved rows live in Room and nothing on the delete path can reach them; second sentence corrected. A plural because `%1$d` in a plain string renders "1 saved phrases". **Drawn only above zero** — the line is ABSENT at zero, never a sentence about nothing (the same decision an empty recents section already gets) |

### 5.4.1 Sheets 19f/19g — testTags (C-1)

`tt_lang_sheet_remove` (19f root) · `tt_lang_sheet_remove_confirm` · `tt_lang_sheet_remove_cancel` ·
`tt_lang_sheet_remove_inuse` (19g root) · `tt_lang_sheet_remove_inuse_confirm` ·
`tt_lang_sheet_remove_inuse_saved` (the whole bookmark ROW; its glyph is decorative and the
sentence carries the fact) · `tt_lang_sheet_remove_inuse_cancel`.

### 5.4.2 Accessibility

No `cd_*` keys of their own. Both sheets are `TranzlateSheetScaffold`s, which put `paneTitle` on
the content root and `heading()` on the title, so the title is what a screen reader lands on when
the sheet opens; both icons are decorative (`contentDescription = null`) because the title carries
the meaning.

**The row 🗑 now says "Remove", and it used to say "Delete" (#229).** This section argued the
divergence was tolerable — that "Delete" *"still names the action the user is starting"* — and
deferred the re-wording to whenever the `cd_text_lang_retry` / `offline_cd_retry` pair was fixed.
That was wrong on its own terms: a screen-reader user heard "Delete Spanish", tapped, and was
asked "Remove Spanish?", while the sighted user beside them met one verb. Frame 19f's own caption
is the rule — *"The verb in the button matches the verb in the title"* — and a description is not
exempt from it. The visible copy was the reviewed and drawn half, so the spoken half moved.

Per **locale**, not per key: `fil` said `Burahin` against a sheet saying `Alisin`, and `pt-rBR`
said `Excluir` against `Remover`. All three had drifted separately, so a fix to `values/` alone
would have left two of them wrong. Pinned in all three by `PackFailureCopyTest`.

The `cd_text_lang_retry` / `offline_cd_retry` pair below is **still open** and is not folded in
here — consolidating two keys into one needs the `stringResource` call site in
`OfflineLanguagesScreen.kt`, which #229's PR owned only the previews of. Recorded as a residual on
**#250**, the open issue about that same control on that same screen.

## 6. Language picker — accessibility (C-4)

Row descriptions carry the language name **and** its state, because the trailing icon alone says
nothing to a screen reader.

| Key | Type | `en` | Args |
|-----|------|------|------|
| `cd_text_lang_search` | string | `Search languages` | — |
| `cd_text_lang_clear` | string | `Clear search` | — |
| `cd_text_lang_row_on_device` | string | `%1$s, on device` | language name |
| `cd_text_lang_row_downloading` | string | `%1$s, downloading` | language name |
| `cd_text_lang_row_downloadable` | string | `%1$s, available for offline use` | language name |
| `cd_text_lang_row_online_only` | string | `%1$s, online only` | language name |
| `cd_text_lang_row_failed` | string | `%1$s, download failed` | language name |
| `cd_text_lang_row_voice` | string | `%1$s, can be spoken offline` | the already-formatted row description above |
| `cd_text_lang_download` | string | `Download %1$s for offline use` | language name |
| `cd_text_lang_stop` | string | `Stop download and remove %1$s` | language name |
| `cd_text_lang_retry` | string | `Retry download for %1$s` | language name |
| `cd_lang_back` | string | `Back` | — |

> **Never "Cancel".** ML Kit's `RemoteModelManager.download()` has no cancel, so stopping a
> download *is* removing the partial model — `cd_text_lang_stop` says what actually happens.

> **`cd_text_lang_retry` changed VALUE in #130 PR-18, and the control is why.** It read
> `Try downloading %1$s again` while the failed row's action was a bare ↻ icon with no visible
> text. PR-18 replaces the icon with the drawn `Retry` pill, and WCAG 2.5.3 (*Label in Name*)
> requires a control's accessible name to CONTAIN the words drawn on it — otherwise a
> voice-control user says "tap Retry" and nothing happens. `Retry download for %1$s` contains
> it; the old wording did not. The key survives rather than being retired, because a screen
> reader user still needs the language name the sighted user reads a few dp to the left. The
> offline manager's row keeps its own icon-only control and its own `offline_cd_retry` — a
> divergence §9 now names.

> **`cd_text_lang_row_voice` wraps, it does not replace.** The speaker glyph itself is silent to
> TalkBack (`contentDescription = null`); the fact is folded into the row's one description so a
> screen reader hears a sentence, not a row followed by a loose decorative node. There is no
> "no offline voice" string: rev 5 cut sheet 19j because the mark is only ever drawn where the
> voice exists, so the absence has nothing to announce here — it is reported by the Speak action
> on the result screen (`text_tts_unavailable`, issue #159).

> `cd_lang_back` is the picker's own back target and deliberately a separate key from the
> composer's `cd_text_back`: C-3 gives one key one home rather than two modules one name. The row
> for it in `STRINGS_text-translation.md` §6 is where it was first agreed; this catalogue is now
> the home, because the key ships from this module.

## 7. Offline languages manager (Settings → Downloads)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `offline_title` | string | `Offline translation` | — | screen title |
| `offline_subtitle` | string | `Download languages to translate without internet (40–65 MB each)` | — | one of the four pack-size strings (#219) — the figure and its derivation are on `lang_sheet_data_body` in §5.2. It said `~30MB` while the picker's own per-row size (`text_lang_on_device_size`) was a **measured** on-disk byte count (plan R3): one screen measured and this one guessed, by 15 MB in the wrong direction |
| `offline_loading` | string | `Loading languages…` | — | |
| `offline_included` | string | `Included with every language` | — | #224 — the ML Kit pivot (English) is included with every downloaded pack and cannot be downloaded or removed on its own: `deleteDownloadedModel("en")` is a measured NO-OP (Branch A, `docs/research/issue-224-en-row-delete.md`). Owner ruling 2026-08-05: the row STAYS — English is the 59th id in `BundledLanguageCatalog.offlineCapableIds`, so hiding it would make the "59 languages" counter (C-11) lie — but is NON-ACTIONABLE: no ⬇/🗑. Rendered as the row's quiet sub-line (`tt_offline_included`) in place of a control; the pivot is identified by `isPivotLanguage` / `PIVOT_LANGUAGE_ID` in `OfflineLanguagesViewModel.kt` |

> **This screen has no failure copy of its own since #130 PR-18.** Its failed row reads the
> shared `lang_pack_error_*` set (§4.1) through the same map the picker reads — which is the
> whole of issue #175.

### 7.1 Offline manager — accessibility (C-4)

| Key | Type | `en` | Args |
|-----|------|------|------|
| `offline_cd_back` | string | `Back` | — |
| `offline_cd_download` | string | `Download %1$s` | language name |
| `offline_cd_stop` | string | `Stop downloading %1$s` | language name |
| `offline_cd_delete` | string | `Remove %1$s` | language name |
| `offline_cd_retry` | string | `Retry downloading %1$s` | language name |

### 7.2 Offline manager — mobile-data consent

No keys of its own since #130 PR-17. The screen raises the SAME sheet the picker raises, from the
same set — §5.

---

## 8. Retired

Struck through on purpose: `verifyStringKeyDocs` ignores `~~strikethrough~~`, so re-adding one of
these keys to a resource file fails the build instead of quietly matching this row.

| Key | Retired by | Why |
|-----|-----------|-----|
| ~~`offline_state_available`~~ · ~~`offline_state_downloading`~~ · ~~`offline_state_downloaded`~~ · ~~`offline_state_deleting`~~ · ~~`offline_state_failed`~~ | issue #152 | the always-on per-row state sub-line was removed from the design in #82; the five keys survived the #146 module move because that PR's contract was a byte-identical move. Zero Kotlin references across every module and source set at the point of deletion. |
| ~~`text_lang_data_dialog_title`~~ · ~~`text_lang_data_dialog_body`~~ · ~~`text_lang_data_dialog_once`~~ · ~~`text_lang_data_dialog_wait`~~ | #130 PR-17 | the picker's `MeteredConsentDialog`, replaced by sheet 19a (§5). Call sites at deletion: 4 `stringResource` in `LanguagePickerScreen.kt`, 12 resource lines across three locales, zero test references. |
| ~~`offline_data_dialog_title`~~ · ~~`offline_data_dialog_body`~~ · ~~`offline_data_dialog_once`~~ · ~~`offline_data_dialog_wait`~~ | #130 PR-17 | the offline manager's inline `AlertDialog`, the second copy of the set above, replaced by the SAME sheet. Call sites at deletion: 4 `stringResource` in `OfflineLanguagesScreen.kt`, 12 resource lines, zero test references. |
| ~~`text_lang_error_network`~~ · ~~`text_lang_error_storage`~~ · ~~`text_lang_error_generic`~~ | #175, #130 PR-18 | the picker's half of the doubled failure copy. The WORDING survives, under `lang_pack_error_*` (§4.1); only the screen-scoped names are retired, because the key is now read by two screens and two sheets. Call sites at deletion: 3 `stringResource` (all inside `LanguagePickerScreen.failureCauseRes`, itself deleted), 9 resource lines across three locales, zero test references. |
| ~~`offline_error_storage`~~ · ~~`offline_error_network`~~ · ~~`offline_error_generic`~~ | #175, #130 PR-18 | the offline manager's half — the copy that DIVERGED. One dropped connection read "No connection. Reconnect and try again." on one screen and "Download failed — check your connection, then retry" on the other, which invites the reading that they are two faults. Call sites at deletion: 3 `stringResource` in `OfflineLanguagesScreen.kt`'s inline `when`, itself deleted; 9 resource lines across three locales, zero test references. |

> **Both `_wait` keys said "Wait for Wi-Fi", and the app never waited for Wi-Fi.** They shipped
> from #90 onwards while `RealOfflineModelManager` passed a bare `DownloadConditions` and nothing
> queued anything — which the rev3 ruling's REJECT §7.8 refuses pre-**E-W1**. Retiring them is the
> honesty half of PR-17; `lang_sheet_data_not_now` is what replaced the promise.

## 9. Known duplication — a consolidation task, not a gate failure

The picker and the offline manager were built by different PRs and shipped **four sets of the same
copy twice**. **Two of the four are now gone:** `text_lang_data_dialog_*` ≡ `offline_data_dialog_*`
merged into the single sheet-19a set (§5) by #130 PR-17, and the three `text_lang_error_*` ≡
`offline_error_*` merged into `lang_pack_error_*` (§4.1) by #130 PR-18, which is **#175**. The
ruling's REJECT §7.8 bounces any PR that adds a third copy of either, and
`DownloadFailureSourceTest` now makes a third failure map a red test rather than a review reflex.

Still doubled: `text_lang_loading` against `offline_loading` (identical), and `cd_lang_back`
against `offline_cd_back` (both `Back`). Both are identical wording, so neither can tell a user
two different things; they are tidiness, not a defect.

**A third pair this section never listed**, found by the independent enumeration PR-18 ran over
the concept rather than over remembered key names: `cd_text_lang_retry` (`Retry download for
%1$s`) against `offline_cd_retry` (`Retry downloading %1$s`) — the SAME divergence shape as
#175, on the control that answers the failure #175 was about, in the accessibility layer where
nobody looks. It is left for its own issue rather than folded in here, because #175's brief
enumerates six keys and a PR that quietly does eight is a PR whose `Call sites:` line stops
meaning anything. **Now a residual on #250**, which owns that control on that screen for PR-23.

> **This paragraph quoted the wrong value until #219's PR, and the gate could not have caught
> it.** It said `cd_text_lang_retry` reads `Try downloading %1$s again` — the pre-PR-18 wording,
> retired two sections above in §6, which documents the change and the WCAG 2.5.3 reason for it.
> `verifyStringKeyDocs` is **resource→doc only** (#220): it checks that every shipped key appears
> in a catalogue, never that what the catalogue SAYS about a key is still true. A stale value in
> doc prose is invisible to it and to an exact-key grep, and this one sat four lines from the
> §9 heading for the life of PR-18. The other three catalogue rows in this file were checked by
> hand against `values/strings.xml` at the same time.

## 10. Translation status (C-12)

Every key above has a `fil` and a `pt-rBR` value in the module's locale files today. Those
wordings were authored in-project, not by a native speaker, and are queued for native-speaker
review with the rest of the `NEEDS-TRANSLATION` backlog.
