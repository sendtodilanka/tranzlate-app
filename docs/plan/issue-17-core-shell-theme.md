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

**PR 4 · A3 + A4 — wiring + Settings Appearance.** `TranzlateTheme(darkTheme, dynamicColor)` driven
by the stored preference; delete the `isSystemInDarkTheme()` branch in `PrimaryActionButton.kt:48`
(`TODO(#7)`) so the button follows the *app* theme. `SettingsScreen` gains its first real section
(Light / Dark / System + dynamic colour) using stock M3 — the placeholder stays for the rest.
Strings go into the STRINGS catalogue first (C-3), testTags per C-1.
*Verify:* toggle each option on device, both orientations.

**PR 5 · A5 + A6 — window chrome + splash.** `DisposableEffect(darkTheme)` re-applying
`enableEdgeToEdge(statusBarStyle/navigationBarStyle = SystemBarStyle.auto(…) { darkTheme })` so bar
icons follow the app, not the system (verified: the default `detectDarkMode` reads
`resources.configuration`, `EdgeToEdge.kt:167`). Then `androidx.core:core-splashscreen` +
`installSplashScreen().setKeepOnScreenCondition { themeNotResolved }`.
*Verify:* device set to light + app set to Dark → status-bar icons must be light, and there must be
no white flash before the first frame.

## 3. Out of scope

Batch 🅑 nav shell (drawer back button, `NavigationDrawerItem`, one snackbar host, nav3 decorator) ·
batch 🅒 text-vertical fixes · theme-colour presets (#7) · the rest of the Settings screen.

## 4. Risks

- **A7 touches the two busiest screens.** Mitigate with the dark screenshot test + a device pass on
  Home's keyboard behaviour (the IME-pan bug in PR #14 came from exactly this area).
- **Splash gating can hide a slow start.** T-6 keeps the gate to one local read; if the preference
  flow ever grows a network dependency this must be revisited.
- **Dynamic colour changes every role at once.** Our contrast evidence (PALETTES.md) covers the
  static scheme only; the dynamic path is the system's guarantee, not ours. Ship it opt-in (T-2) and
  say so in the Settings supporting text.
