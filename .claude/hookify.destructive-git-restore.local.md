---
name: block-destructive-git-restore
enabled: true
event: bash
action: block
conditions:
  - field: command
    operator: regex_match
    pattern: (^|[;&|]|\s)git\s+(-C\s+\S+\s+)?(checkout(\s+-[^\s;&|]+)*\s+--(\s|$)|restore(\s|$))
  - field: command
    operator: not_contains
    pattern: --staged
---

🛑 **BLOCKED — this command discards uncommitted work.**

This has destroyed real work in this project **three times**, and the third time was
*while the hook meant to warn about it was being tested* (CLAUDE.md rule 12, shape E).

**Do this instead — copy first, then act:**

```
cp <file> /path/to/scratchpad/<file>.bak     # then make your change
cp /path/to/scratchpad/<file>.bak <file>     # to undo it
```

`cp` is the mutation-backup tool in this repo. A restore command is not.

**If you genuinely need git to do it**, the block is telling you to stop and confirm the
file has nothing in it you have not committed — check `git status --porcelain <path>`
first, and ask the owner if it does.

---

**Scope, stated so this is not mistaken for full cover:** this blocks the forms that are
**unambiguously about files** — `git checkout -- <path>`, `git restore <path>`, and both
behind `git -C <dir>`. It deliberately does **not** block `git checkout <name>` with no
`--`, because a regex cannot tell a branch from a path and blocking branch switches would
get this rule turned off.

That remaining form is git's own documented idiom and is the most likely one to be typed —
it needs `guard-restore.sh`, which can ask git whether the path is dirty. Its three gaps
are **issue #222**. Until that lands, this rule covers what regex can cover honestly.

**Two further limits, named rather than discovered later:**

- **`--staged` is exempted**, because `git restore --staged <path>` touches only the index
  and leaves the working tree alone — blocking it would block correct work. The exemption
  is a plain substring, so the deliberate two-flag `--staged --worktree` form slips
  through. That is a conscious trade: it is rare, explicit, and `guard-restore.sh` is the
  right place to catch it because it can check whether the path is actually dirty.
- **This blocks the command even when it only appears as TEXT** — a test harness, a
  documentation example, or a message quoting the command is blocked too. That is how the
  first attempt to test these rules was itself blocked. Write such text to a file with the
  Write tool rather than echoing it through a shell.
