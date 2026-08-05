---
name: land-pr
description: Land a PR safely — rebase onto the tip, re-verify, require CI green on THAT commit, merge, then update the epic tracker. Use whenever a PR is ready to merge, or when merging a batch of sibling PRs. Encodes the two incidents that broke main.
---

# Landing a PR without breaking main

`main` has been broken twice by the same mistake, and both times every
individual signal was green:

- **PR #108** — a string became a plural on another branch; the two together
  failed, neither alone did.
- **PRs #132 / #134** — one added a parameter to `LanguageRepository.setLastUsed`,
  the other added a test fake implementing the retired signature. Each branch
  was green. Neither branch could see the other. Chain-merging them off the
  same older base put the break on `main`, and the next PR's post-rebase CI is
  what surfaced it.

**The rule both incidents produce: a branch that predates the tip proves nothing
about the merge result.** The `PreToolUse` guard in `.claude/hooks/guard-git.sh`
enforces this mechanically — it denies a merge when the PR's branch is behind
`origin/main`, in every form the command takes: the number after a flag, a URL,
a branch name, or no argument at all. This skill is the procedure the guard
protects.

## Procedure — one PR at a time, never a batch in one loop

1. **Rebase onto the tip**, in the PR's own worktree:
   ```
   git fetch origin && git rebase origin/main
   ```
   Expect a conflict in `docs/plan/issue-NN-*.md` when sibling PRs each updated
   the tracker — resolve by keeping BOTH sides' completed rows.

2. **Re-verify locally before spending CI.** From the worktree:
   ```
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   ./gradlew preflight
   ```
   `preflight` (#253) compiles every androidTest source set and both APKs — the
   old five-command line (`test … spotlessCheck detekt`) compiled no androidTest
   at all, so a rebase could break `:core:database`'s tests and still show green.
   A rebase can break compilation neither branch broke — that is why this runs here.

3. **Push and wait for CI ON THE REBASED COMMIT — verified by SHA, not by the badge:**
   ```
   git push --force-with-lease
   sha=$(git rev-parse --short HEAD)
   rid=$(gh run list --branch <branch> --limit 1 --json databaseId --jq '.[0].databaseId')
   gh run watch "$rid" --exit-status
   # THE CHECK THAT PREVENTS A THIRD main break — the run's SHA must equal the tip:
   run_sha=$(gh run list --branch <branch> --limit 1 --json headSha --jq '.[0].headSha[0:7]')
   [ "$run_sha" = "$sha" ] || { echo "STALE: CI ran $run_sha, tip is $sha — DO NOT MERGE"; exit 1; }
   ```
   `gh pr checks` fails on this repo's token scope — watch the run instead.
   **Green on the branch's *old* history does not count, and a `CONFLICTING` PR
   runs no workflow at all — its last green sits on a stale commit with nothing
   saying so (that nearly broke #246's landing). Comparing `headSha` to the tip is
   the only thing that catches it; the badge cannot (#253).**

4. **Merge** — with the epic tracker row *already carrying this PR's number*.
   The owner's strict rule: a merge that leaves the tracker stale is an
   incomplete merge. Write the row into `docs/plan/issue-NN-*.md` on the branch
   and push it before you merge; `.claude/hooks/guard-tracker.sh` denies the
   merge when nothing under `docs/plan/*.md` mentions `#N`, on the branch or on
   `main`.

   This step used to end "otherwise it rides the next one". That ride-along is
   the failure mode rather than a workaround for it — the rule broke three times
   in one session (PR-3, PR-6/PR-7, PR-0c) and every time by deferring the tick
   to merge time, when the merge is already done and the branch can no longer
   carry it. A row written on the branch will conflict with its siblings on the
   next rebase; step 1 already tells you how, and that conflict is the cheap
   half of this.

5. **Then, and only then**, start the next PR at step 1 — with the tip that now
   includes what you just merged.

## When several PRs are ready at once

Do not loop them. Pick the one with the smallest blast radius, land it fully,
then rebase the rest onto the new tip. Sibling branches built in parallel are
exactly the shape that produced both incidents; parallelism belongs in the
BUILD phase, not the merge phase.

## What a co-verify lens must ask, every time (rule 11)

The four standing questions — **enumerate · reproduce · mutate ·
verify-the-documents** — live in `CLAUDE.md` rule 11, each with the shipped
defect that put it on the list. They are deliberately not restated here: this
file, `CLAUDE.md` and `.claude/agents/co-verify-lens.md` each carried a copy and
the three had already begun to drift. Run the lens as the `co-verify-lens`
agent, which reads rule 11 itself and pins a cross-model default.

## What the guard will not catch

It compares commits, not semantics. Two PRs can be behind by zero commits and
still disagree — a renamed symbol, a changed default, a new required field. The
local re-verify in step 2 is what catches those, which is why it is not
optional even when the guard stays quiet.
