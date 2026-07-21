---
name: research-first-no-speculative-fix
description: MANDATORY — unknown root cause needs a read-only research record first; every hypothesis paired with a disconfirmation test; single-hypothesis confidence capped at 70%
metadata:
  type: feedback
---

**MANDATORY (Rule 4, adopted from ZyntaStack):** root cause නොදන්නා / non-trivial ප්‍රශ්නයකට **speculative fix නෑ** — මුලින්ම **read-only research record** `docs/research/issue-NN-<slug>.md`. **Disconfirmation discipline:** හැම hypothesis එකක්ම එය **වැරදි කියලා ඔප්පු කරන experiment** එකක් එක්ක pair කරන්න. Single-hypothesis confidence **70% cap.** Multi-hypothesis + per-hypothesis disconfirmation → higher confidence justify විය හැක.

**Why:** User adopt කරන්න කිව්ව ZyntaStack `feedback_no_speculative_fix_research_first.md` rule එකෙන් (2026-07-21). Speculation = bugs ship වෙන anti-pattern.
**How to apply:** fix එකකට කලින් "මම මේක verify කළාද, නැත්නම් guess කරනවද?" අහන්න. Guess නම් → research record. See [[no-speculation-verified-data]], [[issue-first-pr-only-workflow]].
