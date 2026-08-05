# Memory Index — tranzlate-app

**`CLAUDE.md` is the canonical rulebook.** These files predate rules 8–11 and do
not cover them; where a file and CLAUDE.md disagree, CLAUDE.md wins and the file
is stale. Rule 11 is why: a rule kept in more than one place drifts.

- [No code copy from Tranzlate](no-code-copy-from-tranzlate.md) — MANDATORY; learn behaviour only, verify vs standards, write fresh; Tranzlate is a suspect reference
- [Issue-first / PR-only workflow](issue-first-pr-only-workflow.md) — MANDATORY; issue before work, PR-only, plan-doc gate, Fixes:#N (adopted from ZyntaStack)
- [Research-first / no speculative fix](research-first-no-speculative-fix.md) — MANDATORY; research record + disconfirmation before a fix; single-hypothesis capped 70%
- [PR co-verify merge-gate](pr-coverify-merge-gate.md) — MANDATORY; ≥1 co-verify lens (≠author) before merge; cross-model for high-risk
- [No direct push to main](no-direct-push-to-main.md) — MANDATORY; all changes reach main via PR only, never `git push --no-verify`
- [Sinhala prose mandate — RETIRED](sinhala-prose-mandate.md) — a percentage I reported as passing and never computed; CLAUDE.md rule 9 replaces it with comprehension
- [No speculation / verified data only](no-speculation-verified-data.md) — every claim needs a source or disconfirmation experiment, else "verified data නෑ"
- [`git grep -E` ignores `\b`](git-grep-ignores-word-boundary.md) — #221 the false-zero enumeration trap; a `\b` count of 0 beside a matching bare term is the signature; use `grep -rE 'Sym\b'` / `git grep -wE 'Sym'`, never `git grep -E 'Sym\b'`
- [gradlew needs JAVA_HOME (Android Studio JBR)](gradlew-needs-java-home-jbr.md) — terminal ./gradlew fails without JAVA_HOME set to the JBR (Java 21)
