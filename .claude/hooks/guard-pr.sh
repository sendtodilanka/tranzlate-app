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

printf '%s' "$cmd" | grep -qE '(^|[;&|][[:space:]]*)([^[:space:];&|]*/)?gh[[:space:]]+pr[[:space:]]+create' || exit 0

# No body at all → the author is opening a PR some other way; not our business.
# This runs FIRST now (#178). It used to sit after the marker scan, so an
# invocation the hook could not read reached the deny instead of the fail-open.
printf '%s' "$cmd" | grep -qE '\-\-body|\-\-body-file' || exit 0

# WHERE THE BODY TEXT IS. The hook sees the command BEFORE the shell expands
# it, so only some forms are readable at all:
#
#   --body "$(cat <<'EOF' … EOF)"   readable — the heredoc text is in $cmd
#   --body 'literal text'          readable — likewise
#   --body-file body.md            readable — if we open the file
#   --body "$(cat body.md)"        NOT readable — resolved at run time
#   --body "$BODY"                 NOT readable — a variable we do not have
#
# #178: the last two used to be DENIED. That contradicted this hook's own
# fail-open contract and rejected PR bodies that were in fact compliant.
body=$cmd

bf=$(printf '%s' "$cmd" | sed -n "s/.*--body-file[= ]\{1,\}['\"]\{0,1\}\([^ '\"]\{1,\}\).*/\1/p" | head -1)
if [ -n "$bf" ]; then
  # Unreadable or missing → allow. The hook cannot know what it says.
  [ -r "$bf" ] || exit 0
  body=$(cat -- "$bf" 2>/dev/null) || exit 0
elif ! printf '%s' "$cmd" | grep -q '<<'; then
  # No heredoc. If --body's argument STARTS with a substitution or a variable,
  # the text is not in $cmd and we must not judge it. Deliberately narrow: a
  # literal body containing $( inside a fenced code block is still scanned,
  # which is most of this repo's PR bodies.
  printf '%s' "$cmd" | grep -qE "\-\-body[= ]+[\"']?[\$\`]" && exit 0
fi

missing=""
printf '%s' "$body" | grep -q 'Call sites:' || missing="Call sites:"
if ! printf '%s' "$body" | grep -q 'Reproduced:'; then
  missing="${missing:+$missing and }Reproduced:"
fi
# #206. `Call sites: N found, N changed` says a number; it says nothing about
# how the number was reached. #171 shipped `4 found, 4 changed` and was wrong,
# because the grep was written from a phrase I already remembered and could
# only find what I already knew — CONTRIBUTING.md said "Sinhala prose ≥70%"
# and the pattern was `70% Sinhala|Sinhala script`. The same shape produced
# three more errors in one session. The fix is not a better grep; it is TWO
# searches that could not both miss the same thing.
if ! printf '%s' "$body" | grep -q 'Enumerated by:'; then
  missing="${missing:+$missing and }Enumerated by:"
fi
[ -z "$missing" ] && exit 0

jq -nc --arg r "PR body is missing: ${missing}

Issue #161: five of my PRs in one session shipped a defect a lens then caught, and two causes are checkable right here.

  Call sites: N found, N changed   (or 'none' — say so explicitly)
      #146 converted 2 of 6. #150 fixed 5 of 9. Both were one grep -c away.

  Reproduced: <the harm, before and after>   (or 'n/a — <why>')
      #149 was 'fixed' without ever running the harm it described, and the harm survived.

  Enumerated by: <search A> and <search B>   (or 'none — <why>')
      #171 said 'Call sites: 4 found, 4 changed' and was wrong: one grep, written
      from the phrase I remembered, found only what I already knew. Name two
      searches that could not both miss the same thing — by concept and by symbol,
      by name and by call site, a grep and a compiler error.

Run the grep and the repro, then put the numbers in the body. If neither applies, say so in those words — an explicit 'none' and 'n/a' both pass." \
  '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
