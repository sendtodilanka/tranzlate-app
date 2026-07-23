---
status: proposed   # debate output — owner has not accepted the plan-doc yet
issue: 17
title: Design debate — where enableEdgeToEdge belongs so the system bars follow the app's theme
date: 2026-07-23
method: design-debate deep mode — 2 rounds, 2 cross-model experts (opus + fable), opus judge, fable verifier
confidence: medium
---

# Verdict

DECISION: ship **Design D-final v2** — a single `enableEdgeToEdge` call site inside `setContent`, in a `DisposableEffect(darkTheme)`, carrying a **live-reading** `detectDarkMode` (`rememberUpdatedState(darkTheme)` → `SystemBarStyle.auto(l, d) { darkState.value }`), with the plain no-arg `enableEdgeToEdge()` in `onCreate` DELETED. `:core:designsystem` is not touched. Design B is rejected outright; Design C is rejected; Design A's placement is held as a specified FALLBACK, not the default.

WHERE BOTH EXPERTS AGREE (adopt as settled, no further debate):
1. **B is dead.** T-5 (`docs/plan/issue-17-core-shell-theme.md:22`, verified) forbids `:core:designsystem` reaching for `Activity`/`Window`, and `docs/audit/FIX_QUEUE.md:32` already records the owner's "Activity cast + SideEffect ක්‍රමය පාවිච්චි කරන්නේ නෑ". A white-label library that casts `LocalView.context` to an Activity also breaks on nested/preview theme use (`Theme.kt:28` default + 16 preview call sites). Q1 answered: NO, do not mutate the Window from the design-system composable.
2. **The real defect neither original design fixed** — verified in `EdgeToEdge.kt`: the config-listener child View is installed only on the FIRST call and replays THAT call's styles. So (a) any no-arg first call permanently pins the system detector (kills C and round-1 D), and (b) a captured `{ darkTheme }` boolean replays a stale value (kills A-as-on-disk at `MainActivity.kt:108,110`). **Requirement, design-independent: the first call must carry a lambda that READS live state.** Both experts converged on this; it costs zero lines.
3. **One derivation only.** The double derivation in A (`MainActivity.kt:102-103` vs `:120`) is a genuine single-source-of-truth defect; the shipped code must derive dark once and feed bars, colours and the splash gate from it.
4. **Delete the `callbackFlow` helper** (`MainActivity.kt:51-57` + imports). Q5 answered: it does not earn its keep.
5. **Keep the two scrim constants** (`MainActivity.kt:40-41`) — `SystemBarStyle.auto` requires explicit ints and the library defaults are not public.
6. **No shared `ComponentActivity` extension now** — one `<activity>` in `AndroidManifest.xml:25-34`, flavors share the source set. YAGNI.
7. **The splash background stays system-resolved.** No design on the table fixes a light splash for a system-light / app-dark user. Must NOT be claimed as fixed in the PR body.
8. **`docs/audit/FIX_QUEUE.md:32` is factually wrong** — it names `enableEdgeToEdge(detectDarkMode = { darkTheme })`, a parameter that does not exist on `enableEdgeToEdge` in activity 1.13.0 (it belongs to `SystemBarStyle.auto`). Fix it whichever design ships, before the next session copies it.

WHY D-final v2 OVER A-prime v3 (the only live disagreement): once both carry the live detector they are functionally equivalent on every probe either expert proposed. Three tiebreakers, all pointing the same way — (i) **governance**: D-final is `docs/plan/issue-17-core-shell-theme.md:70-71` implemented literally (verified verbatim), while A-prime needs an owner-approved plan amendment under CLAUDE.md rule 3; (ii) **surface area**: ~30 lines deleted / ~6 added, one call site, no `callbackFlow`, no `repeatOnLifecycle`, no eager `stateIn`, no second darkness holder — and Expert 1's hit that A-prime's `stateIn(..., Eagerly)` contradicts the very `repeatOnLifecycle(STARTED)` rationale it cites went unanswered; (iii) **no fallback guess**: A-prime re-introduces a `?: ThemeSettings.Default` boolean at Activity scope, which is exactly what the null-means-unread splash-gate contract was built to eliminate.

