# Plan — issue #174: nothing is announced while a translation runs

status: accepted
(accepted basis: issue #174 is owner-filed and states the required outcome; this
plan only settles the two questions the issue left open — Polite vs Assertive,
and what to do about `a11y_limit_reached` — and both are settled against
published guidance plus this repo's own shipped copy, not against preference.)

Fixes: #174 · refs #152, #172

## 1. The harm, restated from the code

`a11y_translating` ships in all three locales and has **zero** Kotlin call sites.
Re-derived on this branch:

```
grep -rn 'R.string.a11y_translating' --include='*.kt' .   →  0
```

So a screen-reader user who taps Translate hears nothing until the outcome
arrives. The other two C-4 outcome keys are wired (`a11y_result_ready` ×2,
`a11y_error` ×2); the in-progress state is the only one that was never connected.

`ShimmerResult.kt:43-45` already *documented* the fix as if it existed —
"announcements come from the feature's own loading live region (`tt_text_loading`,
a11y contract §2.3)". `tt_text_loading` existed in the contract, in one
instrumented assertion that it is **absent**, and nowhere in production. A KDoc
describing a thing that was never built is rule 11's fourth cause.

## 2. Render sites — enumerate before changing

`TextUiState.Translating` is *rendered* in two places, not one:

| Site | File:line (pre-fix) | Shape |
|---|---|---|
| Portrait read face | `ComposerScreen.kt:961` | `ResultCard { ShimmerResult() }` |
| Split result pane | `ComposerScreen.kt:1293` | `ShimmerResult()` |

