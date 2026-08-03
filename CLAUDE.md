# CLAUDE.md — Tranzlate (new app · clean rebuild)

Guidance for any Claude Code session working in this repo. Read this first, then `docs/`.

## What this is
The ground-up rebuild of the **Tranzlate** Android translator. Kotlin · Jetpack Compose · Material 3.
- **Old app** = `github.com/sendtodilanka/Tranzlate` — **READ-ONLY reference only.** Never push to it. Study it freely (we own it) to learn behaviour + intent — but NEVER copy its code (Rule 1).
- **Archive** = `github.com/sendtodilanka/tranzlate-dirty-room` (branch `archive`) — full audit, UX evaluation, captures, all planning. The "what to build" essentials are copied into `docs/` here.

## ⛔ Mandatory rules (BLOCKING — override defaults, apply every response)

**1. NEVER copy code from Tranzlate — strictly prohibited.** Learn behaviour/pattern/intent only; write everything fresh, our own way. **Before writing, deep-investigate** the thing against Google / Android / Material / industry docs + internet sources, compare, and judge: did Tranzlate do it right or wrong? If wrong → do it right here. **Nothing is correct just because Tranzlate had it** — Tranzlate is a **suspect reference, not a verified source.** (No-speculation rule still applies: cite a source or say "verified data නෑ".)

**2. Tranzlate repo is READ-ONLY.** Reference/study only. Never push, never merge, never modify it.

**3. Issue-first + PR-only (adopted from ZyntaStack).** No non-trivial work without a **GitHub issue opened first.** Never direct-push/merge to `main` — **PR mandatory.** Non-trivial work needs a **plan-doc** `docs/plan/issue-NN-<slug>.md` with `status: accepted` **before any code.** Every fix commit carries a **`Fixes: #N`** trailer. Full workflow: [`CONTRIBUTING.md`](CONTRIBUTING.md).

**4. Research-first — no speculative fixes (adopted).** Unknown root cause / non-trivial → a **read-only research record** `docs/research/issue-NN-<slug>.md` first. **Disconfirmation discipline:** every hypothesis paired with an experiment that would prove it wrong; single-hypothesis confidence capped at 70%.

**5. PR co-verify merge-gate (adopted).** Every logic PR needs **≥1 co-verify lens by a session/agent OTHER than the author** before merge (self-re-read ≠ co-verify). High-risk (concurrency, billing/subscription, usage/limits, data, consent/privacy) → adversarial code-trace + a **cross-model** lens. PR body cites review evidence (file:line / test output — not a bare "LGTM").

**6. Verify, don't assume (build/test gate).** No feature/fix ships without its tests + acceptance passing (per the feature's TEST_A11Y contract). "Happy path only" is not acceptable (see EDGE_CASES no-dead-end rule). `--no-verify` is a human-only emergency override — Claude never uses it.

**7. Every composable ships `@PreviewLightDark` (BLOCKING).** No Compose UI merges without previews — **screens AND every custom item built from standard M3 parts** (list rows, row action buttons, cards, chips, dialogs, counters…). One preview per meaningful STATE (e.g. an offline row: downloadable / downloading / downloaded / deleting / failed). House style: preview lives in the SAME file, `private`, wrapped in `TranzlateTheme { … }` (items add `Surface(color = colorScheme.surface)`), literal fake data — **never DI**, never a ViewModel. Named `<Composable><State>Preview`. Owner reviews UI from previews, so a missing preview is a missing deliverable.

**8. The git guard rails are MANDATORY — never bypass, never disable (BLOCKING).** `.claude/hooks/guard-git.sh` (wired in `.claude/settings.json`) denies three acts, and the `/land-pr` skill carries the procedure it protects. **Landing any PR goes through `/land-pr`.** The guard exists because `main` was lost to the SAME accident twice — PR #108, then PRs #132/#134: sibling branches, each green alone, merged off a base older than the tip, so no CI run could ever see the combination. The rule was already written in the checklist when the second one happened; writing a rule down is not enforcing it. If a hook fires, **fix the cause** — a hook edited, skipped, or worked around is a rule violation, not a shortcut. It fails OPEN by design (no `gh`, no network, unparseable command → allow), so a denial is always a real finding.


