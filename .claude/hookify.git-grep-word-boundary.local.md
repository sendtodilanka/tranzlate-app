---
name: block-git-grep-word-boundary
enabled: true
event: bash
action: block
pattern: git\s+grep[^;&|\n]*\\b
---

🛑 **BLOCKED — `git grep` does not support `\b`, and it will tell you "no match".**

Measured (issue #221):

```
git grep -lE 'OfflineModelFailure\b'   ->  0 files, exit 1
git grep -lE 'OfflineModelFailure'     ->  11 files
grep -rlE 'OfflineModelFailure\b' …    ->  11 files
```

It does not error. It returns **no match** — the same answer it gives for a symbol that
genuinely appears nowhere.

**Why this is blocked rather than warned:** this feeds CLAUDE.md rule 11's
`Call sites: N found, N changed` marker, which `guard-pr.sh` requires in every PR body.
A `\b` search returns **zero**, zero reads as *"nothing else references this"*, the marker
is present so the hook passes, and the PR ships a count that is fiction. The command is
never right, so there is nothing legitimate to block.

**Use instead:**

```
grep -rE 'Symbol\b' --include='*.kt' <dirs>
git grep -E '(^|[^A-Za-z0-9_])Symbol([^A-Za-z0-9_]|$)'
```

**And the check that catches this class in general:** run every enumeration again without
the `\b`. A count of zero beside a looser count of eleven is the signature.
