#!/usr/bin/env bash
# Behaviour test for the `git grep …\b` enumeration warn in `guard-pr.sh` (#221).
#
# ── WHY THIS FILE EXISTS ──────────────────────────────────────────────────────
#
# `git grep -E` does not support `\b`: it returns 0 matches, exit 1 — the SAME
# answer as "this symbol appears nowhere". So an enumeration written
# `git grep -cE 'Sym\b'` reports a fiction, the `Call sites:` marker presents it
# as truth, `guard-pr.sh` passes because the marker is PRESENT, and the reviewer
# sees a clean count. guard-pr.sh now WARNS when such an enumeration reaches a PR
# body — the body-side companion to `hookify.git-grep-word-boundary`, which
# blocks the command but never sees a `--body-file` (the text is in the file, not
# the command).
#
# #213 is the reason this file exists at all: a hook is not enforcement until you
# have shown what makes it fire in the REAL hook path — a payload through the real
# script — with a POSITIVE control (the broken idiom warns) AND a NEGATIVE control
# (the correct idiom, and prose that merely names the tool, stay silent). A guard
# that also fires on the correct write is one people learn to scroll past, so the
# negative controls below include the actual prose from shipped #221 PR bodies.
#
#   .claude/hooks/tests/guard-pr-word-boundary.sh
#
# Exit 0 = pass. Exit 1 = a real finding. Needs bash, jq.
set -uo pipefail

here=$(cd -- "$(dirname -- "$0")" && pwd)
HOOK=${GUARD_PR_HOOK:-$here/../guard-pr.sh}
SETTINGS=${GUARD_PR_SETTINGS:-$here/../../settings.json}
TMP=$(mktemp -d "${TMPDIR:-/tmp}/guard-pr-word-boundary.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT

pass=0; fail=0
ok()  { pass=$((pass+1)); }
bad() { fail=$((fail+1)); printf '  FAIL  %s\n' "$1"; }

for tool in jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "SKIP: no $tool on PATH"; exit 0; }
done
[ -r "$HOOK" ] || { echo "SKIP: hook not readable at $HOOK"; exit 0; }

# Classify the hook's decision for a given full command string. The command may
# be multi-line (a real PR body is), so slurp it into ONE json string with -Rs.
kind() { # kind <command> -> silent | warn | deny | other
  local out
  out=$(printf '%s' "$1" | jq -Rsc '{tool_input:{command:.}}' | bash "$HOOK" 2>/dev/null)
  if   [ -z "$out" ];                                             then echo silent
  elif printf '%s' "$out" | grep -q '"permissionDecision":"deny"'; then echo deny
  elif printf '%s' "$out" | grep -q '"systemMessage"';            then echo warn
  else echo other
  fi
}
check() { # check <expected> <command> <label>
  local got; got=$(kind "$2")
  if [ "$got" = "$1" ]; then ok; else bad "want $1, got $got — $3"; fi
}

# A compliant PR body (all three markers present, so it REACHES the warn check)
# whose `Enumerated by:` line is the evidence under test.
compliant() { # compliant <evidence-line>
  printf "gh pr create --title t --body 'Fixes #221\n\nCall sites: 1 found, 1 changed\nReproduced: yes, before and after\nEnumerated by: %s\n'" "$1"
}

printf '=== 1. enumeration-shaped git-grep + \\b in the body -> WARN ===\n'
# The count/list forms that actually feed a `Call sites:` number.
check warn "$(compliant "git grep -cE 'Sym\b'")"                    "canonical -cE count"
check warn "$(compliant "git grep -lE 'OfflineModelFailure\b'")"    "the issue's -lE measured form"
check warn "$(compliant "git grep -Ec 'Sym\b'")"                    "flags reordered -Ec"
check warn "$(compliant "git grep -l 'Sym\b'")"                     "basic regex, -l list"

printf '\n=== 2. correct tools / idioms in the body -> SILENT (allow) ===\n'
check silent "$(compliant "grep -rE 'Sym\b' --include='*.kt' core")"  "POSIX grep -rE honours \\b"
check silent "$(compliant "git grep -wE 'Sym'")"                      "-w whole word, no \\b"
check silent "$(compliant "git grep -E '(^|[^A-Za-z0-9_])Sym([^A-Za-z0-9_]|\$)'")" "spelled-out boundary, no \\b"
check silent "$(compliant "git grep -c 'Sym'")"                       "count with no \\b"

printf '\n=== 3. real prose from shipped #221 PR bodies -> SILENT (no crying wolf) ===\n'
# Verbatim shapes found across 8 of 142 merged PR bodies (Search A, #221 fix).
check silent "$(compliant "grep -rE throughout — never git grep -E, which has no \b and answers no match with exit 1 (#221)")" "never git grep -E prose"
check silent "$(compliant "a real methodology trap: git grep -E does not support \b")" "does-not-support prose"
check silent "$(compliant "git grep -E 'has-l\b'")"                   "-E first; a '-l' inside the search term must not count"

printf '\n=== 4. the COMPLEMENTARY path — a --body-file the command block never sees ===\n'
bf="$TMP/body.md"
cat > "$bf" <<'BODY'
Fixes #221

Call sites: 1 found, 1 changed
Reproduced: yes
Enumerated by: git grep -cE 'Sym\b'
BODY
check warn "gh pr create --title t --body-file $bf"                   "broken enumeration in a body FILE warns"
cat > "$bf" <<'BODY'
Fixes #221

Call sites: 1 found, 1 changed
Reproduced: yes
Enumerated by: grep -rE 'Sym\b' --include='*.kt' core
BODY
check silent "gh pr create --title t --body-file $bf"                 "correct idiom in a body FILE stays silent"

printf '\n=== 5. the existing deny is preserved (a missing marker still blocks) ===\n'
# A broken enumeration is present, but a marker is missing: the DENY must win, and
# the warn must not mask it.
check deny "gh pr create --title t --body 'Reproduced: yes
Enumerated by: git grep -cE '\''Sym\b'\''
'"                                                                    "missing Call sites: -> deny"
check deny "gh pr create --title t --body 'Call sites: 1 found, 1 changed
Reproduced: yes
'"                                                                    "missing Enumerated by: -> deny"

printf '\n=== 6. FAIL OPEN — the same contract as every guard here ===\n'
for probe in '{"tool_input":{"comma' '{"tool_input":{}}' '{}'; do
  out=$(printf '%s' "$probe" | bash "$HOOK" 2>/dev/null); rc=$?
  { [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open on payload: $probe (rc=$rc)"
done
# A non-gh command is none of this hook's business.
check silent "git status"                                            "non-gh command"
# A body whose text is a variable/substitution is unreadable — the hook skips it
# rather than judging text it does not have, so even a broken-looking var name is
# silent.
check silent 'gh pr create --title t --body "$BODY"'                 "unreadable --body \"\$VAR\" is skipped"

printf '\n=== 7. THE WIRING — settings.json must still route gh to guard-pr.sh ===\n'
if [ -r "$SETTINGS" ]; then
  if grep -q 'guard-pr.sh' "$SETTINGS" && grep -q '"if": "Bash(\*gh \*)"' "$SETTINGS"; then ok
  else bad "settings.json no longer gates guard-pr.sh on Bash(*gh *) — this warn never runs"
  fi
else
  printf '        n/a  settings.json not readable at %s\n' "$SETTINGS"
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
