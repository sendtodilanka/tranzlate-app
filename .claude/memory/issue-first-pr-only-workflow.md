---
name: issue-first-pr-only-workflow
description: MANDATORY — issue-first, PR-only (no direct main merge), plan-doc gate before code, Fixes:#N trailer; adopted from mature ZyntaStack projects
metadata:
  type: feedback
---

**MANDATORY (Rule 3, adopted from ZyntaStack):** කිසිම non-trivial වැඩක් **GitHub issue එකක් open නොකර** පටන් ගන්නේ නෑ. **Direct-push/merge to `main` තහනම් — PR අනිවාර්යයි.** Non-trivial → `docs/plan/issue-NN-<slug>.md` (`status: accepted`) **code එකට කලින්.** හැම fix commit එකකම **`Fixes: #N`** trailer. Branch: `feat/` `fix/` `research/` `docs/` `refactor/` + `issue-NN-<slug>`. Merge = squash/merge-commit, **rebase-merge නෑ**. Lifecycle: Reported→Triaged→Investigating→Planned→In-Review→Merged→Verified.

**Why:** User adopt කරන්න කිව්වා `/Users/dilanka/Documents/Claude Sessions/ZyntaStack/zw-infra-zyntastack` + `zw-voice` (mature, battle-tested) projects වලින් (2026-07-21). Full protocol: repo `CONTRIBUTING.md`.
**How to apply:** issue → (research doc if unknown) → plan-doc accepted → branch → implement+tests (Fixes:#N) → PR (co-verify) → merge. See [[research-first-no-speculative-fix]], [[pr-coverify-merge-gate]], [[no-direct-push-to-main]].
