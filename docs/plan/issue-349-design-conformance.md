# Plan — zero-touch design-conformance infrastructure (issue #349)

status: accepted
(accepted basis: owner directive 2026-08-05 "meka zero-touch human architecture ekak
wenna oni" + 2026-08-07 "design specs SSOT eke thiyenawa — eke mention karala nathi ewa
witharak measure karanam hari". Not a new decision to ratify — his gate written down as an
order of work. Sub-task of the accepted rev5-completion objective, `issue-130-rev5-completion.md`.)

Refs: #349 · #256 · #130. Design authority: `docs/design/language-screens/` (rev5 SSOT).
Red-team + design record: `docs/research/issue-349-design-conformance.md`.
Component 1 delivered in **PR #TBD**; Component 2 is a follow-up PR.

## The ruling, as stated

> Build ONE screen to the SSOT, show the owner; approve → build the rest; not approved →
> the manual build+review method failed, build the infrastructure instead. 20d (PR #338)
> was rejected: review "measured ~1-4px" and a lens approved it, yet font/colour/edge
> divergences remained. So the MACHINE must catch divergences objectively — the owner
> cannot, and re-reviewing a screen 10× wastes his money. And (2026-08-07): the SSOT
> **states** the design values; build to them; measure only the unstated; stated wins.

## Why measuring-everything is wrong (the reshaping evidence)

`reconcile.mjs` compares the measured `specs/*.json` against the stated `stated-spec.json`
(README §Tokens). It flags divergence in **all 26 frames** — 165 font, 115 colour, 78
row-height. The mockup draws imprecise values: app-bar 24sp where the SSOT states 22sp;
14.5sp where it states 13.5; `#ffffff` where the surface token is `#f8fafd`. Frames measure
at clean 1:1 device dp (412×892, 1280×800, 800×1280, 760×812, 892×412), so these are genuine
drawing imprecisions, not scale artifacts. A pure-measurement golden enforces the drawing's
imprecision over the SSOT's intent, and would have "fixed" the correct 20d app-bar (22sp)
to a wrong 24sp. Hence: **golden = STATED (authoritative) ⊕ MEASURED (unstated only).**

## Scope

**Component 1 (this PR)** — the spec SOURCES, committed durably (red-team §0: they exist
only in a working tree today; `git clean` deletes them):
- `stated-spec.json` — the authoritative STATED catalogue transcribed from README §Tokens
  (palette light+dark, metrics, type scale) + a `measuredUnstated` tier (onErrorContainer
  `#410e0b`, meter fill — landed #348).
- `extract-specs.mjs` — Playwright extractor → `specs/*.json` (26 frames), the MEASURED layer
  for unstated positions + conflict detection. Frame = largest element with non-transparent
  bg + border-radius + overflow:hidden (the light device screen); descendants only.
- `reconcile.mjs` — reports measured-vs-stated divergence; stated wins.
- `specs/*.json` (26) — the measured layer.
- These docs.

Out: the Kotlin conformance gate + any screen restyle (Component 2).

**Component 2 (follow-up PR)** — the automated gate + machine-verified 20d. Detailed below;
built after Component 1 lands so the gate reads a committed `stated-spec.json`/`specs/`.

## Component 2 design (for the follow-up PR)

JVM `@Test`s in the `check`/`build` graph (server-enforced tier — NOT a client hook, NOT a
PR-body manifest; a text marker is a rule-12 falsehood-vector). Per frame, starting 20d:
- **Colour** — each landmark's rendered colour (`captureToImage`, sampled off the AA edge)
  == a stated token, within ≤6 RGB-distance. Off-spec colour → FAIL. Unstated colours
  (camera-green, meter fill) on an explicit, justified allowlist.
- **Type** — each text landmark's `fontSize`/`fontWeight` (`GetTextLayoutResult`): stated-role
  landmarks (app-bar/row/body/overline) == the stated size exactly; unstated roles
  (headline/display) allowlisted to their measured/M3 value.
- **Metric** — row height ∈ {48, 56–60}dp; icon/sheet 48dp; radius == half the row height;
  gaps/padding multiples of 8dp (`getBoundsInRoot`, ±2px, relative to prior sibling below any
  variable-height/owner-approved-copy region — absolute only above it).
- **Completeness** — group counts (`onAllNodesWithTag(...).assertCountEquals(n)`, n read off
  the stated structure) so a missing element (the original 20d defect class) fails.
- Assertions run **before** `captureRoboImage()` so a non-conforming screen can't record a
  fresh golden.
- **Acceptance (red-before-green):** the gate, run at the rejected 20d `4c088b0`, must go RED
  on its real divergences (app-bar weight 500 vs 400; Remove-card position; pre-#348 colour);
  then 20d is fixed to GREEN. A gate that can't flag the already-rejected screen is not built.
- ~15–20 curated `testTag`s per frame, hand-mapped to their golden role in the test
  (rule-1-safe — copy is rewritten, so text-matching is unsafe).

## Conditions carried from the red-team (ADOPT-WITH-CONDITIONS)

1. Component 1 committed as its own reviewed PR (this one).
2. Gate = hard JVM assertions, not a manifest/lens marker.
3. Curated hand-mapped testTags; no exhaustive auto-mapping.
4. Relative-position below variable-height regions; absolute only above.
5. Assertions before `captureRoboImage`.
6. co-verify-lens flags any PR touching `feature/language/**` that ALSO edits
   `language-screens-spec.html` or `specs/*.json` (cheapest bypass: edit the SSOT to match a
   wrong build — the spec HTML has zero branch protection).

## File ownership (Component 1 touches only these)

- `docs/design/language-screens/tools/design-conformance/stated-spec.json` (new)
- `docs/design/language-screens/tools/design-conformance/extract-specs.mjs`
- `docs/design/language-screens/tools/design-conformance/reconcile.mjs` (new)
- `docs/design/language-screens/tools/design-conformance/package.json` + `package-lock.json` + `.gitignore`
- `docs/design/language-screens/specs/*.json` (26, new)
- `docs/plan/issue-349-design-conformance.md` (this file)
- `docs/research/issue-349-design-conformance.md` (new)

## Verify

Component 1 is tooling + data + docs — no app code, no gate yet. Sanity: `node reconcile.mjs`
runs and reports the 26-frame divergence; `node extract-specs.mjs` reproduces `specs/*.json`
byte-for-byte (needs `npm i` + `npx playwright install chromium`, node_modules gitignored).
No `./gradlew` surface is touched, so `preflight` is unaffected. Component 2's own PR carries
the build/test gate.
