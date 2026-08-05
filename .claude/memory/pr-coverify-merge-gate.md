---
name: pr-coverify-merge-gate
description: MANDATORY — every logic PR needs >=1 co-verify lens by a session/agent other than the author before merge; high-risk needs adversarial trace + cross-model; review-evidence cited
metadata:
  type: feedback
---

**MANDATORY (Rule 5, adopted from ZyntaStack #168/#14):** logic තියෙන හැම PR එකකටම merge එකට කලින් **author නොවන session/agent එකකින් ≥1 co-verify lens** — self-re-read = co-verify **නෙවෙයි** (correlated-judge failure). **High-risk** (concurrency, billing/subscription, usage/limits, data, consent/privacy) → **adversarial code-trace + cross-model lens** (verifier model ≠ author model; Workflow `model:` override හෝ different-model session). PR body එකේ **Review-evidence** — ground-truth (file:line / test output / gate result), bare "LGTM" evidence නෙවෙයි; verdict (CLEAN / OPEN:<items>) state කරන්න.

**Why:** User adopt කරන්න කිව්ව ZyntaStack Rule #14 එකෙන් (2026-07-21) — grounding incident: self-declared "no co-verify" PR එකක් self-reference bug එකක් ship කළා.
**How to apply:** මගේම PR එකක් merge කරන්න කලින් fresh lens එකක් (agent/workflow) run කරන්න. High-risk → cross-model. See [[issue-first-pr-only-workflow]].

**Gate 3න් 1යි — conflate කරන්න එපා (canonical: `docs/ENGINEERING_STANDARDS.md` rule 8):** co-verify එකෙන් බලන්නෙ **change එකක්** හරිද කියලා — මේ rule එක, per-PR, merge එකට කලින්. ඊට කලින් gate 2ක් තියෙනවා: **design-debate** එකෙන් build කරන්න කලින් **design එක තෝරනවා** (advocate); **red-team** එකෙන් තෝරගත්ත **අවදානම්** design එකකට adopt කරන්න කලින් පහර දෙනවා (attacker). පිළිවෙළ: **තෝරනවා → red-team → build → co-verify.** Design-debate එකේ adversarial judging එකෙන් winner තෝරනවා — winner එක silent-fail වෙනවද කියලා stress-test කරන්නෙ නෑ; ඒක red-team එකේ වෙනම වැඩේ.
