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
| `text_lang_detect` | string | `Detect language` | — | the auto-detect entry on a language pill (Google Translate parity). Lives in `:core:ui` because it is read by the shared `languageLabel()` composable, which is called today from `:feature:language`'s `LanguagePickerScreen` and from `:feature:text`'s `HomeScreen` and `ComposerScreen` — verified by call-site sweep. Duplicating the label per feature is how two surfaces start disagreeing about the same word. |

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
