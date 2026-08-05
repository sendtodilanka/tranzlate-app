# Plan — #258: a leading path defeats the guard hooks

status: accepted
(accepted basis: owner directive, 2026-08-04 — chose "fix #258 now" over deferring,
after the VERIFIED-TOUCH architecture review named this its Pillar 0. #258 is the
one live, unresolved bug that every other guard's value depends on.)

Refs: #258 (type:bug, P1, S1). Rule 8 surface — the git guard rails. Rule 5
high-risk (the mechanism that protects `main`), so a cross-model adversarial lens
is mandatory before merge.

## The harm, in one line

Typing `/usr/bin/git push origin main` instead of `git push origin main` pushes
straight to `main` with **no** guard firing and **no** error — the same `main` that
was lost twice (#108, #132/#134). Every guard that keys on `git`/`gh` is bypassable
the same way.

## Why — verified, two layers, not one

Fixing only the `settings.json` wiring is not enough. Both layers were checked
against the code on `main` at `c91cb08`:

**Layer 1 — the `if:` wiring (`.claude/settings.json`).** The matcher decides
whether the hook runs at all, before the hook sees anything.

| line | hook | matcher | path-qualified? |
|---|---|---|---|
| 11 | guard-git (git) | `Bash(git *)` | **MISSED** — start-anchored |
| 18 | guard-git (gh) | `Bash(gh *)` | **MISSED** |
| 25 | guard-pr | `Bash(gh *)` | **MISSED** |
| 32 | guard-tracker | `Bash(gh *)` | **MISSED** |
| 39 | device-claim | `Bash(*adb *)` | caught (fixed #223) |
| 46 | guard-restore | `Bash(*git *)` | caught (fixed #222) |

**Layer 2 — each hook's own command detector.** Even with the wiring fixed, some
detectors would run and find nothing:

- `guard-git.sh:39,44,49` — `(^|[;&|][[:space:]]*)git` / `(git|gh)`. No path group:
  `/usr/bin/git push` is not matched. **Vulnerable.**
- `guard-pr.sh:27` — `(^|[;&|][[:space:]]*)gh[[:space:]]+pr[[:space:]]+create`. **Vulnerable.**
- `gh-merge-target.sh:28` (`_GH_MERGE_RE`, sourced by guard-git + guard-tracker) —
  **already** `([^[:space:]]*/)?gh` with runner/env handling. The *merge-target*
  resolution is already path-safe; only its wiring blocks it. **Not vulnerable.**

So the merge checks need only the wiring; the push / `--no-verify` / pr-create
checks need the wiring **and** an inline path group.

## The fix

1. **Wiring** — `settings.json` lines 11/18/25/32: `Bash(git *)`→`Bash(*git *)`,
   `Bash(gh *)`→`Bash(*gh *)`. Same shape already proven live for `Bash(*adb *)`
   and `Bash(*git *)`. (A broader matcher over-fires harmlessly — the hook
   re-validates and exits; the device-claim header accepted exactly this trade.)
2. **guard-git.sh** lines 39/44/49 — insert the proven path group
   `([^[:space:];&|]*/)?` after the segment anchor, mirroring `device-claim.sh:141`.
3. **guard-pr.sh** line 27 — same insert before `gh`.
4. `gh-merge-target.sh` — no change; already path-safe by inspection — `_GH_MERGE_RE`
   (line 28) includes `([^[:space:]]*/)?gh` and handles `command`/`env`/`sudo`/env-assign
   runners. Only its wiring (`if:`) blocked it, which part 1 fixes.

## Convergence-breaker — the SECOND instance, so the class-guard is TRACKED (deferred under the freeze)

The leading-path bypass already bit once (adb, #223). Rule 12 says at the second
repeat you stop patching case-by-case and add a check derived from the source — a
test that reads the live `settings.json` matchers and fails if any git/gh/adb guard
is start-anchored again. **It is NOT built in this PR.** The owner set a governance
freeze (2026-08-04): fix broken guards, add no new machinery until rev5's real
defects are closed. So this PR is the **defect fix only**; the regression guard is
recorded on the wave-1f worklist in `issue-130-rev5-completion.md`, next to the
existing "wire the hook tests into preflight" item, to be built when that wiring is.
The reproduction below is the verification for now — a deliberate deferral of the
guard, not a substitute for it.

## How it is verified (rule 11: reproduce, mutate-first)

- **Reproduce, before the fix:** pipe `/usr/bin/git push origin main` at `guard-git.sh`
  → it allows (exit 0, no deny). Recorded before touching code.
- **Mutate-first:** the mutation (a leading `/usr/bin/` path) was chosen and run
  BEFORE the fix touched the detectors — path-form `allow`, plain-form `deny`,
  recorded above. After the fix the same path-form probes flip to `deny`. The
  mutation was not shaped by reading the fixed code because it predates it.
- **Internal detectors:** direct payload tests per hook — path-qualified denied,
  plain still denied, unrelated still allowed, `high ...`-style false-substring not
  mis-denied by the internal regex.
- **Real-tool wiring:** the `if:` matcher is the harness's, not the hook's. Its
  path behaviour is already proven for `Bash(*adb *)` "by typing the real command
  at the real tool" (device-claim header). Whether a mid-session `settings.json`
  edit reloads is uncertain (update-config's watcher caveat); the honest basis for
  the wiring is that proven precedent, stated as such — not a re-typed test claimed
  but not run.

## Co-verify (rule 5, high-risk)

Cross-model adversarial lens, non-author. Attack surface: (a) does `Bash(*gh *)`
over-fire in a way that breaks legitimate commands; (b) is the inline path group
correct — does it match a word merely ending in `git` (`mygit push`), which it must
NOT; (c) does the invariant test actually go RED on the unfixed hooks (a test that
cannot fail is #242's shape); (d) did I miss a guard.

## What would prove this plan wrong

If the `if:` matcher does NOT support a leading `*` wildcard the way the two live
`Bash(*…)` entries assume — then the wiring fix is inert and #258 needs a different
mechanism (a matcher on `Bash` alone, re-validated inside every hook). The two
existing `Bash(*…)` entries are the evidence it does; if a test shows otherwise,
this plan reopens.
