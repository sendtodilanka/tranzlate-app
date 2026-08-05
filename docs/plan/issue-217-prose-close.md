# Plan — issue #217: a closing keyword in PR-body prose closed an issue the PR argued must stay open

status: accepted
(accepted basis: #217 is owner-filed, S1, states the harm with reproducible
ground truth, and names three candidate fixes. This plan settles only the scope —
which candidate is adopted, which is refused with evidence, and which is handed
back for a red-team instead of built. It changes documentation and the Definition
of Done; it changes no code and adds no runtime mechanism.)

Closing intent: the implementing commit carries the `Fixes: #217` trailer. Every
mention of #217 in this plan's prose is written per §4.2 — the number never sits
immediately after a closing verb — so this doc practises the rule it proposes.

Refs: #173 (the issue that was wrongly closed) · #215 (the audit that found it).

---

## 1. The incident, ground-truthed

PR #202's body, line 55, was arguing that #173 must stay **open**:

> `Refs: #173`, not `Fixes:`. Rule 3 asks for `Fixes:`, but it would **auto-close
> #173** and lose the second half — the exact accident #173 was filed early to
> prevent…

GitHub's closing-keyword parser read `close #173` out of the hyphenated word
`auto-close #173` and closed #173 the instant #202 merged. Verified today, months
later, from GitHub's own computed parse of that body:

```
$ gh pr view 202 --json closingIssuesReferences --jq '.closingIssuesReferences[].number'
173
$ gh issue view 173 --json state --jq .state
CLOSED
```

