---
status: accepted   # owner accepted 2026-07-23
issue: 17
title: Core/shell 🅐 — theme foundation (preference, Settings toggles, window/splash, screen Scaffolds)
date: 2026-07-23
author: Claude (Opus 4.8) · evidence = material3 1.4.0 / activity 1.13.0 sources + android-36 android.jar + device run
---

# Plan — issue #17

Owner's call (2026-07-23): fix the **core/shell** layer before individual screens. This is batch 🅐
of [`docs/audit/FIX_QUEUE.md`](../audit/FIX_QUEUE.md).

## 1. Decisions

| # | Decision | Why |
|---|---|---|
| T-1 | `ThemeMode` enum `SYSTEM(0) · LIGHT(1) · DARK(2)`, persisted in the **existing** `prefs.theme` Int | The key and its `0 = system` default are already in `DATA_MODEL.md` and in `TranzlatePreferencesDataSource.kt:33,59,66` — with zero consumers. Keep the wire format, add the type. |
| T-2 | New key `prefs.dynamic_color`, Boolean, **default `false`** | The GT-identical palette is the brand identity (issue #15). Dynamic colour is opt-in. `DATA_MODEL.md` must gain the row — that file is the authority for prefs keys. |
| T-3 | Three-way theme choice, not a two-state switch | `prefs.theme` already defaults to *system*; GT itself offers Light / Dark / System default. A plain switch cannot express "follow the system". |
| T-4 | On API < 31 the dynamic-colour row is **shown but disabled, with a supporting line** stating it needs Android 12+ | EDGE_CASES §7: no disabled control without a reason. Hiding it entirely would leave the user wondering why a documented feature is missing. |
| T-5 | Theme state is hoisted to `MainActivity` (a shell-scoped state holder), not read inside `TranzlateTheme` | The splash (A6) and `enableEdgeToEdge` (A5) both need the resolved value *before* content draws. `:core:designsystem` must not reach for `Activity`/`Window`. |
| T-6 | The splash is held **only** until the theme preference emits — no timeout hack, no other work gated on it | Google's budget is ≤1000 ms; a single local DataStore read is well inside it. Gating anything else invites startup regressions. |
| T-7 | `isNavigationBarContrastEnforced` is **not** set | `setNavigationBarContrastEnforced` is `@Deprecated` in android-36's `android.jar` (verified) and has no effect on Android 15+; `enableEdgeToEdge` already manages it on older releases (`EdgeToEdge.kt:392`). |

## 2. Work order — one PR per step

Each step is independently shippable and independently verifiable on device.

**PR 1 · A1 — window theme.** `android:windowBackground` = `#FAF9F8` / `#131314` (matching
`Color.kt:31,70`) + `android:forceDarkAllowed=false` in both `values/themes.xml` and
`values-night/themes.xml`; fix the comment that already claims this is handled. Also removes the
wrong Android 12+ splash colour — the platform falls back to `windowBackground` when it is a single
colour ([splash screen docs](https://developer.android.com/develop/ui/views/launch/splash-screen)).
*Verify:* cold-start video/screenshot, light + dark.

**PR 2 · A7 — `Scaffold` on `ResultScreen` + `HomeScreen`.** Done early, before the toggle exists,
because it is the most user-visible defect on the current build. Fixes two audit P0s at once:
the black-on-black icons (`LocalContentColor` default `Color.Black`, `ContentColor.kt:33` — `Scaffold`
→ `Surface` → `CompositionLocalProvider(LocalContentColor …)`, `Scaffold.kt:96` / `Surface.kt:107`)
and snackbars drawn under the gesture nav bar (`Scaffold.kt:242-248,282-286`).
Home must keep its IME-pinned composer working (`imePadding` + `adjustResize`).
*Verify:* dark-mode screenshot of the result screen; snackbar position with 3-button nav.
*Test:* **deferred, deliberately.** The intended gate is a dark-theme screenshot test, but the repo
has no JVM screenshot harness — no Robolectric, no Roborazzi/Paparazzi (checked: the catalog only
carries `ui-test-junit4`/`ui-test-manifest` for instrumented tests). Standing that up is its own
piece of infrastructure across modules and would dwarf a two-file fix, and a weaker proxy test
(e.g. a Konsist rule asserting "a screen root contains `Scaffold(`") would buy false confidence
rather than coverage. Verification for this PR is therefore measured on device — raw framebuffer
luminance inside each icon's bounds, in both themes. The harness is queued as its own item; it is
the gate that protects contrast, RTL and 200 %-text-scale too, not just this one bug.

**PR 3 · A2 — preference layer.** `ThemeMode` in `:core:model`; `prefs.dynamic_color` added to the
data source **and** to `DATA_MODEL.md`; `ReplaceFileCorruptionHandler` on the DataStore factory plus
`.catch { IOException → emptyPreferences() }` on the reads. The corruption handler is **load-bearing
from PR 5 onwards**: once the splash waits on this flow, an unhandled corruption turns a crash into a
permanently stuck splash. No visible change yet (default is still *system*).
*Test:* unit tests for the mapping and for the corruption fallback.

**PR 4 · A3 — wiring only.** *(Split from A4 after the fact: A3 is a pure refactor with no
behaviour change, A4 is the visible one. Reviewing "the theme, not the system, decides dark" apart
from "here is a new screen" is easier, and it matches the owner's one-thing-at-a-time rule.)*
`TranzlateTheme` is driven by the stored preference; the
`isSystemInDarkTheme()` branch in `PrimaryActionButton.kt:48` (`TODO(#7)`) is replaced by a colour
resolved inside the theme, following the `LocalFloatingSurface` precedent — that local's own doc
already forbids branching on the system flag at a call site. The `ThemeMode → isDark` decision moves
into `:core:model` as a pure function so it is unit-testable without a Compose host.
*No behaviour change:* the stored preference is still SYSTEM for everyone.
*(A `ThemeSettings` overload of `TranzlateTheme` was added here and then removed in PR 5: the activity
needs the resolved dark flag for the system bars as well as the theme, so it resolves once and passes
both. A3's shape was wrong and A5 is what showed it.)*

> ⚠️ **AMENDED after this plan was accepted.** The mechanism sketched in this section —
> `DisposableEffect(darkTheme)` re-applying `SystemBarStyle.auto(…) { darkTheme }` — carries the exact
> bug a later design debate found: androidx pins a configuration-change listener to the **first**
> `enableEdgeToEdge` call and replays that call's styles forever, so a **captured** `darkTheme` boolean
> replays a stale value, and a no-arg call in `onCreate` pins the bars to the system detector. The
> shipped shape reads a live field instead, and there is no `onCreate` call at all. **Implement from
> [`issue-17-debate-edge-to-edge.md`](issue-17-debate-edge-to-edge.md), not from the snippet below.**

**PR 5 · A5 + A6 — window chrome + splash.** `DisposableEffect(darkTheme)` re-applying
`enableEdgeToEdge(statusBarStyle/navigationBarStyle = SystemBarStyle.auto(…) { darkTheme })` so bar
icons follow the app, not the system (verified: the default `detectDarkMode` reads
`resources.configuration`, `EdgeToEdge.kt:167`). Then `androidx.core:core-splashscreen` +
`installSplashScreen().setKeepOnScreenCondition { themeNotResolved }`.
*Verify:* device set to light + app set to Dark → status-bar icons must be light, and there must be
no white flash before the first frame.

**PR 6 · A4 — Settings Appearance. Last on purpose.** `SettingsScreen` gains its first real section (Light / Dark /
System + dynamic colour) using stock M3; the placeholder stays for the rest. Needs a new
`STRINGS_settings.md` — the catalogue is the key authority (C-3) and only Text has one today.
testTags per C-1.
*Verify:* toggle each option on device, both orientations; confirm no light frame on a cold start
after choosing Dark.

> **Resequenced after the A3 co-verify lens.** A4 was originally PR 4, ahead of A5/A6. That was
> wrong: A4 is the *trigger* for both of them. Until a user can pick a mode, the stored preference
> and the system always agree, so the status-bar icons cannot be wrong (A5) and the first-frame
> fallback cannot be visible (A6). The moment the toggle ships, both defects become reachable —
> A6's light-flash on every cold start being the user-visible one. Shipping the switch that turns
> the feature on *before* the two fixes that make it correct would have put the defect in front of
> users for one PR's worth of time, for no reason other than the order I happened to write the plan
> in.

## 3. Out of scope

Batch 🅑 nav shell (drawer back button, `NavigationDrawerItem`, one snackbar host, nav3 decorator) ·
batch 🅒 text-vertical fixes · theme-colour presets (#7) · the rest of the Settings screen.

## 4. Risks

- **A4 is the switch that turns the whole batch on.** It must land after A5 and A6, never before —
  see the note under PR 6. This is the one ordering constraint in the batch that is not cosmetic.
- **A7 touches the two busiest screens.** Mitigate with the dark screenshot test + a device pass on
  Home's keyboard behaviour (the IME-pan bug in PR #14 came from exactly this area).
- **Splash gating can hide a slow start.** T-6 keeps the gate to one local read; if the preference
  flow ever grows a network dependency this must be revisited.
- **Dynamic colour changes every role at once.** Our contrast evidence (PALETTES.md) covers the
  static scheme only; the dynamic path is the system's guarantee, not ours. Ship it opt-in (T-2) and
  say so in the Settings supporting text.
