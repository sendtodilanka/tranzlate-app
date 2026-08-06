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
  icon tone on an `errorContainer` fill. Always a foreground colour. Darker than
  `onErrorContainer #8C1D18` on purpose. Dark counterpart `#F9DEDC`
  (= `onErrorContainer` dark).
- **`#C4D7F5`** (16×) — muted-primary meter tint: downloading progress track,
  storage-bar track & "used" segment, "Other apps" legend dot. Always a fill, no
  text. No M3 role equals it in light (`primaryContainer` = `#D3E3FD`).

## Decisions

1. **Correct** `DarkSurfaceContainerHigh` `#282A2C` → `#2D2F31` (Color.kt) and the
   `DESIGN_SYSTEM §1.2` row.
2. **Add two theme-scoped extension colours** — the fixed 48-role `ColorScheme`
   has no slot for a new tone, so follow the repo's existing extension-colour
   pattern (`LocalFloatingSurface` / `LocalPrimaryActionColors` /
   `LocalResultCardColors`): `staticCompositionLocalOf` provided by
   `TranzlateTheme`, dark value resolved from the ACTIVE scheme.
   - `LocalFailureTextColor` — light `#410E0B` / dark `onErrorContainer #F9DEDC`.
   - `LocalMeterTrackColor` — light `#C4D7F5` / dark `surfaceContainerHigh #2D2F31`.
   - The two new raw hexes live in `Color.kt` (§10: it is the only home for raw hex).
3. **Document** both in `DESIGN_SYSTEM` (new §1.4) and update the §0 WCAG summary.

## WCAG (formula (L1+0.05)/(L2+0.05), same as §0; OLD values reproduce §0 exactly)

- dark `surfaceContainerHigh` correction — text pairs stay **AAA**:
  `onSurfaceVariant` 8.45→7.89, `onSurface` 11.22→10.47; outline border 4.53→4.22
  (≥3:1). No AA/AAA threshold crossed.
- `LocalFailureTextColor` — light `#410E0B`/`errorContainer` **12.77 AAA** (higher
  than the `#8C1D18` the sheet uses today, 7.17); dark 7.17 AAA.
- `LocalMeterTrackColor` — carries no text. Progress `primary` fill over the track
  4.37 (≥3:1 ✓). Light storage used-vs-track **1.25** and legend dot-vs-page
  **1.40** fall below the 3:1 non-text guideline — flagged, non-blocking: the
  figure is redundant with the numeric label and legend text, and rev5 is
  authoritative per the ruling.

## Out-of-scope findings (for the orchestrator to file / fold in)

- `TranzlateSheet.sheetIconContentColor(Loss)` resolves the failure-sheet badge
  icon to `onErrorContainer #8C1D18`, but rev5 draws those icons in `#410E0B`
  (19d/19f/19g). A per-component rewire to `LocalFailureTextColor` — separate work.
- The failed picker row and the meter/storage components that consume these two
  tokens are not built yet; the tokens ship ahead of them by design (this ruling).

## File ownership (this PR touches only these)

- `core/designsystem/src/main/kotlin/.../Color.kt`
- `core/designsystem/src/main/kotlin/.../Theme.kt`
- `core/designsystem/src/main/kotlin/.../FailureTextColor.kt` (new)
- `core/designsystem/src/main/kotlin/.../MeterTrackColor.kt` (new)
- `docs/specs/00-foundations/DESIGN_SYSTEM.md`
- `docs/plan/issue-347-color-token-reconciliation.md` (this file)

## Verify

`./gradlew preflight` + `./gradlew build` green, with
`JAVA_HOME=Android Studio JBR`, `ANDROID_HOME=~/Library/Android/sdk`. No screen is
restyled; existing sheet previews keep rendering (they read the corrected role).