**9. Write so the OWNER can understand it (BLOCKING).** Dilanka does not write code. The old rule said "Sinhala prose ≥70%", and I reported every reply as passing it while he understood none of them. A sentence from that day: *"Classifier එක PR body එකේ 'tracker' හොයනවා, ඒ නිසා #165 එකට තමන්ගේම hook එකෙන් merge වෙන්න බෑ — substring match එකක් නිසා `#60` satisfied වෙනවා hex colour එකකින්."* — **0% meaning to him.**

**And it never passed.** Measured (#170 co-verify): 27.5% of characters are Sinhala, 26.7% of letters, 53.3% of UTF-8 bytes — the most generous reading is still short of 70. The rule was not a bad proxy that I optimised. **It was a proxy I never computed, and asserted compliance with anyway** — a rule 10 violation about the rule that was supposed to be governing me. The figure "75%" stood in this file until #170; it had never been computed either.

The requirement is comprehension, not script:
- **Lead with what it means for the app or for him**, not with the mechanism. "Downloads could start on mobile data without asking" — not "the consent gate's metered check".
- **Everyday comparisons instead of jargon.** A guard that lets bad code through is *a lock that only works if you turn the key one particular way*.
- **English only for things he must act on** — a file he opens, a button he taps, a decision he makes. Never for describing how something works.
- **Patient and long beats short and dense.** He asked for long-form explanation; compression is what makes it unreadable.
- **The check:** would this sentence mean anything to someone who has never seen code? If not, rewrite it — do not translate it.

**Scope:** chat replies to the owner, and owner-facing HTML. Commit messages, PR bodies, issues and plan-docs stay technical — engineers and review lenses read those.

**10. No speculation (verified data only)** · **`./gradlew` needs JAVA_HOME=Android Studio JBR** — see `.claude/memory/`.

**11. Enumerate, reproduce, mutate-first, verify (BLOCKING — the four causes).** In one session a co-verify lens caught a defect I had introduced in **five** PRs (#142, #145, #146, #156, #159). Not carelessness — four specific skips, each now a step that cannot be waved through:
- **Enumerate before changing.** `grep -c` every call site and path of the thing being touched, and put **"Call sites: N found, N changed"** in the PR body. #146 converted 2 of 6; #150 fixed 5 of 9. Both were one grep away.
- **Reproduce the harm, then re-run that same reproduction.** Put **"Reproduced: …"** in the PR body — before and after, or `n/a` with a reason. #149 was "fixed" without ever running the harm it described, and the harm survived on the path nobody checked.
- **Decide the mutation BEFORE writing the test.** A mutation chosen afterwards gets shaped by the code it just read: a sort test whose example data happens to be in order, a stack test at depth 2 where the root hides the missing guard. Both shipped.
- **Verify documents against code.** The ruling's file list, the Konsist gate's silence and an issue's own count were each wrong. A plan is a plan — including this project's accepted ones.

**These four are the single source.** `.claude/skills/land-pr/SKILL.md` and `.claude/agents/co-verify-lens.md` point here instead of restating them — there were three copies and they had already started to drift.

`.claude/hooks/guard-pr.sh` denies `gh pr create` without the first two markers; `.claude/hooks/guard-tracker.sh` denies `gh pr merge` when no plan doc mentions the PR (the owner's tracker rule broke three times in one session). **Both hooks fail open** — no `gh`, no plan docs, a command they cannot resolve, a `grep` that could not run → allow — so, as with the git guard, a denial is a real finding. `.claude/agents/co-verify-lens.md` sends every lens back to these four and pins a model so a high-risk PR gets a cross-model reviewer by default; an agent definition has no fail-open mode, and a lens that is never run is simply a rule 5 violation. A promise to be careful is the same shape of non-fix as the checklist that was already written the second time `main` broke.

**12. Check the claim, not just the change (BLOCKING — the six shapes).** Rule 11 is about code changes, and its hooks check that a marker is **present** — not that what it says is **true**. `Call sites: 4 found, 4 changed` passed the hook in #171 while being wrong. In one session my own errors were almost all in **claims and orchestration**, and every one was cheaply checkable:

- **A search written from what I already know finds what I already know.** #171 missed a fifth call site because `CONTRIBUTING.md` said "Sinhala prose ≥70%" and my pattern was `70% Sinhala|Sinhala script`. #180 then asserted the cut was "recorded nowhere" — it was **two lines** below the passage I had just read. #183 attributed a mark to the wrong row from a **flattened** parse. → **Two independent searches that could not both miss the same thing**, named in the PR body. `guard-pr.sh` denies `gh pr create` without **`Enumerated by:`**. **Its limit, stated as plainly as the device hook's:** this is a **presence** check. The #207 lens wrote `Enumerated by: nothing, I did not search at all, this is a lie` and was **allowed**, and appended to #171's real body a line describing *one* search dressed up as two — also allowed. It catches total absence, not a bad enumeration. Worth having for the reason `Call sites:` is — naming the second search is where you notice you do not have one — but do not read it as proof that you did.
- **A number stated without the command that produced it.** I told the owner a sentence was "75% Sinhala letters". It is 27.5%, and I had never computed it — a rule 10 violation inside the rulebook. I then repeated "measured at fontScale 0.5/0.85/1.0" as evidence when the harness **cannot detect font scale at all** here. → **Paste the command beside the number, or do not state the number.**
- **Two things compared without establishing they are comparable.** I showed the owner two screenshots as proof of a timing race. They were **two different builds** — `fake` vs `prod`. → **Record the provenance of each capture before comparing them.**
- **A shared resource used without an exclusive claim.** Two agents on one emulator **at least three times** — #185, #187, #194. The #207 lens found the third: #185's own comment says *"two agents were driving `emulator-5554` concurrently"*, and it is chronologically the **first**, not an afterthought. **A retrospective that undercounts its own subject is the fourth cause wearing the coat of the rule meant to stop it.** **Nothing errors** — `adb` serves both — so the symptom is a screenshot that disagrees with the code, which reads as a bug in the code. → `.claude/hooks/device-claim.sh` makes the contention visible. It **warns rather than denies**, because a hook sees a command and not a caller, and a guard that blocks the legitimate holder gets switched off. **And it did not work (#213).** It returned silently unless a claim file already existed, and the only place the claim protocol was written down was a comment *inside the hook*, which no agent reads — so the first agent never claimed, the file never existed, and the second agent was never warned. **It had never fired and could not have.** It passed review because it was tested by piping a payload at it with a claim file the test itself planted, and nobody asked what had to be true in the real repo to reach that branch — **a presence check standing in for a behaviour check, committed while building the mechanism against exactly that.** It also gave `adb shell` and `adb -s emulator-5556 shell` byte-identical output, so the one rule protecting the owner's own handset was the one thing the device hook did not check. **A hook is not enforcement until you have shown what makes it fire in the real repo, not in the test.**
- **A destructive command where a copy would do.** `git checkout --` destroyed uncommitted work twice; a conflict splice cut **mid-construct**, dropping a function's braces and the next KDoc's opener. → **`cp` for mutation backups. Never a boundary as a cut point.** `.claude/hooks/guard-restore.sh` warns when `git checkout -- <path>` or `git restore <path>` targets a file with uncommitted changes. The #207 lens said calling this shape unmechanisable was inconsistent with shipping `Enumerated by:` for a fuzzier one, and it was right. **It then happened a third time, while that hook was being tested:** a probe was appended to this file, `git checkout --` cleaned it up, and three edits to this very rule went with it. Caught only by re-reading the file instead of trusting the script that said it had written.
- **Reasoning from a similar case without checking the difference.** I told an agent to cancel the star write on transition, by analogy with `translateJob`/`speakJob`. It refused and **proved by mutation** that it would have silently discarded the user's save — a stale translation is worthless, a stale save is the thing the user asked for. Also: my `Throwable` rationale (`CancellationException` **is** an `Exception`), and framing a question `EDGE_CASES.md:114` had already settled as open. → **When an agent contradicts a brief with evidence, the evidence wins.** Four of my briefs were wrong this way in one session and every correction came back with a command attached.

**13. ⏳ TEMPORARY — BLOCKING while it lasts: the rev5 completion plan is the standing objective (#256).** Owner, 2026-08-03: *"All information provided pertains exclusively to the Language screen rev5 design. The overarching objective is the complete implementation of rev5. Resolve all existing issues, including open GitHub issues; **do not implement new features referenced in the issues section.** Conduct a comprehensive audit of the current codebase related to rev5 and remediate any identified deficiencies. Proceed with the remaining rev5 implementation until completion."*

**The plan is `docs/plan/issue-130-rev5-completion.md`** and it is `status: accepted`. It classifies all 59 then-open issues once, so the boundary is never re-argued per PR: **8 are new features he excluded and must NOT be built** (#7 #8 #20 #22 #78 #102 #139 #212 — they stay open and untouched), **10 are a different surface** (monetization/Access/History, including **#114, which means the shipped app earns nothing — deferred by scope, not by importance**), and **41 are in scope**: 14 user-visible rev5 defects, 4 test gaps, 23 gates and process.

**Three phases, in order — fix → audit → finish.** Phase 1 is ordered by what unblocks what, not by severity (#241 cannot be verified honestly until #253 and #231 land; every enumeration depends on #221). Phase 2 is an audit with the official `pr-review-toolkit` specialists, not a re-read — the last such pass found **ten** defects in code that had already passed a co-verify lens each and merged (#234–#245). **Phase 3 (PR-20…PR-28) does not start while phase 1 has an open S0/S1 in groups A or B**, per his standing fix-before-build rule.

**What would retire this rule:** rev5 complete and #256 closed. **What would invalidate the plan rather than complete it:** if phase 2 returns findings that change the rev5 *design* rather than the code, phase 3's PR list is stale and the ruling reopens before anything is built — **#203 and #184 are the two most likely to do that**, both being frames that may not describe a producible state.

**Why this is in the rulebook and not only in the plan doc:** he asked for it explicitly, to prevent the objective being lost across sessions. A plan doc is read when someone goes looking; a mandatory rule is read first.

> Rules 3-6 are adopted + adapted from the mature `zw-infra-zyntastack` / `zw-voice` projects (Forgejo→GitHub, voice/hardware→Android). Canonical detail: `CONTRIBUTING.md` + `.claude/memory/`.

## Why we're rebuilding
Old app works (v1.0.4) but is architecturally broken — 76 findings, crash-on-launch on the refactor branch, 0-of-58 core UI patterns correct vs standards, and production monetization is non-functional (ad revenue 0 = ships Google **test** ad ids, broken paywall = hardcoded `Idle`, consent auto-granted). Keep the old app shippable; build this correctly in parallel.

## North star — behave like Google Translate
The whole app's behaviour must equal the **Google Translate** Android app. Tranzlate's extras (multiple engines, subscription, ads) layer on top and must not break that core UX.

## The structure (`docs/APP_STRUCTURE.html`)
**Every big job has ONE home. Screens just ASK; they don't do the work.**
- **Screens** (Text · Voice · Camera · Dialog · History · Settings) →
- **4 shared "brains"** (one home each): 🌐 Translation · 👤 Access(subscription) · 📊 Usage(limits) · 📣 Ads →
- **Data + outside services** (languages · history · saved · Google · MLKit · Cloud AI · Play billing)

**Advanced (required):**
- **Reusable library modules** — `:subscription` `:ads` `:consent` — droppable into OTHER apps with clean APIs. (Old app's `:subscription` is already a library ✓.)
- **White-label** — one codebase → many apps (offline / spanish / french …) via product flavors. Each app: own `applicationId` + signing + icon + name + **config** (AdMob ids, Qonversion keys, default language, feature toggles). Adding an app = adding a flavor, no code changes.

## Engines & languages (`docs/specs/02`)
- Default **MLKit offline** (free, private) → **GOT** free online (unofficial `translate_a/single` — **risk accepted by owner**) → **GCT** Google Cloud Translation (paid, accurate).
- **Offline model download:** MLKit's `RemoteModelManager.download()` returns `Task<Void>` — **no progress %, no cancel** (verified). Use indeterminate progress + delete-to-cancel + WorkManager. Don't promise real % or true stop.
- **Language UX:** separate the **Picker** (select any language) from the **Offline manager** (download/delete in Settings). Full list = static bundled (no Cloud API call from the phone).

## Non-negotiable foundations (`docs/specs/00-foundations/`) — read before building
- **DECISIONS.md** — D-0..D-4 + **canonical conventions C-1..C-13** (testTag naming `tt_<feature>_<control>`, string-key authority, engine enums + mapping, cache rule, counter format…). Every feature inherits these — don't re-litigate.
- **DATA_MODEL.md** — typed `Translation` entity, prefs/usage keys, engine mapping.
- **DESIGN_SYSTEM.md** — real color tokens (light/dark hex, WCAG-checked), type scale, spacing, motion, adaptive dimensions. No Material template stubs.
- **EDGE_CASES.md** — **Availability** ("can I start?") + **Outcomes** ("what if it fails?") + the **NO-DEAD-END rule.** Every action needs both; every error/empty state must guide the user. Happy-path-only is not acceptable.
- **STRINGS / TEST_A11Y** (per feature) — every string key (en/fil/pt-rBR) + fake-engine golden outputs + testTags + per-control a11y.

> **Buildable = feature spec + these foundations + conventions, consistency-checked.** Proven this session: a spec alone is not buildable.

## Learn from old app — but write EVERYTHING fresh (see Mandatory Rule 1)
**NEVER copy code from Tranzlate. Strictly prohibited.** Learn its *behaviour / feature intent / content* only — then write fresh, our own way. Before writing anything, deep-investigate against Google/Android/Material/industry docs + internet, compare, and decide if Tranzlate did it right or wrong. If wrong, do it right here. **Nothing is correct just because Tranzlate had it that way — treat Tranzlate with suspicion; it is not a verified source.**
- **Re-derive freshly (don't copy):** the language catalog (verify BCP-47 + capabilities), entity shapes (design clean), client integrations (write to current API docs), strings/copy (rewrite per Material UX writing), brand direction (redesign per DESIGN_SYSTEM).

## Build order (`docs/BUILD_ROADMAP.md`)
1. **Scaffold** the multi-module project (version catalog, Compose/Hilt, DI) + design-system tokens + adaptive nav shell (`NavigationSuiteScaffold`) + the white-label flavor scaffold (one flavor to start).
2. **Build the 4 brains** as modules — Translation (engines + fallback + offline-downloads manager), Access, Usage, Ads. This is the core.
3. **Text feature vertical** (`docs/specs/01-text-translation.md`) first, with tests.
4. Then Camera → Voice → Dialog → secondary (History, Offline-downloads, Settings, Paywall).
> MVP subset (roadmap §3): Text + Camera + Language picker + History/Saved + Subscription + engines + nav shell. Defer Voice/Dialog/Collections to v2.

## Git workflow (this repo)
Feature branch → PR to `main`. A Claude push-guard blocks direct pushes to `main` (feature-branch pushes are fine). Commit/push only when asked.

## Project rules (`.claude/memory/`)
- **Write so the owner can understand it** — rule 9 above is the statement of it. This line used to restate it as "replies ≥70% Sinhala script"; that percentage is the retired proxy, and a rule restated in two places is a rule that drifts (see rule 11).
- **No speculation** — every claim needs a source or a disconfirmation experiment, else say "verified data නෑ".
- **Terminal `./gradlew`** needs `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (Java 21; no standalone JDK on this machine).

## First steps for the build session
1. Read `docs/APP_STRUCTURE.html` + `docs/specs/00-foundations/DECISIONS.md`.
2. Decide the module graph; scaffold the Android project (multi-module + version catalog + Compose/Hilt).
3. Wire the design-system tokens + adaptive nav shell + one white-label flavor.
4. Build the **Translation brain** (engines + fallback + offline-downloads manager).
5. Build the **Text feature** per `docs/specs/01-text-translation.md`.
