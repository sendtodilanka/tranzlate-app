# Plan — issue #201: `detekt` and `spotless` discover files by four hardcoded ring globs

status: accepted
(accepted basis: #201 is owner-filed, states the harm, names the fix — "derive from
`subprojects`, as #173 did" — names the alternative that must be rejected, and names the
mutation that must prove it. This plan settles only what the issue left open: what is
scanned *inside* a module, what happens to the accidental worktree exclusion, which
residuals survive, and how the analysed file set is known not to have moved.)

Refs: #173 · #163, #193, #152 — and `Fixes: #201`, see §7.

## 1. Scope

The three gates in the root `build.gradle.kts` that #202 left behind deliberately:
`detekt`'s `source`, `spotless.kotlin`'s `target`, `spotless.kotlinGradle`'s `target`.
Nothing else. No `feature/**` file is touched — PR-17 is live in those.

## 2. The harm, reproduced on this branch before any code was written

Baseline, untouched tree: `./gradlew detekt spotlessCheck --rerun-tasks` → **BUILD
SUCCESSFUL**, and the resolved scope is `detekt` 237 files, `spotlessKotlin` 237 files,
`spotlessKotlinGradle` 29 files.

Then `bench/perf` — a module at a new top-level ring, `include(":bench:perf")` added —
carrying `Probe.kt` with a deliberate `TODO:` comment, a deliberate magic number, `val x=1`,
a stray indent and no final newline, plus a `build.gradle.kts` with `val probeTags   =
listOf( "a","b" )`:

| Probe on the **unfixed** config | Result |
|---|---|
| `bench/perf/src/main/kotlin/bench/perf/Probe.kt`, detekt violation + ktlint damage | **BUILD SUCCESSFUL**, scope unchanged at 237 / 237 / 29 — **0** `bench/` files in any gate |
| `bench/perf/build.gradle.kts`, ktlint damage | **BUILD SUCCESSFUL** |

CI's gate step is that same command (`.github/workflows/ci.yml:105`), so green there meant
"everything in four named rings is clean", and nothing said so.

**#201's own text overstates the hole for THREE of the four rings, and the first version of
this paragraph then overstated the correction.** The issue says a module at `bench/perf`
**or** `core/data/local` would pass "having been looked at by neither". For `core`,
`feature` and `lib` only the first is true: those globs carry an extra `**`
(`core/**/src/**/*.kt`), so they were already depth-tolerant. A damaged
`core/data/local/src/main/kotlin/probe/NestedProbe.kt` on the **unfixed** config failed
`detekt` with three findings and `spotlessKotlinCheck` by name.

**`app` is the exception, and this paragraph claimed otherwise until the #211 co-verify lens
disproved it.** `app`'s old glob was `app/src/**/*.kt` — **one** `**`, anchored at `app/src`
— so a Kotlin file under `app/` but outside `app/src/` escaped exactly as a brand-new
top-level ring did. The lens proved it by planting `app/coverifynested/.../NestedAppProbe.kt`
with the same three violations on the **unfixed** config: **zero findings**, while its
`core/data/local` twin in the same run was caught. The shipped derivation is depth-agnostic
per module and closes both, so no code changed — but the reassuring half of the claim was
wrong, which is the direction rule 12 exists to catch. **The count that matters: the issue was
right about one ring in four, not zero.** So this hole was strictly
narrower than #173's, where both escapes were real. It is still a hole: a new top-level ring
is exactly what `bench/`, `tools/` or a white-label ring would be.

## 3. What the scan is derived from now

`subprojects` — the registered module list, the same source #202 gave `verifyStringKeyDocs`.
A module exists because `settings.gradle.kts` says so; that file is committed and CI checks it
out, so the scope cannot differ between a laptop and CI. Every project instance exists before
any build script is evaluated, so the list is complete (the intermediate `:core`, `:feature`,
`:lib` included) and nothing has to be *evaluated* to read it.

