#!/usr/bin/env bash
# PreToolUse/Bash advisory for `git checkout -- <path>` and `git restore <path>`.
#
# Exists because the same command destroyed uncommitted work twice in one day:
#
#   - #189: a mutation harness reverted with `git checkout --`, throwing away the
#     whole uncommitted fix along with the mutation, and two runs then read stale
#     result XML before it was noticed.
#   - #198: the identical mistake, same command, same cause, hours later.
#
# Both times the file held BOTH the mutation and work that was not yet
# committed, and `git checkout --` cannot tell them apart — it restores to HEAD,
# which is exactly what it promises. The tool is not wrong; reaching for it is.
# `cp` a backup, mutate, `cp` it back.
#
# #207's lens pointed out that calling this shape "no honest mechanical form"
# was inconsistent with shipping `Enumerated by:` for a much fuzzier one. It was
# right: this is a narrow, exact-repeat pattern — same command, same harm — and
# it is at least as checkable.
#
# WHY IT WARNS AND DOES NOT DENY: `git checkout -- <path>` is the correct tool
# when the file has no uncommitted work worth keeping, which is most of the
# time. A deny would block correct use, and a guard that blocks correct use gets
# switched off. The warning fires only when there is something to lose.
#
# FAIL OPEN, like every guard here: not a restore, no such path, not a git repo,
# no `jq`, an unparseable command → silent. A message is always a real finding.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

# `git checkout -- <path>` / `git checkout <path>` after `--`, or `git restore
# <path>`. `--staged`/`--source` are different operations and are left alone.
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+)*[[:space:]]+(checkout|restore)([[:space:]]|$)' || exit 0
printf '%s' "$cmd" | grep -qE '\-\-staged|\-\-source' && exit 0

# Only the file-restoring forms. `git checkout <branch>` moves HEAD and is not
# this hook's business; the `--` separator is what marks a pathspec.
paths=$(printf '%s' "$cmd" | sed -n 's/.*--[[:space:]]\{1,\}\(.*\)/\1/p' | tr -s ' ' '\n' | grep -v '^$' | head -20)
if [ -z "$paths" ]; then
  printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+restore[[:space:]]' || exit 0
  paths=$(printf '%s' "$cmd" | sed -n 's/.*git[[:space:]]\{1,\}restore[[:space:]]\{1,\}\(.*\)/\1/p' | tr -s ' ' '\n' | grep -v '^$' | grep -v '^-' | head -20)
fi
[ -n "$paths" ] || exit 0

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

dirty=""
while IFS= read -r path; do
  [ -n "$path" ] || continue
  # Anything git reports as modified/added/deleted here is about to be lost.
  st=$(git status --porcelain -- "$path" 2>/dev/null | head -5) || continue
  [ -n "$st" ] && dirty="${dirty}${dirty:+
}  ${path}"
done <<EOF
$paths
EOF

[ -n "$dirty" ] || exit 0

jq -nc --arg m "About to discard UNCOMMITTED changes in:
${dirty}

\`git checkout --\` / \`git restore\` restore to HEAD. If this file holds a
mutation AND work you have not committed, both go — the command cannot tell them
apart, and it will not ask.

That is exactly what happened twice in one day (#189, #198): the uncommitted fix
went with the mutation, and two runs afterwards read stale results before anyone
noticed.

If you are reverting a mutation: \`cp\` a backup first and \`cp\` it back.
If you meant to discard this work: carry on, this is the right command." \
  '{systemMessage:$m}'
exit 0
