# Plan — rev5 completion: the operational order to finish the Language screen

status: accepted
(accepted basis: owner directive, 2026-08-03, verbatim below. This is not a new
decision to be ratified — it is his objective written down as an order of work.)

Refs: #130 (the epic). This file is the **operational plan**;
`issue-130-language-rev3.md` remains the **per-PR tracker** and
`issue-130-language-rev3-ruling.md` remains the **design authority**.

## The objective, as stated

> *"All information provided pertains exclusively to the Language screen rev5
> design. The overarching objective is the complete implementation of rev5.*
>
> *Resolve all existing issues, including open GitHub issues; **do not implement
> new features referenced in the issues section**.*
>
> *Conduct a comprehensive audit of the current codebase related to rev5 and
> remediate any identified deficiencies.*
>
> *Proceed with the remaining rev5 implementation until completion."*

Three phases, in order: **fix → audit → finish**. Phase 3 does not start while
phase 1 has open S0/S1, per his standing fix-before-build rule.

---

## Scope decision — what "existing issues" means here

59 issues open at the time of writing. Classified once, so the boundary is not
re-litigated per PR.

### ❌ NOT to be built — new features he explicitly excluded (8)

`#7` theme-colour presets · `#8` LLM enhancement · `#20` screenshot-test harness ·
`#22` performance foundation · `#78` camera vertical (he also holds it) ·
`#102` old-app result components · `#139` ad layer · `#212` PDF reader.

These stay open and untouched. **A defect discovered inside one of them is still
a defect** — but building the feature is out of scope.

### ⏸ DEFERRED — not rev5 (10)

- **Monetization / Access / paywall (7):** `#114` `#124` `#125` `#126` `#127`
  `#128` `#129`.
- **`#191`** — History undo. Owner-ruled 2026-08-02, and blocked on #241.
- **`#115`** — 72 fil/pt-BR strings never natively reviewed. **Not monetization:**
  a cross-cutting i18n QA task spanning every module, blocked on an external
  human reviewer.
- **`#116`** — launch follow-ups parked during 1.1.0. **Not monetization:**
  Composer/Result-screen hygiene (`ComposerScreen.kt` limit-face styling,
  landscape error asymmetry, `composerFitFor()` test gap, `StatFs` on Main).

The first draft of this section called **nine** of these "monetization, Access and
Ads" (#191 always had its own clause).
**The #256 lens checked each and two are neither** — corrected here and in
CLAUDE.md rule 13. Both are still out of scope, on the honest ground that neither
is Language-screen work.

**#114 is S1 and means the shipped app earns nothing.** It is deferred by scope,
not by importance, and he has been told twice. It is not rev5.

### ✅ IN SCOPE — 41, in three groups

**Group A — rev5 screens, user-visible (14).** These are what "complete
implementation of rev5" means being true.

`#154` `#158` `#183` `#184` `#203` `#219` `#224` `#226` `#229` `#230` `#243`
`#244` `#248` `#250`

**Group B — rev5 tests that cannot fail, or do not exist (4).** Quality of what
already shipped.

`#240` `#241` `#242` `#254`

**Group C — the gates and the process (23).** Not rev5 code, but several
directly gate rev5 quality: #241 needs #231 and #253; every enumeration depends
on #221.

`#40` `#157` `#163` `#188` `#193` `#196` `#204` `#208` `#210` `#215` `#217`
`#220` `#221` `#222` `#223` `#228` `#231` `#232` `#245` `#251` `#252` `#253`
`#255`

---

## Phase 1 — resolve, in this order

Ordered by **what unblocks what**, not by severity alone. Merges stay serial
(`/land-pr`); building may parallelise **only where file ownership does not
overlap** — the rule that was absent when #246 and #249 collided.

