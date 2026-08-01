# String Catalogue Foundation — Text Translation Feature
> **⚠ Superseded in part (2026-07-29, issue #50 / BUSINESS_MODEL.md):** every PLUS/PREMIUM three-tier reference below is stale — D-2 rev.2 collapses tiers to **FREE + PRO**, and C-10 rev.2 removes user engine selection (the mode chip). The tier tables/fakes/strings here get their full rewrite when the Access brain + paywall land; until then this banner wins on conflict.


> Tranzlate · feature: **Text Translation** (Home input → Result)
> Locales: `en` (default), `fil` (Filipino), `pt-rBR` (Brazilian Portuguese)
> Source of truth: `app/src/main/res/values/strings.xml` (+ `values-fil/`, `values-pt-rBR/`)

මෙම catalogue එකේ ඇති සියලුම `en` / `fil` / `pt-rBR` අගයන් **පවතින `strings.xml` file තුනෙන් verbatim උපුටාගත්** ඒවා විතරයි (line numbers cite කර ඇත). අලුතින් හදන්න ඕන key එකකට හෝ rewrite කරන්න ඕන copy එකකට translation එකක් **guess කරන්නේ නෑ** — ඒවා `NEEDS TRANSLATION` ලෙස flag කර තිබේ. "verified data නෑ" වන තැන් පැහැදිලිව සලකුණු කර ඇත.

---

## 1. Legend

| Mark | තේරුම |
|------|-------|
| **REUSED** | key දැනටමත් `strings.xml` තුළ ඇත — fil + pt-rBR translations ද පවතී; අලුතින් නිර්මාණය කරන්න එපා |
| **NEW** | key එකක් නැත — නිර්මාණය කළ යුතුය |
| **REWRITE** | key එක ඇත, නමුත් `en` copy එක වැරදි/misleading — නැවත ලිවිය යුතුය (translations regenerate කළ යුතුය) |
| `NEEDS TRANSLATION` | faithful `fil` හෝ `pt-rBR` අගයක් සහතික කළ නොහැක — translator කෙනෙකු අවශ්‍යයි (guess කර නැත) |

---

## 2. Master string table

### 2.1 Input / empty-state

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `home_placeholder_hint_1` | REUSED (`values/strings.xml:147`) | string | `Type here…` | `Mag-type dito…` | `Digite aqui…` | — |
| `home_placeholder_hint_2` | REUSED (`:148`) | string | `Tap to enter text…` | `I-tap para maglagay ng text…` | `Toque para inserir texto…` | — |
| `home_edit_no_text_to_translate_warning` | REUSED (`:144`) | string | `Please enter text to translate.` | `Pakilagay ang text na isasalin.` | `Digite um texto para traduzir.` | — |

> **Helper/placeholder note:** පවතින app එකේ separate "helper" string එකක් නැත — `home_placeholder_hint_1` / `_2` දෙකම placeholder rotation සඳහා යොදාගනී (verified: `values/strings.xml:147-148`). වෙනම helper line එකක් අවශ්‍ය නම් එය **NEW** key එකක් වේ (`text_input_helper`, `NEEDS TRANSLATION`).

### 2.2 Character counter

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `text_char_counter` | **NEW** · **AMENDED 2026-07-26** | string | ~~`%1$d/%2$d`~~ → **`%1$d / %2$d`** | same | same | `%1$d` = used, `%2$d` = limit |

> **Spacing amendment (2026-07-26, issue #42 / PR #43 · C-5):** the approved design draws the counter **spaced** — `12 / 500`, not `12/500`. The shipped resource and the shipped assertion (`TextTranslationScreenTest.typeThenTranslate_showsGoldenResult`) both use the spaced form; every spec sentence and test literal must follow. The `%1$d`/`%2$d` argument order and the no-plurals reasoning below are unchanged.

> **Plurals අවශ්‍ය නෑ.** මෙය count එකක් නොව **ratio (used ∕ limit)** එකක්. දෙපැත්තම සංඛ්‍යා — grammatical number agreement නැත; සියලු locale වලට එකම numeric format එක. `limit` සඳහා default `Constants.Defaults.TEXT_LIMIT = 500` (verified `values/strings.xml:272` — "500-character limit per translation").

### 2.3 Detected-language label

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `language_detected` | REUSED (`:88`) | string | `%1$s (Detected)` | `%1$s - Natukoy` | `%1$s - Detectado` | `%1$s` = language name |

### 2.4 Primary action — Translate button

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `button_translate` | REUSED (`:38`) | string | `Translate` | `Isalin` | `Traduzir` | — |
| `cd_translate` | REUSED (`:128`) | string | `Translate` | `Isalin` | `Traduzir` | — (contentDescription) |

### 2.5 Result-action controls — labels + contentDescriptions

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `cd_swap_language` | REUSED (`:129`) | string | `Swap Language` | `Palitan ang Wika` | `Trocar Idioma` | — (swap/reverse-direction CD) |
| `home_result_option_reverse_translate` | REUSED (`:150`) | string | `Reverse Translation` | `Baliktarin ang Pagsasalin` | `Tradução Reversa` | — (reverse label) |
| `home_result_option_copy` | REUSED (`:151`) | string | `Copy` | `Kopyahin` | `Copiar` | — (copy label) |
| `cd_copy` | **NEW** | string | `Copy translation` | `Kopyahin ang pagsasalin` *(from `Kopyahin` :151 + noun)* → `NEEDS TRANSLATION` | `Copiar tradução` *(from `Copiar` :148)* → `NEEDS TRANSLATION` | — (copy CD) |
| `button_favourite` | REUSED (`:56`) | string | `Favourite` | `Paborito` | `Favorito` | — (star label) |
| `cd_favourite` | **NEW** | string | `Add to favourites` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — (star CD; toggle-aware, see note) |
| `home_result_option_add_to_collection` | REUSED (`:149`) | string | `Add to Collection` | `Idagdag sa Koleksyon` | `Adicionar à Coleção` | — (add-to-collection label) |
| `cd_add_to_collection` | **NEW** | string | `Add to collection` | `Idagdag sa Koleksyon` *(reuse label :146)* | `Adicionar à Coleção` *(reuse label :146)* | — (collection CD) |
| `text_action_listen` | **NEW** | string | `Listen` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — (speak/TTS label) |
| `cd_speak` | **NEW** | string | `Read translation aloud` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — (speak CD) |

> **`cd_favourite` toggle note:** star එකට states දෙකක් තියෙනවා (on/off). Accessibility නිවැරදිව කරන්න **NEW keys දෙකක්** සලකා බලන්න: `cd_favourite_add` ("Add to favourites") + `cd_favourite_remove` ("Remove from favourites"). මෙය single string එකකට වඩා screen-reader UX වැඩිදියුණු කරයි.
> **Speak/TTS:** existing `chat_intent_speak_now` = "Speak now…" (`:94`) යනු **microphone input** prompt එකක් — text-translation එකේ TTS "listen/read-aloud" action එකට **අදාළ නෑ**. එබැවින් `text_action_listen` / `cd_speak` NEW වේ.

### 2.6 Translation-mode display names

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `text_mode_automatic` | **NEW** | string | `Automatic` | `NEEDS TRANSLATION` *(cand. `Awtomatiko`)* | `NEEDS TRANSLATION` *(cand. `Automático`)* | — |
| `text_mode_offline` | **NEW** | string | `Offline on this device` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — |
| `text_mode_standard_online` | **NEW** | string | `Standard online` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — |
| `text_mode_advanced_ai` | **NEW** | string | `Advanced AI` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — |

> මේ 4 මාදිලි index-to-tier mapping (verified `CLAUDE.md` "Translation Models (4 tiers)"): `0 AUTO` → Automatic, `1 ML2-mini (offline)` → Offline on this device, `2 ML2 ONLINE` → Standard online, `3 NLP3.5 ONLINE` → Advanced AI. දැනට codebase එකේ මේ **user-facing display names strings.xml තුළ නෑ** (`data/model/TranslationModels.kt` සතුව verified data නෑ — file එක මම read කර නැත; නම් internal string literal ලෙස තිබිය හැක). ඒවා `strings.xml` වෙත ගෙන ඒම මෙම foundation එකේ NEW වැඩකි. Candidate translations `Awtomatiko`/`Automático` plausible වුවත් සම්පූර්ණ set එකම human-verify විය යුතුය.

### 2.7 Metered counter + Premium / lock chip

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `text_metered_counter` | **NEW** | string | `%1$d/%2$d left today` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | `%1$d` = remaining, `%2$d` = daily quota |
| `text_premium_chip` | **NEW** | string | `Premium` | `Premium` *(loanword, unchanged)* | `Premium` *(loanword, unchanged)* | — (lock/upsell chip) |

> **Counter shape:** `text_metered_counter` යනු ratio-style counter එකක් (remaining ∕ quota) — plurals අවශ්‍ය නෑ, `%1$d/%2$d` numeric. Advanced-AI (NLP3.5, model 3) free-tier daily quota = **20/day** (verified `CLAUDE.md`: `FEATURE_LIMIT_PER_DAY` default 20; `Constants.Defaults.FEATURE_LIMIT_PER_DAY = 20`). "left today" වචන අඩංගු නිසා `en` copy human-verify කරන්න; word-included variant එකක් ඕන නම් plurals සලකා බැලිය හැක, නමුත් ratio-first approach එකෙන් plurals මගහරී.
> **Chip brand:** Constants දෙක `PERMISSION_PREMIUM = "premium"` සහ `PERMISSION_PLUS = "plus"` (verified `CLAUDE.md` → `Constants.Qonversion`). Chip label එක tier-aware විය යුතු නම් `text_plus_chip` = "Plus" ලෙස **දෙවන NEW key** එකක් ද අවශ්‍ය වේ.

### 2.8 Usage-limit-reached copy  ⚠ REWRITE

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `home_edit_dialog_reach_text_limit_title` | **REWRITE** (`:145`) | string | *current:* `Need More Words? Upgrade Now!` | `Kulang pa? Mag-upgrade na!` *(regenerate)* | `Precisa de Mais Palavras? Faça Upgrade Agora!` *(regenerate)* | — |
| `home_edit_dialog_reach_text_limit_subtitle` | **REWRITE** (`:146`) | string | *current (BROKEN):* `You are limited to %1$s characters per translation. Subscribe to Translate Pro for unlimited translations and an enhanced experience!` | *current:* `Hanggang %1$s karakter lang ang pwede sa bawat pagsasalin. Mag-subscribe sa Translate Pro para sa unlimited na pagsasalin…` | *current:* `Você está limitado a %1$s caracteres por tradução. Assine o Translate Pro para traduções ilimitadas…` | `%1$s` = char limit |

**Proposed rewritten `en` (char-limit dialog):**
- title → `You've reached the character limit`
- subtitle → `Free translations are capped at %1$s characters. Upgrade to Premium for unlimited-length translations and an ad-free experience.`

**Proposed NEW daily-quota-reached copy** (Advanced-AI 20/day exhaustion — *distinct from char limit*, කිසි key එකක් නැත):

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `text_daily_limit_title` | **NEW** | string | `You've used today's free translations` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — |
| `text_daily_limit_body` | **NEW** | string | `You've reached your %1$d free Advanced AI translations for today. Upgrade to Premium for unlimited access, or try again tomorrow.` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | `%1$d` = daily quota (20) |

> **⚠ ඇයි "Translate Pro" copy එක broken?** ලබාදුන් product model එකේ tiers දෙකක් තියෙනවා — **Plus** සහ **Premium** (`Constants.Qonversion.PERMISSION_PLUS` / `PERMISSION_PREMIUM`, verified `CLAUDE.md`). නමුත් copy එකේ කියන්නේ single "**Translate Pro**" tier එකක් ගැන — මෙය පවතින entitlement structure එකට **inconsistent** සහ misleading. **Disconfirmation-worthy note:** "Translate Pro" යනු `strings.xml` පුරාම brand string ලෙස පවතී (`drawer_translate_pro` :134, `subscription_title_2` :210, `setting_feature_card_title` :292 — තුනම verified). එබැවින් "Translate Pro" යනු **intentional marketing brand එකක්ද** නැත්නම් **stale copy එකක්ද** යන්න PM/product owner විසින් තහවුරු කළ යුතුය. Task brief එකෙන් product = "Plus"/"Premium" ලෙස කියා ඇති නිසා ඉහත rewrite එහි ලියා ඇත — නමුත් brand-wide "Translate Pro" strings (:134, :210, :292 ආදිය) ද එකවර සමපාත කර (align) ගත යුතුය, නැතිනම් app එක තුළ names දෙකක් පෙනේ.

### 2.9 Error state — title + body + Retry

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `home_result_error_view_title` | REUSED (`:152`) | string | `Translation Unavailable` | `Hindi Ma-translate` | `Tradução Indisponível` | — (error title) |
| `home_result_invalid_value` | REUSED (`:153`) | string | `We couldn't detect the language because the text is too short. Try adding more words for better results.` | `Hindi matukoy ang wika dahil masyadong maikli ang text. Subukang magdagdag ng mas maraming salita.` | `Não conseguimos detectar o idioma porque o texto é muito curto. Tente adicionar mais palavras para obter melhores resultados.` | — (too-short body) |
| `text_error_generic_body` | **NEW** | string | `Something went wrong while translating. Please check your connection and try again.` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | — (generic retryable body) |
| `button_retry` | REUSED (`:58`) | string | `Retry` | `Subukang muli` | `Tentar novamente` | — |

> `home_result_invalid_value` යනු **"text too short / language-undetectable"** error body එකට පමණයි. Network/API failure වැනි generic retryable error එකකට body string එකක් **නෑ** — `text_error_generic_body` NEW. (Note: `toast_try_again_later` = "Please try again later." :101 පවතී, transient toasts වලට reuse කළ හැක.)

### 2.10 Offline-model-missing guidance

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Args |
|-----|--------|------|------|-------|----------|------|
| `camera_src_language_not_downloaded` | REUSED (`:82`) | string | `To use camera translation with %1$s offline, please download the language pack first or connect to the internet.` | *(fil present — see note)* | *(pt present — see note)* | `%1$s` = language |
| `language_download_dialog_subtitle` | REUSED (`:158`) | string | `Translate this language even when you are offline by downloading an offline translation file.` | `Isalin ang wikang ito kahit offline sa pamamagitan ng pag-download ng translation file.` | `Traduza este idioma mesmo quando estiver offline, baixando o arquivo de tradução offline.` | — |
| `text_offline_model_missing` | **NEW** | string | `%1$s isn't available offline yet. Download the language pack or switch to an online mode to translate.` | `NEEDS TRANSLATION` | `NEEDS TRANSLATION` | `%1$s` = language |

> **ඇයි NEW `text_offline_model_missing`?** පවතින `camera_src_language_not_downloaded` (:82) copy එක **camera** feature එකට specific ("camera translation") — text-translation flow එකට word-for-word නොගැලපේ. ඒ නිසා text-specific variant එකක් NEW. `language_download_dialog_subtitle` (:158) download-dialog subtitle එකක් ලෙස reuse කළ හැක, නමුත් inline "model missing" banner එකට වෙනම copy අවශ්‍ය වේ.

---

## 3. Summary counts

| Category | REUSED | NEW | REWRITE |
|----------|:---:|:---:|:---:|
| Input / empty-state | 3 | 0 (1 optional helper) | 0 |
| Char counter | 0 | 1 | 0 |
| Detected label | 1 | 0 | 0 |
| Translate button | 2 | 0 | 0 |
| Result actions + CDs | 4 | 4 (+2 optional favourite states) | 0 |
| Mode display names | 0 | 4 | 0 |
| Metered / chip | 0 | 2 (+1 optional Plus chip) | 0 |
| Usage-limit copy | 0 | 2 (daily-quota) | 2 (char-limit) |
| Error state | 3 | 1 | 0 |
| Offline-missing | 2 | 1 | 0 |
| **Total** | **15** | **~15** | **2** |

---

## 4. Action items (translation backlog)

1. **REWRITE — Plus/Premium copy:** `home_edit_dialog_reach_text_limit_title` + `_subtitle` (`en`) rewrite කර, "Translate Pro" → "Premium" පැහැදිලි කරන්න; ඉන්පසු `fil` + `pt-rBR` regenerate කරන්න. Brand-wide "Translate Pro" strings (`drawer_translate_pro`, `subscription_title_2`, `setting_feature_card_title`) product owner සමඟ align කරන්න.
2. **NEEDS TRANSLATION (fil + pt-rBR):** `cd_copy`, `cd_favourite`, `text_action_listen`, `cd_speak`, `text_mode_*` (4), `text_metered_counter`, `text_daily_limit_*` (2), `text_error_generic_body`, `text_offline_model_missing`.
3. **NEW `en` copy to finalize with PM:** mode display names (2.6), metered counter wording (2.7), daily-limit dialog (2.8).
4. **Accessibility:** star action සඳහා toggle-aware CD keys දෙකක් (`cd_favourite_add` / `cd_favourite_remove`) සලකා බලන්න.

---

## 5. Verification provenance

- සියලු **REUSED** අගයන් `grep -n` මගින් line-cited: `values/strings.xml`, `values-fil/strings.xml`, `values-pt-rBR/strings.xml`.
- Tier/quota facts (`FEATURE_LIMIT_PER_DAY=20`, `TEXT_LIMIT=500`, 4 model tiers, `PERMISSION_PLUS`/`PERMISSION_PREMIUM`) `CLAUDE.md` වෙතින් verified.
- **verified data නෑ:** `data/model/TranslationModels.kt` තුළ user-facing mode strings දැනට literal ලෙස තිබේද යන්න මම file එක read නොකළ නිසා තහවුරු කර නැත — foundation එකේ ඒවා `strings.xml` වෙත ගෙන ඒම NEW ලෙස සලකා ඇත.
---

## Canonical additions & overrides (C-3..C-6) — these win over any earlier row

| key | type | en | fil | pt-rBR | args |
|-----|------|----|----|--------|------|
| `text_char_counter` | string | ~~`%1$d/%2$d` → "12/500" (NO spaces)~~ → **`%1$d / %2$d` → "12 / 500" (WITH spaces, C-5 amended 2026-07-26)** | reuse | reuse | used,limit |
| `text_metered_counter` | string | `%1$d/%2$d today` (used/limit, C-6) | NEEDS-TRANSLATION | NEEDS-TRANSLATION | used,limit |
| `a11y_translating` | string | Translating… | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_result_ready` | string | Translation ready | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_error` | string | Translation failed | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_limit_reached` | string | Daily Advanced-AI limit reached | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |

> **C-3:** this catalogue is the ONLY string-key authority; TEST_A11Y references these keys (no invented `a11y_*`/`cd_*`). `usage_counter` is retired (C-6). NEEDS-TRANSLATION = tracked content task, not a spec blocker (C-12).

---

## 6. Issue #11 vertical additions (2026-07-22 — C-3 "missing keys get ADDED to STRINGS")

Keys shipped by the Home/Composer/Drawer/Result vertical (PR-C). `en` values are the UI_SPEC §2 approved copy; every `fil`/`pt-rBR` cell is **NEEDS TRANSLATION** unless a reuse is cited (tracked content task, C-12 — `tools:ignore="MissingTranslation"` is the interim guard in the shipped resource files).

| Key | Type | `en` | Notes |
|-----|------|------|-------|
| `text_input_placeholder` | string | `Enter text` | UI_SPEC §2.2 composer placeholder (supersedes old-app hint rotation for this surface) |
| `cd_text_input` | string | `Text to translate` | contract §2.1 row 1 |
| `cd_text_edit` | string | `Edit text` | 5a read face — the source text is tappable to resume editing (issue #46); TalkBack action label |
| `cd_text_counter` | string | `%1$d of %2$d characters used` | contract §2.1 row 7 |
| `cd_text_source_lang` / `cd_text_target_lang` | string | `Source language, %1$s` / `Target language, %1$s` | contract §2.1 rows 4–5 |
| `cd_text_mic` | string | `Translate by voice` | composer mic morph (voice vertical later) |
| `cd_text_mode_chip` | string | `Translation model, %1$s` | contract §2.1 row 6 |
| `cd_text_menu` / `cd_text_clear` / `cd_text_back` / `cd_text_more` | string | `Open navigation` / `New translation` / `Back` / `More options` | hub top bar + result top bar |
| `cd_lang_back` | string | `Back` | the picker's own back target. Same words as `cd_text_back`, deliberately a separate key: the picker lives in `:feature:language` since #130 PR-6, and C-3 gives one key one home rather than two modules one name |
| `cd_text_copy_source` / `cd_text_speak_source` | string | `Copy source text` / `Read source text aloud` | result source block |
| `cd_text_thumb_up` / `cd_text_thumb_down` | string | `Good translation` / `Bad translation` | result feedback (guided no-op until #8) |
| `cd_text_retry` | string | `Retry translation` | contract §2.1 row 13 |
| `home_greeting_morning` / `home_greeting_afternoon` / `home_greeting_evening` | string | `Morning` / `Afternoon` / `Evening` | UI_SPEC §2.1 time-aware greeting |
| `home_greeting_named` | string | `%1$s, %2$s` | greeting + account name (name slot — no account system yet) |
| `home_subtitle` | string | `What would you like to translate?` | UI_SPEC §2.1 |
| ~~`home_tile_conversation` (+`_sub`) · `home_tile_camera` (+`_sub`)~~ | string | ~~`Conversation`/`Two-way talk` · `Camera`/`Point and translate`~~ | **REMOVED (issue #26, D-5 rev.2)** — canvas quick-action tiles gone; Conversation→Chat tab, Camera→Camera tab (bottom nav) |
| `text_guided_mode` / `text_guided_voice` / `text_guided_more` / `text_guided_feedback` | string | "… arrives with the … update" family | EDGE_CASES no-dead-end guided messages for not-yet-built asks. *(2026-08-01, #152: the row used to read `/ _tts / _conversation / _bookmark` as well; those three keys were never created, and the suffix shorthand hid both that and the fact that `text_guided_more` / `text_guided_feedback` were undocumented under a literal search. Keys are spelled out in full from here on.)* |
| `text_copied` | string | `Copied` | EDGE_CASES §7 copy success feedback |
| `text_engine_badge_offline` / `text_engine_badge_online` / `text_engine_badge_advanced` | string | `Offline · instant` / `Online` / `Advanced AI` | UI_SPEC §2.4 engine badge per resolved Engine (C-9) |
| `text_error_generic_body` | string | `Something went wrong while translating. Please check your connection and try again.` | §2.9 (already proposed NEW there) |
| `text_error_unsupported_pair` | string | `This language pair isn't supported yet. Try a different language pair.` | UNSUPPORTED_PAIR outcome (G8) |
| `text_lang_sheet_source_title` / `text_lang_sheet_target_title` | string | `Translate from` / `Translate to` | interim minimal picker sheet. Both keys now ship from `:feature:language` — `STRINGS_language.md` §2 is their home |
| ~~`drawer_search`~~ / `drawer_history` / `drawer_saved` / `drawer_offline_languages` / `drawer_settings` / `drawer_help` / `drawer_about` | string (`:app`) | ~~`Search`~~ / `History` / `Saved` / `Offline languages` / `Settings` / `Help` / `About` | UI_SPEC §2.3 drawer sections (Search retired — never built, issue #26; +Help/About) |
| `drawer_recents_header` / `drawer_recents_empty` | string (`:app`) | `Recents` / `Your recent translations will appear here` | drawer Recents |
| `drawer_account_guest` / `drawer_tier_free` | string (`:app`) | `Guest` / `Free` | account row (static until the Access brain) |
| ~~`app_guided_search`~~ | string (`:app`) | ~~`Search arrives with the history update`~~ | **retired** — Search removed from the drawer (issue #26) |

**Reuse decisions applied (C-3):** `cd_translate`, `cd_swap_language`, `button_retry`, `home_edit_no_text_to_translate_warning`, `text_char_counter`, `cd_copy`, `cd_speak`, `cd_favourite`, `text_mode_automatic`, `a11y_translating` — used with the catalogue keys/values above (the contract's older `cd_text_translate`/`cd_text_swap`/`cd_text_speak` spellings defer to these per the C-3 conformance override).

---

## 7. Issue #42 Home card-stack additions (2026-07-26 · PR #43 — C-3 "missing keys get ADDED to STRINGS")

Keys shipped by the D-5 rev.3 Home rebuild. All live in `feature/text/src/main/res/values/strings.xml`; `en` values are the approved Claude Design export's copy, every `fil`/`pt-rBR` cell is **NEEDS TRANSLATION** (tracked content task, C-12 — `tools:ignore="MissingTranslation"` is still the interim guard).

| Key | Type | `en` | Notes |
|-----|------|------|-------|
| `home_title` | string | `Translate` | top app bar title (start-aligned) |
| `home_pro` | string | `Pro` | top-bar upsell chip label |
| `cd_home_settings` | string | `Settings` | top-bar settings icon CD |
| `home_tools` | string | `Tools` | section label above the 2×2 grid |
| `home_translate` | string | `Translate` | the morphed action button's label (distinct from `cd_translate`, which is the CD) |
| `home_tool_offline` / `home_tool_offline_sub` | string | `Offline mode` / `Translate without a connection` | ✅ the hardcoded `6 languages ready` this row used to document is gone — the subtitle no longer states a count it cannot know |
| `home_tool_voice` / `home_tool_voice_sub` | string | `Voice` / `Speak and hear it` | |
| `home_tool_camera` / `home_tool_camera_sub` | string | `Camera` / `Signs and menus` | |
| `home_tool_conversation` / `home_tool_conversation_sub` | string | `Conversation` / `Two-way talk` | |
| `home_row_download` / `home_row_download_sub` | string / **plurals** | `Download languages` / `%1$d language available offline` · `%1$d languages available offline` | ✅ the hardcoded `133 available · 2 updates ready` this row used to document is gone; the subtitle became a real plural with a real count |
| `home_mini_phrasebook` / `home_mini_quotes` | string | `Phrasebook` / `Quotes` | half-width shortcut cards |
| `home_phrasing_title` / `home_phrasing_sub` | string | `Natural phrasing` / `Rewrites idioms so they land right` | AI banner |
| `home_badge_new` | string | `NEW` | banner badge |
| `home_guided_pro` | string | `Subscriptions arrive with the access update` | EDGE_CASES no-dead-end |
| `home_guided_phrasebook` | string | `Phrasebook arrives in a later update` | " |
| `home_guided_quotes` | string | `Saved quotes arrive with the history update` | " |
| `home_guided_phrasing` | string | `Natural phrasing arrives with the AI update` | " |

**Reused unchanged by the new Home:** `text_input_placeholder`, `cd_swap_language`, `cd_text_mic`, `text_char_counter` (spaced form, §2.2), `text_over_char_limit`, `text_guided_voice`.

**Now ORPHANED — still defined, no longer referenced by any Composable** (verified by `R.string.*` sweep of `:feature:text`, 2026-07-26). None were deleted: they are either owed a new home by the rev.3 redesign or are legitimately reserved for a screen not yet built. **Do not delete without checking the "why" column.**

| Key(s) | Why it is orphaned | Disposition |
|---|---|---|
| `home_greeting_morning` / `home_greeting_afternoon` / `home_greeting_evening` / `home_greeting_named` · `home_subtitle` | the greeting canvas is gone — the card stack replaced it | **retire** unless a greeting returns to the design |
| `text_mode_automatic` · `cd_text_mode_chip` | the mode chip has no home in the rev.3 top bar | **keep** — needed the moment the engine picker is re-sited (UI_SPEC §4) |
| `cd_text_menu` | drawer removed (D-5 rev.3) | **retire** with the dead drawer files |
| `cd_text_clear` | the ✕ / new-translation action went with the old top bar | **keep** — the action itself is still owed |
| `cd_text_input` · `cd_text_counter` · `cd_text_source_lang` · `cd_text_target_lang` | ✅ **attached** — PR [#44](https://github.com/sendtodilanka/tranzlate-app/pull/44) re-attached all four (the field via `semantics`, the counter via `semantics`, both pills via `semantics(mergeDescendants = true)`); verified in a live accessibility-tree dump. *(Briefly unattached in #43 — a real regression, now closed.)* | **no longer orphaned** |
| `cd_translate` | the Translate button now carries a visible `home_translate` label, so it has an accessible name without the CD | **keep** (no regression) — reuse if the button ever becomes icon-only |
| `cd_text_retry` | Result-screen retry not re-wired to this key | check when FIX_QUEUE C2 lands |

**Shell strings (`app/src/main/res/values/strings.xml`) — orphaned by the same PR, none deleted:** `nav_home` / `nav_chat` / `nav_camera` (no bottom bar) and the whole `drawer_*` + `app_guided_search` family (no drawer). They come out with the dead `DrawerContent.kt` / `TopLevelDestination.kt` files. `chat_coming_soon_title` / `coming_soon` **stay** — Chat is still a destination, now reached from the Conversation tool card.

**Orphan sweep refreshed 2026-08-01 (#152).** The table above was last verified 2026-07-26 and has
drifted in both directions, so here is the current count rather than a re-assertion of the old one.
A fresh `R.string.*` / `@string/*` sweep across **every module and every source set** finds **22**
`:feature:text` keys with zero references. Fourteen of them are not in the table above:
`text_guided_mode`, `text_guided_more`, `text_guided_feedback`, `cd_text_more`,
`cd_text_copy_source`, `cd_text_speak_source`, `cd_text_thumb_up`, `cd_text_thumb_down`,
`text_engine_badge_offline`, `text_engine_badge_online`, `text_engine_badge_advanced`,
`a11y_translating`, `home_edit_no_text_to_translate_warning`, `cd_text_retry`. Going the other way,
`cd_text_menu` no longer exists as a resource at all, and `cd_text_clear` is referenced again.

Nothing here is deleted by #152 — that issue's mandate was the five `offline_state_*` keys, and
these belong to work in flight. But the "why" column now owes fourteen more rows, and an
unreferenced `a11y_translating` is worth a second look: C-4 makes it a canonical live-region
string, so zero references is more likely a missing announcement than a dead key.

> **2026-08-01 (#152):** the `:app` shell strings now have their own catalogue,
> `STRINGS_shell.md`, and that file is their home. The paragraph above is kept as the record of
> *why* they were orphaned. Of the keys it names, only `nav_home` / `nav_chat` / `nav_camera` and
> `chat_coming_soon_title` still exist as resources; the `drawer_*` family, `app_guided_search`
> and `coming_soon` are gone.

---

## 8. Issue #159 speak-outcome additions (2026-08-01 · PR #159 co-verify — C-3 "missing keys get ADDED to STRINGS")

The speak button used to have ONE failure message for every way speech could fail, and on device it told a **lie**: after a cache-hit translation the result renders before the ~500 ms engine bind finishes, so a tap in that window got *"Speech isn't available for this language on this device"* — about a language and a device that were both fine — and the same button worked seconds later.

The window is now **waited out** instead of reported (`ResultSpeaker.speak` suspends until the bind reports), so "still binding" is no longer sayable at all. What is left are two different truths, which need two different messages — and the second one has to guide, or it is a dead end (EDGE_CASES).

| Key | Status | Type | `en` | `fil` | `pt-rBR` | Shown when |
|-----|--------|------|------|-------|----------|------------|
| `text_tts_unavailable` | REUSED (`feature/text/values/strings.xml:127`) — meaning NARROWED | string | `Speech isn't available for this language on this device.` | `Hindi available ang speech para sa wikang ito sa device na ito.` | `A fala não está disponível para este idioma neste aparelho.` | `SpeakOutcome.NO_VOICE` — the engine bound and reported no voice for the target language (`setLanguage` → `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED`) |
| `text_tts_engine_unavailable` | **NEW** (`:128`) | string | `No speech engine on this device. Add one in system settings to hear translations.` | `Walang speech engine sa device na ito. Magdagdag ng isa sa system settings para marinig ang mga pagsasalin.` | `Nenhum mecanismo de fala neste aparelho. Adicione um nas configurações do sistema para ouvir as traduções.` | `SpeakOutcome.ENGINE_UNAVAILABLE` — no usable engine at all, or it refused to start |

**No message at all** for `SpeakOutcome.STARTED` (audio is playing) and for `SpeakOutcome.CANCELLED` (the engine was released while the tap waited — backgrounding, or the face leaving the result). A request the user already walked away from must not greet them with a failure when they come back.

`fil` / `pt-rBR` above are authored in-issue and **queued for native-speaker review** (C-12, DoD gate 12) — the same standing caveat the three `feature/text` string files carry in their headers.

---

## 9. Issue #152 additions (2026-08-01 — C-3 "missing keys get ADDED to STRINGS")

Twenty keys were shipping from `feature/text/src/main/res/values/` with no row anywhere in this
catalogue. Nothing caught that, because until #152 nothing checked C-3 at all — a rule that holds
only while a reviewer remembers it. The `verifyStringKeyDocs` Gradle task now fails the build on
the next one. `en` values below are transcribed **verbatim from the shipped resource**, not
re-authored.

### 9.1 Composer and result controls

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `composer_paste` | string | `Paste` | — | paste affordance on the empty composer |
| `text_paste_empty` | string | `Clipboard is empty.` | — | paste with nothing to paste — the tap gets an answer instead of nothing (EDGE_CASES) |
| `cd_text_reverse` | string | `Reverse translation direction` | — | contentDescription for the C-7 reverse action |
| `cd_text_counter_limit` | string | `%1$d-character limit reached` | limit | the counter's at-limit description; the under-limit form is `cd_text_counter` |
| `cd_speak_stop` | string | `Stop reading` | — | the read-aloud control while speech is playing — a toggle-aware pair with `cd_speak` |

### 9.2 Actions that cannot run right now (EDGE_CASES availability)

Each of these names the reason, not just the refusal.

| Key | Type | `en` | Args |
|-----|------|------|------|
| `text_swap_needs_detect` | string | `Translate once to detect the language before swapping.` | — |
| `text_tts_unavailable` | string | `Speech isn't available for this language on this device.` | — |
| `text_star_unavailable` | string | `Couldn't save this one — its language wasn't detected.` | — |

### 9.3 Translation failure block

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_error_title` | string | `Couldn't translate` | — | error block heading |
| `text_error_offline` | string | `You're offline. Connect to the internet and try again.` | — | |
| `text_error_limit_reached` | string | `You've used today's free AI translations. They reset at midnight.` | — | states when it comes back, so the wait is knowable |
| `text_error_not_entitled` | string | `This translation quality needs a Pro subscription.` | — | |
| `text_error_edit` | string | `Edit text` | — | the way out of the error block — the block is never a dead end |

### 9.4 Daily-AI meter and the at-limit sheet (C-11, paywall trigger #1)

| Key | Type | `en` | Args | Notes |
|-----|------|------|------|-------|
| `text_ai_meter` | string | `AI translations: %1$d/%2$d left today` | left, total | ⚠ this reads **left / total**. C-6 fixes the canonical metered counter as `text_metered_counter` = `%1$d/%2$d today` reading **used / limit** — the opposite direction. `text_metered_counter` has no resource today, so nothing conflicts on screen yet; whichever survives, C-6 has to be amended or this key renamed, because two counters counting opposite ways is how a user is told two different things about the same quota |
| `limit_sheet_title_quota` | string | `Daily free AI limit reached` | — | the free pool emptied (D-2 rev.2) |
| `limit_sheet_body_quota` | string | `Your 5 free AI-quality translations are used for today. They reset at midnight — or go Pro for unlimited.` | — | ⚠ hardcodes **5**; if the FREE pool size ever moves, this string lies |
| `limit_sheet_title_pro` | string | `This quality needs Pro` | — | the feature is PRO-only, a different fact from an emptied pool |
| `limit_sheet_body_pro` | string | `Natural-phrasing AI translations are a Pro feature. Your free translations keep working.` | — | the second sentence is the no-dead-end half |
| `limit_sheet_cta` | string | `See Pro plans` | — | opens the paywall (`STRINGS_paywall.md`) |
| `limit_sheet_dismiss` | string | `Not now` | — | C-11 requires the sheet be dismissible; the free engines keep working underneath |
