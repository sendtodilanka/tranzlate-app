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
| `text_lang_recent_header` | string | `Recent` | — | list section header |
| `text_lang_all_header` | string | `All languages` | — | list section header |
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

## 4. Language picker — empty, loading and failure states (EDGE_CASES, no dead ends)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_loading` | string | `Loading languages…` | — | first paint |
| `text_lang_no_results` | string | `No languages match “%1$s”` | query | names the query back |
| `text_lang_no_results_body` | string | `Check the spelling, or try the language's own name.` | — | the way out, not just the bad news |
| `text_lang_error_network` | string | `No connection. Reconnect and try again.` | — | download outcome |
| `text_lang_error_storage` | string | `Not enough space. Free some up and try again.` | — | download outcome |
| `text_lang_error_generic` | string | `Download didn't finish. Try again.` | — | download outcome |

## 5. Language picker — mobile-data consent dialog

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_data_dialog_title` | string | `Download %1$s over mobile data?` | language name | asked before spending the user's data |
| `text_lang_data_dialog_body` | string | `Language packs are large. You can wait for Wi-Fi instead.` | — | |
| `text_lang_data_dialog_once` | string | `Download once` | — | confirm |
| `text_lang_data_dialog_wait` | string | `Wait for Wi-Fi` | — | dismiss |

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
| `cd_text_lang_download` | string | `Download %1$s for offline use` | language name |
| `cd_text_lang_stop` | string | `Stop download and remove %1$s` | language name |
| `cd_text_lang_retry` | string | `Try downloading %1$s again` | language name |
| `cd_lang_back` | string | `Back` | — |

> **Never "Cancel".** ML Kit's `RemoteModelManager.download()` has no cancel, so stopping a
> download *is* removing the partial model — `cd_text_lang_stop` says what actually happens.

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
| `offline_error_storage` | string | `Not enough space — free some storage, then retry` | — | |
| `offline_error_network` | string | `Download failed — check your connection, then retry` | — | |
| `offline_error_generic` | string | `Something went wrong — retry` | — | |

### 7.1 Offline manager — accessibility (C-4)

| Key | Type | `en` | Args |
|-----|------|------|------|
| `offline_cd_back` | string | `Back` | — |
| `offline_cd_download` | string | `Download %1$s` | language name |
| `offline_cd_stop` | string | `Stop downloading %1$s` | language name |
| `offline_cd_delete` | string | `Delete %1$s` | language name |
| `offline_cd_retry` | string | `Retry downloading %1$s` | language name |

### 7.2 Offline manager — mobile-data consent dialog

| Key | Type | `en` | Args |
|-----|------|------|------|
| `offline_data_dialog_title` | string | `Download %1$s over mobile data?` | language name |
| `offline_data_dialog_body` | string | `Language packs are large. You can wait for Wi-Fi instead.` | — |
| `offline_data_dialog_once` | string | `Download once` | — |
| `offline_data_dialog_wait` | string | `Wait for Wi-Fi` | — |

---

## 8. Retired

Struck through on purpose: `verifyStringKeyDocs` ignores `~~strikethrough~~`, so re-adding one of
these keys to a resource file fails the build instead of quietly matching this row.

| Key | Retired by | Why |
|-----|-----------|-----|
| ~~`offline_state_available`~~ · ~~`offline_state_downloading`~~ · ~~`offline_state_downloaded`~~ · ~~`offline_state_deleting`~~ · ~~`offline_state_failed`~~ | issue #152 | the always-on per-row state sub-line was removed from the design in #82; the five keys survived the #146 module move because that PR's contract was a byte-identical move. Zero Kotlin references across every module and source set at the point of deletion. |

## 9. Known duplication — a consolidation task, not a gate failure

The picker and the offline manager were built by different PRs and ship **two copies of the same
copy**: `text_lang_data_dialog_*` ≡ `offline_data_dialog_*` (identical `en` strings), the three
`text_lang_error_*` against the three `offline_error_*` (same three outcomes, different wording),
`text_lang_loading` against `offline_loading` (identical), and `cd_lang_back` against
`offline_cd_back` (both `Back`).

Recorded here rather than fixed: merging them is a behaviour-visible copy change across two
screens and belongs in its own issue with the owner's sign-off on the surviving wording. The
divergent pair is the one that matters — a user meeting "Not enough space. Free some up and try
again." on one screen and "Not enough space — free some storage, then retry" on the other is being
told the same thing twice in two voices.

## 10. Translation status (C-12)

Every key above has a `fil` and a `pt-rBR` value in the module's locale files today. Those
wordings were authored in-project, not by a native speaker, and are queued for native-speaker
review with the rest of the `NEEDS-TRANSLATION` backlog.
