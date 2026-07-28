---
status: accepted   # owner decided live: requirements A–H in session, then "A ekata yamu" after the A/B device comparison
issue: 46
title: Screen 5a — one composer surface (type · result · re-edit) hosted as its own Nav3 destination
date: 2026-07-28
author: Claude (Opus 5) · evidence = both variants built and measured on emulator-5554 (API 36) + an independent code audit by a second agent
---

# Plan — issue #46

> The text vertical splits typing (Home's editable card) from reading (a separate Result screen). The approved Claude Design export **"Offline Translator M3"** puts typing, the result and re-editing on **one** surface — screen **5a**. This plan ships that surface and decides how it is hosted.

## 1. What the owner asked for (A–H)

| | Requirement |
|---|---|
| **A** | Home keeps only the "Enter text" placeholder + voice button — no counter, nothing editable |
| **B** | Tapping the placeholder *or* the voice button opens 5a; voice input happens there |
| **C** | 5a is where the user types, sees the result and re-edits — it **replaces** the Result screen |
| **D** | Back from 5a → Home **and clears the text**; Home never shows previously typed text |
| **E** | 5a → another screen → back to 5a **preserves** the typed text |
| **F** | The placeholder + voice button look identical on Home and on 5a |
| **G** | The counter appears only while editing, so it lives on 5a |
| **H** | The transition is a continuous in-place morph, not a screen swap |

Content decisions taken later in the same session:
- **No engine badge anywhere.** The MLKit → Google online → Google Cloud Translate waterfall stays invisible; user-facing engine selection is **deferred** (possible future feature). Goal: low API cost, good results.
- **The result face has no mic.** Mic/Translate exist only while editing — no text → mic, text → Translate, result showing → neither.

## 2. The decision this plan records: how 5a is hosted

Two hostings were built in full and compared on device, behind a temporary `COMPOSER_AB` switch:

- **A — own destination.** 5a is a Nav3 destination (`ComposerNavKey`); the input card is a shared element, so the push reads as Home's card growing in place.
- **B — Home state.** 5a is a state of the Home destination (`AnimatedContent` over `TextViewModel.composerEditing`), back intercepted with `BackHandler`; nothing is pushed.

### 2.1 Measured on emulator-5554 (API 36, targetSdk 36 → predictive back on by default)

Identical synthetic input driven at both, so any difference is in the app.

| Probe | A | B |
|---|---|---|
| **Back gesture, then cancelled** (drag out, drag back to the edge, release) | composer + draft kept **4/4** | draft **destroyed 3/3**; one further run **exited to the launcher** |
| **Predictive back preview** (gesture held mid-drag) | Home revealed underneath, scrubs with the drag | none — composer frozen, snaps to Home on release |
| **Mid-morph render** (20× slow motion, raw frame capture) | one crisp placeholder, card solid | "Enter text" drawn **twice** (~28px apart), card washed out |
| Rotation (activity recreated) | text kept | text kept |
| Process death + relaunch | restores into 5a with text | restores into 5a with text |
| Requirements D and E | correct | correct |
| Morph trajectory (card top / placeholder) | monotonic, 0px downward step | monotonic, 0px downward step |

**Root cause of B's back defects** (verified against library source): `androidx.activity.compose.BackHandler` overrides only `onBackCompleted()` — it implements neither `onBackProgressed` nor `onBackCancelled`, which is precisely why AndroidX ships a separate `PredictiveBackHandler`. So B has no channel to preview *or* cancel the gesture. Nav3's `NavDisplay` drives a `SeekableTransitionState` from gesture progress and honours cancellation.

### 2.2 Independent code audit (second agent, ≠ author — Rule 5 lens)

Verdict **A**, confidence medium-high. Beyond the measured defects it found:

- **B adds a second back interceptor.** `NavDisplay` composes both the outgoing and incoming scene during its ~700 ms transition, so a back press landing in that window while `editing == true` can fire Home's `BackHandler` — wiping the draft while the language picker stays open. Mechanism confirmed from source; the millisecond tie-break was not device-reproduced.
- **B nests a second `SharedTransitionLayout`** inside the shell's, making the outer one inert and inviting a silent scope mismatch the next time an element is shared across the Home/composer boundary.
- **B cannot represent "composer open" independent of "Home is on top"**, so a deep link, a share-sheet "translate this", or a Camera → composer entry point is structurally unsupported without extra plumbing. A expresses all three as a back-stack shape.

One audit claim — that B's one-time keyboard choreography goes stale after the first open — **could not be confirmed or refuted**; the device probe's resolution was too coarse and the cited mechanism is doubtful for `AnimatedContent` branches (unlike a popped nav entry, it has no `SaveableStateHolder` retaining the flag). Recorded as unresolved; it does not carry the decision.

### 2.3 Decision

**Variant A.** B's defects are fixable — swap in `PredictiveBackHandler`, drive `AnimatedContent` from gesture progress, and reconcile the two back paths — but every line of that is code we would write, test and maintain, and A gets the same behaviour from the navigation library. Same feature, less custom code, measurably more correct.

Cost accounting at decision time: A-only code ≈ 39 lines across 2 files (a nav key + an entry block + the shared-bounds wiring). B-only code ≈ 54 lines across 4 files, plus a new `activity.compose` dependency and new public `TextViewModel` API (`composerEditing`, `onComposerOpen`) that every future caller would have to know about.

## 3. Build steps

1. **`ComposerScreen.kt`** — the 5a surface: top row (back + the reused language pills, requirement F), then the one card with an edit face and a read face.
   - Both faces: source language label, with `Paste` when the field is empty and `✕` clear once there is text (the design swaps them the same way it swaps mic ⇄ Translate). `✕` is `onClearAll()`, which had lost its UI when the Result screen went.
   - Edit face: text field, counter (requirement G), mic ⇄ Translate.
   - Read face: the source text stays in place and stays tappable to resume editing; the translation lands beneath it in a **tonal result card** (see §3.1). No badge, no mic, no counter, and the keyboard drops — those exist only while editing.
   - `isEditing` is `rememberSaveable` — the shared `uiState` keeps the last result while the user re-edits, so which face shows cannot be derived from `uiState` alone.

### 3.1 The result card (design-measured, corrected 2026-07-28)

The first cut rendered the result as `source → divider → target` inside the same white card. **That was wrong** — the owner pointed back at the export, which is interactive: typing into its frame and pressing Translate reveals the result as a **tonal card nested inside the composer card**. Measured from the export's computed styles:

| | light | dark | token |
|---|---|---|---|
| container | `#D3E3FD` | `#0842A0` | `primaryContainer` — exact in both modes |
| label / icons | `#0842A0` | `#A8C7FA` | `LocalResultCardColors.label` |
| translation | `#041E49` | `#D3E3FD` | `LocalResultCardColors.text` |

The two content tones are not a single M3 role — which of them equals `onPrimaryContainer` flips between light and dark — so they are expressed as `ResultCardColors`, resolved from the active scheme in `TranzlateTheme` exactly like the existing `LocalPrimaryActionColors`. Geometry: radius `shapes.large`, no elevation (separated by tone alone), target language in **CAPITALS**, translation at `titleLarge`, then speak · copy at the left with bookmark pushed to the right edge. Verified on device: **8/8 colour values exact** against the design in both modes.
2. **`HomeScreen.kt`** — replace the editable card with a non-editable `InputPreviewCard` (placeholder + mic, both opening 5a). `LanguageRow`/`LanguagePill` become `internal` so 5a reuses the identical pills (requirement F).
3. **`TextViewModel.kt`** — `onComposerDismissed()` is the single home of requirement D: cancel any in-flight translation, clear the input, reset to `Idle`.
4. **`TranzlateApp.kt`** — push `ComposerNavKey`; the pop guard routes every exit (arrow, button, gesture) through `onComposerDismissed()`. One `SharedTransitionLayout` at the shell wraps `NavDisplay`; `sharedBounds(..., resizeMode = RemeasureToBounds)` on both the Home preview card and the 5a card is the one morph anchor (requirement H).
5. **Retire the Result screen** (requirement C): delete `ResultNavKey` and `ResultScreen.kt` — already unreachable, nothing pushed it.
6. Delete every B-only artifact: the `COMPOSER_AB` switch and `ComposerHosting` enum, Home's `inPlaceComposer` branch, `composerEditing`/`onComposerOpen`/`KEY_EDITING`, and the `activity.compose` dependency.

## 4. Choreography (requirement H)

Two things had to be fixed before the morph read as one continuous motion:

- **`resizeMode = RemeasureToBounds`.** The default `ScaleToBounds` *scales* the card's content, so the placeholder was dragged down as the card grew and sprang back — exactly the "comes down then goes up" the owner reported. Remeasuring re-lays-out each frame instead, pinning the placeholder to the card's top.
- **Keyboard after the morph, not during it.** The IME inset resizes the pane, retargeting the container transform mid-flight. Focus is requested immediately (the cursor is visible while the card grows) but the keyboard waits one motion beat on first entry, so the morph lands on a stable target. Re-entering the edit face from the result face has no morph and so has no delay.

Measured after the fix, sampling a 20×-slowed transition: card top 501 → 325, placeholder 549 → 460, **worst downward step 0px** — monotonic.

## 5. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | 5a is documented as a screen, so it could drift into feeling like a page change | The shared-element morph is the contract; measured monotonic, and the owner compared both hostings on device |
| 2 | Requirement D deletes the user's text — a cancelled back gesture must never trigger it | This is exactly why A won: `NavDisplay` honours `onBackCancelled`, verified 4/4 on device |
| 3 | `onReverse` / `onClearAll` lose their UI when the Result screen goes | Kept and still unit-tested — they are foundations contract behaviour (C-7), not dead weight; commented as such in `TextViewModel` |
| 4 | Instrumentation cannot run (issue #40 — Espresso vs API 36) | Verification is adb-driven: uiautomator state assertions per requirement, plus frame measurement for the morph |

## 6. Acceptance

Driven on emulator-5554 against the final build, each step asserted from a `uiautomator` dump:

| Step | Expected | Result |
|---|---|---|
| launch | Home | ✅ |
| tap "Enter text" | 5a, edit face, keyboard up (**B**) | ✅ |
| type "Good morning" | counter `12 / 500`, mic → Translate (**G**) | ✅ |
| tap the French pill | language picker | ✅ |
| back | 5a, **text preserved** (**E**) | ✅ |
| Translate | tonal result card, **keyboard down**, no counter / mic / Translate (**C**) | ✅ |
| tap the source line | back to the edit face, keyboard up | ✅ |
| tap `✕` | field cleared, still editing | ✅ |
| back | Home (**D**) | ✅ |
| reopen | 5a **empty** (**D**) | ✅ |

Result-card colours sampled from device screenshots against the export: **8/8 exact**, light and dark. Read-face accessibility tree dumped on device — Back · source/target language · Swap · New translation · the tappable source line (`onClickLabel` = "Edit text") · target label · translation · Read aloud · Copy · Add to favourites, and **no** mic, Translate or counter node.

Gates: `spotlessCheck` ✅ · `detekt` ✅ · unit tests (`:feature:text`, `:app` incl. Konsist) ✅ · fake + prod debug compile ✅ · `androidTest` sources compile ✅ (they cannot run — issue #40).

## 6.1 Co-verify findings and what was done

A pre-merge lens by an agent other than the author returned **OPEN:7, no blocker** — requirements A–H correct, the clearing rule sound on every exit path, the shared-element wiring matched on both sides. Closed since:

| Finding | Action |
|---|---|
| `TextTranslationScreenTest` still drove the pre-5a Home (`tt_text_input` etc. on launch) — **major ×2** | Rewritten to open 5a first; a third case now covers requirement D |
| `ResultBlock.kt` (132 lines) orphaned by deleting the Result screen | Deleted — its only reference was its own preview |
| `ic_close.xml` unused | Now used — it is the `✕` clear affordance |
| `docs/specs/01` still described `ResultScreen` "on its own destination" | Rewritten for 5a; live testTags listed, retired ones named |
| 16 Result-screen string keys with no caller | **Left in place.** C-3 makes the catalogue the authority, so removing keys is a catalogue change; several also back deferred (engine selection) or undecided (👍👎) features. Noted for a C-3 reconciliation pass |
| gitignore commit has no `Fixes:` trailer | Left — trivial chore, not fix-class |

## 7. Follow-up (not this issue)

Process death while the read face is showing loses the translation: `uiState` is a plain `MutableStateFlow`, so it restarts at `Idle` while `isEditing` correctly restores `false` — the read face then renders a blank area with no actions, a dead end under the EDGE_CASES rule. Reproduced on device in **both** variants, so it is independent of this decision. `restoreResultIfNeeded()` already exists but must not be called unconditionally — on a fresh open `Idle` is normal and replaying would resurrect the previous translation. Tracked separately.