Q2 (pre-29 scrims): MOOT for the surviving candidates. D-final's single call carries the same explicit scrim styles A would, so it gets identical scrim handling at API 26-28; at API 24-25 `EdgeToEdgeApi23` sets `navigationBarColor = darkScrim` unconditionally with no nav appearance flag, so every design behaves the same. The scrim gap was only ever an argument against flags-only Design B, which is already dead. No pre-29 system image is installed on this machine (`android-36.1`, `android-37.1` only), so this stays an argument from source — and any claim of "confirmed on device" for API 26-28 must be rejected.
Q3: no meaningful difference between `SideEffect`, keyed `DisposableEffect` and `distinctUntilChanged` — all three miss the event where the derived value is unchanged but the pinned listener re-fires. The discriminating axis is the live detector, not the effect mechanism.
Q4: `lifecycleScope.launch { repeatOnLifecycle(...) }` in an Activity is NOT an anti-pattern (splash setup already runs outside composition unchallenged), it is simply unnecessary here.
Q6: yes — D-final v2 / A-prime v3 are both better than the three originals, and they share the one thing that actually matters: the live detector.

# Decisive first test

FIRST-FRAME ORDERING, on the already-installed `Tranzlate_Play` AVD (API 36) — no image download, one build. This is the only open question between D-final and A-prime, and it is the cheapest thing that can settle it.

Setup: throwaway build with the stored preference forced to DARK (the Settings toggle is unshipped), emulator system theme LIGHT. Cold start with screen recording; step frames forward from splash dismissal and read the status-bar icon luminance per frame (the technique this repo already proved with at `docs/audit/FIX_QUEUE.md:93` — NOT logcat timestamps, because the delay between a `WindowInsetsControllerCompat` write and the rendered icon colour is verified data නෑ).

PASS = the first drawn content frame already shows light/white status-bar icons (matching the `#FFFFFF` figure recorded at `FIX_QUEUE.md:96`), with no intermediate dark-icon frame. → D-final v2 ships as-is.
FAIL = any drawn frame precedes the effect's call. → add an EARLY call in `onCreate` carrying the SAME live-detector styles reading a `@Volatile private var currentDark` field (initialised from `resources.configuration`, updated by the composition effect). NEVER a no-arg call — a no-arg first call pins the listener to the system detector.

# Recommended sequence

1) Implement D-final v2 in `app/.../MainActivity.kt` only: delete `enableEdgeToEdge()` from `onCreate`, delete the `callbackFlow` helper + collector block + now-unused imports, and add the single `DisposableEffect(darkTheme)` with `rememberUpdatedState`-backed live detectors on both bar styles. Keep the scrim constants. Leave `:core:designsystem` untouched.
2) THEN run the decisive first-frame test above (API 36 AVD, pref forced DARK, system LIGHT). If it fails, add the early `onCreate` call with live-detector styles reading a `@Volatile currentDark` field, and re-run.
3) THEN run the corrected stale-listener probe: temporarily add `android:configChanges="uiMode"` to the single `<activity>`, set pref=DARK and system=DARK, foreground the app and flip the system DARK→LIGHT. PASS = bars stay app-dark. This is the regression guard for the live detector; revert the manifest line afterwards — it is a probe, never a shipped change.
4) THEN fix the docs in the same PR: correct `docs/audit/FIX_QUEUE.md:32` to `SystemBarStyle.auto(l, d) { darkTheme }`, and reconcile the A5/A6 rows marked "✅ #25" against the fact that PR #25 is still OPEN.
5) THEN add a one-line code comment at the call site stating that this must remain the FIRST and ONLY `enableEdgeToEdge` call, with the listener-pinning reason — otherwise a future session adds a no-arg call in `onCreate` and silently reverts the fix.
6) THEN open the PR with the frame evidence and a cross-session co-verify lens (CLAUDE.md rule 5), explicitly stating that the splash background is still system-resolved and is NOT fixed by this change.
7) ONLY THEN ship A4 (the Settings toggle) — the plan's own resequencing note requires A5/A6 to land first.

# Key risk

