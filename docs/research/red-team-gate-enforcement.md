# Red-team record — gate-enforcement design (rule 8, first live use)

**Red-team-verdict: DROP both proposed hooks; ship the artifact-only survivors.**

Date: 2026-08-05. Trigger: the owner asked to *verify the three adversarial gates actually run in
future sessions* — a governance mechanism whose failure would be silent (a gate written but never
run). Per ENGINEERING_STANDARDS.md rule 8, the chosen design was red-teamed **before** adoption.
This file is the record gate 2 requires; it is what the ENGINEERING_STANDARDS.md rule 8 incident
paragraph points at.

## The design that was attacked
Three guards to make the gates leave a verifiable artifact:
- **Gate 3 (co-verify):** the lens posts a `Co-verify-verdict:` PR comment; a `guard-coverify.sh`
  denies `gh pr merge` without one.
- **Gate 2 (red-team):** a committed `red-team` agent + a **path-based merge-guard** requiring a
  `Red-team-verdict:` record when the diff touches `.claude/hooks|settings.json|build-logic|agents|skills`.
- **Gate 1 (design-debate):** no guard (correct — "is this a feature?" is not grep-able).

Honest constraint stated up front: a `PreToolUse` hook sees a command string, not the caller or the
review's quality (#213) — every guard is a **presence** check, catching "skipped entirely," never
"rubber-stamped."

## The attack — three cross-model adversaries (all read the real repo, not the design's self-claims)

**1. Does-it-fire (#213 shape).** Gate 3's guard **CAN** fire (every open PR has 0 verdict comments →
denies by default) but is one `if` from #213 (the natural "check newest verdict" makes the *absence*
branch fall through to allow). **Gate 2's path-trigger is the real #213 shape: DEAD on the standing
objective** — the last rev5 PRs (#277/#276/#272/#266) touch **none** of its trigger paths; rule 5's
high-risk classes (concurrency/data/consent) live in **app code** the glob cannot see; it fires only
for governance PRs whose authors already run the gate.

**2. Bypass/gaming.** A faked `Co-verify-verdict: APPROVE` costs **one `gh pr comment`, 0 authenticity
bits** (same token as the author), and a newer fake **overrides a real BLOCK**. Both guards inherit
`gh-merge-target.sh`, so **`time`/`nohup`/`$VAR`/a script-file/backtick/heredoc merge evades** — and
this same class (**#265, open**) already lets `time gh pr merge <N>` walk past the shipped `main`-guard.

**3. Rot-skeptic (cross-model, Sonnet 5).** The decisive tier finding: **`main` had zero GitHub branch
protection** (`gh api …/branches/main/protection` → 404) — every guard, old and new, is client-side and
sees only this CLI's Bash tool; a web/Terminal/API merge is invisible to all of them. Building a third
generation of hooks at that weak tier while the real door is unlocked is optimizing the wrong layer.
The repo's record: **every mechanism that caught a defect re-did the work** (`/land-pr`'s local
re-verify, specialists reading code, a committed test-suite re-run); **every one that failed was a text
marker treated as proof** (#213, #207's `Enumerated by: this is a lie` that passed). Three more hooks of
that shape add three more instances of the pattern that has never once caught anything here.

## Verdict → what shipped
- **Gate 2 merge-hook: DROPPED.** Dead on app code, hollow on governance. The committed `red-team`
  *agent* is kept; "should this design have been red-teamed?" is folded into `co-verify-lens.md` (judgment
  a glob cannot make).
- **Gate 3 merge-hook: DROPPED.** A zero-authenticity presence check on a bypassable resolver.
- **Survivors (shipped in the #290 PR):** the lens posts its `Co-verify-verdict:` (a durable record);
  DoD (rule 2) lists the gate artifacts as checkable items — *presence, not proof*, stated as such;
  the `red-team` agent is committed. **No new hook.**

## Two real findings the red-team surfaced (beyond the design)
1. **#265 is live** — `time gh pr merge` / a script-file merge walks past the client-side `main`-guard.
   **Subsumed** by the branch protection below; hold #265 under the freeze.
2. **`main` had no branch protection — FIXED 2026-08-05.** Now: required check `build`, `strict`
   (up-to-date), `enforce_admins`, no required human review (zero-touch), force-push/delete blocked;
   `delete_branch_on_merge` enabled. This is the server-side backstop the whole hook layer lacked.

**The honest limit:** this makes "did the gate run?" a *lookup* (a posted verdict, a committed record),
not a promise — materially better than a doc, but not "cannot be skipped." A path can be bypassed, an
artifact faked. The enforcement is the DoD + the artifact + branch protection, not another script.
