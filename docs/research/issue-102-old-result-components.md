# Research — issue #102: old Tranzlate `ui/screen/result/components` (READ-ONLY study)

Rule 1 discipline: this is a BEHAVIOUR/INTENT extraction. No code was copied;
every item below is judged against Material 3 + Google Translate (north star)
before any of it is allowed into this app. Tranzlate is a SUSPECT reference.

Source studied (read-only): `SourceCard.kt` (132 ln) · `ResultCard.kt`
(210 ln) · `LoadingView.kt` (51 ln) · `ErrorView.kt` (86 ln), commit 46db0a1.

## What the old app does — and the verdict on each

| # | Old behaviour | Verdict | Reason |
|---|---|---|---|
| S1 | **Source card** shows the text that was translated, in a tonal card, indented (15% start spacer) so it reads as "your message" | **ADOPT (intent)** — we already show the source in the read face; keep our full-width card, drop the % spacer | The chat-bubble indent is a percentage hack; M3 asks for margin tokens, and our layout already distinguishes source vs result by tone |
| S2 | **Long-press the source → dropdown with Copy / Edit** | **ADOPT the capability, REJECT the interaction** | Long-press is an invisible affordance (M3: primary actions must be visible; EDGE_CASES no-dead-end). We already have visible Edit (tap card) — ADD a visible **Copy source** action |
| S3 | Source copy uses a manual `pointerInteropFilter` to position the menu at the finger | **REJECT** | Fragile touch-interop for cosmetic menu placement; M3 `DropdownMenu` anchors itself |
| R1 | **Result card**: target-language button (flag emoji + name + ▾) that opens the language picker | **ADOPT (intent), REDESIGN** | Useful — retranslate target from the result. But flag emoji ≠ language (a language is not a country; `getFlagEmoji` from a 2-letter id mislabels e.g. en/ar/es). Use our language pill instead |
| R2 | **Speak (TTS) with play⇄stop state** | **ALREADY SHIPPED** (#85) | — |
| R3 | **Expand/collapse** toggles result typography bodyLarge ⇄ titleLarge | **ADOPT (intent), REDESIGN** | Real need (long text readability), but "expand" that only changes font size is surprising. Our `resultTypeFor` already auto-sizes by length; a manual **Zoom/full-screen result** is the honest version — DEFER to its own issue |
| R4 | **Copy result** | **ALREADY SHIPPED** | — |
| R5 | **Thumb up / thumb down** feedback, `rememberSaveable` only — goes NOWHERE | **REJECT (as built)** | Dead UI: no analytics, no store, no undo semantics; D-3 already queried it. Fake feedback is worse than none. Keep the strings (they exist) but do NOT ship the control until there is a sink |
| L1 | **Loading = 4 shimmer bars** at 100/60/80/40% width | **ALREADY SHIPPED** (`ShimmerResult`) | Our version exists; verify bar rhythm matches |
| E1 | **Error card** with title + message + OK button that returns to Home carrying the text back for editing | **ADOPT the RECOVERY, REJECT the styling** | Recovery intent is right (no dead end). Styling manually swaps error/errorContainer by theme — that is what M3 colour roles already do; our ErrorCard uses the roles correctly |
| E2 | Error card is indented by a trailing 20% spacer | **REJECT** | Same percentage hack as S1 |

## What we ALREADY have that the old app lacks

Reverse translation (C-7), star/save to History, engine-cause-specific error
copy, TalkBack live regions, the AI meter, adaptive panes.

## Gaps worth adopting (the actual deliverable)

1. **Copy the SOURCE text** — visible action on the source card (old app had
   it, hidden behind long-press; we have no source copy at all). Strings
   `cd_text_copy_source` already exist in our catalogue, unused.
2. **Speak the SOURCE text** — same story; `cd_text_speak_source` exists,
   unused, and our TTS seam already supports an arbitrary language tag.
3. **Change target language from the result face** — retranslate without
   going back; our language pills exist but the read face has no picker
   entry.

Everything else is either already shipped, rejected on M3 grounds, or
deferred with a reason.

## Deferred (recorded, not this issue)

Result zoom / full-screen reading mode (R3 done honestly) · thumbs feedback
(R5) until a sink exists (analytics or a quality-report endpoint).