Two, ranked.
(1) HIGHEST — regression by future edit. The whole fix depends on an invisible property: the FIRST `enableEdgeToEdge` call owns the config-listener forever. Anyone who later adds a harmless-looking `enableEdgeToEdge()` to `onCreate` (it is the androidx KDoc sample, so this is likely) silently re-pins the bars to the system theme, and nothing fails at build or test time. Mitigation: the code comment in step 5 plus the step-3 probe kept as a documented manual check.
(2) The `configChanges="uiMode"` class of bug is only DEFENDED against, not proven absent — the probe is a temporary manifest hack, not a shipped regression test, and there is no automated coverage. Plus: API 26-28 scrim behaviour cannot be verified on this machine (no pre-29 image installed), so that band rests on source reading alone; and D-final's residual (a detached ComposeView with the Activity alive freezes the detector at the last composed value) is theoretical today with one Activity and one `setContent`, but would become real if a second window/`setContent` is ever added.

# Owner decision needed

One genuine time-vs-certainty choice, plus one bookkeeping item.

(a) API 26-28 scrim verification. මේ machine එකේ install වෙලා තියෙන්නේ Android 36/37 images විතරයි. පරණ Android (8.0-9.0) එකේ bar පාට හරියටම හරිද කියලා ඇත්තටම phone එකක බලන්න නම්, අලුත් emulator image එකක් download කරන්න ඕන — ඒක වැඩ පැයක් විතර යනවා, ඒ image එක අපේ Mac එකට තියෙනවද කියලා තහවුරු කරලා නෑ. නැත්නම් androidx source එකේ code එක කියවලා ගත්ත තීරණය පිළිගෙන ඉදිරියට යන්න පුළුවන් — surviving design දෙකටම මේ තැන එකම විදිහට හැසිරෙනවා කියලා code එකෙන් පේනවා. **මගේ නිර්දේශය: download එක නොකර ඉදිරියට යන්න.** ඔබ "නෑ, පරණ phone එකකත් බලමු" කිව්වොත් විතරයි ඒක කරන්නේ.

(b) කඩදාසි වැරැද්දක්: `docs/audit/FIX_QUEUE.md` එකේ A5/A6 වැඩ දෙක "✅ merged in #25" කියලා සලකුණු කරලා තියෙනවා, ඒත් PR #25 තාම open — merge වෙලා නෑ. ඒක මේ PR එකේම හදනවා; ඔබෙන් තීරණයක් ඕන නෑ, දැනගන්න විතරයි.

වෙන කිසිම දෙයක් නෑ — green light.

# Owner summary (Sinhala)

**තීරණය කෙටියෙන්:** app එකේ theme එක (Light / Dark / Follow System) වෙනස් කරද්දී උඩ තියෙන battery-clock තීරුවේ සහ යටින් තියෙන navigation තීරුවේ icon පාට **phone එකේ setting එකට නෙවෙයි, අපේ app එකේ setting එකට** අනුව වෙනස් වෙන විදිහට හදනවා — ඒ code එක **MainActivity එකේ එකම එක තැනක** විතරයි ලියනවා.

**වැදගත්ම හොයාගැනීම:** Android library එකේ පොඩි උගුලක් තියෙනවා. ඒ code එකට **පළවෙනි වතාවේ කතා කරන විදිහ** එයා ඔලුවේ තියාගෙන, ඊට පස්සේ හැම වතාවෙම ඒකම නැවත නැවත පාවිච්චි කරනවා. එතකොට වෙන්නේ, පස්සේ theme එක වෙනස් වුණාම library එක පරණ පාට එකම නැවත දාන එක. දෙබසට ආපු designs තුනෙන් **තුනටම** මේ ප්‍රශ්නය තිබුණා — අපි දැන් හදන්නේ, "පාට මොකක්ද කියලා **හැම වතාවෙම අලුතෙන් බලපන්**" කියලා library එකට කියන විදිහට. ඒකට අමතර code පේළියක්වත් ඕන නෑ, ඒත් ඒක තමයි මුළු නිවැරදිභාවයම තියෙන තැන.

