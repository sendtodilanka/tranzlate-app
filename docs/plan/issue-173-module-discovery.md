# Plan — issue #173 hole 2: `verifyStringKeyDocs` discovers files by a path shape

status: accepted
(accepted basis: #173 is owner-filed, states the harm, names the fix — "derive the
scan from the registered module list, `rootProject.subprojects`" — and names the
mutation that must prove it. This plan settles only what the issue left open: what
is scanned *inside* a module, which alternatives are refused and why, and how the
result is known to be the same in CI.)

Refs: #173 · #172, #152, #163 — **not** `Fixes:`, see §7.

## 1. Scope — hole 2 only, deliberately

#173 has two halves. This is the second one: **module discovery**. Hole 1 — the
reverse direction, a documented key whose resource no longer exists — is **not**
done here and is why #173 stays open. Hole 1 needs the catalogues to carry a
parseable planned / shipped / retired status, which means editing every
`STRINGS_*.md`; those are the files PR-15 and every future feature PR touch, and
its first run today would be ~60 findings that are almost all deliberate. That is
its own change with its own review, not a rider on a build-logic fix.

## 2. The harm, reproduced on this branch before any code

`StringKeyDocsConventionPlugin.kt:30-38` built its file set from four `include()`
globs, one per named ring, each assuming exactly
`<ring>/<module>/src/<sourceSet>/res/values*/strings.xml`.

Baseline, untouched tree: `210 keys, resource files: 24, catalogues: 6`, and an
independent `find . -name strings.xml -not -path '*/build/*'` returns the same 24
files — today's tree has no blind spot at all. The hole is latent, so it can only
be shown by creating the case:

| Probe on the **unfixed** plugin | Result |
|---|---|
| `bench/perf/src/main/res/values/strings.xml` + `include(":bench:perf")`, key `bench_undocumented_key_m1a` in no catalogue | **green**, `210 keys, resource files: 24` — byte-identical |
| `core/data/local/src/main/res/values/strings.xml` + `include(":core:data:local")`, key `core_local_undocumented_key_m1b` | **green**, `210 keys, resource files: 24` — byte-identical |

An undocumented shipped key, and the gate does not merely fail to complain — its
printed evidence does not change by one character. That is the whole failure mode
this gate exists to end, present inside the gate itself.