Inside a module: `**/src/**/*.kt` for Kotlin, `**/*.gradle.kts` for build scripts, both minus
`**/build/**`. Depth-agnostic, so it does not re-introduce a smaller version of the same
assumption; a nested module is reached twice, once through its own entry and once through its
parent's tree, and a `FileCollection` resolves to a `Set`, so nothing is analysed twice.

Two roots are not modules and are added explicitly:

- `gradle.includedBuilds` for `build-logic`'s **scripts** — an included build is not a
  subproject, so no module tree reaches it. Derived from the build model rather than named as
  a path, so `includeBuild` in `settings.gradle.kts` is the only place that decides it too.
  (Verified it resolves: `includedBuilds.size=1`, `dir=<root>/build-logic`.)
- The root project's own `*.gradle.kts`, at **depth 1 only** — see §4.

## 4. What happens to the accidental worktree exclusion

The old globs kept `.claude/worktrees/` out of the gates **by accident of the ring names**: a
path beginning `.claude/` matches none of `app/`, `core/`, `feature/`, `lib/`. That accident
was load-bearing, and losing it is the main risk in this change, so it is now structural
instead: every tree is anchored at a **module directory**, and the worktrees live under the
**root** directory, which is deliberately not in `subprojects`. The only root-anchored tree
left is `*.gradle.kts` — depth 1, which cannot reach `.claude/worktrees/…` at any depth.

Proved, not assumed (M4): a `Probe.kt` with the same violations and a damaged
`build.gradle.kts` planted at `.claude/worktrees/fake-sibling/` leave the gate **green** and
the scope at 237 / 237 / 29 with **zero** matching files.

## 5. Alternatives refused, with the reason

**A whole-tree `**` walk from the repo root.** #173 measured it: **48** `strings.xml` under
`.claude/worktrees/` against the **24** the repo owns. Applied to detekt or spotless it would
analyse another branch's uncommitted Kotlin, fail `main`'s build on it, and differ
laptop-vs-CI by construction — the #163 shape, deliberately built in. `subprojects` rather
than `allprojects` is the same decision: the root project's directory IS the repo root.

**Asking AGP/KGP for each module's real source dirs.** More precise in theory; it makes the
gate depend on the variant API, on cross-project configuration and on evaluation order. #163
is a Konsist gate that was green locally and red in CI on one commit with the cause never
diagnosed. These gates read plain checkout files, which is why local green is evidence. Not
traded away for a layout this repo does not have.

**Keeping the globs and adding a fifth for `bench/`.** That is the same guess with one more
entry, and the next ring escapes in the same silence.

## 6. Residuals, stated rather than hidden

- **`build-logic`'s Kotlin — 15 files — is analysed by neither gate**, and was not before this
  change either. It is an included build, so it is in no module's tree; only its build
  *scripts* are covered. Measured here: 252 `.kt` in the repo outside `build/` and the
  worktrees, 237 in the gate, and the 15-file difference is exactly
  `build-logic/convention/src/main/kotlin/**`. Closing that means turning the gates on code
  that has never been linted, which is its own change with its own findings, not a rider on a
  discovery fix. **This is a separate issue, not fixed here.**
- A `.kt` inside a module but outside every `src/` directory. The patterns are depth-agnostic,
  not shape-agnostic; Kotlin outside a source set is compiled by nothing.
- A `.gradle.kts` at the repo root nested deeper than depth 1 and belonging to no module and no
  included build. By construction the root's own scripts are `build.gradle.kts` and
  `settings.gradle.kts`, and deeper is where the worktrees are — the exclusion is the point.
- The adjacent case #202's lens named — a module whose `include()` line is deleted while its
  files stay on disk — is unchanged here and is not defended by this scan either.

## 7. `Fixes: #201`

Unlike #173, #201 is a single ask covering exactly these three gates, and all three are
derived here. Nothing in the issue is left undone, so the trailer closes it.

## 8. Mutation register (decided before the code — rule 11)

Written to `scratchpad/i201-mutations.md` before `build.gradle.kts` was touched. Every run is
`./gradlew detekt spotlessCheck --rerun-tasks`; probes are planted as fresh files and removed
with `rm` / `cp` backups (never `git checkout --`), `git status` clean after each.

