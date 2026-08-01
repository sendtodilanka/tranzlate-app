#!/usr/bin/env bash
# PreToolUse/Bash guard for `gh pr create`.
#
# Exists because the owner asked why my code introduces bugs faster than it
# fixes them, and the honest answer was four habits — not carelessness, four
# specific skips. Two of them are checkable from the PR body itself:
#
#   - I changed 2 of 6 call sites (#146) and fixed 5 of 9 (#150), both one
#     `grep -c` away. So a PR must state how many it found and how many it
#     changed.
#   - I "fixed" #149 without ever reproducing the harm it described, and the
#     harm survived. So a PR must state the reproduction, before and after.
#
# A promise to be careful is the same shape of non-fix as the checklist that
# was already written when `main` broke the second time. This is the gate.
#
# Design rules, inherited from guard-git.sh:
#  - FAIL OPEN. No body, no jq, an unparseable command → allow. Only a
#    confirmed violation blocks.
#  - Deny with the fix, not just the complaint.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

printf '%s' "$cmd" | grep -qE '(^|[;&|][[:space:]]*)gh[[:space:]]+pr[[:space:]]+create' || exit 0

# The body is usually a heredoc or a $(cat <<EOF …). Scan the WHOLE command:
# unlike guard-git.sh, here the payload IS what we need to inspect.
missing=""
printf '%s' "$cmd" | grep -q 'Call sites:' || missing="Call sites:"
if ! printf '%s' "$cmd" | grep -q 'Reproduced:'; then
  missing="${missing:+$missing and }Reproduced:"
fi
[ -z "$missing" ] && exit 0

# No body at all → the author is opening a PR some other way; not our business.
printf '%s' "$cmd" | grep -qE '\-\-body|\-\-body-file' || exit 0

jq -nc --arg r "PR body is missing: ${missing}

Issue #161: five of my PRs in one session shipped a defect a lens then caught, and two causes are checkable right here.

  Call sites: N found, N changed   (or 'none' — say so explicitly)
      #146 converted 2 of 6. #150 fixed 5 of 9. Both were one grep -c away.

  Reproduced: <the harm, before and after>   (or 'n/a — <why>')
      #149 was 'fixed' without ever running the harm it described, and the harm survived.

Run the grep and the repro, then put the numbers in the body. If neither applies, say so in those words — an explicit 'none' and 'n/a' both pass." \
  '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
