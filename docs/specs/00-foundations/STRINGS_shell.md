# String Catalogue Foundation — Shell & shared surfaces (`:app`, `:core:ui`, `:feature:camera`)

> Tranzlate · the strings that belong to no single feature: navigation labels, the
> not-shipped-yet destinations, one shared control label, and the camera scaffold placeholder.
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Resources: `app/src/main/res/values/strings.xml`, `core/ui/src/main/res/values/strings.xml`,
> `feature/camera/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/` for each)

C-3 makes this file the key authority for the three modules above. It exists because issue #152
found that a shipped key could have no catalogue at all; these six-plus keys were the leftovers
that fitted none of the feature catalogues. `app_name` is deliberately absent — it comes from each
white-label flavour's `resValue`, not from a resource file.

Every `en` value is transcribed **verbatim from the shipped resource**.

---

## 1. Navigation labels (`:app`)

| Key | Type | `en` | Args |
|-----|------|------|------|
| `nav_home` | string | `Home` | — |
| `nav_chat` | string | `Chat` | — |
| `nav_camera` | string | `Camera` | — |

## 2. Not-shipped-yet destinations (`:app`)

Chat and Camera are reachable destinations that do not do their job yet. The copy states a fact
and points at what **does** work; it promises no date, because we cannot keep one.

| Key | Type | `en` | Args |
|-----|------|------|------|
| `chat_coming_soon_title` | string | `Conversation` | — |
| `chat_coming_soon_body` | string | `Two-way conversation isn't part of this version yet. You can still translate anything you type on the home screen.` | — |
| `camera_coming_soon_title` | string | `Camera` | — |
| `camera_coming_soon_body` | string | `Translating signs and menus with the camera isn't part of this version yet. You can still type or paste the text on the home screen.` | — |
| `cd_coming_soon_back` | string | `Back` | — |

> `cd_coming_soon_back` is the contentDescription (C-4) for the icon-only back button on both
> placeholder screens — one key, because it is literally one control reused, unlike the
> per-feature back keys which each live with their own screen.

## 3. Shared control label (`:core:ui`)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_lang_detect` | string | `Detect language` | — | the auto-detect entry on a language pill (Google Translate parity). Lives in `:core:ui` because it is read by the shared `languageLabel()` composable, which is called today from `:feature:language`'s `LanguagePickerScreen` and from `:feature:text`'s `HomeScreen` and `ComposerScreen` — verified by call-site sweep. Duplicating the label per feature is how two surfaces start disagreeing about the same word. **Variant settle (#130 PR-14) — see below.** |

> ### `text_lang_detect` is ONE key for every shape the Detect affordance takes
>
> The rev3 ruling gives PR-14 "Detect chip variant string ruling-key settle
> (C-conventions single key)". Settled, against the rev 5 export rather than
> against the ruling text:
>
> - **The words never change with the shape.** Decoding the spec's bundler block
>   and reading each frame's markup, the Detect affordance is drawn as a list row
>   in `15a picker light/dark`, in 17a's `from · landscape` and in
>   `from · foldable`, and as a **chip in the top bar** in `from · tablet portrait`
>   and `from · tablet landscape`. All five spell `Detect language`. The chip is a
>   different container, not different copy.
> - **The one frame that says something else is a rejected one.** `15b picker`
>   draws `Detect language automatically`; 15b is REJECT §7.8 in the ruling, so
>   that string is not built and no key is reserved for it.
> - **Therefore: no `text_lang_detect_chip`, and no per-variant key of any kind.**
>   PR-16 builds the tablet chip against THIS key. C-3 already says one key has one
>   home; this row says which shapes that one key has to cover, so the question
>   does not get re-opened by the PR that draws a new one.
> - **`languageBlockLabel()` is the precedent, not an exception:** it upper-cases
>   this same key for the result block. One key, three presentations, already
>   shipped.
>
> PR-14 itself adds **zero** string keys — its landscape bar reuses
> `cd_lang_back`, `text_lang_sheet_{source,target}_title`,
> `text_lang_on_device_count` and `text_lang_all_header`. That is deliberate:
> `:feature:language` already carries TWO divergent sets of the same three
> failure messages (`text_lang_error_*` / `offline_error_*` — issue #175 open),
> and the ruling's REJECT §7.8 bounces a third copy at review. It carried a
> doubled mobile-data set too (`text_lang_data_dialog_*` /
> `offline_data_dialog_*`); **#130 PR-17 retired both** in favour of one
> `lang_sheet_data_*` set behind sheet 19a, which is what the same fix looks like
> when it is in scope.

## 4. Camera scaffold (`:feature:camera`)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `feature_camera_placeholder` | string | `Camera translation` | — | module scaffold placeholder. The real camera catalogue arrives with the camera vertical; this row exists so the scaffold is documented rather than exempt. |

## 5. Translation status (C-12)

All three modules carry full `fil` + `pt-rBR` parity today — every key above has a value in both
locale files — which is why `MissingTranslation` is **not** suppressed in `:app`; lint guards it.
The `feature/camera` resource still carries a stale `TODO(NEEDS-TRANSLATION)` comment claiming its
locale files are queued; they shipped. All authored wordings await native-speaker review, which is
the part that is genuinely still open.
