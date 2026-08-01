#!/usr/bin/env bash
# PreToolUse/Bash guard: a merge may not leave the epic tracker stale.
#
# The owner's rule, verbatim: "a merge that leaves this table stale is an
# incomplete merge." It broke THREE times in one session — PR-3, PR-6/PR-7,
# PR-0c — each time for the same reason: the row's scope text was written while
# building and the tick was left for merge time, then the merge happened without
# it. Three corrective intentions, three failures. So it stops being an
# intention.
#
# The check is deliberately weak: some plan doc must MENTION the PR number. It
# cannot tell ✅ from 👁, and it is not trying to — it catches the failure that
# actually happens, which is a row that was never touched at all.
#
# Design rules, inherited from guard-git.sh:
#  - FAIL OPEN. No gh, no plan docs, an unresolvable command, a grep that could
#    not run → allow. Only a confirmed violation blocks.
#  - Deny with the fix, not just the complaint.
set -uo pipefail

# Which PR a `gh pr merge` acts on — shared with guard-git.sh, because the two
# copies of that answer were two copies of the same hole (issue #167).
# shellcheck source=./gh-merge-target.sh
. "$(dirname "${BASH_SOURCE[0]}")/gh-merge-target.sh" 2>/dev/null || exit 0

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0
scan=${cmd%%<<*}

command -v gh >/dev/null 2>&1 || exit 0

# Does this PR owe the tracker a row? Decided by what it TOUCHES and where it
# lives — never by what it SAYS. The first version asked whether the body
# matched '#130|PR-[0-9]+|epic|tracker', so PR #165, whose body describes this
# very hook, matched its own documentation and could never be merged through
# itself. guard-git.sh's header records the identical mistake one surface
# earlier: its first commit was denied because the message explained the flag.
is_epic() {
  local num=$1 head=$2 issue files
  # 1. It edits a tracker. Top-level docs/plan/*.md only: drafts/ hold unfiled
  #    issues and review/ holds generated HTML, and neither carries a row.
  #    Captured first rather than piped: `grep -q` closes the pipe on its first
  #    match, and `pipefail` would then read the producer's SIGPIPE as failure.
  files=$(gh_run 10 gh pr view "$num" --json files --jq '.files[].path' 2>/dev/null) || files=""
  if printf '%s\n' "$files" | grep -qE '^docs/plan/[^/]+\.md$'; then
    return 0
  fi
  # 2. Or its branch names an issue that HAS a plan doc. PR-3 (#142) — one of
  #    the three failures this hook exists for — touched no plan file at all
  #    and still owed issue #130's table a row.
  if [[ $head =~ issue-([0-9]+) ]]; then
    issue=${BASH_REMATCH[1]}
    files=$(git ls-tree --name-only origin/main docs/plan/ 2>/dev/null) || files=""
    if printf '%s\n' "$files" | grep -qE "^docs/plan/issue-${issue}-"; then
      return 0
    fi
  fi
  return 1
}

# Is the number written down?  0 = yes · 1 = genuinely absent · 2 = cannot tell.
#
# Read the PR's own branch, not the caller's working tree. Asking
# `git rev-parse --show-toplevel` answered with wherever you happened to be
# standing, so the same merge was allowed from the PR's worktree and denied from
# the main checkout. origin/main is checked too: a row that an earlier PR
# already landed is not stale.
row_present() {
  local num=$1 head=$2 ref rc searched=0
  for ref in "origin/${head}" origin/main; do
    git cat-file -e "${ref}:docs/plan" 2>/dev/null || continue
    searched=1
    # Bounded on the right. A plain "#60" also matches inside "#601410", and a
    # census of docs/plan found EIGHT numbers — #12 #13 #16 #27 #30 #34 #44 #60
    # — already "satisfied" by hex colours and longer issue numbers. #273, #303
    # and #601 would have been born satisfied too.
    git grep -qE "#${num}([^0-9]|\$)" "$ref" -- ':(glob)docs/plan/*.md' 2>/dev/null
    rc=$?
    [ $rc -eq 0 ] && return 0
    # grep says 1 for "found nothing" and 2 for "could not look". Treating them
    # the same is how `chmod 000 docs/plan` produced a DENY out of a guard whose
    # header promises to fail open, and rule 8 promises a denial is real.
    [ $rc -gt 1 ] && return 2
  done
  [ $searched -eq 1 ] || return 2
  return 1
}

for num in $(gh_pr_merge_numbers "$scan"); do
  head=$(gh_run 10 gh pr view "$num" --json headRefName --jq .headRefName 2>/dev/null) || continue
  [ -z "$head" ] && continue
  gh_run 15 git fetch -q origin main "$head" 2>/dev/null || continue   # offline — allow
  is_epic "$num" "$head" || continue
  row_present "$num" "$head"
  rc=$?
  [ $rc -eq 0 ] && continue                                            # written down — allow
  [ $rc -ne 1 ] && continue                                            # could not look — allow

  jq -nc --arg r "PR #${num} owes the epic tracker a row, and no file under docs/plan/ mentions #${num} — not on ${head}, not on main.

The owner's rule: a merge that leaves the tracker stale is an INCOMPLETE merge. It has broken three times, always the same way — the row's text written while building, the tick left for merge, the merge landing without it.

Fix before merging, on THIS branch:
  1. Put the PR number in the row, e.g. '| ✅ #${num}, <date> |', in docs/plan/issue-NN-*.md
  2. Commit it to ${head} and push. The row cannot ride a later PR — deferring the tick IS the failure mode, three times over.

This hook only checks that the number appears somewhere in docs/plan/*.md. It cannot tell a ✅ from a 👁, so it is on you to make the row true." \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  exit 0
done
exit 0
