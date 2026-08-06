# Plan — reconcile design-system colour tokens to the rev5 SSOT (issue #347)

status: accepted
(accepted basis: owner directive, 2026-08-05, verbatim below. Not a new decision
to ratify — his colour-SSOT ruling written down as an order of work. Sub-task of
the accepted rev5-completion objective, `issue-130-rev5-completion.md`.)

Refs: #347. Design authority for the palette: `docs/design/language-screens/`
(rev5 drawings) + `README.md:52-54`. Token home: `core/designsystem/.../Color.kt`
+ `Theme.kt`, documented in `docs/specs/00-foundations/DESIGN_SYSTEM.md §1`.

## The ruling, as stated

> *"rev5 is the colour SSOT. Where the app's design-system colour token differs
> from rev5, correct the TOKEN to match rev5 (light AND dark). This is the
> foundation for the pixel-exact rebuild — once the tokens equal rev5, building
> screens with tokens = building to rev5."*

## Scope — colour reconciliation of the TOKENS only

In: the 48-role M3 palette in `Color.kt`/`Theme.kt` + `DESIGN_SYSTEM §1`, and
adding a named home for rev5 colours that have no token.
Out: restyling any screen (that is per-screen rev5 work). No component is rewired
to a corrected/new token here.

## Method — exhaustive, not eyeballed

The rev5 hexes were extracted by decoding the spec HTML
(`json.loads(html.unescape(<script type="__bundler/template">))`) and counting the
distinct hex per element, then joined against the app tokens parsed straight out
of `Color.kt`. Two independent passes agree (README:52-54 named-token diff, and a
full 84-role app dump annotated with frame-occurrence counts). Scripts:
`scratchpad/decode_frames.py`, `build_diff_table.py`, `wcag.py`.

## Findings

**One existing-token MISMATCH (of 84 role-values):**

- **dark `surfaceContainerHigh`**: app `#282A2C` → rev5 `#2D2F31`. `#2D2F31`
  appears **202×** in the drawings (chips, ONLINE-ONLY fill, icon-button hover,
  meter track); the current token value `#282A2C` appears **0×**. Light
  `surfaceContainerHigh #E9EEF6` matches (484×). Every other README:52-54 named
  role matches the app hex-for-hex.

**Two rev5 colours with NO token** (would otherwise be scattered as raw hex):

- **`#410E0B`** (37×) — failed-row / failure-sheet / "Did not download" TEXT &
  icon tone on an `errorContainer` fill. Always a FOREGROUND colour = the semantics of
  `onErrorContainer`. Dark counterpart `#F9DEDC` already **is** `onErrorContainer` dark;
  the light role was the sole outlier (`#8C1D18`), corrected here (decision 2).
- **`#C4D7F5`** (16×) — muted-primary meter tint: storage-bar **used** segment, "Other
  apps" legend dot (dark `#0842A0`); and the downloading-row progress **track** (dark
  `#2D2F31`). Always a fill, no text. No M3 role equals it in light (`primaryContainer`
  = `#D3E3FD`). The used-segment reading is the one carried by `LocalMeterFillColor`.

## Decisions (revised during implementation — see the note below)

1. **Correct** `DarkSurfaceContainerHigh` `#282A2C` → `#2D2F31` (Color.kt §1.2) and the
   `DESIGN_SYSTEM §1.2` row. (Unchanged from the initial plan.)
2. **Correct the `onErrorContainer` LIGHT role** `#8C1D18` → `#410E0B` (Color.kt §1.1 +
   `DESIGN_SYSTEM §1.1`), rather than adding a parallel `LocalFailureTextColor` token.
   `#410E0B` is always a FOREGROUND on `errorContainer` (JSON `fg`), which is exactly what
   `onErrorContainer` denotes; dark `onErrorContainer` is ALREADY the rev5 failure tone
   `#F9DEDC`; and every consumer (`TranzlateSheet` Loss tone, `PackFailureSheets`,
   `ErrorCard`, History) already reads `onErrorContainer` — so correcting the role renders
   rev5 automatically, no per-screen rewire, restoring the M3 error10-on-error90 baseline
   pairing. **No new failure-text token is created.**
3. **Add ONE extension colour** for the meter tint (the 48-role scheme has no slot),
   following the repo's `LocalFloatingSurface` pattern (`staticCompositionLocalOf`, dark
   resolved from the ACTIVE scheme in `TranzlateTheme`):
   - `LocalMeterFillColor` (`MeterColors.kt`) — light `#C4D7F5` (`LightMeterFill`, the one
     new raw hex, in `Color.kt` §10) / dark `primaryContainer #0842A0`. This is the
     storage-bar **used** segment — the confirmed 20d defect. The downloading-row progress
     **track** shares the light value but takes `surfaceContainerHigh #2D2F31` in dark, so
     it is composed per-screen, NOT from this token (documented in `MeterColors.kt` + §1.4).
4. **Document** in `DESIGN_SYSTEM` (new §1.4) and update the §0 WCAG summary.
5. **Pin** all three to the SSOT with a failing JVM test (`RevFiveColorTokenTest`).