**තෝරගත්තේ මොකක්ද:**
- **තෝරගත්තා** — design-system module එකට අත නොතියා, MainActivity එකේ එකම තැනක code එක. පේළි 30ක් විතර **අයින් වෙනවා**, අලුතෙන් එකතු වෙන්නේ 6ක් විතරයි. දැනටමත් ඔබ approve කරපු plan doc එකේ ලියලා තියෙන ක්‍රමයම මේක, ඒ නිසා plan එක වෙනස් කරන්න ඕන නෑ.
- **ප්‍රතික්ෂේප කළා** — design-system module එක ඇතුළෙන් phone එකේ window එකට අත තියන යෝජනාව. ඒ module එක අනිත් apps වලටත් (white-label) ගලවලා දාන්න හදපු එකක්; ඒක ඇතුළෙන් window එකට අත තිබ්බොත් අනිත් apps වල කැඩෙනවා. මේක ඔබත් කලින්ම "පාවිච්චි කරන්නේ නෑ" කියලා ලියලා තිබුණා — expert දෙන්නම එකඟයි.

**ලොකුම අවදානම:** මේ නිවැරදි කිරීම **පේන්න නෑ**. අනාගතයේ කවුරු හරි "නිකම් standard විදිහට" තව එක පේළියක් එකතු කළොත්, ප්‍රශ්නය හෙමින් ආපහු එනවා — build එකවත් test එකවත් error දෙන්නේ නෑ. ඒ නිසා ඒ තැනට පැහැදිලි අවවාද comment එකක් දාලා, phone එකේ පරීක්ෂාවත් ලියලා තියනවා.

**මතක තියාගන්න එක දෙයක්:** app එක ඇරෙද්දී එන පළවෙනි splash තිරයේ පාට තාම **phone එකේ setting එකට** අනුවයි එන්නේ. Phone එක Light, app එක Dark නම්, තප්පරයක් විතර සුදු splash එකක් පේනවා. **මේ වැඩෙන් ඒක හැදෙන්නේ නෑ** — යෝජනා තුනෙන් එකකින්වත් ඒක හැදෙන්නේ නෑ, ඒ නිසා "හැදුවා" කියලා PR එකේ ලියන්නෙත් නෑ. ඕන නම් ඒක වෙනම වැඩක් විදිහට පස්සේ බලමු.

**ඊළඟ පියවර:** code එක ලියලා → emulator එකේ app එක Dark, phone එක Light තියලා cold start එකක් record කරලා, පළවෙනි frame එකේම icon පාට හරිද කියලා frame by frame බලනවා → ඊට පස්සේ theme flip test එක → ඊට පස්සේ PR. ඒ ඔක්කොම හරි ගියාට පස්සේ විතරයි Settings එකේ theme තෝරන toggle එක ship කරන්නේ.

---

# Verifier pass

**Checked:** All file/line-cited factual claims from Expert 0 and Expert 1: system-image/AVD inventory; androidx.activity 1.13.0 EdgeToEdge.kt sources (listener pinning, default detector, enableEdgeToEdge signature, Api23/26/29 scrim code); app/src/main/AndroidManifest.xml:25-34; docs/plan/issue-17-core-shell-theme.md:22,70-74,78-83; docs/audit/FIX_QUEUE.md:31,32-33,93,96; MainActivity.kt:51-57,66-124; git/PR state of feat/issue-17-window-chrome-splash. Behavioral test predictions were not executed — only source-derivable facts checked.