(#173 quotes `201 keys, resource files: 24` from PR #172's lens. The file count is
still exactly right; the key count is **210** on `origin/main` today. Cited
numbers age — rule 11's fourth cause.)

## 3. What the scan is derived from now

`subprojects` — the registered module list. A module exists because
`settings.gradle.kts` says so, and nothing outside that list ships resources into
an APK, so the module list *is* the scope rather than a guess about it. 28 modules
today (25 `include()` lines plus the intermediate `:core`, `:feature`, `:lib`).

Inside each module the pattern is `**/res/values*/strings.xml`, minus
`**/src/test*/**`, `**/src/androidTest*/**`, `**/build/**` — depth-agnostic, so it
does not re-introduce a smaller version of the same assumption. Every source set
(`main`, a flavour, a build type, a flavour+type combination), every locale
qualifier, and a non-standard `res` srcDir are all covered. A module nested inside
another module's directory is reached twice, once through its own entry and once
through its parent's tree; `FileCollection.getFiles()` is a `Set`, so the count is
unaffected — verified, the file list is still exactly 24 and identical to `find`.

## 4. Alternatives refused, with the reason

**A whole-tree `**` walk from the repo root.** Simplest to write and the worst to
live with. This repo's standing convention puts agent worktrees at
`.claude/worktrees/<name>` — full checkouts of this same repo, nested inside it.
Measured while writing this plan: **48** `strings.xml` files under that directory,
twice the 24 the repo owns. A root-anchored walk scans all of them, so `main`'s
build would fail on an uncommitted key on someone else's branch, and the gate's
result would differ between a developer machine and CI by construction — the #163
shape, deliberately built in. `subprojects` rather than `allprojects` is the same
decision: the root project's directory *is* the repo root.

**Asking AGP for each variant's real `res` source dirs.** More precise in theory.
It makes the gate depend on the AGP variant API, on cross-project configuration
and on evaluation order. #163 is a Konsist gate that was green locally and red in
CI on one commit with the cause never diagnosed; the reason *this* task has been
trustworthy is that it reads plain checkout files — no classpath, no AST, no
variant model — so local green is evidence. Not traded away for a layout this repo
does not have.

**Keeping the glob and asserting its count against the module list** (#173's stated
minimum). Rejected as strictly worse than the derivation it would guard: it still
has to answer "how many files *should* a module have?", which is unanswerable —
zero is legitimate for most modules here. Derivation removes the question.

## 5. What is still out of reach, stated rather than hidden

A `strings.xml` in a directory that is part of no registered module **and** not
nested inside one. Nothing compiles it and no APK packages it, so it ships no key
and owes no catalogue row; `include()`-ing it is what would make its keys real, and
that is the same act that puts it in this scan. `build-logic` is outside for the
same reason — an included build, not a subproject.

The nested case is not a gap at all, and that was checked rather than assumed: the
`core/data/local` probe **with its `include()` line removed** still fails the
build, caught through `:core:data`'s own depth-agnostic tree.

## 6. Why CI cannot diverge here

- The task still reads only files from the checkout. No classpath, no AST, no
  variant model, no network — unchanged from #172, which is the property that has
  made it reliable.
- Discovery now depends on `settings.gradle.kts`, a committed file, instead of on
  the *shape* of the directory tree. CI checks out that exact file. The old glob
  and the new derivation are equally exposed to the one real difference between a
  laptop and CI — files present locally but not committed — and the walk refused in
  §4 is the only option that would have made that difference enormous.
- `subprojects` is read at configuration time, when Gradle has already created
  every project instance from settings; it needs no project to be *evaluated*, so
  there is no evaluation-order dependency to be ordered differently on CI.
- Configuration cache is on (`org.gradle.configuration-cache=true`) and the entry
  stores and is reused across the runs below.

## 7. `Refs:`, not `Fixes:`

#173 covers two holes and only one is closed here. A `Fixes:` trailer would
auto-close the issue on merge and hole 1 would be lost — which is exactly what
#173 was filed early to prevent happening to #152's second ask. #173 stays open on
hole 1; the trailer is `Refs:`.

## 8. Mutation register (decided before the code — rule 11)

Written to `scratchpad/i173-mutations.md` before the plugin was touched. Each run
is `./gradlew verifyStringKeyDocs --rerun-tasks`. Probes restored with `cp`
backups; `git status` clean after every one.

| # | Mutation | Expected | Observed |
|---|---|---|---|
| M1a | new top-level ring `bench/perf` + `include(":bench:perf")`, undocumented key | **red, naming it** | red — `1 of 211 shipped keys appear in no STRINGS_*.md catalogue`, `bench/perf (1)` → `bench_undocumented_key_m1a  bench/perf/src/main/res/values/strings.xml` |
| M1b | `core/data/local` + `include(":core:data:local")`, undocumented key | **red, naming it** | red — `core/data/local (1)` → `core_local_undocumented_key_m1b  core/data/local/src/main/res/values/strings.xml` |
| M1c | M1b **without** the `include()` line (post-hoc; proves §5's nested claim) | red via the parent module | red, same message |
| M2a | `bench/perf` with a documented key (`nav_home`) instead | **green** | green — `210 keys in 25 resource file(s) across 30 registered module(s)` |
| M2b | `core/data/local` with a documented key | **green** | green — `210 keys in 25 resource file(s) across 29 registered module(s)` |
| M3 | untouched tree | green, counts unchanged | green — `210 keys`, `resource files: 24`; scanned-file list `diff`s clean against an independent `find` |
| M4 | all six `STRINGS_*.md` removed (`cp` backup, restored, `shasum -c` OK) | red, loudly | red — "No STRINGS\_\*.md catalogue found under docs/ … that is a failure, not a pass" |
| M5 | every `include()` commented out (post-hoc; proves the vacuous-pass guard is reachable through the new path) | red, loudly | red — "No values\*/ strings.xml found across **0 registered module(s)**" |
| M6 | a nested repo copy at `.claude/worktrees/fake-sibling/core/ui/src/main/res/values/strings.xml` with an undocumented key (post-hoc; proves §4's refusal) | **green**, unchanged | green — `210 keys in 24 resource file(s)`, the nested copy invisible |

M1a and M1b were run against the **unfixed** plugin first (§2) and were green with
a byte-identical count. A fix verified only against `find` output would have been
shaped by the assumption being replaced, which is why the issue named this
mutation and not that comparison.

## 9. What ships

- `StringKeyDocsConventionPlugin.kt` — discovery derived from `subprojects`; the
  reasoning and the two refusals recorded where the wiring is.
- `VerifyStringKeyDocsTask.kt` — new `modulePaths` `@Input`; the report and the
  printed line now state the scan's scope (modules and file count) and the report
  lists every module and every scanned file. #173 is a scan that was silently
  narrower than the repository, and a scope nobody can see is a scope nobody
  checks; as an input it also means a lost ring changes the task's output instead
  of leaving it byte-identical.
- No `feature/**` and no `STRINGS_*.md` are touched — PR-15 is live in those.

**Honest gap:** `build-logic` has no test source set, and a test added there would
not run in CI (`./gradlew test` and `./gradlew build` at the root do not run an
included build's tests). Adding one means a workflow change and is not smuggled
into this fix. The verification is the mutation register above, which is the
standard #172 set for this same gate.
