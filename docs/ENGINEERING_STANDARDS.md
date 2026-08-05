# Engineering standards — the constraints, not the aspirations

Written 2026-08-03 after the owner said the repository was at risk. Every rule
below exists because something here failed in a specific, recorded way. A
standard with no incident behind it is not in this file.

---

## 1. WIP limit: **two** PRs in flight, and never two that must land in order

**Incident:** `main` was lost twice to the same accident — PR #108, then PRs
#132/#134. Sibling branches, each green alone, merged off a base older than the
tip, so no CI run could ever see the combination (the same accident `CLAUDE.md`
rule 8 records for the git guard — one incident, two rules draw from it). **It
recurred on 2026-08-03**
between #247 and #249: one added a constructor parameter, the other added call
sites with the old signature, both green alone. `/land-pr`'s local re-verify
caught it before CI was asked.

**The rule:**
- **Maximum two open PRs.** At four (2026-08-03) an ordering constraint appeared
  between #233 and #257 and had to be tracked by hand.
- **If two PRs must land in a specific order, that is one PR or two waves** —
  never two concurrent branches with a note about sequence.
- **Drain before opening.** A third PR waits.

**Why a limit and not care:** the guard compares *commits*, not semantics. Two
branches can be zero behind and still break together. The only defence that has
ever worked is fewer things in flight.

## 2. Definition of Done — checkable, or it is not done

A PR is done when **all** hold. No exceptions, no "will follow up".

1. `./gradlew preflight` green **locally**, and CI green **on the exact SHA**
   (`gh run list --json headSha` compared to `git rev-parse origin/<branch>`).
   A `CONFLICTING` PR runs **no workflow** and keeps its stale green — the badge
   is not the check.
2. **Co-verify lens by a non-author** (rule 5 — the gate). An official
   `pr-review-toolkit` specialist chosen by what the PR touches is **optional, the
   caller's choice** — not a merge gate (per `co-verify-lens.md`).
3. `Call sites:`, `Reproduced:`, `Enumerated by:` present **and true** —
   re-derived as the **last act before merge**, never at open. On #256 that field
   went stale twice inside one PR.
4. **At least one issue closes** with `Fixes:`, or the PR states exactly what
   remains.
5. Tracker row moved in the **same** PR.
6. Worktree removed after merge, `git status --porcelain` checked **first** —
   one dead worktree held the only copy of a 449-line research record.

**rev5 is done when** every Phase 3 row in `issue-130-language-rev3.md` shows ✅
**and** `docs/research/issue-130-rev5-audit.md` records all four audit passes with
no design-invalidating finding.

## 3. Stop-the-line: a mechanism that fails twice gets rebuilt, not patched

**Incident:** four rounds on one hook, each fixing exactly what was reported and
each followed by a new instance of the same class. The reviewing lens said it
plainly: *"I'd trust a fourth round less than a documented invariant checked once,
or a real automated test file."*

**The rule — at the SECOND repeat, not the fourth:**
1. **Name the invariant** the instances share. There, it was *a deferral into a
   void*: the hook claiming another mechanism covered a command it could not see.
2. **Derive the check from the other mechanism's source**, never a copy. A copy is
   a second approximation, free to drift from both.
3. **Commit the check.** Three mutation harnesses had defects in two days. A
   check that lives only in a transcript is not a check.

**And the check must fail LOUD when its own assumptions break.** The first fix
exited 2 from a generator — but `done < <(gen)` runs in a subshell, so it printed
its own abort message and still exited 0. **A guard that announces its failure and
then reports success is worse than one that says nothing.**

## 4. Verification debt is tracked like any other

**The pattern, four times in three days:** the thing built to verify was itself
unverified, one level up.

| | |
|---|---|
| `device-claim.sh` | could never fire — needed a file nothing created (#213) |
| a mutation harness | read stale XML and reported `0 failures` with nothing run |
| a second harness | read `BUILD FAILED` (task-not-found) as a killed mutation |
| the invariant generator | mis-parsed and degraded to a **smaller green**, exit 0 |

**The rule: before trusting any check, make it FAIL on purpose.** A harness only
ever seen saying "caught" has not been shown to work. Red before green, every
time, and the red must be produced deliberately.

## 5. Issue hygiene — one issue, one closeable unit

**Incident:** 35 issues opened on 2026-08-02 against 9 closed. Then the first
correction made it worse — #253 accumulated three unrelated items and could no
longer close until four separate PRs landed.

- **One issue = one closeable unit of work.** Sharing a *fix* is the test;
  sharing a *topic* is not.
- **Work needing no argument goes in the plan's wave list**, not an issue.
- **Every fix PR closes at least one issue.**
- **Check it is not already true on `main` before filing.**

**The count is not the target.** Sixty small closeable issues are healthier than
fifteen that each need four PRs.

## 6. Agent orchestration — ownership is declared before dispatch

- **Declare the file map first.** No two live agents own one file. If two tasks
  touch one file they are **sequential**.
- **Cut every branch with `git switch -c <b> origin/main`** and verify with
  `git diff --stat origin/main...HEAD` before opening. #233 was cut from a feature
  branch instead of `main` and silently carried all of #225's diff.
- **Namespace the scratchpad per agent.** Parallel agents share one directory, so
  a `cp` backup named `Foo.kt.bak` can restore another agent's file (#251).
- **Quote, never cite.** Agents cannot read `.claude/memory/` — a brief that says
  "per the X decision" asks them to take on faith exactly what every brief tells
  them not to.

## 7. Risk register — reviewed at every wave boundary

| # | Risk | Severity | State |
|---|---|---|---|
| R1 | **The shipped app earns nothing** — production ships Google *test* ad ids (#114) | **S1, revenue-zero** | Open since 2026-07-31. **Deferred by scope, not importance.** Not rev5. |
| R2 | Instrumentation tests don't run in **CI** (#40); `preflight` compiles them only | S1 | Open in CI (no emulator). #241's androidTest is the first to actually **run** — locally on `Tranzlate_API24` (2026-08-05). |
| R3 | Verification debt (§4) — four instances in three days | S1 | Mitigated by the committed invariant suite (#257, merged 2026-08-04); **unproven over time** |
| R4 | Pro tier caps paying users at 500 chars (#124); entitlement failures read as "out of free quota" (#125/#126) | S1 | Deferred — Access surface, not rev5 |
| R5 | `build-logic`'s own Kotlin is analysed by **neither** lint gate (#210) | S2 | Open. The code implementing the gates is ungated. |
| R6 | A leading path defeated **every** hook, including the `main`-protection guard (#258) | S1 | **#258 fixed (#264)**; the residual class — env/runner prefix + path-qualified `git restore` — is **#265**, deferred under the freeze. |

**R1 is the one a business would escalate first**, and it is deliberately not
being worked because the owner scoped this effort to rev5. That decision is his
and is recorded so it is not mistaken for an oversight.
