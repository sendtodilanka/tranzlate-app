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
enforces this mechanically — it denies `gh pr merge <N>` when the PR's branch is
behind `origin/main`. This skill is the procedure the guard protects.

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
   ./gradlew test :app:assembleTranzlateProdDebug :app:assembleTranzlateFakeDebug spotlessCheck detekt
   ```
   A rebase can break compilation that neither branch broke — that is the whole
   point of running it here.

3. **Push and wait for CI on the rebased commit:**
   ```
   git push --force-with-lease
   gh run watch $(gh run list --branch <branch> --limit 1 --json databaseId --jq '.[0].databaseId') --exit-status
   ```
   `gh pr checks` fails on this repo's token scope — watch the run instead.
   Green on the branch's *old* history does not count.

4. **Merge**, then immediately **update the epic tracker row** in the plan doc.
   The owner's strict rule: a merge that leaves the tracker stale is an
   incomplete merge. If the tracker lives on the branch you just merged, the
   row goes in that same PR; otherwise it rides the next one.

5. **Then, and only then**, start the next PR at step 1 — with the tip that now
   includes what you just merged.

## When several PRs are ready at once

Do not loop them. Pick the one with the smallest blast radius, land it fully,
then rebase the rest onto the new tip. Sibling branches built in parallel are
exactly the shape that produced both incidents; parallelism belongs in the
BUILD phase, not the merge phase.

## What the guard will not catch

It compares commits, not semantics. Two PRs can be behind by zero commits and
still disagree — a renamed symbol, a changed default, a new required field. The
local re-verify in step 2 is what catches those, which is why it is not
optional even when the guard stays quiet.
