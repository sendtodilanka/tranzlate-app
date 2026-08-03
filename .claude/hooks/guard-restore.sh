#!/usr/bin/env bash
# PreToolUse/Bash advisory for the restore forms a REGEX CANNOT CLASSIFY.
#
# ── SCOPE, and why it is this narrow (#222) ────────────────────────────────────
#
# `hookify.destructive-git-restore.local.md` is AUTHORITATIVE for every form a
# regex can decide, and it BLOCKS: `git checkout … -- <path>`, `git restore
# <path>`, and both behind `git -C <dir>`. Until 2026-08-03 this hook WARNED on
# those same forms, so for every command both saw, the block fired first and this
# hook's dirtiness check never mattered — a precise mechanism replaced by a blunt
# one, with both still running. That redundancy is what the owner named.
#
# **The deferral rule, stated once: this hook stands down only for commands the
# hookify rule can actually REACH.** Its pattern is
#
#     (^|[;&|]|\s)git\s+(-C\s+\S+\s+)?(checkout(\s+-[^\s;&|]+)*\s+--(\s|$)|restore(\s|$))
#
# so its global-option prefix knows `-C <dir>` and nothing else, and the flags it
# tolerates before `--` must each be one dash-token with no space in it. Deferring
# on the presence of `--` ALONE — which is what the first draft of this scoping
# did — dropped three real forms into the gap between the two mechanisms:
# `git --work-tree=<d> --git-dir=<d>/.git checkout -- <path>`,
# `git --git-dir=… --work-tree=… checkout -- <path>` and
# `git checkout --conflict merge -- <path>`. The single-regex hook this replaced
# caught all three by accident; the two-part system caught none, and said nothing
# about it. Found by the #257 co-verify lens, which named the first; the other two
# came out of reproducing it. **A regression introduced by a fix must not be
# silent**, so `hookify_sees` below is checked before any deferral.
#
# What is left here is therefore what a regex cannot answer and git can, plus
# whatever that regex cannot reach:
#
#   A. `git checkout` with NO `--` separator.
#      `man git-checkout` ARGUMENT DISAMBIGUATION: `git checkout abc` is a BRANCH
#      switch if `abc` resolves as a tree-ish, and a file restore if it does not.
#      A regex cannot tell those apart, and blocking both would block branch
#      switches and get the rule turned off — so the hookify rule deliberately
#      declines this family. It is git's OWN documented idiom (EXAMPLES §1:
#      `rm -f hello.c` / `git checkout hello.c`), i.e. the form most likely to be
#      typed, and before #222 it was guarded by nothing at all.
#
#   B. `git restore` that writes the working tree and that hookify cannot see —
#      either because it carries the literal `--staged` (hookify's exemption is a
#      plain SUBSTRING, so `--staged --worktree` slips its net; its own text says
#      this hook is the right place to catch it) or because a global option other
#      than `-C` put it outside the pattern. `man git-restore` OPTIONS: "If
#      neither option is specified, by default the working tree is restored…
#      Specifying both restores both." Short clusters (`-SW`, `-s@ -SW`) carry no
#      literal `--staged`, so hookify still blocks those and they are NOT handled
#      here. `--staged`/`-S` WITHOUT a worktree flag touches only the index and is
#      never warned about, by either question.
#
# Everything else is deliberately silent. If you are here because a restore was
# not warned about, check whether it was BLOCKED instead — that is the design.
#
# ── WHY IT EXISTS AT ALL ──────────────────────────────────────────────────────
#
# The same command destroyed uncommitted work three times:
#   - #189: a mutation harness reverted with `git checkout --`, throwing away the
#     whole uncommitted fix along with the mutation; two runs then read stale
#     result XML before it was noticed.
#   - #198: the identical mistake, same command, hours later.
#   - #207: a third time, WHILE this hook was being tested — a probe appended to
#     CLAUDE.md was cleaned up that way, and three edits to the rule being
#     written went with it.
# The file held BOTH the mutation and work not yet committed, and the command
# cannot tell them apart: it restores to HEAD, which is exactly what it promises.
# The tool is not wrong; reaching for it is. `cp` a backup, mutate, `cp` it back.
#
# ── WHY IT WARNS AND DOES NOT DENY ────────────────────────────────────────────
#
# `git checkout <name>` is overwhelmingly a branch switch. A deny would block
# correct use, and a guard that blocks correct use gets switched off. The warning
# fires only when git itself reports the named path as having something to lose.
#
# ── FAIL OPEN, like every guard here ──────────────────────────────────────────
#
# Enumerated from the code, not from memory — every early exit before a message:
# no `jq` or an unreadable payload, an empty command, a segment whose command is
# not `git`, a git verb that is not checkout/restore, a `--` present (hookify's),
# paths supplied via `--pathspec-from-file` so they are not in the command at
# all, a `restore` without BOTH `--staged` and a worktree flag, no candidate
# paths, not inside a work tree, and nothing git reports as dirty. A message is
# therefore always a real finding.
#
# KNOWN LIMITS, named here rather than discovered later:
#   - Tokenisation splits on unquoted whitespace and on `;`, `&`, `|`. A path
#     containing any of those inside quotes is missed — silent, never a false
#     warning. `--pathspec-from-file` is missed for the same reason: the paths
#     live in a file, not in the command.
#   - A git alias (`git co`) is not resolved; the verb must be spelled out.
#   - `cd /elsewhere && git checkout <path>` is checked against the hook's own
#     working directory, not the one the command will run in.
#   - **The hook sees the command BEFORE the shell expands it.** `git -C "$W"
#     checkout <path>` arrives with `"$W"` literal, so the directory does not
#     exist as far as this hook is concerned and it goes quiet. Found while
#     proving the fix live: the first two probes used a shell variable and
#     produced no warning, which looked exactly like the fix not working. Silent
#     and never a false warning, but worth knowing before concluding from one
#     quiet run that a guard is broken — or that it is fine.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

