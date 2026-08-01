#!/usr/bin/env bash
# PreToolUse/Bash guard for git + gh operations.
#
# Exists because this repository has lost `main` to the SAME accident twice
# (PR #108, then PRs #132/#134): sibling PRs, each green on its own branch,
# merged off a base older than the tip — so no CI run could ever see the
# combination that broke. Reading the checklist did not prevent it. This does.
#
# Design rules:
#  - FAIL OPEN. A network hiccup, a missing gh, a detached HEAD — none of those
#    are reasons to stop someone working. Only a CONFIRMED violation blocks.
#  - Deny with the fix, not just the complaint.
#  - Fast: no fetch on the paths that do not need one.
set -uo pipefail

# Which PR a `gh pr merge` acts on is shared with guard-tracker.sh — see that
# file for why one line in two copies was two bugs.
# shellcheck source=./gh-merge-target.sh
. "$(dirname "${BASH_SOURCE[0]}")/gh-merge-target.sh" 2>/dev/null || exit 0

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

# Match the COMMAND, not its payload. Everything after a heredoc marker is data
# being written — a commit message, a PR body, this file's own documentation —
# not a flag being passed. Learned the hard way and immediately: the very first
# commit of this hook was denied BY this hook, because the message explained
# what the banned flag is.
scan=${cmd%%<<*}

deny() {
  jq -nc --arg r "$1" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  exit 0
}

# ── 1. the skip-the-gate flag: human-only emergency override (CLAUDE.md rule 6)
# Anchored to a git/gh invocation so the string can be discussed in prose.
if printf '%s' "$scan" | grep -qE '(^|[;&|][[:space:]]*)(git|gh)[[:space:]][^;&|]*[[:space:]]--no-verify([[:space:]]|$)'; then
  deny "CLAUDE.md rule 6: --no-verify is a human-only emergency override. Fix what the hook is failing on instead — a skipped gate is how a broken commit reaches main."
fi

# ── 2. direct push to main ────────────────────────────────────────────────────
if printf '%s' "$scan" | grep -qE '(^|[;&|][[:space:]]*)git[[:space:]]+([^;&|]*[[:space:]])?push'; then
  if printf '%s' "$scan" | grep -qE 'push[^;&|]*[[:space:]](origin[[:space:]]+)?(main|HEAD:main|[^[:space:]]+:main)([[:space:]]|$)'; then
    deny "Direct push to main is blocked (CLAUDE.md git workflow). Open a PR: git push -u origin <branch> && gh pr create --base main."
  fi
  # A bare `git push` while main is checked out is the same act, less obviously.
  if printf '%s' "$scan" | grep -qE '(^|[;&|][[:space:]]*)git[[:space:]]+push[[:space:]]*($|[;&|])'; then
    branch=$(git symbolic-ref --quiet --short HEAD 2>/dev/null || echo "")
    [ "$branch" = "main" ] && deny "Bare 'git push' on main pushes to main. Branch first: git checkout -b <name>."
  fi
fi

# ── 3. gh pr merge while the branch is behind the tip — THE recurring accident ─
# The target used to be read as "the number written right after `merge`", which
# saw one of the ten ways this command is written. gh_pr_merge_numbers resolves
# the way gh does — flags anywhere, a URL, a branch, or no argument at all.
command -v gh >/dev/null 2>&1 || exit 0         # no gh — allow
for num in $(gh_pr_merge_numbers "$scan"); do
  head=$(gh_run 15 gh pr view "$num" --json headRefName --jq .headRefName 2>/dev/null) || continue
  [ -z "$head" ] && continue
  gh_run 20 git fetch -q origin main "$head" 2>/dev/null || continue   # offline — allow
  behind=$(git rev-list --count "origin/${head}..origin/main" 2>/dev/null) || continue
  if [ "${behind:-0}" -gt 0 ]; then
    deny "PR #${num} (${head}) is ${behind} commit(s) BEHIND origin/main, so its green CI never saw the merge result. This exact situation broke main twice (#108, #132/#134). Rebase, push, wait for CI green on THAT commit, then merge:
  cd <the branch's worktree> && git fetch origin && git rebase origin/main && git push --force-with-lease
  gh run watch \$(gh run list --branch ${head} --limit 1 --json databaseId --jq '.[0].databaseId') --exit-status && gh pr merge ${num} --merge"
  fi
done
exit 0
