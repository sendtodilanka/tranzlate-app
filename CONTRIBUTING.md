# Contributing — tranzlate-app

> Solo developer (Dilanka) + AI. Process discipline **adopted + adapted from the mature ZyntaStack projects** (`zw-infra-zyntastack`, `zw-voice`) — retargeted Forgejo→**GitHub**, voice/hardware→**Android**.

## TL;DR
1. **Issue-first** — open a GitHub issue before any non-trivial work.
2. **PR-only** — never direct-push/merge to `main`. Every change lands via PR.
3. **Plan-doc gate** — non-trivial work needs `docs/plan/issue-NN-<slug>.md` with `status: accepted` **before code**.
4. **Research-first** — unknown root cause → `docs/research/issue-NN-<slug>.md` (read-only investigation, disconfirmation discipline) before a fix.
5. **`Fixes: #N` trailer** — every fix-class commit references its issue.
6. **PR co-verify** — ≥1 lens by a session/agent ≠ author before merge; cross-model for high-risk.
7. **Verify with tests** — no merge without the feature's tests + acceptance green.
8. **No code copied from Tranzlate** (Mandatory Rule 1). **Sinhala prose ≥70%.**

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