dirty=""
seen=0

# Split the command on shell separators, so `cd x && git checkout y` is examined
# and `git commit -m "checkout -- y"` is not mistaken for one.
while IFS= read -r seg; do
  [ -n "$seg" ] || continue

  set -f                     # no pathname expansion while word-splitting
  # shellcheck disable=SC2206
  toks=( $seg )
  set +f
  n=${#toks[@]}
  [ "$n" -gt 0 ] || continue

  i=0
  # Leading `VAR=value` environment assignments.
  while [ "$i" -lt "$n" ] && printf '%s' "${toks[$i]}" | grep -qE '^[A-Za-z_][A-Za-z0-9_]*='; do
    i=$((i+1))
  done
  [ "$i" -lt "$n" ] || continue

  case "${toks[$i]}" in
    git|*/git) ;;
    *) continue ;;
  esac
  i=$((i+1))

  # Global options between `git` and the verb. `-C <dir>` and `--work-tree` say
  # WHICH tree the command will write, so they decide where dirtiness is checked.
  #
  # `hookify_sees` records whether the hookify rule's regex can REACH this
  # command at all — see DEFERRING, below. It allows exactly `git <verb>` or
  # `git -C <dir> <verb>`; every other global option, and a second `-C`, puts the
  # command outside its alternation entirely.
  workdir=""
  hookify_sees=yes
  ncdash=0
  while [ "$i" -lt "$n" ]; do
    case "${toks[$i]}" in
      -C)            workdir=${toks[$((i+1))]:-}; i=$((i+2)); ncdash=$((ncdash+1)) ;;
      --work-tree)   workdir=${toks[$((i+1))]:-}; i=$((i+2)); hookify_sees=no ;;
      --work-tree=*) workdir=${toks[$i]#--work-tree=}; i=$((i+1)); hookify_sees=no ;;
      -c|--namespace|--exec-path|--config-env|--super-prefix|--git-dir)
                     i=$((i+2)); hookify_sees=no ;;
      --*=*)         i=$((i+1)); hookify_sees=no ;;
      -*)            i=$((i+1)); hookify_sees=no ;;
      *)             break ;;
    esac
  done
  [ "$ncdash" -le 1 ] || hookify_sees=no
  [ "$i" -lt "$n" ] || continue

  verb=${toks[$i]}
  i=$((i+1))
  [ "$verb" = checkout ] || [ "$verb" = restore ] || continue

  rest=( ${toks+"${toks[@]:$i}"} )

  # Paths supplied in a FILE are not in the command; nothing here can check them.
  for t in ${rest+"${rest[@]}"}; do
    case "$t" in --pathspec-from-file*) continue 2 ;; esac
  done

  # Locate `--`, and note whether every token before it is a lone dash-token.
  # hookify's checkout alternation is `checkout(\s+-[^\s;&|]+)*\s+--`, so a flag
  # taking a SPACE-SEPARATED value (`--conflict merge -- <path>`) breaks it.
  dd=-1; k=0; predash_all_flags=yes
  for t in ${rest+"${rest[@]}"}; do
    if [ "$dd" -lt 0 ]; then
      if [ "$t" = "--" ]; then dd=$k
      else case "$t" in -*) ;; *) predash_all_flags=no ;; esac
      fi
    fi
    k=$((k+1))
  done

  cands=()
  if [ "$verb" = checkout ]; then
    if [ "$dd" -ge 0 ]; then
      # DEFERRING, and its one precondition: `--` makes this unambiguously a
      # pathspec, which is hookify's to BLOCK — but only if hookify's regex can
      # see it. Deferring on the `--` alone left `git --work-tree=<d> checkout --
      # <path>` and `git checkout --conflict merge -- <path>` caught by NEITHER
      # mechanism: hookify's prefix group knows only `-C`, and its flag group
      # only lone dash-tokens. The old single-regex hook caught both by accident.
      # A regression introduced by a fix must not be silent, so this hook keeps
      # exactly the forms hookify cannot reach.
      [ "$hookify_sees" = yes ] && [ "$predash_all_flags" = yes ] && continue
      # Past `--` every token is a pathspec, so no tree-ish probe is wanted.
      cands=( ${rest+"${rest[@]:$((dd+1))}"} )
    else
      skip_next=0
      for t in ${rest+"${rest[@]}"}; do
        if [ "$skip_next" = 1 ]; then skip_next=0; continue; fi
        case "$t" in
          -b|-B|--orphan) skip_next=1 ;;
          -*)             ;;
          *)              cands+=( "$t" ) ;;
        esac
      done
      # ARGUMENT DISAMBIGUATION: with one argument git prefers a tree-ish, and
      # with two the first IS the tree-ish. So drop candidate[0] when git can
      # resolve it as a commit — that command switches branches and loses
      # nothing. Candidates AFTER the first are pathspecs no matter what else
      # they name, and that is load-bearing: with branches `a` and `b` and a
      # tracked file also called `b`, `git checkout a b` silently restores the
      # file. git prints "Updated 1 path" and exits 0.
      if [ ${#cands[@]} -gt 0 ]; then
        probe=( git )
        [ -n "$workdir" ] && probe=( git -C "$workdir" )
        if "${probe[@]}" rev-parse --verify --quiet "${cands[0]}^{commit}" >/dev/null 2>&1; then
          cands=( ${cands+"${cands[@]:1}"} )
        fi
      fi
    fi
  else
    # restore. Two separate questions, and conflating them is what #222 was:
    #   1. does this WRITE THE WORKING TREE?  `man git-restore` OPTIONS — the
    #      working tree is restored unless `--staged`/`-S` is given without
    #      `--worktree`/`-W`.
    #   2. can hookify SEE it? Its exemption is the literal substring `--staged`,
    #      so `-S`/`-SW` clusters are blocked by it and are not ours; and its
    #      prefix group is the same `-C`-only one as above.
    staged=no; wt=no; staged_literal=no
    for t in ${rest+"${rest[@]}"}; do
      case "$t" in
        --staged)   staged=yes; staged_literal=yes ;;
        --worktree) wt=yes ;;
        --*)        ;;
        -*)         case "$t" in *S*) staged=yes ;; esac
                    case "$t" in *W*) wt=yes ;; esac ;;
      esac
    done
    [ "$staged" = yes ] && [ "$wt" = no ] && continue     # index only — harmless
    [ "$hookify_sees" = yes ] && [ "$staged_literal" = no ] && continue
    if [ "$dd" -ge 0 ]; then
      cands=( ${rest+"${rest[@]:$((dd+1))}"} )
    else
      skip_next=0
      for t in ${rest+"${rest[@]}"}; do
        if [ "$skip_next" = 1 ]; then skip_next=0; continue; fi
        case "$t" in
          -s|--source) skip_next=1 ;;
          -*)          ;;
          *)           cands+=( "$t" ) ;;
        esac
      done
    fi
  fi

  [ ${#cands[@]} -gt 0 ] || continue

  gitc=( git )
  [ -n "$workdir" ] && gitc=( git -C "$workdir" )
  "${gitc[@]}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || continue

  checked=0
  for p in "${cands[@]}"; do
    [ "$checked" -lt 20 ] || break
    checked=$((checked+1))
    # Strip a surrounding quote pair the shell itself would have removed.
    case "$p" in
      "'"*"'") p=${p#\'}; p=${p%\'} ;;
      '"'*'"') p=${p#\"}; p=${p%\"} ;;
    esac
    [ -n "$p" ] || continue
    st=$("${gitc[@]}" status --porcelain -- "$p" 2>/dev/null | head -5) || continue
    if [ -n "$st" ]; then
      seen=1
      dirty="${dirty}${dirty:+
}  ${p}"
    fi
  done
done <<EOF
$(printf '%s' "$cmd" | tr ';&|' '\n\n\n')
EOF

[ "$seen" = 1 ] || exit 0

jq -nc --arg m "About to discard UNCOMMITTED changes in:
${dirty}

This is \`git checkout\` WITHOUT \`--\`, or \`git restore --staged --worktree\`.
Both write the WORKING TREE, restoring it to HEAD or to the index. If the file
holds a mutation AND work you have not committed, both go — the command cannot
tell them apart, and it will not ask.

That is exactly what happened three times (#189, #198, #207). The third time it
took three edits to the very rule that was being written to stop it.

If you are reverting a mutation: \`cp\` a backup first and \`cp\` it back.
If you meant to switch branches: this name does not resolve to a commit here, so
git will treat it as a path. Check \`git status --porcelain -- <path>\` first.
If you meant to discard this work: carry on, this is the right command." \
  '{systemMessage:$m}'
exit 0