**Confirmed:** 1) `ls ~/Library/Android/sdk/system-images` = exactly `android-36.1`, `android-37.1`; `~/.android/avd` holds `Tranzlate_Play.avd` + `Resizable_Experimental.avd`. No pre-29 image installed. 2) activity is pinned to 1.13.0 (gradle/libs.versions.toml:16) and its sources jar exists in the gradle cache. EdgeToEdge.kt: `setup` Runnable at :100-108 closes over the calling invocation's statusBarStyle/navigationBarStyle (detectDarkMode re-evaluated per run via `statusBarStyle.detectDarkMode(view.resources)` at :105-107); the config-listener child View is added ONLY when no child has an `EdgeToEdgeImpl` tag (:111) and it runs that first `setup` (:113-118) — both experts' pinning claim CONFIRMED (E0's ":100-125" and E1's ":110-127" ranges both accurate). 3) Default `detectDarkMode` is a parameter of `SystemBarStyle.auto` at :164-171, default lambda :167-170 reads `resources.configuration.uiMode` — confirmed. 4) `enableEdgeToEdge` signature (:76-81) takes ONLY statusBarStyle/navigationBarStyle — NO `detectDarkMode` parameter exists on it; FIX_QUEUE.md:32 does record `enableEdgeToEdge(detectDarkMode = { darkTheme })`, i.e. a nonexistent signature — confirmed for both experts. 5) EdgeToEdgeApi23 (:249): `window.navigationBarColor = navigationBarStyle.darkScrim` unconditionally at :263, no `isAppearanceLightNavigationBars` set — E1's concession-basis confirmed. Api26 (:269) varies both scrims via getScrim (:282-283); Api29 (:302) uses getScrimWithEnforcedContrast (:315-317) which returns TRANSPARENT for MODE_NIGHT_AUTO (`auto()`, :219-221) — so 'A/C/D identical on scrims at API 29+ with auto()' follows from source. MainActivity comment's cites :217,262,283 all land on the right lines. 6) AndroidManifest.xml:25-34: single `<activity>` (.MainActivity), no `android:configChanges` — confirmed. 7) plan:70-71 verbatim contains "`DisposableEffect(darkTheme)` re-applying `enableEdgeToEdge(statusBarStyle/navigationBarStyle = SystemBarStyle.auto(…) { darkTheme })`" and :70-74 mandate only that mechanism + splashscreen (no no-arg onCreate call) — both experts' quotes confirmed. 8) plan:22 (T-5) contains both "`:core:designsystem` must not reach for `Activity`/`Window`" and "need the resolved value *before* content draws" — confirmed. 9) FIX_QUEUE.md:32 contains the Sinhala line "(`Activity` cast + `SideEffect` ක්‍රමය පාවිච්චි කරන්නේ නෑ.)" — confirmed. :93 (A1) records per-frame pixel reading; :96 records A5+A6 end-to-end evidence incl. status-bar `#FFFFFF`. :31 (A4) shows Settings toggle ⏳ unshipped (plan:78-83 agrees; E0's ":80" cite is the right section, slightly off-line). 10) FIX_QUEUE.md:32-33 mark A5+A6 "✅ #25" — and PR #25 is OPEN, not merged (gh pr view 25: state OPEN), so 'paper trail ahead of reality' confirmed in substance. 11) MainActivity.kt as-on-disk: callbackFlow at :51-57; splash setup outside composition at :68 and :84; darkness derived twice — :102-103 (combine/isDark) and :120 (`settings.mode.isDark(isSystemInDarkTheme())`); collector's `{ darkTheme }` at :108,110 captures a boolean local, so the pinned listener would replay the first captured value — structure matches both experts' description.

**Refuted:** 1) Expert 0: "the code is still uncommitted on feat/issue-17-window-chrome-splash" — inaccurate as stated: the A5+A6 work IS committed and pushed on that branch (commits 6745703, 30625e7, 63c7163 touch MainActivity.kt; branch exists on origin; PR #25 open from it). Only further local modifications to MainActivity.kt (and FIX_QUEUE.md itself) are uncommitted. The valid kernel — docs say ✅/merged while PR #25 is unmerged — stands, but "uncommitted" is the wrong charge. 2) Minor cite drift: E0's "plan:80" for the unshipped toggle points mid-sentence into the STRINGS note; the supporting text is plan:78-79 ("PR 6 · A4 — Settings Appearance. Last on purpose") — substance right, line off.

**Unverifiable:** 1) Whether Google ships arm64-v8a API 24/25/28 system images usable on this darwin/arm64 host — not checked against any package listing (both experts already flag it as unverified). 2) All behavioral predictions of TEST 1/TEST 2 (stale-listener outcomes under configChanges="uiMode", first-frame timing, compositor latency between WindowInsetsControllerCompat write and rendered icon colour) — logically consistent with the confirmed EdgeToEdge.kt source, but not run. 3) "Google's own Now in Android has the same limitation" (MainActivity.kt:83 comment) and any claims about what a round-1/round-2 debate reply did or did not say — outside the repo.

