---
name: no-direct-push-to-main
description: MANDATORY — never push directly to main/master; all changes reach main only via Pull Request
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 2047cca1-6990-45a8-8d05-a871907485c1
  modified: 2026-07-21T00:39:27.203Z
---

**MANDATORY rule:** `main` (හෝ `master`) branch එකට **කිසිදා direct push කරන්න එපා**. සියලුම වෙනස්කම් feature / `develop` branch එකකින් **Pull Request හරහා පමණක්** `main` එකට යා යුතුයි: `git push origin <feature>` → `gh pr create --base main`. `git push --no-verify` යනු human-only emergency override එකක් — Claude මෙය කිසිදා නොකළ යුතුයි.

**Why:** User මෙය mandatory project rule එකක් ලෙස add කරන්න කිව්වා (2026-07-21). Server-side branch protection එක free private repo (`sendtodilanka/Tranzlate`) එකට නැති නිසා (GitHub Pro ඕන), enforcement ස්ථර දෙකකින්: `.githooks/pre-push` (git-level) + `.claude/hooks/block-push-to-main.sh` (Claude `PreToolUse` hook).
**How to apply:** `git push origin main`, `git push -u origin main`, `git push origin HEAD:main`, හෝ `main` මත සිට bare `git push` — කිසිවක් කරන්න එපා. Feature branch + PR විතරයි. See [[no-speculation-verified-data]], [[sinhala-prose-mandate]].