Both are converted. The issue text says "wire it to the translating state",
singular; converting only the portrait one would be rule 11's first cause
(#146 converted 2 of 6) and is mutation M3 in the register below.

## 3. Polite, not Assertive

**Ruled: `LiveRegionMode.Polite`.**

- The contract already says so (`TEST_A11Y_CONTRACT_text.md:415`) and states the
  principle directly: "Assertive **only** for error/limit (interrupts);
  everything else Polite."
- Android's own semantics guidance: use Polite "in most cases where users'
  attention should only briefly be drawn to alerts or important changing
  content", and Assertive "sparingly to avoid disruptive feedback… for
  situations where it's crucial that users are made aware of time-sensitive
  content" (developer.android.com, Compose accessibility → semantics).
- The decisive argument is about *this* moment. The announcement fires the
  instant the user activates Translate, while TalkBack is still speaking the
  control's own label. Assertive would cut that off — so the user would lose the
  confirmation of **which control they just hit** in order to be told that
  something started. That trade is backwards, and it is the "interrupts the user
  mid-sentence" defect the issue warns about. Polite queues behind the label:
  the user hears "Translate, button" then "Translating…", which is the whole
  sequence they need.
- `a11y_error` stays Assertive (`ComposerScreen.kt:1039`, `ErrorCard.kt:113`) —
  an outcome that ends the task earns an interruption; a progress notice does not.

## 4. Double announcement — what is and is not reachable

Checked against the Compose source rather than assumed
(`ui-android-1.11.4-sources.jar`,
`AndroidComposeViewAccessibilityDelegateCompat.android.kt`):

- **Re-fire on recomposition: not reachable.**
  `sendSemanticsPropertyChangeEvents` (:2564) begins each node with
  `val oldNode = previousSemanticsNodes[id] ?: return@forEachKey` and then emits
  only for properties whose **value changed**. `a11y_translating` is a constant
  with no format argument, so while the node lives its `contentDescription`
  never changes and no further event is produced.
- **Lingering past the state: reachable, and it is the real risk.** A loading
  live region rendered outside the `Translating` branch would sit under a
  finished result and read "Translating…" to anyone swiping through it. The fix
  keeps the region strictly inside the branch, and mutation M2 makes that
  property a test rather than a promise.
- **Fast cache hit (C-8): two queued messages, accepted deliberately.**
  `TextViewModel.startTranslation` (:414) sets `Translating` synchronously, and
  `BUSY_FLOOR_MS = 500` is cancelled on success (:435) — a cache hit is *not*
  held back. So Translating→Result can be near-instant and TalkBack may queue
  "Translating…" then "Translation ready: …". Both are Polite, so neither
  interrupts the other; the cost is roughly one extra second of speech on the
  fastest path. The alternative — announce only after a delay — buys that second
  by risking silence on the slow path, which is the exact harm #174 was opened
  for. We do not trade the reported bug for a quieter version of itself.
  (If the state changes before the next recomposition, the node is never
  composed and nothing is announced at all — also fine: the outcome arrives
  immediately.)

## 5. `a11y_limit_reached` — ruled: amend C-4, do not add the key

C-4 names four canonical keys. Three are real; `a11y_limit_reached` has a
catalogue row (`STRINGS_text-translation.md:184`) and **no resource in any
locale**. The issue requires a decision either way. Ruled: **strike it from C-4**,
for three reasons found in the shipped code, not in preference.

1. **A single fixed string would be false half the time.** `TextUiState.Limit`
   carries `notEntitled`, and the two kinds are different truths — quota spent
   ("come back tomorrow") versus entitlement denied ("this needs Pro"). C-4's
   value, "Daily Advanced-AI limit reached", is simply wrong for the denial
   case: it tells a screen-reader user to wait for a midnight reset that will
   never grant them anything. Announcing a falsehood to the one user who cannot
   see the correct text on screen is worse than the gap it would close.
2. **The state is already announced, correctly, in all three of its surfaces.**
   `tt_text_limit` carries `LiveRegionMode.Assertive` in the portrait face
   (`:1039`) and in the pane (`:1301`), announcing the per-kind body
   (`text_error_limit_reached` / `text_error_not_entitled`); the C-11 sheet
   announces its per-kind title assertively (`:1177`). Adding a fourth,
   kind-blind announcement makes the at-limit moment noisier and less accurate.
3. **The key never agreed with itself.** C-4 says "Daily Advanced-AI limit
   reached"; the contract's own row for the same key
   (`TEST_A11Y_CONTRACT_text.md:419`) says "Daily free limit reached"; the
   shipped sheet title says "Daily free AI limit reached". Three values, one
   key, no resource — the sign of a key that was written down and never
   reconciled with the copy that actually shipped.

C-4 is amended in place with the reason, and the catalogue row is struck through
(C-3: strikethrough = retired, and the `verifyStringKeyDocs` gate runs
resource→catalogue only, so a struck row with no resource is exactly the
recorded-retirement case that gate documents).

**This is not "no announcement at limit".** It is: the limit announcement is the
per-kind copy that is already wired, and C-4 stops naming a key that would have
to contradict it.

## 6. What ships

- `ComposerScreen.kt` — one new private `TranslatingFace()` composable holding
  the shimmer plus the Polite live region and the `tt_text_loading` host tag;
  called from both `Translating` branches. `@PreviewLightDark` per rule 7 (one
  meaningful state).
- `ShimmerResult.kt` — KDoc corrected: it described the live region as existing.
- `TEST_A11Y_CONTRACT_text.md` — §2.3 row 1 made true (host tag now real), the
  `a11y_limit_reached` row replaced by the ruling, and the tag table's
  `tt_text_loading` row updated.
- `DECISIONS.md` C-4 — amended per §5.
- `STRINGS_text-translation.md` — `a11y_limit_reached` row struck with the why;
  `a11y_translating` row gains its call site.
- `KonsistArchitectureTest.kt` — new source-shape gate (see §7).

## 7. The test, and what it honestly does not prove

This repo has **no Compose unit-test runtime** — no Robolectric, no
`createComposeRule` anywhere (verified: 0 files). The instrumented suite in
`app/src/androidTestProd` is compiled by CI but cannot be run (#40, Espresso
`onIdle` on API 35+), so an assertion added there would be un-runnable and could
not be offered as evidence.

So the gate is a **source-shape** Konsist test, in the same form and with the
same stated honesty as the two that already exist for #149/#159: it cannot prove
the announcement *reaches TalkBack* — only a device can. What it makes RED is
every realistic regression, each of which compiles and leaves the rest of the
suite green:

- the `liveRegion` deleted while the string stays (M1 — the shape that produced
  this issue);
- the region rendered outside the `Translating` branch (M2);
- a `Translating` branch that renders no announcing face (M3 — the second
  render site, or a third one added later);
- the composable itself renamed away, emptying the gate (M4, vacuous-pass guard).

Comments and string literals are stripped before matching, reusing the existing
`code()` helper, so a KDoc that says `liveRegion` cannot satisfy the rule.

**Recorded gap, not fixed here:** the absence of any Compose unit-test runtime is
a repo-wide hole that keeps every UI a11y property un-runnable. Adding Robolectric
touches the shared convention plugins and every module's test classpath — that is
its own issue and its own owner decision, not a rider on an a11y fix.

## 8. Mutation register (decided before the test — rule 11)

M1–M4 were written down before the gate was written; M5 was added afterwards for
the one reason a late mutation is legitimate — proving an assertion is not dead
code — and it is marked as such. Each was applied to the committed fix, run
under the full `./gradlew test`, and reverted. Baseline before mutating: green.

| # | Mutation | Expected | Observed — failing assertion |
|---|---|---|---|
| M1 | delete `liveRegion = LiveRegionMode.Polite`, keep the string | 1 red | 1 red, `:441` `contains("liveRegion")`. The preceding `contains("R.string.a11y_translating")` **passed** — the string survives, the announcement does not. That is the shape that produced this issue. |
| M2 | ALSO render the face in the `Result` branch (region outlives its state) | 1 red | 1 red, `:484` `callsOutsideTranslating` → `[ComposerReadBody]` |
| M3 | revert `ResultPane`'s branch to bare `ShimmerResult()` | 1 red | 1 red, `:455` `bareShimmer` → `[ResultPane]` |
| M4 | rename `TranslatingFace` away (vacuous-pass guard) | 1 red | 1 red, `:434` `expected to contain: TranslatingFace` |
| M5 *(post-hoc, reachability only)* | add a THIRD announcing render site | 1 red | 1 red, `:469` `map.size()` expected 2, was 3 |

M2 needed a second attempt worth recording: the first version *also* reverted the
`Translating` branch to a bare shimmer, which tripped M3's rule first and left the
double-announcement rule unproven — a mutation that goes red for the wrong reason
is not evidence. Re-run surgically (branch left intact, face additionally rendered
under `Result`) so only the intended rule could fire.

## Line citations, corrected 2026-08-02 (#187 co-verify)

The three `LiveRegionMode.Assertive` markers this document cites were written
from `origin/main` and **not re-checked after this PR's own edit moved them**.
`TranslatingFace()` and its KDoc add 34 lines above two of the three, so:

| Surface | Cited | Actual |
|---|---|---|
| portrait `tt_text_limit` | `:1039` | `:1039` — above the insertion, unmoved |
| pane `tt_text_limit` | `:1267` | **`:1301`** |
| C-11 sheet title | `:1143` | **`:1177`** |

Both wrong by exactly 34. The claim they support — all three surfaces already
announce the limit assertively, per kind — is unchanged and was re-verified.

This is rule 11's fourth cause committed against this document by the change it
documents: a citation is a claim about the code, and a diff that moves code
invalidates it.
