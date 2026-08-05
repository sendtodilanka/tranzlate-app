---
issue: 158
title: navigateTo double-push — two different Home cards tapped in one frame both push
status: accepted
basis: issue #158 + rev5 completion plan (docs/plan/issue-130-rev5-completion.md) wave 1d
severity: P1 / S2
owner-agent: fix/issue-158-nav-double-push
supersedes: none
refs: [#150, #156]
---

# #158 — the push side of the same-frame race #150/#156 closed on the pop side

## The claim under test (do NOT assume it)

`TranzlateApp.navigateTo` guards only against stacking a key on top of an equal key:

```kotlin
fun navigateTo(key: NavKey) {
    if (backStack.lastOrNull() != key) backStack.add(key)
}
```

Two **different** Home cards tapped in the same frame — e.g. Camera and Offline
languages — are two different keys, so the equal-key guard never fires. If both
`onClick` lambdas run before recomposition, the first `add` is already applied when
the second reads `backStack.lastOrNull()`, and because the second key differs from
the new top, it too pushes: `[Text, Camera, Languages]`. The user tapped one card and
lands on two screens, the wanted one underneath.

`#150`/`#156` fixed exactly this shape on the **pop** side (`popEntry`): a stale
second event acting on a stack the first already changed. The pop's answer is a
composition-identity check — the caller names the destination it is leaving *from*,
and the pop is declined once that destination is no longer the top. `#156` left the
push door open deliberately, rather than widening a crash fix into a nav refactor,
and its co-verify lens filed this.

The issue is explicit: **measure reachability first.** The pop-side race was assumed
unreachable too, until a lens traced it (rule 12 — reasoning from a similar case
without checking the difference). So this plan measures before it fixes.

## Measurement method

Reachability decomposes into two independent facts:

- **F1 (input dispatch, device-dependent):** can two *different* `clickable` nodes
  each have their `onClick` invoked within a single frame, before recomposition
  removes the second card? This is the fact `#158` says must be measured, not assumed.
- **F2 (guard math, pure logic):** given two same-frame invocations with different
  keys, does the current guard add both? This is decidable on the JVM — the second
  read sees the first `add` already applied (SnapshotStateList mutates synchronously;
  recomposition is only *scheduled*). `BackStackPopTest`'s own KDoc states this model.

F2 is proven by the JVM regression below. **F1 is measured on a real device**, because
a Robolectric result could be dismissed as simulated input and the issue's whole point
is empirical reachability:

- **Instrumented androidTest** (`app/src/androidTest`), `createComposeRule()`, a
  faithful two-card harness: two real `Modifier.clickable` cards over a real
  `NavBackStack<NavKey>`, wired to the exact production guard.
- A **genuine two-finger simultaneous tap** injected as one multi-pointer gesture
  (`down p0` on card A, `down p1` on card B, `up p0`, `up p1`) via `performTouchInput`
  on the root — not two sequential `performClick`s — so the reachability is real input,
  not a paused-clock artifact. A paused-clock variant corroborates the worst case.
- **Device:** `Tranzlate_API29` (one of the 4 existing AVDs). The androidTest suite
  cannot run on the booted `emulator-5554` (API 37): Espresso's `onIdle` calls the
  removed `InputManager.getInstance` on API 35+ (#40); `NavShellSmokeTest`'s own KDoc
  says run these on an API ≤ 34 image. CI never runs androidTest (no emulator, #40),
  so this is local evidence — hence the JVM regression carries the gate.

The measurement runs the **unguarded** harness first (expect 2 pushes → F1 proven
reachable) and the **guarded** harness second (expect 1 push → the fix holds under the
same real input).

## Decision rule

- **Reachable** (harness shows 2 pushes) → add one guarded push helper `pushEntry`
  next to `popEntry`, mirroring the pop's composition-identity answer. One invariant,
  both directions, living together.
- **Provably unreachable** (harness shows 1 push even unguarded, and a source trace
  explains why) → do NOT add speculative code; record the proof here and close the
  loop. A cheap assertion documenting the invariant is acceptable only if it does not
  pretend to fix a non-bug.

## The fix (if reachable)

A pure `pushEntry(backStack, from, key)` beside `popEntry`, symmetric to it:

```kotlin
internal fun pushEntry(backStack: MutableList<NavKey>, from: NavKey?, key: NavKey): NavKey? {
    if (from != null && backStack.lastOrNull() != from) return null // stale caller: its screen is no longer top
    if (backStack.lastOrNull() == key) return null                  // never stack a key on itself (popEntry relies on this)
    backStack.add(key)
    return key
}
```

`from` is the screen that rendered the card, captured at **composition** — the shell
reads `val composedTop = backStack.lastOrNull()` in its body (a snapshot read) and
`navigateTo` closes over it. Two same-frame Home-card taps both hold the *same* stale
`navigateTo` (no recomposition between them), so both carry `from = TextNavKey`; the
first push succeeds, the second is declined because the top is no longer `TextNavKey`.
This keeps `navigateTo` the single push chokepoint — every push (screen callbacks and
the shell's picker/manage-packs pushes) flows through it — mirroring `popEntry` as the
single pop chokepoint.

Preserving `if (top == key) return` is load-bearing: `popEntry`'s identity check is
sound only because "no two adjacent entries are ever equal" (its KDoc), which the push
self-dedup guarantees.

## Enumeration (rule 11)

- `backStack.add` (the push): **1** site — `navigateTo`, `TranzlateApp.kt:89`.
- `::navigateTo` references: **3** — lines 96 (`onNavigate` into `AppNavDisplay`), 102
  (`openLanguagePicker`), 122 (`manageLanguagePacks`).
- `onNavigate(...)` push affordances: **10** — 6 on Home (`TranzlateApp.kt:233,235,236,237,238,239`),
  Composer 255, Picker 282, History 311, Settings 333.

All 10 affordances and both shell pushes route through the one `navigateTo`; guarding
it guards every push. Call sites changed: the 1 `backStack.add` chokepoint (moved into
`pushEntry`) + the `navigateTo` body + `composedTop` capture in the shell body.

## Tests

- **Instrumented (measurement, local-only):** `NavDoublePushReachabilityTest` — the
  two-finger harness above. Reachability evidence, pasted into the PR and this doc.
- **JVM regression (gate, mutation-first):** `pushEntry` cases in the nav test source
  set, mirroring `BackStackPopTest`.
  - **Mutation decided first:** delete the `from` identity check (revert `pushEntry` to
    the current equal-key-only guard).
  - Two same-frame pushes from a stale caller `[Text] → push(from=Text, Camera) →
    push(from=Text, Languages)` must leave `[Text, Camera]` (one push). RED under the
    mutation (`[Text, Camera, Languages]`), GREEN with the guard.
  - Plus: a legitimate single push still works; a screen that is still top can push;
    the self-dedup still refuses a key equal to the top.

## Verdict — REACHABLE (measured, not assumed)

Measured on `emulator-5556` (Tranzlate_API29, API 29 — the API ≤ 34 image #40 requires).
Method used: **instrumented androidTest, genuine two-finger simultaneous multi-pointer
gesture** on two `Surface(onClick=…)` cards (not a paused-clock artifact, not
Robolectric). A click counter is the control against a false negative.

### F1 — reachability (device), unguarded harness = the shipped guard verbatim

`NavDoublePushReachabilityTest.equalKeyOnlyGuard_admitsTwoDifferentCardsInOneFrame` —
PASSED, so both its assertions held: `clicks == 2` (the gesture reached BOTH cards) and
the stack became `[Text, Camera, Languages]` (both pushed). One gesture, two pushes.

```
Starting 2 tests on Tranzlate_API29(AVD) - 10
Finished 2 tests on Tranzlate_API29(AVD) - 10
<testsuite name="…NavDoublePushReachabilityTest" tests="2" failures="0" errors="0" …>
  <testcase name="guardedPushEntry_declinesTheSecondSameFrameCard" … />
  <testcase name="equalKeyOnlyGuard_admitsTwoDifferentCardsInOneFrame" … />
```

The pop-side race was assumed unreachable until traced; this one is now shown reachable
by a real MotionEvent stream on a real Android runtime.

### The fix, under the SAME device input

`guardedPushEntry_declinesTheSecondSameFrameCard` — PASSED: `clicks == 2` (both cards
still fire) but the stack is `[Text, Camera]` — one push. The guard declines the stale
second push; it does not stop the gesture reaching the card.

### F2 — guard math (JVM gate, mutation-first)

`BackStackPushTest` — 6 tests, GREEN with the guard. Mutation decided first (delete the
`from` identity check); under it:

```
BackStackPushTest > two different cards tapped in one frame push only the first FAILED
    expected: null but was : LanguagesNavKey                     (BackStackPushTest.kt:45)
BackStackPushTest > a stale caller cannot push onto a screen it no longer sits on FAILED
    expected: null but was : LanguagePickerNavKey(forSource=true) (BackStackPushTest.kt:61)
  tests="6" … failures="2"
```

RED under the mutation, GREEN restored (backup `cp`, not `git checkout`; restore verified
byte-identical). `BackStackPopTest` stays 10/10 GREEN — the shared "no two adjacent
entries equal" invariant that pop relies on is preserved by the push self-dedup.

### Decision

Reachable → the guarded `pushEntry` fix landed. `./gradlew preflight` GREEN.
Installed: `tranzlateProdDebug` + its androidTest APK on `emulator-5556` (API 29), via
`connectedTranzlateProdDebugAndroidTest` (`ANDROID_SERIAL=emulator-5556`, class-filtered).