| # | Mutation | Expected | Observed |
|---|---|---|---|
| M1 | new top-level ring `bench/perf` + `include(":bench:perf")`, `Probe.kt` with a detekt violation and ktlint damage | before: **green** · after: **red, by name** | before: `BUILD SUCCESSFUL`, 0 `bench/` files in scope · after: `detekt FAILED` — `ForbiddenComment`, `MagicNumber`, `NewLineAtEndOfFile` at `bench/perf/src/main/kotlin/bench/perf/Probe.kt`; `spotlessKotlinCheck FAILED` on the same file |
| M2 | the same module with clean, formatted Kotlin | **green** | `BUILD SUCCESSFUL`; scope 238 / 238 / 30 — the module is scanned, not skipped |
| M3 | `bench/perf/build.gradle.kts` with ktlint damage | before: **green** · after: **red, by name** | before: `BUILD SUCCESSFUL` · after: `spotlessKotlinGradleCheck FAILED` — `bench/perf/build.gradle.kts` |
| M4 | the same violations at `.claude/worktrees/fake-sibling/` (a nested checkout) | **green, unchanged** | `BUILD SUCCESSFUL`; scope 237 / 237 / 29, `worktrees` hits: **0** |
| M5 | the untouched tree | green, and the analysed file set identical | `BUILD SUCCESSFUL`; the resolved file list of all three tasks `diff`s **clean** before vs after — same `shasum`, `70ab5db…` |
| — | (post-hoc, §2) `core/data/local` nested probe on the **unfixed** config | tests #201's claim | **red** — the `core`/`feature`/`lib` globs were already depth-tolerant |
| — | (#211 lens) `app/coverifynested/…` probe on the **unfixed** config, same run | tests THIS doc's correction | **green — the correction was overstated.** `app/src/**/*.kt` has one `**`, so `app/` outside `app/src/` escaped like a new ring. Both caught after the fix |
| — | (post-hoc) the same nested probe on the **fixed** config | still red | red, same three findings — no depth regression |

M1 and M3 were run against the **unfixed** config first. A fix verified only against a `find`
listing would be shaped by the assumption it replaces, which is why the issue named this
mutation and not that comparison.

The analysed set is read off the **resolved tasks** — `detekt.source`,
`spotlessKotlin.target`, `spotlessKotlinGradle.target` — by an init script, not off the globs,
so the evidence cannot restate the thing under test.

## 9. Enumeration — two independent searches

A search written from what is already known finds what is already known, so the file set was
enumerated twice, from opposite directions:

1. **By what it names** — every hardcoded ring path in any `*.kt`/`*.kts`/`*.yml`/`*.sh`/
   `*.toml` outside the worktrees. Build-gate hits: `build.gradle.kts:28,38,46,47,48`. Every
   other hit is prose in a comment. *This search alone missed line 45* (`app/*.gradle.kts`,
   a single `*`), which is why it is not the only one.
2. **By what it does** — every file-discovery site in build config and CI regardless of what it
   names: `fileTree|FileTree|.setFrom|target(|targetExclude|walkTopDown|Files.walk|find .|grep
   -r` across `*.gradle.kts`, `*.gradle`, `*.yml`, `*.sh`, plus `build-logic/**/*.kt`. Hits:
   `build.gradle.kts:24` (the detekt config file, a fixed path, not discovery), `:25`, `:27`,
   `:38`, `:39`, `:43`, `:51`, and `StringKeyDocsConventionPlugin.kt:86,94` — already derived
   by #202. **No CI step walks the tree**; `ci.yml`'s greps read Gradle output.

Both agree on the answer: **3 discovery sites, all 3 in one file, all 3 changed.**

## 10. What ships

- `build.gradle.kts` — the three gates derived from `subprojects` (+ `gradle.includedBuilds`
  for the included build's scripts, + the root's own two scripts at depth 1), with the
  reasoning, the refusals and the residuals recorded where the wiring is.
- This plan and its ROADMAP row.
- No other file. In particular no `feature/**`, and no attempt to widen coverage to
  `build-logic`'s Kotlin (§6) — that is a different change.
