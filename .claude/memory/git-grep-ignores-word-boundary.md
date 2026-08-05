# `git grep -E` silently ignores `\b` — the false-zero enumeration trap

**#221.** `git grep -E` (and `-G`/basic) does NOT support the `\b` word-boundary
escape. It does not error. It returns **0 matches, exit 1** — the *same* answer
it gives for a symbol that genuinely appears nowhere. (`git grep -P`, PCRE, does
support it, but is not always compiled in; do not rely on it.)

Measured in this repo (2026-08-05; the issue saw 11 when filed — the codebase
grew, the defect did not):

    git grep -lE 'OfflineModelFailure\b'   ->   0 files, exit 1   ← the lie
    git grep -lE 'OfflineModelFailure'     ->  23 files
    grep  -rlE 'OfflineModelFailure\b' …   ->  23 files           ← POSIX grep honours \b

## Why it is dangerous *here*, specifically
CLAUDE.md rule 11 requires `Call sites: N found, N changed`, and `guard-pr.sh`
denies a `gh pr create` without that marker. But the hook checks the marker is
**present**, not **true**. An enumeration written `git grep -cE 'Sym\b'` returns
zero, zero reads as "nothing else references this", the marker is present so the
PR passes, and the reviewer sees a clean count that is fiction. That is rule 12's
second shape — a number with no command that actually produced it — with the
command sitting right there and quietly lying.

## Do this instead
    grep -rE 'Sym\b' --include='*.kt' <dirs>              # POSIX grep supports \b
    git grep -wE 'Sym'                                    # -w = whole word
    git grep -E '(^|[^A-Za-z0-9_])Sym([^A-Za-z0-9_]|$)'  # spell the boundary out

## The signature that catches the whole class
Any `\b` enumeration that comes back **0 while the bare term matches** is the
bug. Cheap, and it is the one check that would have caught this: re-run every
`\b` count against the bare term before you trust it.

## The two mechanisms (they are a pair, not a duplicate)
- **`hookify.git-grep-word-boundary.local.md`** BLOCKS the `git grep …\b`
  *command* at run time (committed `af3a2c3`, refined `783e773`). Two limits worth
  knowing: it scans the whole command string, so even an *echo/label* that
  mentions `git grep …\b` is blocked (assemble the `\b` from a variable to run a
  deliberate reproduction); and it keys on the literal `git grep` spelling, so
  `git -C <dir> grep …\b` is not matched.
- **`guard-pr.sh`** WARNS (never denies) when an *enumeration-shaped* `git grep`
  (a leading `-c`/`-l`/`-L` count/list flag) with `\b` appears in a PR **body** —
  including a `--body-file`, whose text the command-level block never sees. It is
  precise on purpose: 8 of 142 shipped PR bodies name the trap in *prose*
  ("never `git grep -E`, no `\b`", citing #221), and a bare pattern would warn on
  every one of them — a guard that fires on the correct write gets scrolled past.
- **Test:** `.claude/hooks/tests/guard-pr-word-boundary.sh` (21 assertions; the
  #213 rule — prove the warn fires in the real hook path with a positive AND a
  negative control, prose included).
