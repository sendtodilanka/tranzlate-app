#!/usr/bin/env bash
# Invariant test for `guard-restore.sh` + `hookify.destructive-git-restore`.
#
# ── WHY THIS FILE EXISTS ──────────────────────────────────────────────────────
#
# `guard-restore.sh` stands down for commands the hookify rule BLOCKS. To decide
# that, it re-implements — in bash — a judgement whose authority is a Python
# regex in another file. **A hand-written approximation of another mechanism's
# regex drifts from it**, and #257 found that drift four separate times, each in
# a new place, each round of "find a case, patch it" producing one more:
#
#   1. `git --work-tree=<d> --git-dir=<d>/.git checkout -- <path>`   (round 1)
#   2. `git checkout --conflict merge -- <path>`                     (round 2)
#   3. `git checkout HEAD -- <path>`                                 (round 2)
#   4. `git checkout - -- <path>`                                    (round 3)
#
# All four are the same sentence: **the hook said "hookify has this" and hookify
# did not.** A deferral into a void. A fifth round of patching would not have
# been evidence of anything, so this file replaces the patching with a check.
#
# ── WHAT IT ASSERTS, AND WHY THIS FORM ────────────────────────────────────────
#
#   For every command in the corpus: if running it for real DESTROYS uncommitted
#   work, then at least one of the two mechanisms must catch it.
#
# The assertion is **empirical, not textual**. It runs each command against a
# throwaway repo and looks at whether the file changed. That is deliberate: a
# test that compared my reading of hookify's regex against my reading of the
# hook would share the misreading that caused all four instances. Here, git
# itself decides what is destructive, and the two mechanisms are merely asked.
#
# The corpus is **derived from hookify's own pattern**, read out of the rule file
# at run time and never copied — copying it would just create a third
# approximation to drift. The token shapes come from taking that pattern's
# pre-`--` character class apart and generating its boundaries, which is how the
# bare `-` appears here without anyone having thought of it.
#
# ── RUNNING ───────────────────────────────────────────────────────────────────
#
#   .claude/hooks/tests/guard-restore-invariant.sh            # invariant + table
#   .claude/hooks/tests/guard-restore-invariant.sh --mutate   # + mutation check
#   GUARD_RESTORE_RULE=<path> …                               # rule file elsewhere
#
# Exit 0 = pass. Exit 1 = a real finding. Needs bash, git, python3, jq.
set -uo pipefail

