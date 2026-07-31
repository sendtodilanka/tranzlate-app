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

**8. Sinhala prose ≥70%** · **No speculation (verified data only)** · **`./gradlew` needs JAVA_HOME=Android Studio JBR** — see `.claude/memory/`.

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
