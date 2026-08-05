# Plan — #268: commit claude-android-ninja and enforce its FULL use via `android-builder`

status: accepted
(accepted basis: owner directives, 2026-08-04 — *"verify 100% … the build agents use
claude-android-ninja … It mandatory"* and *"utilise all the skills (37) of the package
where possible, not just a few."* He chose enforcement by a dedicated agent type. A
deliberate, owner-authorized exception to the governance freeze: it enforces a rule he
declared mandatory. Refs: #268.)

## Why (proven, not assumed)

`.claude/skills/claude-android-ninja/` (40 reference files) was in the working tree but
**never committed**. Dispatched build agents run in their own worktrees, which lack
untracked files, so the references are absent there. The #267 co-verify lens caught it;
confirmed directly — the #241 build agent **read 0 of the 40 reference files**. It
launched `SKILL.md` (session-level) but could not read the knowledge. That is both the
owner's "only a few, not all 37" complaint and the #213 shape (a mechanism that cannot
fire), same root cause.

## The fix (one unit)

1. **Commit** `.claude/skills/claude-android-ninja/` — 75 files (`.DS_Store` stripped)
   — so it exists in every worktree and clone. `.claude/` is not a Gradle module, so
   `spotless`/`detekt` (`build.gradle.kts:65-75`, module-`src`-scoped) never scan it;
   verified no preflight impact.
2. **`.claude/agents/android-builder.md`** — a build-agent type that:
   - **preloads** the skill (`skills: [claude-android-ninja]` frontmatter — mechanical
     injection, per the docs the #267 lens cited, stronger than a prose instruction);
   - **requires reading ALL reference files that bear on the task**, and naming them in
     its report, so "used" is verifiable not just "launched";
   - **never** `spawn_task` (owner's chip ban → follow-ups become issues);
   - `disallowedTools: [Agent]` — no sub-spawning, matching one-agent-one-worktree.
3. Dispatch Android/Kotlin/Compose/Room work as `agentType: android-builder`.

## Verify

- **Re-co-verify #267** (the definition changed and 75 files were added): confirm the
  skill is now present in a fresh worktree (re-run the lens's own worktree probe), the
  `skills:`/`disallowedTools:` frontmatter is valid, and the mandate to read all
  references is unambiguous.
- **After merge**, the next build task (#240) is dispatched as `android-builder`, and
  its transcript is parsed to confirm it **read N reference files** (not zero) without
  the brief asking — the guarantee this issue is for. Invocation ≠ adherence: the
  co-verify lens still checks the code follows the skill's patterns.

## What it does NOT change

No new hooks, no detection systems, no Verified-Touch pillars — the freeze on those
holds. One skill committed, one agent file, enforcing one owner rule.
