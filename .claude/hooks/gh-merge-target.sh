#!/usr/bin/env bash
# Sourced by guard-git.sh and guard-tracker.sh: which pull request would this
# Bash command merge?
#
# Both guards used to answer that with one line, byte-identical in each file:
#
#     grep -oE 'gh[[:space:]]+pr[[:space:]]+merge[[:space:]]+[0-9]+'
#
# It only sees a number written IMMEDIATELY after `merge`. But the command is
# `gh pr merge [<number> | <url> | <branch>] [flags]` — the target may follow a
# flag, may be a URL or a branch, and may be absent entirely, in which case gh
# merges the current branch's PR. That last form is how you merge from inside a
# worktree, and it had been invisible since the line was written. Measured on a
# PR the guard would otherwise deny (issue #167): nine invocations of the same
# merge extracted nothing and were allowed in silence.
#
# The line living in two files is part of the same defect — one fix had to be
# made twice or it was not made. So it lives here now.
#
# FAIL OPEN, like every guard here: anything this cannot resolve prints nothing
# and the caller allows. A denial has to be a real finding (CLAUDE.md rule 8).

# `gh` is in command position at the start of a segment, after env assignments
# (`GH_PAGER= gh …`), after a runner (`env gh …`) and when reached by path
# (`/opt/homebrew/bin/gh …`). It is NOT in command position in the middle of a
# sentence — guard-git.sh's own first commit was denied by guard-git.sh for
# matching its own commit message, and this anchor is what keeps prose out.
_GH_MERGE_RE='^[[:space:]]*((command|env|sudo)[[:space:]]+)*([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*([^[:space:]]*/)?gh[[:space:]]+pr[[:space:]]+merge([[:space:]]+.*)?$'
_GH_PULL_URL_RE='^https?://[^/]+/([^/]+/[^/]+)/pull/([0-9]+)'

# `timeout` is coreutils, not POSIX, and is absent on a stock macOS. Without
# this shim a missing binary makes every gh call "fail", which fails the guard
# open for a reason that has nothing to do with the merge.
if command -v timeout >/dev/null 2>&1; then _GH_TIMEOUT=timeout
elif command -v gtimeout >/dev/null 2>&1; then _GH_TIMEOUT=gtimeout
else _GH_TIMEOUT=""; fi

# gh_run <seconds> <command…> — run it, bounded when we can bound it.
gh_run() {
  local secs=$1
  shift
  if [ -n "$_GH_TIMEOUT" ]; then "$_GH_TIMEOUT" "$secs" "$@"; else "$@"; fi
}

# _gh_same_repo <owner/name> — is that this checkout's origin? A merge aimed at
# another repository cannot be judged against this checkout's main.
_gh_same_repo() {
  local want=$1 origin
  origin=$(git remote get-url origin 2>/dev/null) || return 1
  origin=${origin%.git}
  case $origin in
    *[:/]"$want") return 0 ;;
    *) return 1 ;;
  esac
}

# gh_pr_merge_number <argument-string> — the PR those arguments select, or
# nothing. Mirrors gh's own resolution rather than the shape the command
# usually takes.
gh_pr_merge_number() {
  local args=${1:-}
  local tok rest ch repo="" cands="" endopts=0 num target=""

  set -f                       # arguments are not globs
  # shellcheck disable=SC2086
  set -- $args
  set +f

  while [ $# -gt 0 ]; do
    tok=$1
    shift
    if [ "$endopts" = "1" ]; then
      cands="${cands}${tok}
"
      continue
    fi
    case $tok in
      --) endopts=1 ;;
      --repo=*) repo=${tok#--repo=} ;;
      --repo)
        repo=${1:-}
        if [ $# -gt 0 ]; then shift; fi ;;
      # Long flags that swallow the next word. Miss one and its value gets read
      # as the PR to merge.
      --author-email|--body|--body-file|--match-head-commit|--subject)
        if [ $# -gt 0 ]; then shift; fi ;;
      --*) ;;                  # boolean long flag, or --flag=value
      -?*)
        # Short flags bundle (`-sd`) and may carry their value attached
        # (`-Rowner/name`), so walk the token a character at a time.
        rest=${tok#-}
        while [ -n "$rest" ]; do
          ch=${rest:0:1}
          rest=${rest:1}
          case $ch in
            R)
              if [ -n "$rest" ]; then
                repo=$rest
                rest=""
              else
                repo=${1:-}
                if [ $# -gt 0 ]; then shift; fi
              fi ;;
            A|b|F|t)
              if [ -n "$rest" ]; then
                rest=""
              elif [ $# -gt 0 ]; then
                shift
              fi ;;
          esac
        done ;;
      -) ;;
      *)
        tok=${tok#[\"\']}
        tok=${tok%[\"\']}
        cands="${cands}${tok}
" ;;
    esac
  done

  if [ -n "$repo" ]; then
    _gh_same_repo "$repo" || return 0
  fi

  local oldifs=$IFS
  IFS='
'
  set -f
  # shellcheck disable=SC2086
  set -- $cands
  set +f
  IFS=$oldifs

  # A bare number needs no lookup, so the ordinary form stays free.
  for tok in "$@"; do
    case $tok in
      ''|*[!0-9]*) ;;
      *) printf '%s' "$tok"; return 0 ;;
    esac
  done

  for tok in "$@"; do
    if [[ $tok =~ $_GH_PULL_URL_RE ]]; then
      _gh_same_repo "${BASH_REMATCH[1]}" || return 0
      printf '%s' "${BASH_REMATCH[2]}"
      return 0
    fi
  done

  target=${1:-}
  command -v gh >/dev/null 2>&1 || return 0
  if [ -n "$target" ]; then
    num=$(gh_run 15 gh pr view "$target" --json number --jq .number 2>/dev/null) || return 0
  else
    # No argument at all: gh merges the current branch's PR, so the guard asks
    # the same question the same way. Failing open here is exactly what hid
    # `gh pr merge --squash --delete-branch`, the natural form from a worktree.
    num=$(gh_run 15 gh pr view --json number --jq .number 2>/dev/null) || return 0
  fi
  case $num in
    ''|*[!0-9]*) return 0 ;;
  esac
  printf '%s' "$num"
}

# gh_pr_merge_numbers <command> — every PR this command would merge, one per
# line. Nothing when it merges none, or when none can be resolved. Two merges
# chained with `&&` are two merges; only the first used to be looked at.
gh_pr_merge_numbers() {
  local seg args num
  printf '%s\n' "$1" | tr ';&|()' '\n\n\n\n\n' | while IFS= read -r seg; do
    [[ $seg =~ $_GH_MERGE_RE ]] || continue
    args=${BASH_REMATCH[5]:-}
    num=$(gh_pr_merge_number "$args")
    [ -n "$num" ] && printf '%s\n' "$num"
  done
}
