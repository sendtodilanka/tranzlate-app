# String Catalogue Foundation — History & Saved (`:feature:history`)

> Tranzlate · feature: **History** + **Saved** tabs
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Resources: `feature/history/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

C-3 makes this file the key authority for every string `:feature:history` ships. Written after the
resources rather than before — the catalogue was the piece issue #152 found missing. Every `en`
value is transcribed **verbatim from the shipped resource**.

---

## 1. Screen chrome

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `history_title` | string | `History` | — | top-bar title |
| `history_tab_saved` | string | `Saved` | — | second tab |
| `history_filter_all` | string | `All` | — | filter chip — the unfiltered default |
| `history_pair` | string | `%1$s → %2$s` | source, target | language pair on a row; the arrow is `→` in the resource |

## 2. Empty states (EDGE_CASES, no dead ends)

Both empty states name the action that fills the list, so neither is a blank screen.

| Key | Type | `en` | Args |
|-----|------|------|------|
| `history_empty` | string | `No translations yet — translate something and it lands here.` | — |
| `history_saved_empty` | string | `Nothing saved yet — tap the bookmark on any translation to keep it here.` | — |

## 3. Delete and undo

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `history_action_delete` | string | `Delete translation` | — | the row's destructive action |
| `history_deleted` | string | `Translation deleted` | — | snackbar confirming what happened |
| `history_undo` | string | `Undo` | — | snackbar action — the reason delete needs no confirmation dialog |

> The pair `history_deleted` + `history_undo` is the whole safety mechanism for a destructive
> action. Removing the undo action turns an unconfirmed delete into data loss; if the snackbar
> ever goes away, the delete needs a confirmation instead.

## 4. Accessibility (C-4)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `history_cd_back` | string | `Back` | — | top-bar navigation icon |
| `history_cd_open` | string | `Open in translator` | — | row tap target — says where the tap goes, not just "open" |
| `history_cd_save` | string | `Save translation` | — | bookmark toggle, off state |
| `history_cd_unsave` | string | `Remove from saved` | — | bookmark toggle, on state |

> `history_cd_save` / `history_cd_unsave` are a toggle-aware pair: the description states the
> action the tap will perform, which is what TalkBack announces. One shared "Bookmark" string
> would leave a blind user unable to tell the current state.

## 5. Translation status (C-12)

Every key above has a `fil` and a `pt-rBR` value in the module's locale files today. Those
wordings were authored in-project, not by a native speaker, and are queued for native-speaker
review with the rest of the `NEEDS-TRANSLATION` backlog.