| Wave | Issues | Why here | Owns |
|---|---|---|---|
| **1a** | #253 · #231 · #221 · #222 · #223 · #232 (in flight, PR #233) | The gates that everything after is verified by. #241 cannot be done honestly until #253 and #231 are. | `build-logic/`, `.claude/`, `ci.yml` |
| **1b** | #241 · #240 · #242 · #254 | The tests that do not exist or cannot fail. Needs 1a. | `core/database/`, `feature/language/**/test` |
| **1c** | #226 · #229 · #230 · #243 · #219 | Copy and previews — one string sweep, one owner. Cheap, and #243 makes a third of the failure copy visible to the owner for the first time. | `feature/language/**/res`, `STRINGS_*.md` |
| **1d** | #224 · #244 · #248 · #250 · #154 · #158 | Behaviour defects on the shipped screens. #250 waits for PR-23 by ruling 8 — carried, not fixed. | `feature/language/**/kotlin` |
| **1e** | #183 · #184 · #203 | Frame/spec disagreements. #183 is owner-answered-by-measurement; #203 needs a decision. | `docs/design/`, `docs/plan/` |
| **1f** | #157 · #163 · #188 · #193 · #196 · #204 · #208 · #210 · #215 · #217 · #220 · #228 · #245 · #251 · #252 · #255 · #40 | Process residue. Batch aggressively — several are one fix. | varies |

### Wave 1f also carries work that is NOT issue-shaped

**Owner, 2026-08-03:** *"Issues consolidation is good but various issues stacking into
one issue adds more complexity."* He was right, and #253 was the example — one
closeable item accumulated three unrelated ones and could no longer close.

**So: an issue is for a defect that needs evidence and argument. A line item that is
merely "do this next" belongs here.** This list is the work tracker; it needs no issue
numbers to be actionable.

| Item | Why it is not an issue | Sequenced after |
|---|---|---|
| Wire `.claude/hooks/tests/guard-restore-invariant.sh` (112 assertions) and `device-claim-behaviour.sh` (31) into `preflight` | Committed by #257 and runnable, but nothing runs them. No argument needed — it is one line in `PreflightConventionPlugin`. **A loud skip must count as a pass**: the invariant sections skip until #233 lands the hookify rules. | #233, #257 |
| Fold the man-page-derived regression table into whatever runs above | Same reason. | as above |


## Phase 2 — the audit

**Not a re-read.** A comprehensive pass over rev5 code with the official
specialists, because the last one found **ten defects in code that had already
passed a co-verify lens each**:

1. `pr-review-toolkit:silent-failure-hunter` over all of `feature/language`.
2. `pr-review-toolkit:pr-test-analyzer` over every rev5 test, asking only *would
   this go red if the behaviour were wrong?*
3. `pr-review-toolkit:type-design-analyzer` over the rev5 model types.
4. A frame-by-frame re-derivation of rev5 against the shipped screens, per the
   design-export method — **undeduped, per row**.

Findings go into phase 1's waves, not into a new backlog.

**Where the audit is RECORDED — named, because rule 13's exit condition depends on
it and the first draft named nothing.** Each of the four passes writes
`docs/research/issue-130-rev5-audit.md`: what was run, against which commit, what it
found, and — the half that was missing — **an explicit line when a pass returns
clean.** A future session checking whether the audit happened opens that one file. If
it does not exist, the audit did not happen; a clean pass that leaves no trace is
indistinguishable from one that was never run.

## Phase 3 — finish rev5

`PR-20 … PR-28`, held until phase 1 has no open S0/S1 in groups A or B.

| PR | Scope |
|---|---|
| PR-20 | 19h + 19m + app-shell sheet host |
| PR-21 | 18a/18b first run (LocaleList suggestions + E-K1) |
| PR-22 | PackEvents + app SnackbarHost + snackbars 20a |
| PR-23 | 20b rewrite + relabel + 20f empty state — **also closes #250** |
| PR-24 | 20c pack-actions sheet |
| PR-25 | 20e Free up space — **the action 19b currently omits** |
| PR-26 | 20d list-detail |
| PR-27 | Ruling 2: remove the Detect ONLINE ONLY chip |
| PR-28 | 19n flavor-scoped copy |

---

## Standing constraints on every PR under this plan

- **File ownership declared before dispatch**; no two live agents own one file.
- Branch cut with `git switch -c <b> origin/main`, verified by
  `git diff --stat origin/main...HEAD` before the PR opens.
- **CI green verified by SHA**, not by the badge — a `CONFLICTING` PR runs no
  workflow and keeps its stale green (#253).
- Co-verify lens ≠ author, **plus an official specialist** chosen by what the PR
  touches.
- `/land-pr` for every merge; tracker row moves in the same PR.
- Worktree removed after merge, `git status --porcelain` checked first.

## What would prove this plan wrong

If phase 2's audit returns findings that **change the rev5 design** rather than
the code, phase 3's PR list is stale and the ruling needs re-opening before
building. #203 and #184 are the two most likely to do that — both are frames
that may not describe a producible state.
