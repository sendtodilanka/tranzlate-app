# CLAUDE.md — Tranzlate (new app · clean rebuild)

Guidance for any Claude Code session working in this repo. Read this first, then `docs/`.

## What this is
The ground-up rebuild of the **Tranzlate** Android translator. Kotlin · Jetpack Compose · Material 3.
- **Old app** = `github.com/sendtodilanka/Tranzlate` — **READ-ONLY reference only.** Never push to it. Look at it freely (we own it) to salvage code + confirm behaviour.
- **Archive** = `github.com/sendtodilanka/tranzlate-dirty-room` (branch `archive`) — full audit, UX evaluation, captures, all planning. The "what to build" essentials are copied into `docs/` here.

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

## Salvage from old app (copy + clean — don't rewrite)
Language seed data (180+), Room entities/migrations, MLKit/Google/Cloud client logic, string resources (en/fil/pt-rBR), brand assets (logo, colors). **Rebuild:** all ViewModels, navigation/IA, subscription/ads/usage layers, onboarding, paywall, every screen.

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
