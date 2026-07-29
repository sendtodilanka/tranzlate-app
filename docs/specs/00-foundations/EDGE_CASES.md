# Edge Cases & Availability (foundation — every feature applies this)

> Problem in the current app: state checks (internet, tier, language, download, limit) are scattered ad-hoc across screens → bugs + dead-ends.
> Rule: **one place per action answers "can I do this right now? if not, why, and what should the user do?"** The UI just reflects that answer. No scattered `if` checks.

## 1. The pattern — an Availability resolver

Every blockable action (translate, download, subscribe, camera, voice…) has ONE resolver that returns:

```kotlin
sealed interface Availability {
    data object Ready : Availability
    data class Blocked(
        val reason: Reason,          // enum — stable, not a string
        val message: Int,            // strings.xml resource
        val primaryAction: Action?,  // what the user can do (a CTA), nullable
    ) : Availability
}
```
- The resolver takes the **current state** (below) and returns `Ready` or `Blocked(reason, message, action)`.
- The UI: enable the action only when `Ready`; otherwise disable it **and** show the message + action. **Never a silent dead-end** — always a reason + a way forward.
- `Loading` states (e.g. entitlement not resolved) → the resolver returns a transient "wait" (don't decide on stale data — the FeatureAccess `Loading` rule).

## 2. State axes (the inputs)

| Axis | Values |
|------|--------|
| Connectivity | online · offline |
| Entitlement | Loading · Free · Pro *(D-2 rev.2, issue #50)* |
| Source language | offline-capable · online-only · Auto(detect) |
| Target language | offline-capable · online-only |
| Download state (offline-capable langs) | downloaded · not-downloaded |
| Engine tier the waterfall is at *(no user selection — C-10 rev.2)* | offline(MLKit) · online-free(GOT) · online-accurate(GCT, quota-gated tail) |
| Usage (metered engine) | under limit · at limit |

## 3. Translation readiness matrix (the user's example + neighbours)

| Internet | Engine | Lang offline-capable? | Downloaded? | Limit | → Result & action |
|:---:|:---:|:---:|:---:|:---:|---|
| offline | offline | yes | **yes** | — | ✅ **Ready** (translate offline) |
| offline | offline | yes | **no** | — | 🚫 Blocked · "offline + not downloaded" → **[Connect to download]** (download needs internet) |
| offline | offline | **no (online-only)** | — | — | 🚫 Blocked · "this language needs internet" → **[Go online] / [Pick another]** |
| **offline** | **online (GOT/GCT)** | — | — | — | 🚫 Blocked · "you're offline" → **[Go online]** or **[Switch to a downloaded language]** |
| online | any | — | — | — | ✅ **Ready** |
| online | GCT (metered) | — | — | **at limit** (Free/Plus) | 🚫 Blocked · "daily limit reached" → **[Upgrade] / [Switch to free mode]** |
| — | metered | — | — | entitlement **Loading** | ⏳ wait (resolve first, don't block/allow on stale) |

> The user's exact case: **offline + online-only language selected → translate disabled, clear reason + action.** ✅ · **offline + already-downloaded → Ready.** ✅

## 4. Reason → message + action catalog (localized, C-3 keys)

| Reason | Message (en) | Action |
|--------|--------------|--------|
| `NO_INTERNET_ONLINE_ENGINE` | You're offline. Connect, or switch to a downloaded offline language. | Go online · Offline languages |
| `OFFLINE_LANG_NOT_DOWNLOADED_OFFLINE` | This language isn't downloaded and you're offline. Connect to download it. | Connect |
| `ONLINE_ONLY_LANG_OFFLINE` | This language only works online. Go online or pick an offline language. | Pick language |
| `DAILY_LIMIT_REACHED` | You've used today's AI-quality translations. Free engines keep working — upgrade for unlimited AI quality. | Upgrade *(free engines continue automatically — there is no mode to switch, C-10 rev.2)* |
| `EMPTY_INPUT` | Enter text to translate. | — (button just disabled) |
| `OVER_CHAR_LIMIT` | Limited to 500 characters per translation. | Upgrade / trim |

## 5. This applies EVERYWHERE — per-feature availability checklist

Every feature spec MUST have an "Availability / edge cases" section resolving its actions against the axes. Examples of "other places":

| Place | Extra axes / edge cases |
|-------|-------------------------|
| **Camera** | camera permission (granted/denied/permanent-deny) · offline+online-only lang · no OCR script for language |
| **Voice** | mic permission · speech-recognizer availability · offline speech? · translation availability (as above) |
| **Dialog** | text-availability × **two sides** (each side's language/connectivity) |
| **Offline downloads (Screen B)** | needs internet to download · Wi-Fi-only setting · low storage space · already downloading |
| **Subscribe / paywall** | internet · Play Billing available · offerings loaded · already subscribed |
| **History / Saved** | empty state · item's language pack removed since saved |
| **TTS (speak result)** | TTS engine + language voice available/installed |

## 6. Plugs into structure
- The resolver reads from the shared "brains": **Connectivity** (data), **FeatureAccess** (entitlement + limits), **Translation module** (engine + language capability + download state).
- One resolver per action, living next to the feature; it **composes** the brains — it does not re-implement their checks.
- Result flows into the screen's UiState → the control's enabled/disabled + inline reason + CTA.

---

## 7. Outcome handling & the NO-DEAD-END rule (universal)

§1–6 = "**can I start?**" (before an action). This = "the action ran — **now what?**". Both are required for EVERY user action.

### Universal action lifecycle
```
Idle ─[availability §1]→ InProgress (visible feedback) ─→ { Success | Empty | Error }
                                                              │
                                                    ALWAYS a next step
```

### 🔒 THE NO-DEAD-END RULE (applies to every screen, every action)
Every **Error / Empty / Blocked** state MUST offer **at least one** of: **Retry · an Alternative · clear Guidance · a way Back.**
The user is **never** stuck with no move. No blank screen, no silent failure, no disabled-with-no-reason, no crash-instead-of-message.

### Translation result — states (the user's example)
| State | What shows | Guidance / next step |
|-------|-----------|----------------------|
| Loading | skeleton in the result area (input stays usable) | — |
| Success | result text + action row | copy · TTS · share · save · reverse |
| Empty / not-found | "No translation found" | try rephrasing · check the language pair · **[Retry]** |
| Detect-failed (Auto, text too short) | "Couldn't detect the language — add a few more words" | input kept, hint shown |
| Error (network / API / timeout) | inline error + plain reason | **[Retry]** · switch to offline (if downloaded) · check connection |
| Online-quota / rate-limited | "Service is busy right now" | retry later · **[Upgrade]** · switch mode |
| Partial (list/OCR) | show the ones that worked, mark the rest | **[Retry failed]** |

### Even Success needs feedback — and result-actions can ALSO fail
| Action on result | Success feedback | Failure handling (no dead-end) |
|------------------|------------------|--------------------------------|
| Copy | toast "Copied" | — |
| TTS (speak) | plays | voice for {lang} not installed → "Voice not installed" → **[Install]** |
| Share | opens sheet | no target app → graceful message |
| Save / star | icon toggles + toast | offline write fails → **[Retry]** |
| Reverse | new result | re-translation re-enters this whole table (may itself error → still guided) |

### Rule for every feature spec
Each feature's spec must have BOTH: an **Availability** section (§1) AND an **Outcomes** section (this) — no action ships without its loading + success + empty + error + guidance states defined. "Happy path only" is not acceptable.
