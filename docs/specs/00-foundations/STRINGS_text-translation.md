# String Catalogue Foundation — Text Translation Feature

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
| `text_char_counter` | **NEW** | string | `%1$d/%2$d` | `%1$d/%2$d` | `%1$d/%2$d` | `%1$d` = used, `%2$d` = limit |

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
| `text_char_counter` | string | `%1$d/%2$d` → "12/500" (NO spaces, C-5) | reuse | reuse | used,limit |
| `text_metered_counter` | string | `%1$d/%2$d today` (used/limit, C-6) | NEEDS-TRANSLATION | NEEDS-TRANSLATION | used,limit |
| `a11y_translating` | string | Translating… | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_result_ready` | string | Translation ready | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_error` | string | Translation failed | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |
| `a11y_limit_reached` | string | Daily Advanced-AI limit reached | NEEDS-TRANSLATION | NEEDS-TRANSLATION | — |

> **C-3:** this catalogue is the ONLY string-key authority; TEST_A11Y references these keys (no invented `a11y_*`/`cd_*`). `usage_counter` is retired (C-6). NEEDS-TRANSLATION = tracked content task, not a spec blocker (C-12).
