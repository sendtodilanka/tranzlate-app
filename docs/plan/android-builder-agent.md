# Plan — a dedicated `android-builder` agent type that always loads claude-android-ninja

status: accepted
(accepted basis: owner directive, 2026-08-04 — verbatim: *"verify 100% and certify
to me the build agents use claude-android-ninja skills set when developing. It
mandatory."* When shown the enforcement choice, he chose "build the android-builder
agent type (the real guarantee)" over relying on brief-discipline. This is a
deliberate exception to the standing governance freeze, made by the owner, because
it enforces a rule he declared mandatory.)

## Why

Skill-use was mandatory only in prose: each build brief had to *say* "load
claude-android-ninja", and the orchestrator had to *verify* the transcript
afterwards. A brief that forgot the line loaded nothing. Verified for #241 (agent
invoked the skill), but that is after-the-fact checking, not a guarantee.

## The mechanism

`.claude/agents/android-builder.md` — a subagent type whose system prompt makes
loading `claude-android-ninja` its first, non-negotiable action, the way
`co-verify-lens` pins a cross-model default. Android/Kotlin/Compose/Room build work
is dispatched with `agentType: android-builder` instead of `general-purpose`, so the
skill is in context by definition, not by brief.

## What it does NOT change

- It does not enforce *adherence* — loading the skill puts its guidance in context;
  whether the written code follows it is still the co-verify lens's job.
- It does not touch the freeze on other governance: no new hooks, no detection
  systems, no Verified-Touch pillars. One agent file, enforcing one owner rule.

## Verify

- `co-verify-lens` reviews this PR (rule 5): does the definition actually mandate the
  skill as step one; is it usable (tools, no over-restriction that blocks the `Skill`
  call); does it correctly defer merge and adherence-checking.
- After merge: the next build task (#240) is dispatched as `agentType:
  android-builder`, and its transcript is parsed to confirm the `Skill` call fired
  without the brief having to ask — that is the guarantee this PR is for.

## Note recorded in memory

`orchestration-and-landing.md` now says Android build work is dispatched as
`android-builder`, and how to verify the `Skill` call in a transcript.
