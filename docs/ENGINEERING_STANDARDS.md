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
2. **Co-verify lens by a non-author** (rule 5 — the gate), **its `Co-verify-verdict:`
   posted to the PR** as a durable record (rule 8, gate 3). An official
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

**Where the earlier rule-8 gates apply, their artifacts are on the PR too:** a
**design-debate** outcome recorded in the plan-doc for a new feature (gate 1); a
**red-team** record for a change that adopts a risky *design* (gate 2). These make
*whether the gate ran* a lookup, not a promise — a durable record, **presence, not
proof of quality**; the real proof is the evidence each artifact carries, never its
mere existence. (No hook enforces these — this project's record is that a hook of that
shape adds rot, not safety; the enforcement is the DoD and the posted artifact.)

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

## 7. Never idle-wait on a result — the enemy is the wait, not the verification

**Incident:** #278 was landed fully — including sitting through its CI-on-tip wait —
**before** the wave-1d builds that did not depend on it were started. They should have been
building during that wait. The owner's framing, 2026-08-05: *"CI and co-verification running is
not a problem in itself. The problem is waiting for its result without taking next actions. CI
and co-verification is strictly necessary for the zero-touch human repo."*

- **CI and co-verify are mandatory and non-negotiable** — a repo with no human must have the
  machine prove everything. They are **not** the cost to cut.
- **The cost is idle-waiting on a result with nothing else in flight.** Dispatch, then
  immediately start the next independent build / lens / scope. A build running, a lens running,
  a CI-on-tip wait — each is a cue to begin the next thing, never to pause.
- **Only the final merge is serial** — rebase → CI-green-on-tip → merge, one PR at a time (the
  guard that protects `main`), bounded by rule 1's two-PR cap. Even its CI-wait overlaps with
  other PRs building and co-verifying, so it is not idle either.
- **Building is wide; landing is narrow.** Parallelise building freely (rule 6's ownership map
  is the safety rail); the merge lane and the open-PR count stay inside rule 1.

## 8. Red-team a design before adopting it — where its failure would be silent

**Incident:** the worst failures were designs that passed implementation-level scrutiny, or
would have, yet were wrong **by construction** — `main` lost twice to a merge pattern each
branch passed alone (rule 1); the #213 device-claim hook that "looked fine" and could never
fire (§4). A co-verify of the implementation catches neither — only an attack on the design does.
(The gate-enforcement design red-teamed under this very rule is the worked example:
`docs/research/red-team-gate-enforcement.md`.)

- **Co-verify (rule 5) checks one CHANGE is correct** — a per-PR gate. It does not catch a
  DESIGN that is wrong by construction.
- **Before adopting a mechanism or process change whose failure would be SILENT** — a bug slips
  through, `main` breaks, a guard weakens — put the **chosen** design through a small red-team
  first: 2–3 adversaries hunting how it fails (*"how does this let a bug through / break
  `main`?"*). Adopt only if it survives.
- **Not every change** — that is co-verify's job. Red-team is for designs where the downside is
  a safety guarantee.
- **Three gates, three stages — do not conflate them.** A **design-debate** (owner's standing
  rule, `.claude/memory/`) CHOOSES the design *before building* — advocates arguing *for*
  competing proposals. A **red-team** ATTACKS the chosen design *before adopting a risky one* —
  adversaries arguing *against* it. A **co-verify** (rule 5) checks the built CHANGE is correct
  *before merge* — per-PR. The order is **choose → attack the choice → build → co-verify**;
  a design-debate's own adversarial judging picks a winner, it does not stress-test the winner
  for silent failure, which is the red-team's separate job. The **integration-lane** — a
  merge-queue that would raise rule 1's two-PR cap — is the standing red-team example: it does
  not ship until a red-team clears it.

## 9. Risk register — reviewed at every wave boundary

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
