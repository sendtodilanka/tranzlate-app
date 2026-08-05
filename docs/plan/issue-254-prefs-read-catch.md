# Plan — #254: widen the Preferences read-side catch (decision + fix)

status: accepted
(accepted basis: rev5 completion plan wave 1b (accepted); the decision below. Refs: #254.)

## The decision (option 1 — widen), and why
`TranzlatePreferencesDataSource.preferences` (`:43-45`) catches **`IOException` only**; anything
else (`else throw cause`) escapes into the **eager** `stateIn(viewModelScope, Eagerly)` consumers
(`TextViewModel:142,145` — source/target lang on the main screen) with **no handler**, crashing
the Text screen on load. This is the **identical shape** #236 fixed at seven sites and #249 at two
more, on the general premise *"nothing should escape into a scope with no handler."* Every read in
the file derives from `preferences` (`.map{}` at :76/78/81/84/87/125/161/165/173…), so the crash
reaches all of them — and **the single source fix protects all of them.**

**Option 1 chosen:** widen the `preferences` catch to degrade **any non-`CancellationException`
Throwable** to `emptyPreferences()`, rethrowing `CancellationException` — matching #236's exact
idiom (find how the seven sites do it and copy the shape, don't reinvent).

**The write side STAYS narrow, deliberately.** `editSafely` catches `IOException` only and a test
asserts a non-IO write **propagates** — that is correct: a failed *save* must stay loud. The
asymmetry is principled, not an oversight: a failed *read* into an eager scope should degrade; a
failed *write* should surface. Record that reasoning in the code comment so the asymmetry reads as
a decision, the way the write side already does.

## Enumerate (rule 11)
All reads derive from `preferences` (confirmed: 9+ `.map{}` derivations). So fixing the source
catch is complete — but the agent must confirm no consumer bypasses `preferences` and reads
`dataStore.data` directly, and name every `stateIn(...Eagerly)` fed by these flows in the PR body.

## Test + mutate-first (rule 11)
A test that a **non-`IOException`** thrown from the upstream `dataStore.data` **degrades to the
defaults** (does not crash the collector). Mutate-first: revert the catch to `IOException`-only →
that test goes **RED** (the non-IO cause escapes). Keep the existing write-side test green.

## Verify
`./gradlew :core:datastore:test :feature:text:test` green; the mutate-first RED→GREEN recorded;
`preflight` at land.