### Note — why this diverges from the initial staged plan (2026-08-05)

The initial plan proposed two extension tokens: `LocalFailureTextColor` (for `#410E0B`)
and `LocalMeterTrackColor` (meter, dark `#2D2F31`). Implementation revised both, on the
evidence and on the direct build brief (which lists "`onErrorContainer` corrected" first,
and calls the meter tone the "used bar fill"). The owner **ruling** — "correct the token
to match rev5" — is unchanged; only the mechanism is refined, so this stays `accepted`.

- **`#410E0B` → role, not token.** The initial plan's own "out-of-scope findings" admitted
  the token approach leaves `TranzlateSheet.sheetIconContentColor(Loss)` rendering the
  wrong `#8C1D18` until a separate rewire. Correcting the `onErrorContainer` role removes
  that gap (the sheets read the role today) and avoids a token whose dark value would only
  DUPLICATE `onErrorContainer` dark `#F9DEDC`. rev5 uses `#8C1D18` **0×** in light — so by
  this plan's own "the app renders a colour the SSOT never uses" argument (made for dark
  `surfaceContainerHigh`), the light `onErrorContainer` value must move.
- **Meter dark `#0842A0`, not `#2D2F31`.** The confirmed 20d defect is the storage **used
  segment** (build used `primaryContainer #D3E3FD`); rev5 draws that segment `#C4D7F5 |
  #0842A0` (digest §B6/§C, and `PackFailureSheets.kt:355` uses `primaryContainer` there
  today). `#2D2F31` is the download-*track* dark — a different element, documented as
  per-screen. (A one-line change if a co-verify lens prefers the track pairing instead.)

## WCAG (formula (L1+0.05)/(L2+0.05), same as §0; recomputed by `scratchpad/wcag.py`)

- **`onErrorContainer` light** `#410E0B` on `errorContainer` `#F9DEDC`: **12.77 AAA** —
  IMPROVES on the pre-fix `#8C1D18` (7.17). Dark unchanged (7.17 AAA). No regression.
- **dark `surfaceContainerHigh`** `#2D2F31` — the two text pairs fall a shade but stay
  **AAA**: `onSurfaceVariant` 8.45→7.89, `onSurface` 11.22→10.47. No AA/AAA threshold
  crossed. (Light `surfaceContainerHigh #E9EEF6` unchanged.)
- **`LocalMeterFillColor`** — carries no text (no on-colour pair). The download `primary`
  fill over the light track scores 4.37 (≥3:1 ✓). The storage **used-vs-track** ratio —
  `#C4D7F5` vs `#E9EEF6` (light) **1.25:1**, `#0842A0` vs `#2D2F31` (dark) **1.47:1** —
  falls below the WCAG 1.4.11 3:1 guideline for graphical objects. **Flagged,
  non-blocking, NOT a regression:** the pre-fix `primaryContainer #D3E3FD` was lower
  (1.11:1); the figure is stated in text and the legend uses labelled solid/dashed dots
  (info is not conveyed by the bar alone); rev5 is authoritative per the ruling.

## Out-of-scope findings (for the orchestrator to file / fold in)

- **RESOLVED by decision 2:** `TranzlateSheet.sheetIconContentColor(Loss)` reads
  `onErrorContainer`, so correcting the role renders the failure-sheet icon/text in rev5
  `#410E0B` (light) with **no rewire**. The initial plan flagged this as separate work; it
  no longer is. (The one enforced Roborazzi golden renders no failure content, so it is
  unaffected — verified.)
- `PackFailureSheets.kt:355` fills the storage-legend "space used" swatch with
  `primaryContainer` (`#D3E3FD` light) — the exact `#C4D7F5` bug. Rewiring it to
  `LocalMeterFillColor.current` is per-screen work in `:feature:language` (the 20b/20d/20f
  conformance PRs own it), NOT this token PR. The token ships ahead of it by design.
- The meter/storage and failed-row picker components that will consume the new token are
  not built/rewired yet; the token ships ahead of them (this ruling).

## File ownership (this PR touches only these)

- `core/designsystem/src/main/kotlin/.../Color.kt`
- `core/designsystem/src/main/kotlin/.../Theme.kt`
- `core/designsystem/src/main/kotlin/.../MeterColors.kt` (new — `LocalMeterFillColor`)
- `core/designsystem/src/test/kotlin/.../RevFiveColorTokenTest.kt` (new — SSOT pin)
- `docs/specs/00-foundations/DESIGN_SYSTEM.md`
- `docs/plan/issue-347-color-token-reconciliation.md` (this file)

(No `FailureTextColor.kt` — decision 2 corrects the `onErrorContainer` role instead. No
`MeterTrackColor.kt` — one `MeterColors.kt` holds the single meter token.)

## Verify

`./gradlew preflight` + `./gradlew build` green, with
`JAVA_HOME=Android Studio JBR`, `ANDROID_HOME=~/Library/Android/sdk`. No screen is
restyled; existing sheet previews keep rendering (they read the corrected role).