Every commit on #202 had honoured the intent — `Refs: #173`, never `Fixes: #173`
(the issue's own `git log --grep 'Fixes: #173'` returned nothing). **The commit
trailer discipline worked. The PR body overrode it**, because GitHub closes from
the body independently of what the commits say.

Once closed, the last surviving record of the unfixed hole (#173 Hole 1) was a
single `⬜ #173` row in `docs/plan/ROADMAP.md`. The audit's staleness sweep flagged
that row as "says ⬜ but the issue is CLOSED"; the obvious repair — tick it ✅ —
would have erased the record. The safety net and the thing it protected were one
row apart, pointing opposite ways. That is why this is S1, not a cosmetic
state-flag flip.

## 2. Root cause — what the convention covers, and what it does not

There are **two independent auto-close paths** on merge, and GitHub documents both
([Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue)):

| Path | Trigger | This repo's discipline |
|---|---|---|
| **Commit** | a closing keyword + `#N` in a commit message that lands on the default branch | `Fixes:` = close, `Refs:` = don't (CLAUDE.md rule 3). **Working** — verified across #234/#235/#236/#237/#238/#218/#231/#175. |
| **PR body** | a closing keyword + `#N` anywhere in the PR description | **Ungoverned.** The convention names one deliberate `Fixes: #N` slot and says nothing about the rest of the body. |

The convention manages the references an author writes **on purpose**. #217 is an
**accidental** collision: nine ordinary English verbs (`close/closes/closed`,
`fix/fixes/fixed`, `resolve/resolves/resolved`), sitting next to a `#N` in prose
that is *discussing* the hazard, are indistinguishable to GitHub from a deliberate
close. Nothing in CLAUDE.md rule 3, CONTRIBUTING.md, ENGINEERING_STANDARDS.md, the
PR template, or `land-pr` mentions this. #217 states it exactly: "a keyword grammar
nobody in this repo has written down."

**Honest test of "the convention already covers this": it does not.** The
`Refs:`/`Fixes:` rule is about the *trailer* and the *one intended* body line.
#202 followed it — `Refs: #173` in every commit — and #173 still closed. So the
answer is not "documentation/close-with-note"; there is a real, specific gap, and
it has one narrow home: **prose in the PR body (and in commit messages) that is not
the deliberate close.**

## 3. GitHub's closing grammar, written down

The thing #217 says nobody wrote down. Verified against GitHub's docs and this
repo's own incident:

- **Keywords (9, case-insensitive):** `close`, `closes`, `closed`, `fix`, `fixes`,
  `fixed`, `resolve`, `resolves`, `resolved`.
- **Syntax:** `KEYWORD #N`, an optional colon allowed — `Closes #10`, `closes: #10`,
  `CLOSES #10` all fire. The keyword **precedes** the reference; `#10 is fixed` does
  **not** link (grammar is keyword-then-reference).
- **Both surfaces:** the PR **body** *and* any **commit message** merged to the
  default branch. (Closing via a commit does not list the PR as "linked", but the
  issue still closes.)
- **The undocumented trap:** the keyword fires even when it is the tail of a
  hyphenated word — `auto-close #173` closed #173. GitHub does **not** document
  this; a public search for it turns up only experiment write-ups, not spec. **This
  is the load-bearing fact for §5:** the grammar you would have to replicate in a
  regex is not fully published, so any regex is guessing at GitHub's parser.

## 4. The fix — process + Definition of Done, no new mechanism

### 4.1 The ground-truth check (the centerpiece)

GitHub already computes, and exposes, exactly which issues a PR body will close:
the `closingIssuesReferences` field. It is authoritative (it *is* GitHub's parser,
not a copy of it), it reflects the **final** body regardless of how the body was
written or later edited, and it is queryable at any time before merge:

```
gh pr view <N> --json closingIssuesReferences --jq '.closingIssuesReferences[].number'
```

**The rule:** before merge, this list must equal the set of issues the PR *intends*
to close. An issue the PR only *discusses* appearing in that list is the #217
defect, caught before the merge instead of after.

This is a **checklist step a person or agent runs**, not a mechanism — the same
shape as "run `./gradlew preflight`" or "compare the run's `headSha` to the tip". It
adds no code, no hook, nothing to maintain, and cannot drift, because it asks
GitHub rather than re-deriving GitHub. It becomes:

- **Definition of Done** (ENGINEERING_STANDARDS.md §2, item 4 — tightened from "at
  least one issue closes" to "closes *exactly* the intended set, verified by
  `closingIssuesReferences`").
- **A pre-merge step in `/land-pr`**, pointing at that DoD item (not restating it).

### 4.2 The prose rule (prevents the collision at the source)

For any issue a PR/commit must **not** close, keep all nine keywords away from its
`#N`:

- **Safest — `Refs: #N`.** Contains no closing keyword, so it is inert by
  construction. This is what #202's trailer used correctly; the mistake was prose
  *elsewhere* in the body, which `Refs:` in the trailer does nothing to protect.
- **Or lead with the number** — `#173 stays open`, `#173 (Hole 1 remains)`. The
  keyword-then-reference grammar means a number with no keyword in front of it links
  nothing. #217's own proposal 2 states this: "`#173 stays open` reads the same and
  matches nothing."
- **The trap to name explicitly:** `does not close #173` still closes #173 — GitHub
  sees `close #173` and ignores the "does not". Negation in prose is not protection;
  distance from the keyword is.

### 4.3 Call sites — enumerate before changing (rule 11)

The close-keyword / issue-closing convention is **stated in 9 governance surfaces**
(plus per-issue plan docs that *use* it, and two user-memory files outside the
repo). Two independent searches, chosen so they could not both miss the same thing:

- **Search A (by symbol)** — `grep -rnE '(Fixes|Refs|Closes|Resolves):[[:space:]]*#?'`
  over the governance files (plain `grep`; the tokens themselves).
- **Search B (by concept)** — `git grep -linE 'closes? at least one issue|auto-?close|closing keyword|commit trailer|merged commit closes|reserve.*Fixes|trailer.*(close|issue)'`
  over all `*.md` (the idea, independent of the token).

| # | Surface | What it states | Changed? |
|---|---|---|---|
| 1 | `CONTRIBUTING.md` (TL;DR 5, commit-format, lifecycle) | the `Fixes: #N` trailer + "merge → issue closes" | **Yes** — new "How a PR closes an issue" section + one pointer on TL;DR 5. This is the authoritative home for §3 + §4.2. |
| 2 | `docs/ENGINEERING_STANDARDS.md` §2.4 | "at least one issue closes with `Fixes:`" | **Yes** — tightened to "closes *exactly* the intended set", verified via `closingIssuesReferences` (§4.1). |
| 3 | `.github/pull_request_template.md` line 5 | `Fixes: #` (in the body — the hazard surface) | **Yes** — one HTML-comment guard by that line (does not render into the body). |
| 4 | `.claude/skills/land-pr/SKILL.md` | step 4 closes via body/`Fixes:` | **Yes** — a pre-merge `closingIssuesReferences` confirmation, *pointing* to §2.4 (no restatement — this file already points to CLAUDE.md rule 11 rather than copying it). |
| 5 | `CLAUDE.md` rule 3 | commit carries `Fixes: #N` | **No** — states the trailer rule correctly; already links "Full workflow: CONTRIBUTING.md", which is where the new detail lives. Adding it here would duplicate the exact way rule 11 warns against. |
| 6 | `.claude/memory/issue-first-pr-only-workflow.md` | "Fixes:#N trailer" | **No** — a memory summary, not the authoritative convention; points to CONTRIBUTING. |
| 7 | `.claude/memory/MEMORY.md` | index line "Fixes:#N" | **No** — index pointer. |
| 8 | `~/.claude/.../orchestration-and-landing.md` §4.3, §C | "`Fixes:`, not `Refs:`"; "auto-close works" | **No** — **user memory, not in the repo**; cannot be edited on a branch. Flagged for the owner to update out-of-band. |
| 9 | `docs/plan/ROADMAP.md`, `issue-173/201/219-*.md` | per-issue *use* of the convention | **No** — historical/scoped; they consume the rule, they don't define it. |

**Call sites: 9 found, 4 changed** (the 5 unchanged are listed above with the
reason each is deliberately left — four are pointers/consumers that would only
create drift, one is outside the repo).

## 5. Why NOT a new hook — red-team of #217 proposal 1

Proposal 1 in #217 is a `PreToolUse` regex hook on `gh pr create`/`gh pr edit`:
scan the body for a closing keyword + `#N` whose commits only `Refs:` it, and deny.
It does **not** survive its red-team, on five independent grounds — any one is
fatal:

1. **It re-derives an unpublished grammar (§3).** GitHub does not document the
   hyphen behaviour that caused this incident. A regex is a *guess* at GitHub's
   parser and is free to drift from it in both directions — miss a real close, or
   deny a safe body. This is exactly the objection the project's own
   gate-enforcement red-team reached: a client-side regex gate of this shape adds
   rot, not safety.
2. **It is blind to the bodies this repo actually writes.** `guard-pr.sh` documents
   (its own lines 40-57) that it fails open on `--body "$(cat body.md)"` and
   `--body "$BODY"` — the text isn't in the command at `PreToolUse` time. A #217
   hook inherits that blindness and would miss precisely the file-authored bodies
   that carry long prose — i.e. the ones most likely to collide.
3. **It fires at the wrong moment.** The body that closes is the **final** body at
   merge. A create-time hook cannot see a later `gh pr edit`, still less a web-UI
   edit. ENGINEERING_STANDARDS.md §2.3 already says these markers must be re-derived
   "as the last act before merge"; a create-time regex is stale by then.
4. **It duplicates a ground truth that is free (§4.1).** `closingIssuesReferences`
   is GitHub's authoritative answer to the exact question the regex approximates.
   Building a worse copy of a value GitHub already computes is the "second
   approximation, free to drift from both" that ENGINEERING_STANDARDS.md §3.2 names.
5. **The verification-debt trap (§4/ES §4).** Every hook in this repo that was
   "tested" by piping a hand-built payload at it later proved it could never fire in
   the real repo (`device-claim.sh`, #213). A #217 regex hook would need the same
   ceremony to be trustworthy, for a check the DoD step already does correctly with
   no code.

**Verdict: do not build it.** The DoD step (§4.1) captures its entire intended
value with none of these costs.

## 6. The one mechanism that could be warranted — DESIGN ONLY, handed back

There is a mechanism that would survive ground 1-4 above, because it uses
`closingIssuesReferences` (ground truth) rather than a regex: **a warn-only,
land-time check** that compares GitHub's closing set to the issues named in the
body's `Fixes:` line(s), and warns if the closing set contains anything the author
did not declare.

```
# sketch — NOT built. Warn-only. Fails open (no gh / no network → allow).
declared=<numbers on the body's "Fixes:" lines>
closing=$(gh pr view <N> --json closingIssuesReferences --jq '.closingIssuesReferences[].number')
extra=<closing minus declared>
[ -n "$extra" ] && warn "PR will ALSO close #$extra, which no 'Fixes:' line declares — prose collision? (#217)"
```

Per the standing constraint and ENGINEERING_STANDARDS.md §3-§4, **this is not built
here.** It is handed back for a red-team first, because two things are unresolved
and only a red-team should settle them:

- **Is it warranted at all over the §4.1 checklist step?** `/land-pr` is already a
  followed-as-a-unit manual procedure with SHA checks; one more `gh pr view` line in
  it may fully cover the risk. Automating buys "cannot forget", at the cost of a new
  mechanism in a project that is deliberately *shedding* client-side gates.
- **Its one convention-dependency:** it assumes every intended close is declared
  with the `Fixes:` keyword specifically. A legitimate `Closes #A` in the body would
  read as an undeclared extra (false positive). The repo standardises on `Fixes:`,
  so the assumption holds today — but a mechanism that silently depends on it must
  say so, and a red-team is where that gets pressure-tested.

If it is ever built, ES §4 applies without exception: it must be shown to **fail on
purpose first** — reconstruct #202 (body prose closing an undeclared issue) and see
the warning fire — before it is trusted. #202 is a live fixture for exactly that
(`closingIssuesReferences` still returns `173`).

## 7. Limits / residual (stated, not hidden)

- **The commit-message path is not covered by the §4.1 check.**
  `closingIssuesReferences` reflects the **body** only. A closing keyword in a
  *commit message* merged to `main` closes independently, and the check would not
  see it. That path stays governed by the existing commit discipline (`Refs:` not
  `Fixes:`) plus the §4.2 prose rule, which §4 now writes down for commits too.
  Commit messages are terse (subject + WHY) and far less prone to discursive
  collisions than PR bodies — the incident was a body — so the residual is small,
  but it is real and named.
- **The prose rule is guidance, not enforcement.** It relies on authors reading it.
  Its backstop is the §4.1 check at land time, which is ground-truth and catches the
  body-path collision regardless of whether the author remembered the rule.
- **`closingIssuesReferences` is only as timely as when you run it.** The DoD places
  it as a pre-merge act precisely so a late body edit cannot slip a collision in
  after an early check.

## 8. Verification — does the fix catch #202?

Yes, and the fixture is live. Run against the real incident:

```
$ gh pr view 202 --json closingIssuesReferences --jq '.closingIssuesReferences[].number'
173
```

#202 declared its intent as `Refs: #173` (close nothing). The check returns `173`.
`173 ∉ declared` → the §4.1 DoD step flags it, and the author — who wrote three
sentences explaining that #173 must stay open — stops and rewrites the prose per
§4.2. The exact accident is caught before merge, with a command that already
works today.

## 9. Note on "ENGINEERING_STANDARDS.md rule 8"

The brief cites "ENGINEERING_STANDARDS.md rule 8" for the hand-it-back-for-red-team
requirement. That file has rules **1-7 only**; there is no literal rule 8
(`grep -nE '^## [0-9]+\.' docs/ENGINEERING_STANDARDS.md` → 1…7). The principle the
brief invokes is real and is honoured here — it lives in ES §3 (a mechanism that
fails twice is rebuilt, not patched; derive checks from source, not copies) and §4
(make a check fail on purpose before trusting it), reinforced by the standing
gate-enforcement red-team verdict. This plan does not invent a "rule 8"; it points
at the standards that actually carry the principle. Whether to *add* a numbered
"red-team new mechanisms before building" rule to ES is an owner call and out of
this issue's scope.
