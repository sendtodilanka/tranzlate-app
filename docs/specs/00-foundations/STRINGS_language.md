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
| `lang_sheet_data_body` | string | `A language pack is usually 20–45 MB. Your plan may charge for it.` | — | ⚠ the app states a pack size in three places and this is the third wording — see the `offline_subtitle` note in §7. The two measured packs are 44.2 MB (E-S1, af↔en) and 45.7 MB (#90 E3, de↔en), so `~30MB` is the one that is wrong; consolidating them is #175's neighbour, not this row's job |
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
| `lang_sheet_space_body` | string | `There is %1$s free on this device. A language pack usually needs 20–45 MB.` | free space | the figure is measured at the moment of refusal, never the drawn `12 MB`. The `20–45 MB` range is copy, and the same range `lang_sheet_data_body` uses — see the note there about the two packs this project has measured |
| `lang_sheet_space_used` | string | `Other apps and system` | — | the bar's FILL. Used-against-free on one volume, never packs-against-device: at 110 MB the library cannot be plotted against a whole device without misstating one of the two figures (`docs/design/language-screens/README.md:15`) |
| `lang_sheet_space_free` | string | `%1$s free` | free space | the bar's track |
| `lang_sheet_space_manage` | string | `Manage packs` | — | the sole action. The drawn second action, `Free up space`, opens 20e — **PR-25** — so it is omitted rather than wired to nothing (rev3 ruling, PR-18 row). A third wording of "Manage packs" is not introduced: `lang_dialog_manage_packs` is the tablet card's docked action and owner ruling 5 relabels the Home row in PR-23, so this key joins that relabel rather than pre-empting it |

### 5.3.1 Sheet 19b — testTags (C-1)

`tt_lang_sheet_space` (root) · `tt_lang_sheet_space_bar` (the storage bar, semantics cleared —
the body already says "There is 12 MB free" in words) · `tt_lang_sheet_space_manage`.

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
| `offline_subtitle` | string | `Download languages to translate without internet (~30MB each)` | — | ⚠ `~30MB` is a fixed estimate in the copy. The picker's own per-row size (`text_lang_on_device_size`) is a **measured** on-disk byte count that falls back to no number when it cannot be measured (plan R3) — so one screen measures and the other guesses, and `settings_mobile_data_supporting` guesses a third time at `about 30 MB`. One real number, stated once |
| `offline_loading` | string | `Loading languages…` | — | |

> **This screen has no failure copy of its own since #130 PR-18.** Its failed row reads the
> shared `lang_pack_error_*` set (§4.1) through the same map the picker reads — which is the
> whole of issue #175.

### 7.1 Offline manager — accessibility (C-4)

| Key | Type | `en` | Args |
|-----|------|------|------|
| `offline_cd_back` | string | `Back` | — |
| `offline_cd_download` | string | `Download %1$s` | language name |
| `offline_cd_stop` | string | `Stop downloading %1$s` | language name |
| `offline_cd_delete` | string | `Delete %1$s` | language name |
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
the concept rather than over remembered key names: `cd_text_lang_retry` (`Try downloading %1$s
again`) against `offline_cd_retry` (`Retry downloading %1$s`) — the SAME divergence shape as
#175, on the control that answers the failure #175 was about, in the accessibility layer where
nobody looks. It is left for its own issue rather than folded in here, because #175's brief
enumerates six keys and a PR that quietly does eight is a PR whose `Call sites:` line stops
meaning anything.

## 10. Translation status (C-12)

Every key above has a `fil` and a `pt-rBR` value in the module's locale files today. Those
wordings were authored in-project, not by a native speaker, and are queued for native-speaker
review with the rest of the `NEEDS-TRANSLATION` backlog.