---

# Plan doc as produced by the debate

---
issue: 17
slug: issue-17-edge-to-edge-placement
type: plan
status: accepted
severity: medium
priority: P1
owner: dilanka
risk: silent-regression
---

# Issue #17 · A5 — where `enableEdgeToEdge` lives (placement decision)

> Amends the mechanism detail of `docs/plan/issue-17-core-shell-theme.md` §PR 5 (A5+A6).
> The accepted mechanism there (`DisposableEffect(darkTheme)` + `SystemBarStyle.auto(…) { darkTheme }`)
> **stands**; this doc adds the one thing it did not specify — a *live-reading* dark detector — and
> records that no plain no-arg `enableEdgeToEdge()` call may exist anywhere in the Activity.

## Context

System bar icon colours must follow the app's stored `ThemeMode` (LIGHT / DARK / SYSTEM), not the
phone's night setting. Today the two always agree because the Settings toggle (A4) is unshipped —
so this is a fix landing *ahead* of the trigger that makes it visible, per the plan's resequencing note.

Facts established by reading `androidx.activity:activity:1.13.0` sources (`EdgeToEdge.kt`), verified,
not to be re-derived:

- `enableEdgeToEdge` takes **only** `statusBarStyle` / `navigationBarStyle`. There is **no
  `detectDarkMode` parameter on it** — that lambda belongs to `SystemBarStyle.auto` (`:164-171`).
- The default detector reads `resources.configuration.uiMode` (`:167-170`) — i.e. the **system**
  theme, never the app preference.
- **Listener pinning (decisive):** the config-listener child View is added only when no child
  already carries an `EdgeToEdgeImpl` tag (`:111`), and it replays the **first** call's `setup`
  Runnable (`:100-108`, `:113-118`). Whichever call runs first owns the styling forever.
- API 29+ `getScrimWithEnforcedContrast` returns TRANSPARENT for `auto()` (`:315-317`, `:219-221`) —
  only the two appearance flags vary. API 26-28 varies both scrims (`:282-283`). API 24-25 sets
  `navigationBarColor = darkScrim` unconditionally with no nav appearance flag (`:263`).
- Edge-to-edge is platform-enforced on Android 15+ at targetSdk 35+, which this app targets.

## Options debated

| # | Design | Outcome |
|---|--------|---------|
| A | One call outside composition: `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`, `callbackFlow` system-dark helper, `combine` + `distinctUntilChanged`, captured `{ darkTheme }` | **Held as fallback.** Placement is defensible (NiA does this), but as written it derives dark twice (`MainActivity.kt:102-103` vs `:120`), captures a stale boolean at `:108,110`, and its `stateIn(…, Eagerly)` contradicts the `repeatOnLifecycle` rationale it cites. |
| B | No-arg call in `onCreate` + `LocalView`/`SideEffect` inside `TranzlateTheme` casting to Activity | **Rejected.** Violates T-5 (`issue-17-core-shell-theme.md:22`): `:core:designsystem` is a white-label library and must not reach for `Activity`/`Window`. Also breaks under nested themes / the 16 `@PreviewLightDark` call sites, and the no-arg first call pins the system detector. |
| C | No-arg call before `super.onCreate` **plus** a keyed `DisposableEffect` re-apply | **Rejected.** The no-arg call is first → listener pinned to `resources.configuration` permanently. |
| **D-final** | **Single call site, in `DisposableEffect(darkTheme)` inside `setContent`, with a live detector; no-arg `onCreate` call deleted** | **ACCEPTED.** |

## Decision

In `app/src/main/kotlin/com/codeboxlk/tranzlate/MainActivity.kt` only. `:core:designsystem` untouched.

1. **Delete** the plain `enableEdgeToEdge()` from `onCreate`. There must be exactly **one**
   `enableEdgeToEdge` call in the app, and it must be the one below.
