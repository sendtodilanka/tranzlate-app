# Contributing — tranzlate-app

> Solo developer (Dilanka) + AI. Process discipline **adopted + adapted from the mature ZyntaStack projects** (`zw-infra-zyntastack`, `zw-voice`) — retargeted Forgejo→**GitHub**, voice/hardware→**Android**.

## TL;DR
1. **Issue-first** — open a GitHub issue before any non-trivial work.
2. **PR-only** — never direct-push/merge to `main`. Every change lands via PR.
3. **Plan-doc gate** — non-trivial work needs `docs/plan/issue-NN-<slug>.md` with `status: accepted` **before code**.
4. **Research-first** — unknown root cause → `docs/research/issue-NN-<slug>.md` (read-only investigation, disconfirmation discipline) before a fix.
5. **`Fixes: #N` trailer** — every fix-class commit references its issue. But a closing keyword next to *any* `#N` — even in prose you meant only as discussion — closes that issue; see **How a PR closes an issue** below (#217).
6. **PR co-verify** — ≥1 lens by a session/agent ≠ author before merge; cross-model for high-risk.
7. **Verify with tests** — no merge without the feature's tests + acceptance green.
8. **No code copied from Tranzlate** (Mandatory Rule 1). **Replies to the owner are written for comprehension, not for a script ratio** — CLAUDE.md rule 9. (This line said "Sinhala prose ≥70%" until #170; that percentage is the retired proxy.)

## Branch naming
| Work | Prefix | Example |
|------|--------|---------|
| Feature | `feat/` | `feat/issue-12-text-translate` |
| Bug fix | `fix/` | `fix/issue-25-cache-double-count` |
| Research scaffolding | `research/` | `research/issue-8-mlkit-download` |
| Doc-only | `docs/` | `docs/adopt-mandatory-rules` |
| Refactor (no behaviour change) | `refactor/` | `refactor/extract-usagepolicy` |

## Commit message format
```
<imperative subject ≤72 chars>

<body — WHY, not what>

Fixes: #<issue>
Co-Authored-By: Claude <noreply@anthropic.com>
```

## Issue → PR full path (lifecycle)
```
Open GitHub issue (label: type + severity + priority)
        ↓  triage
If root cause unknown / non-trivial:
  docs/research/issue-NN-<slug>.md  (read-only investigation, hypotheses + disconfirmation tests)
        ↓
docs/plan/issue-NN-<slug>.md  → status: accepted   (BEFORE any code)
        ↓
branch feat|fix/issue-NN-<slug>  → implement + tests  (commit: Fixes: #NN)
        ↓
push branch → open PR (body: Plan: docs/plan/issue-NN-*.md + Review-evidence)
        ↓
CO-VERIFY (≥1 lens ≠ author; cross-model if high-risk) + tests/acceptance green
        ↓
merge (squash or merge-commit; never rebase-merge) → issue closes
```

Lifecycle states: `Reported → Triaged → Investigating → Planned → In-Review → Merged → Verified`.

## How a PR closes an issue (closing-keyword safety)

GitHub auto-closes an issue when a merged PR's **body** *or* a merged **commit message** contains a closing keyword next to the issue number. This has already cost us: PR #202's body wrote `auto-close #173` while *arguing #173 must stay open* — GitHub read `close #173` and closed it (#217). `Refs: #173` in every commit did not save it: GitHub closes from the body independently of what the commits say.

**The grammar** — 9 keywords, case-insensitive, an optional colon: `close` `closes` `closed` · `fix` `fixes` `fixed` · `resolve` `resolves` `resolved`, each **immediately before** `#N` (`Fixes #10`, `closes: #10`). The keyword must come *before* the number — `#10 is fixed` links nothing. It fires even as the tail of a hyphenated word (`auto-close #173`), which GitHub does **not** document — so never trust wording to be "obviously not a close".

**To close on purpose:** `Fixes: #N` in the commit trailer (rule 3) and in the PR body's `## Issue` slot. Nothing else.

**To mention an issue you must NOT close:** keep all nine keywords away from its number.
- Prefer `Refs: #N` — no closing keyword, so it is inert by construction.
- Or lead with the number: `#173 stays open`, `#173 (remains)`.
- `does not close #173` **still closes #173** — GitHub sees `close #173` and drops the "does not". Negation is not protection; distance from the keyword is.

**The pre-merge check (ground truth, not a guess):** GitHub publishes exactly which issues a body will close. Confirm it equals your intent before merging —

```
gh pr view <N> --json closingIssuesReferences --jq '.closingIssuesReferences[].number'
```

An issue you only *discussed* appearing in that list is the #217 defect. This is a Definition-of-Done item (`docs/ENGINEERING_STANDARDS.md` §2) and a `/land-pr` pre-merge step.

## Severity × Priority
| | Meaning | Response |
|---|---|---|
| **S0/P0** | crash / data loss / security / revenue-zero | hotfix ASAP |
| **S1/P1** | major feature broken | next release |
| **S2/P2** | workaround exists | backlog |
| **S3/P3** | cosmetic / trivial | someday |

## PR co-verify (Mandatory Rule 5)
- Every PR carrying **logic** (code, `.github/`, CLAUDE.md/process, plan-docs) needs **≥1 co-verify lens by a session/agent OTHER than the author** before merge. Self-re-read ≠ co-verify.
- **High-risk** (concurrency, billing/subscription, usage/limits, data, consent/privacy) → **adversarial code-trace + ≥1 cross-model lens** (verifier model ≠ author model).
- PR body carries **Review-evidence** with ground-truth (file:line / test output / gate result) — a bare "LGTM" is not evidence. Verdict level stated (CLEAN / OPEN:<items>).

## What NOT to do
- ❌ Copy code from Tranzlate (Rule 1)
- ❌ Direct push/merge to `main`
- ❌ `--no-verify` (human-only emergency)
- ❌ Rewrite shared history / force-push
- ❌ Ship a feature without its tests + edge-case/outcome states
- ❌ Speculative fix without research record when root cause is unknown

## Enforcement
Free-plan private repo → no server-side branch protection. Enforced by: a Claude `PreToolUse` push-guard (blocks direct `main` push) + this discipline + PR co-verify. `git config core.hooksPath .githooks` once per clone if a `.githooks/pre-push` is added.