here=$(cd -- "$(dirname -- "$0")" && pwd)
HOOK=${GUARD_RESTORE_HOOK:-$here/../guard-restore.sh}
RULE=${GUARD_RESTORE_RULE:-$here/../../hookify.destructive-git-restore.local.md}
TMP=$(mktemp -d "${TMPDIR:-/tmp}/guard-restore-invariant.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT

pass=0; fail=0; skip=0
ok()   { pass=$((pass+1)); }
bad()  { fail=$((fail+1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '        %s\n' "$1"; }

for tool in git python3 jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "SKIP: no $tool on PATH"; exit 0; }
done
[ -r "$HOOK" ] || { echo "SKIP: hook not readable at $HOOK"; exit 0; }

# ── the fixture ───────────────────────────────────────────────────────────────
# A throwaway repo. Destructive commands are run HERE and nowhere else.
REPO="$TMP/repo"
mkdir -p "$REPO"
(
  cd "$REPO" || exit 1
  git init -q .
  git config user.email test@example.invalid
  git config user.name  guard-restore-test
  printf 'COMMITTED\n' > dirty.txt
  printf 'COMMITTED\n' > clean.txt
  # `collide` is a tracked file AND a branch name. That collision is the only
  # thing that can tell "ask git whether this name is a branch" apart from "never
  # ask" and from "ask about every argument" — see the two rows in the table
  # below. The mutation mode found both missing when this file was first written,
  # because the fixture could not express the difference.
  printf 'COMMITTED\n' > collide
  git add dirty.txt clean.txt collide
  git commit -qm "fixture"
  git commit -qm "second" --allow-empty
  git branch collide
  # A PREVIOUS branch must exist, or `git checkout -` cannot resolve and the
  # bare-dash case quietly becomes harmless — the fixture would then pass by
  # failing to reproduce the harm, which is the failure this whole file is
  # about. Found exactly that way: sections 1 and 2 stayed silent on the bare
  # dash until this repo could actually be hurt by it.
  git branch sidebranch
  git checkout -q sidebranch
  git checkout -q -
) >/dev/null 2>&1 || { echo "SKIP: could not build the fixture repo"; exit 0; }
# Prove the fixture can express the harm, rather than assuming it.
( cd "$REPO" && git rev-parse --verify --quiet '@{-1}' >/dev/null ) \
  || { echo "SKIP: fixture has no previous branch, so `git checkout -` cannot be tested"; exit 0; }

reset_fixture() {
  # Restore the fixture WITHOUT a restore command — rewriting the files and
  # re-pointing HEAD is enough, and this file must not model the very habit
  # `guard-restore.sh` exists to discourage.
  ( cd "$REPO" && git symbolic-ref HEAD refs/heads/"$(git rev-parse --abbrev-ref HEAD 2>/dev/null | head -1)" ) >/dev/null 2>&1
  ( cd "$REPO" && git symbolic-ref HEAD "$FIXTURE_HEAD" ) >/dev/null 2>&1
  printf 'UNCOMMITTED-WORK\n' > "$REPO/dirty.txt"
  printf 'COMMITTED\n'        > "$REPO/clean.txt"
  printf 'UNCOMMITTED-WORK\n' > "$REPO/collide"
}
FIXTURE_HEAD=$(cd "$REPO" && git symbolic-ref HEAD)

hook_warns() { # hook_warns <command> -> 0 if it warned
  local out
  out=$(cd "$REPO" && printf '%s' "$1" | jq -Rc '{tool_input:{command:.}}' | bash "$HOOK" 2>/dev/null)
  [ -n "$out" ]
}

# ── hookify's own pattern, read from the rule file, never copied ──────────────
RULE_PRESENT=no
if [ -r "$RULE" ]; then
  RULE_PRESENT=yes
  python3 - "$RULE" > "$TMP/pattern" <<'PY' || RULE_PRESENT=no
import re, sys
txt = open(sys.argv[1], encoding="utf-8").read()
# The rule has two `pattern:` keys. Take the one paired with regex_match.
m = re.search(r"operator:\s*regex_match\s*\n\s*pattern:\s*(.+)", txt)
if not m:
    sys.exit("no regex_match pattern found")
print(m.group(1).rstrip())
PY
fi

if [ "$RULE_PRESENT" != yes ]; then
  cat <<EOF
SKIPPED — the invariant needs BOTH mechanisms and only one is present.

  looked for: $RULE

The hookify rules are not tracked on main until #233 lands; until then this hook
is the only mechanism, so "did the OTHER one catch it" has no answer. Point
GUARD_RESTORE_RULE at the rule file to run this against it.
EOF
  skip=1
fi

hookify_blocks() { # hookify_blocks <command> -> 0 if the rule would block
  [ "$RULE_PRESENT" = yes ] || return 1
  python3 - "$(cat "$TMP/pattern")" "$1" <<'PY'
import re, sys
pat, cmd = sys.argv[1], sys.argv[2]
# hookify's engine compiles with re.IGNORECASE unconditionally, and the rule's
# second condition is `not_contains: --staged`.
sys.exit(0 if (re.search(pat, cmd, re.IGNORECASE) and "--staged" not in cmd) else 1)
PY
}

# ── the corpus, generated from the pattern's own pre-`--` token class ─────────
gen_tokens() {
  python3 - "$(cat "$TMP/pattern" 2>/dev/null || echo '')" <<'PY'
import re, sys
pat = sys.argv[1]
# Pull out the group hookify allows between the verb and `--`, e.g.
# `(\s+-[^\s;&|]+)*` -> token class `-[^\s;&|]+`. Derived, not transcribed: if
# the rule is rewritten, this follows it or the test says it cannot parse it.
m = re.search(r"checkout\((.+?)\)\*", pat)
toks = []
if m:
    inner = re.sub(r"^\\s\+", "", m.group(1))          # strip the leading \s+
    lit  = re.match(r"([^\[\\]*)", inner).group(1)      # literal prefix, e.g. "-"
    cls  = re.search(r"\[\^([^\]]*)\]", inner)          # negated set, e.g. \s;&|
    quant= inner[-1] if inner and inner[-1] in "+*?" else ""
    excl = cls.group(1) if cls else ""
    # Boundaries of that class, which is where a bash approximation drifts.
    toks.append(lit)                                   # `-`  : fails `+`, passes `-*`
    toks.append(lit + "f")                             # `-f` : the ordinary case
    toks.append(lit + "-ours")
    toks.append(lit + "-conflict=merge")
    if ";" in excl: toks.append(lit + ";x")            # an EXCLUDED character
    if "&" in excl: toks.append(lit + "&x")
    toks.append(lit * 2)                               # `--` : the separator itself
# Non-flag shapes: hookify's group cannot span them at all.
toks += ["HEAD", "main~1", "@", "merge"]
for t in toks:
    print(t)
PY
}

printf '=== 1. INVARIANT — anything that destroys work is caught by SOMETHING ===\n'
if [ "$RULE_PRESENT" = yes ]; then
  prefixes=( "" "-C $REPO " "--work-tree=$REPO --git-dir=$REPO/.git " "-C $REPO -C $REPO " )
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    case "$tok" in
      *[\ \;\&\|]*)
        note "n/a  token [$tok] carries a shell separator; the shell splits the command, so it is not one git invocation"
        continue ;;
    esac
    for pfx in "${prefixes[@]}"; do
      for shape in "checkout $tok -- dirty.txt" "checkout $tok dirty.txt"; do
        cmd="git ${pfx}${shape}"
        reset_fixture
        before=$(cat "$REPO/dirty.txt")
        ( cd "$REPO" && eval "$cmd" ) >/dev/null 2>&1
        after=$(cat "$REPO/dirty.txt")
        reset_fixture
        [ "$before" = "$after" ] && continue          # harmless: nothing to catch
        if hookify_blocks "$cmd" || hook_warns "$cmd"; then
          ok
        else
          bad "DESTROYS WORK, CAUGHT BY NEITHER: $cmd"
          hookify_blocks "$cmd" && note "hookify: blocks" || note "hookify: silent"
          hook_warns    "$cmd" && note "hook:    warns"  || note "hook:    silent"
        fi
      done
    done
  done < <(gen_tokens)
fi

printf '\n=== 2. NO DEFERRAL INTO A VOID — the hook is silent only if hookify blocks ===\n'
if [ "$RULE_PRESENT" = yes ]; then
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    case "$tok" in *[\ \;\&\|]*) continue ;; esac
    cmd="git checkout $tok -- dirty.txt"
    reset_fixture
    before=$(cat "$REPO/dirty.txt")
    ( cd "$REPO" && eval "$cmd" ) >/dev/null 2>&1
    after=$(cat "$REPO/dirty.txt")
    reset_fixture
    [ "$before" = "$after" ] && continue
    if hook_warns "$cmd"; then ok
    elif hookify_blocks "$cmd"; then ok
    else bad "deferral into a void: $cmd"; fi
  done < <(gen_tokens)
