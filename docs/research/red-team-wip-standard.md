# Red-team record — revising Standard 1 (dropping the WIP count-cap)

**Gate-2 artifact (ENGINEERING_STANDARDS.md rule 8).** This change alters rule 1's merge-lane
guarantees — the standing red-team example named in Standard 8 itself — so it does not ship without a
durable red-team record. This is that record.

**Decision under attack:** owner directive 2026-08-06 — replace Standard 1's "max 2 open PRs"
count-cap, because it throttles rule 7 (never idle-wait). Reconcile the two.

**Attacker:** cross-model red-team (Sonnet 5), 2026-08-06. **Verdict: SURVIVES, with six refinements
folded into the shipped text.**

---

## The crux — proven LIVE (throwaway repo), not from documentation
Does removing the count-cap leave a MECHANICAL block on a stale-base (behind-tip) merge — the exact
#108 / #132/#134 accident? **YES, server-side.** Reproduced on a disposable repo mirroring main's
protection, then deleted:
- Two sibling PRs off the same tip, both `mergeStateStatus: CLEAN` (genuinely green). Merged one.
- On the now-behind sibling: `gh pr merge --squash` → rejected, *"the head branch is not up to date
  with the base branch."*
- The `--admin` escape hatch `gh` advertises: with `enforce_admins:false`, `--admin` **forced** the
  behind-tip merge through. With `enforce_admins:true` — main's REAL value, confirmed via
  `gh api …/branches/main/protection` → `enforce_admins.enabled:true` — `gh pr merge --squash --admin`
  → rejected: `Required status check "build" is expected`. PR stayed OPEN. Same admin token both
  times; only `enforce_admins` changed the outcome.
- CI's `build` check is a real compile (`.github/workflows/ci.yml` runs `./gradlew build` + `preflight`),
  and `pull_request: synchronize` fires a fresh required check on the rebased SHA before `strict`
  considers the branch clean.

⇒ **The count-cap was never the backstop; GitHub branch protection (`strict` + `enforce_admins`) is** —
independent of the client hooks (which #265 shows are prefix-bypassable, so `guard-git.sh`'s
behind-tip check is not load-bearing here).

## The six refinements (per-component verdicts → folded into the shipped Standard 1)
1. **CONFLICT="none" via the client hook — NEEDS-CHANGE.** `guard-git.sh`'s behind-tip DENY is
   bypassable beyond #265's list (`time`/`nohup`/`eval`/backtick/heredoc/wrapper-script). NOT
   load-bearing (the server is). The shipped text does not call the client hook "mechanically
   enforced" — it names the SERVER as the backstop.
2. **Branch protection (`strict` + `enforce_admins`) — SURVIVES**, the real backstop (crux above).
3. **Local re-verify — SURVIVES but SCOPED.** Local re-verify + CI-on-the-rebased-SHA catch
   COMPILE-level breaks (#247/#249). NEITHER catches a semantic break that still compiles (a shared
   default/invariant changed in two different files); the shared-brain/string-key independence rule
   guards that class, not the merge machinery. The shipped text states this honestly.
4. **"INDEPENDENT = no shared file" — WIDENED.** Also: no shared brain module
   (`core/translate|access|usage|ads`) and no shared cataloged string key, even via different files
   (PR #310 alone spanned `feature/language` + `feature/text` strings — the file-map misses
   shared-brain dependencies).
5. **Stale co-verify — ADDRESSED.** Removing the count removes the incidental brake on verdict
   staleness. New DoD item: a rebase resolving an actual CODE conflict invalidates the prior
   `Co-verify-verdict:`.
6. **The guarantee rests on a GitHub setting OUTSIDE version control — GUARDED.** `/land-pr` now
   confirms `strict` + `enforce_admins` are both `true` before trusting the lane; nothing else would
   notice a silent revert. The three cross-references that named "rule 1's two-PR cap" (Standard 7 ×2,
   Standard 8 ×1) were updated in the same PR.

## Minimal safe design (adopted, shipped in this PR)
No fixed count-cap; a **tip-freshness brake** (an open PR ≤3 commits behind before another opens);
**wider independence** (no shared file / brain module / string key); serial one-at-a-time merge
**backstopped server-side**; a **co-verify-freshness** DoD item; a `/land-pr` **protection self-check**.
**#265 stays deferred** — its class is subsumed by the server backstop. **No new hook** — the prior
red-team (`red-team-gate-enforcement.md`) already concluded hooks of this shape add rot, and this
pass's live testing confirms the server + DoD + posted artifact are the enforcement.
