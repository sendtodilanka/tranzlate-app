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
# The check is deliberately weak: the plan docs must MENTION the PR number. It
# cannot tell ✅ from 👁, and it is not trying to — it catches the failure that
# actually happens, which is a row that was never touched at all.
#
# Design rules, inherited from guard-git.sh:
#  - FAIL OPEN. No gh, no docs dir, no PR number, an unparseable command → allow.
#  - Deny with the fix, not just the complaint.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0
scan=${cmd%%<<*}

printf '%s' "$scan" | grep -qE '(^|[;&|][[:space:]]*)gh[[:space:]]+pr[[:space:]]+merge' || exit 0
num=$(printf '%s' "$scan" | grep -oE 'gh[[:space:]]+pr[[:space:]]+merge[[:space:]]+[0-9]+' | grep -oE '[0-9]+$' || true)
[ -z "$num" ] && exit 0

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -d "$root/docs/plan" ] || exit 0

# Only epic PRs carry tracker rows. A PR that touches no plan doc and is not
# part of an epic is not this hook's business — ask the PR itself.
body=$(timeout 15 gh pr view "$num" --json body,title --jq '.title + " " + .body' 2>/dev/null) || exit 0
printf '%s' "$body" | grep -qE '#130|PR-[0-9]+|epic|tracker' || exit 0

grep -rqF "#${num}" "$root/docs/plan" 2>/dev/null && exit 0

jq -nc --arg n "$num" --arg r "PR #${num} looks like epic work, but no file under docs/plan/ mentions #${num}.

The owner's rule: a merge that leaves the tracker stale is an INCOMPLETE merge. It has broken three times, always the same way — the row's text written while building, the tick left for merge, the merge landing without it.

Fix before merging:
  1. Set the row's status in docs/plan/issue-NN-*.md to the PR number, e.g. '| ✅ #${num}, <date> |'
  2. If the tracker lives on the branch you are merging, the row goes in THAT PR
  3. If it does not, the row rides the next PR — and then this hook will pass because the number is already written down

This hook only checks that the number appears. It cannot tell a ✅ from a 👁, so it is on you to make the row true." \
  '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