fi

printf '\n=== 3. REGRESSION TABLE — every form #222/#223/#257 has ever turned up ===\n'
# expectation | command      (WARN = this hook must warn; SILENT = it must not)
while IFS='|' read -r want cmd; do
  case "$want" in ''|'#'*) continue ;; esac
  want=${want// /}; cmd=${cmd/#" "/}
  cmd=${cmd//@REPO@/$REPO}
  reset_fixture
  if hook_warns "$cmd"; then got=WARN; else got=SILENT; fi
  if [ "$got" = "$want" ]; then ok; else bad "want $want, got $got: $cmd"; fi
done <<'TABLE'
# --- git's own documented idiom: checkout with no --, man git-checkout EXAMPLES 1
WARN   | git checkout dirty.txt
WARN   | git checkout HEAD~1 dirty.txt
WARN   | git checkout .
WARN   | git checkout -p dirty.txt
WARN   | git checkout --ours dirty.txt
WARN   | git -C @REPO@ checkout dirty.txt
WARN   | git --git-dir=@REPO@/.git --work-tree=@REPO@ checkout dirty.txt
SILENT | git checkout clean.txt
SILENT | git checkout main
SILENT | git checkout -b newbranch
SILENT | git checkout --detach HEAD
SILENT | git checkout nonexistent-name
# --- ARGUMENT DISAMBIGUATION: only git can say whether a name is a branch.
# `collide` is BOTH a branch and a dirty tracked file. One argument -> git takes
# the tree-ish and the file survives, so warning would be a false positive. Two
# arguments -> the second is a pathspec whatever else it names, and the file is
# destroyed with "Updated 1 path" and exit 0.
SILENT | git checkout collide
WARN   | git checkout sidebranch collide
# --- the four drift instances, all of which hookify cannot match
WARN   | git --work-tree=@REPO@ --git-dir=@REPO@/.git checkout -- dirty.txt
WARN   | git --git-dir=@REPO@/.git --work-tree=@REPO@ checkout -- dirty.txt
WARN   | git checkout --conflict merge -- dirty.txt
WARN   | git checkout HEAD -- dirty.txt
WARN   | git checkout main~1 -- dirty.txt
WARN   | git checkout - -- dirty.txt
WARN   | git -C @REPO@ -C @REPO@ checkout -- dirty.txt
WARN   | git --work-tree=@REPO@ restore dirty.txt
SILENT | git --work-tree=@REPO@ checkout -- clean.txt
# --- forms hookify DOES match: this hook must stand down
SILENT | git checkout -- dirty.txt
SILENT | git -C @REPO@ checkout -- dirty.txt
SILENT | git checkout -f -- dirty.txt
SILENT | git restore dirty.txt
SILENT | git restore .
SILENT | git restore --source=HEAD~1 dirty.txt
SILENT | git restore -s@ -SW dirty.txt
# --- index-only: harmless, must never warn anywhere
SILENT | git restore --staged dirty.txt
SILENT | git --work-tree=@REPO@ restore --staged dirty.txt
SILENT | git --work-tree=@REPO@ restore -S dirty.txt
# --- the hole hookify's substring exemption opens, delegated here by its own text
WARN   | git restore --source=HEAD --staged --worktree dirty.txt
WARN   | git restore --staged -W dirty.txt
WARN   | git --work-tree=@REPO@ restore -SW dirty.txt
# --- not a restore at all
SILENT | git status
SILENT | git switch main
SILENT | git commit -m "note about checkout -- x"
SILENT | echo hi
TABLE

printf '\n=== 4. FAIL OPEN ===\n'
for probe in '{"tool_input":{"comma' '{"tool_input":{}}' '{}'; do
  out=$(cd "$REPO" && printf '%s' "$probe" | bash "$HOOK" 2>/dev/null); rc=$?
  if [ -z "$out" ] && [ "$rc" -eq 0 ]; then ok; else bad "not fail-open on payload: $probe (rc=$rc)"; fi
done
out=$(cd "$TMP" && printf 'git checkout dirty.txt' | jq -Rc '{tool_input:{command:.}}' | bash "$HOOK" 2>/dev/null); rc=$?
{ [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open outside a work tree (rc=$rc)"
out=$(cd "$REPO" && printf 'git checkout dirty.txt' | jq -Rc '{tool_input:{command:.}}' | PATH=/nonexistent /bin/bash "$HOOK" 2>/dev/null); rc=$?
{ [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open with an empty PATH (rc=$rc)"

# ── mutation mode ────────────────────────────────────────────────────────────
if [ "${1:-}" = "--mutate" ]; then
  printf '\n=== 5. MUTATIONS — does this file actually go red? ===\n'
  mut="$TMP/mutant.sh"
  # label | perl expression applied to a COPY of the hook
  while IFS='|' read -r label expr; do
    case "$label" in ''|'#'*) continue ;; esac
    label=${label// /}
    cp "$HOOK" "$mut"
    perl -0pi -e "$expr" "$mut" 2>/dev/null
    if cmp -s "$HOOK" "$mut"; then
      bad "MUTATION [$label] did not apply — the mutant is identical to the hook"
      note "a mutation run that cannot tell 'not killed' from 'not applied' grades itself"
      continue
    fi
    before=$fail
    GUARD_RESTORE_HOOK=$mut "$0" >/dev/null 2>&1 && caught=no || caught=yes
    fail=$before
    if [ "$caught" = yes ]; then ok; else bad "MUTATION [$label] SURVIVED — this file does not test it"; fi
  done <<'MUTATIONS'
defer-on-bare-dashdash | s/\[ "\$hookify_sees" = yes \] && \[ "\$predash_all_flags" = yes \] && continue/continue/
drop-global-prefix-check | s/\[ "\$hookify_sees" = yes \] && \[ "\$predash_all_flags" = yes \] && continue/[ "\$predash_all_flags" = yes ] \&\& continue/
restore-ignores-hookify | s/\[ "\$hookify_sees" = yes \] && \[ "\$staged_literal" = no \] && continue/[ "\$staged_literal" = no ] \&\& continue/
drop-C-consumption | s/^      -C\).*\n//m
rev-check-every-candidate | s/cands=\( \$\{cands\+"\$\{cands\[\@\]:1\}"\} \)/keep=(); for c in "\${cands[\@]}"; do "\${probe[\@]}" rev-parse --verify --quiet "\$c^{commit}" >\/dev\/null 2>\&1 || keep+=( "\$c" ); done; cands=( \${keep+"\${keep[\@]}"} )/
never-ask-git | s/if "\$\{probe\[\@\]\}" rev-parse --verify --quiet "\$\{cands\[0\]\}\^\{commit\}" >\/dev\/null 2>&1; then/if false; then/
MUTATIONS
fi

printf '\n%d passed, %d failed' "$pass" "$fail"
[ "$skip" -eq 1 ] && printf ', invariant sections SKIPPED (no hookify rule file)'
printf '\n'
[ "$fail" -eq 0 ]