2. In `setContent`, next to the existing dark derivation:
   `val darkState = rememberUpdatedState(darkTheme)`, then
   `DisposableEffect(darkTheme) { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { darkState.value }, navigationBarStyle = SystemBarStyle.auto(lightScrim, darkScrim) { darkState.value }); onDispose {} }`.
   The lambdas **read live state** — this is what makes the pinned listener replay the *current*
   value instead of a captured boolean.
3. **Delete** the `callbackFlow` system-dark helper, the collector block and their now-unused imports.
   No `MutableStateFlow` replacement: `isSystemInDarkTheme()` in composition already tracks `uiMode`,
   and the manifest declares no `android:configChanges`, so the Activity recreates on system flips.
4. **One derivation** of `darkTheme` feeds bar styling, colours and the splash gate.
5. **Keep** the two scrim constants — `SystemBarStyle.auto` requires explicit ints and the library
   defaults are not public API.
6. **No** shared `ComponentActivity` extension: one `<activity>` exists and flavors share the source
   set. Extract mechanically if a second Activity ever appears.
7. **Mandatory comment** at the call site: *first call owns the config listener — do not add another
   `enableEdgeToEdge` call, especially not the no-arg KDoc-sample form.*
8. **Doc fixes, same PR:** correct `docs/audit/FIX_QUEUE.md:32` (it names the nonexistent
   `enableEdgeToEdge(detectDarkMode = …)`) → `SystemBarStyle.auto(l, d) { darkTheme }`; and reconcile
   the A5/A6 rows marked "✅ #25" against PR #25 still being **open**.

Rationale for D over A once both carry the live detector: they are functionally equivalent on every
probe proposed, so the tiebreakers decide — D is the accepted plan implemented literally (no
amendment needed), it deletes ~30 lines to add ~6, and it avoids re-introducing a
`?: ThemeSettings.Default` boolean guess at Activity scope, which the null-means-unread splash-gate
contract exists to eliminate.

## Decisive test

Installed AVDs are API 36/37 only — **no pre-29 image is installed**, so the API 26-28 scrim band is
settled from source, not device. Any "confirmed on device" claim for that band must be rejected.

1. **First-frame ordering (decisive, run first).** Throwaway build, pref forced DARK, emulator system
   LIGHT. Cold start, screen-record, step frames from splash dismissal and read status-bar icon
   luminance per frame — the per-frame pixel technique already used for A1 (`FIX_QUEUE.md:93`), not
   logcat timestamps (write-to-render latency is verified data නෑ).
   **PASS** = first drawn content frame already shows light icons (cf. `#FFFFFF` at `FIX_QUEUE.md:96`).
   **FAIL** → add an early `onCreate` call carrying the **same live-detector styles** reading a
   `@Volatile private var currentDark` (seeded from `resources.configuration`, updated by the effect).
   Never a no-arg call.
2. **Stale-listener probe (regression guard).** Temporarily add `android:configChanges="uiMode"` to the
   single `<activity>`; pref DARK, system DARK; foreground, flip system DARK→LIGHT.
   **PASS** = bars stay app-dark. Revert the manifest line — it is a probe, not a shipped change.
3. Build + existing tests green; cross-session co-verify lens per CLAUDE.md rule 5, PR body citing the
   frame evidence.

## Risks

- **Silent regression (highest).** Correctness rests on an invisible property — first call owns the
  listener. A future no-arg `enableEdgeToEdge()` (it *is* the androidx KDoc sample) reverts this with
  no build or test failure. Mitigated only by the call-site comment and test 2 as a manual check.
- **No automated coverage** for the `configChanges="uiMode"` scenario; test 2 is a temporary manifest hack.
- **API 26-28 scrims unverified on device** — no pre-29 system image installed; arm64 availability at
  those levels on this host is verified data නෑ.
- **Detached-ComposeView staleness (theoretical).** If the ComposeView is detached while the Activity
  lives, `rememberUpdatedState` stops updating and the detector reads the last composed value. Not
  reachable with one Activity and one `setContent`; becomes real if a second window is added.
- **Out of scope, must not be claimed as fixed:** the splash window background still resolves from the
  system night qualifier, so a system-light / app-dark user still sees a light splash before dark
  content. No candidate design addresses this.

