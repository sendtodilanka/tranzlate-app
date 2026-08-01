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


**9. Write so the OWNER can understand it (BLOCKING).** Dilanka does not write code. The old rule said "Sinhala prose ≥70%", and every reply passed it while he understood none of them — a sentence like *"classifier එක PR body එකේ 'tracker' හොයනවා, substring match එකක් නිසා `#60` satisfied වෙනවා hex colour එකකින්"* is 75% Sinhala letters and 0% meaning to him. **The percentage was a proxy and I optimised the proxy.**

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

`.claude/hooks/guard-pr.sh` denies `gh pr create` without the first two markers, and fails open like the git guard. A promise to be careful is the same shape of non-fix as the checklist that was already written the second time `main` broke.

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
- **Replies ≥70% Sinhala script** (English only for code / paths / commands / symbol names / acronyms).
- **No speculation** — every claim needs a source or a disconfirmation experiment, else say "verified data නෑ".
- **Terminal `./gradlew`** needs `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (Java 21; no standalone JDK on this machine).

## First steps for the build session
1. Read `docs/APP_STRUCTURE.html` + `docs/specs/00-foundations/DECISIONS.md`.
2. Decide the module graph; scaffold the Android project (multi-module + version catalog + Compose/Hilt).
3. Wire the design-system tokens + adaptive nav shell + one white-label flavor.
4. Build the **Translation brain** (engines + fallback + offline-downloads manager).
5. Build the **Text feature** per `docs/specs/01-text-translation.md`.
