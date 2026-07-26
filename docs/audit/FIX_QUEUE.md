# Fix queue — එකින් එක

වැඩ කරන ක්‍රමය: **එකක් = එක issue = එක පොඩි PR = emulator එකේ බලනවා → ඊළඟ එක.**
Bulk edit නෑ.

**මූලාශ්‍රය තීරුව:** 🔍 = audit එකෙන් · 👤 = **owner හොයාගත්තා**.
එකම ලැයිස්තුවක් තියාගන්නේ පිළිවෙළ නැති නොවෙන්න — ඒත් කොහෙන් ආවද කියලා පේනවා.
👤 ගණන වැඩි වෙනවා කියන්නේ මගේ audit එකේ හිඩැස් තියෙනවා කියන එකයි — ඒක බලාගෙන ඉන්න ඕන දෙයක්.

**තත්ත්වය තීරුව:** ⏳ = දැන් කරන එක · 🔵 = PR එකේ, **තාම merge නෑ** · ✅ = merge වුණා · හිස් = පෝලිමේ.
අංක (A1 · B2 …) **වෙනස් වෙන්නේ නෑ** — ඉවර වුණාට පස්සෙත් ඒ අංකයම තියෙනවා, ඒ නිසා පරණ සටහන් වල reference රැඳෙනවා.

විස්තර: [`2026-07-23-deep-audit.md`](2026-07-23-deep-audit.md) · Sinhala පිටුව: `docs/plan/review/audit-2026-07-23.html`

---

**පිළිවෙළ (owner, 2026-07-23):** මුලින්ම **core / shell** — theme, `MainActivity`, window, shell.
ඊට පස්සේ තමයි screens. යටින්ම තියෙන ස්ථරය කලින්.

---

## 🅐 Core / Shell — Theme ✅ **සම්පූර්ණයි** (A8 harness infra පසුව, #20)

Issue [#17](https://github.com/sendtodilanka/tranzlate-app/issues/17) · plan [`issue-17-core-shell-theme.md`](../plan/issue-17-core-shell-theme.md) (accepted)

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| A1 | ✅ [#18](https://github.com/sendtodilanka/tranzlate-app/pull/18) | 👤 | Window theme: `android:windowBackground` + `android:forceDarkAllowed=false` | `values*/themes.xml`, `values*/colors.xml` |
| A2 | ✅ [#21](https://github.com/sendtodilanka/tranzlate-app/pull/21) | 🔍 | Theme preference එක: DataStore keys + repository (`SYSTEM`/`LIGHT`/`DARK` + dynamic on/off). **+ corruption handler** — A6 මේ pref එක එනකම් splash එක රඳවන නිසා, file එක හැදුණොත් crash එකක් වෙනුවට **splash එකේම හිරවෙනවා**. ඒ නිසා ඒක මෙතනට ගෙනාවා (කලින් C5) | `TranzlatePreferencesDataSource.kt`, `DataStoreModule.kt:23` |
| A3 | ✅ [#24](https://github.com/sendtodilanka/tranzlate-app/pull/24) | 🔍 | `TranzlateTheme(darkTheme, dynamicColor)` ඒ preference එකෙන් run කරන එක + `PrimaryActionButton` එකේ `isSystemInDarkTheme()` branch එක අයින් කිරීම (`TODO(#7)`) | `Theme.kt`, `PrimaryActionButton.kt:48` |
| A4 | ✅ [#31](https://github.com/sendtodilanka/tranzlate-app/pull/31) | 🔍 | **Settings → Appearance** — light/dark/system + dynamic toggle. Device-verified: Dark තෝරද්දී මුළු app+bars live re-theme, persist, API 28 එකේ dynamic row disabled+reason | `feature/settings` |
| A5 | ✅ [#25](https://github.com/sendtodilanka/tranzlate-app/pull/25) · **API 24·28·29·36 device-verified** (disagree case: app dark + system light → bars follow the app; only API 24 nav bar is the platform's own limit) | 👤 | `MainActivity`: `DisposableEffect(darkTheme)` + **එකම** `enableEdgeToEdge`, styles `SystemBarStyle.auto(l, d) { currentDark }` (⚠️ `enableEdgeToEdge` එකට `detectDarkMode` parameter එකක් **නෑ** — ඒක තියෙන්නේ `SystemBarStyle.auto` එකේ). (`Activity` cast + `SideEffect` ක්‍රමය පාවිච්චි කරන්නේ නෑ.) Design-debate එකෙන් හැඩය තීරණය කළා → [`issue-17-debate-edge-to-edge.md`](../plan/issue-17-debate-edge-to-edge.md) | `MainActivity.kt` |
| A6 | ✅ [#25](https://github.com/sendtodilanka/tranzlate-app/pull/25) | 👤 | `core-splashscreen` + `setKeepOnScreenCondition` — theme preference එක එනකම් රඳවගන්න (නැත්නම් dark තෝරලා තියෙන කෙනෙකුට **light flash**). API 23+ backport. **සීමාව 1000ms** (Google) | `MainActivity.kt:19` |
| A7 | ✅ [#19](https://github.com/sendtodilanka/tranzlate-app/pull/19) | 🔍 | `ResultScreen` + `HomeScreen` → `Scaffold` | `ResultScreen.kt`, `HomeScreen.kt` |
| A8 | | 🔍 | **Screenshot-test harness** — issue [#20](https://github.com/sendtodilanka/tranzlate-app/issues/20) · roadmap Phase F. දැන් repo එකේ JVM screenshot test එකක් හදන්න ක්‍රමයක් නෑ — ඒ නිසා A7 එකේ bug එක වගේ එකක් ආපහු ආවොත් CI එකෙන් අහුවෙන්නේ නෑ. dark mode විතරක් නෙවෙයි — contrast · RTL · අකුරු 200% (TEST_A11Y gates 11-12) | නව infra (`build-logic`) |

## 🅑 Core / Shell — Navigation shell ✅ **සම්පූර්ණයි**, ඒත් **shell එකම මාරු වුණා** (issue #26 → issue #42)

> **⚠ 2026-07-26 · D-5 rev.2 → D-5 rev.3 (issue [#42](https://github.com/sendtodilanka/tranzlate-app/issues/42) · PR [#43](https://github.com/sendtodilanka/tranzlate-app/pull/43)):** owner ගේ Claude Design export **"Offline Translator M3"** එක Home එකේ contract එක වුණා. **පහළ `NavigationBar` එකයි drawer එකයි දෙකම අයින්** — `NavigationSuiteScaffold` නෑ, FAB නෑ. Shell එක දැන් හුදෙක් `NavDisplay` එකක්; **Home එකේ card stack එකම තමයි navigation එක** (tool grid · list row · top-bar icon).
>
> මේ batch එකේ වැඩ **නාස්ති වුණේ නෑ** — B1/B2/B5 (drawer back · `NavigationDrawerItem` · M3 motion) drawer එකත් එක්කම **retired**, B6 (bottom nav) **reversed**, ඒත් **B4 (nav3 `rememberViewModelStoreNavEntryDecorator()`) තාමත් shipped සහ අවශ්‍යයි**, සහ B6 එකේ ආපු back-guard (`if (backStack.size > 1)`) එකත් `TranzlateApp.kt` එකේ තියෙනවා. පහළ ලැයිස්තුව **historical record** එකක් — අලුත් shell එකේ තත්ත්වය B7-B9.
>
> ~~**D-5 → D-5 rev.2 (2026-07-24/25, issue #26):** primary nav = පහළ `NavigationBar` (**Home / Chat / Camera**) via `NavigationSuiteScaffold`; drawer = **secondary විතරයි**; bar එක top-level tabs වල විතරයි (`NavigationSuiteType.None`). **FT (Lingo French) app structure එක ගත්තා.**~~ Shipped වුණේ ↓ B1/B2/B4/B5/B6; **B3 (SnackbarHost) deferred**.

**අලුත් shell එකේ ණය (D-5 rev.3, 2026-07-26):**

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| B7 | | 🔍 | **Dead code**: `DrawerContent.kt` · `DrawerViewModel.kt` · `TopLevelDestination.kt` තව `:app` එකේ තියෙනවා, ඒත් `TranzlateApp.kt` එකෙන් **reference වෙන්නේ නෑ** (ඉතුරු වෙලා තියෙන්නේ stale comment එකක් විතරයි). ඒවත් එක්කම orphan strings: `nav_home`/`nav_chat`/`nav_camera` · `drawer_*` · `app_guided_search` | `:app` navigation package + `values/strings.xml` |
| B8 | | 🔍 | **Entry point නැති destinations**: History · Saved · Help · About · Recents · account/tier row — drawer එක යද්දී ඒවට දොරක් නෑ. **Mode chip (engine picker) එකටත් දොරක් නෑ** — C-2/D-2/US-6 ඔක්කොම ඒක පේනවා කියලා උපකල්පනය කරනවා. Bar/drawer එකක් ආපහු දාලා විසඳන්න **එපා** — අලුත් decision record එකක් ඕන | UI_SPEC §4 |
| B9 | | 🔍 | **Medium/Expanded design නෑ** — rev.3 phone-first; හැම width එකකම එකම stack එක render වෙනවා. C-13 එකේ nav column එක on-hold, `material3-adaptive-navigation-suite` dependency එක දැන් unused | DECISIONS D-5 rev.3 open item |

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| B1 | ✅ [#27](https://github.com/sendtodilanka/tranzlate-app/pull/27) | 🔍 | Menu ඇරලා Back එබුවම app එක වැහෙනවා → `ModalDrawerSheet(drawerState)` | `DrawerContent.kt` |
| B2 | ✅ [#38](https://github.com/sendtodilanka/tranzlate-app/pull/38) | 🔍 | `DrawerRow` (hand-rolled Surface+Row) → M3 `NavigationDrawerItem`. Drawer secondary → `selected=false` (primary "දැන් ඉන්නේ කොහෙද" state එක bottom bar එකේ) | `DrawerContent.kt` |
| B3 | ⏳ **deferred** (follow-up) | 🔍 | Shell `SnackbarHost` ×1. ~~Bottom-nav එකෙන් පස්සේ `NavigationSuiteScaffold` structure එකට rethink ඕන~~ → **2026-07-26 (D-5 rev.3): shell එකේ `Scaffold` එකක්ම නෑ** — `TranzlateApp` කියන්නේ `NavDisplay` එකක් විතරයි, `HomeScreen` තමන්ගේම `Scaffold` + `SnackbarHostState` එකක් තියාගන්නවා. Guided messages ගොඩක් Home එකෙන් එන නිසා දැනට ඒක වැඩ කරනවා, ඒත් screen එකකට එකක් = duplication + inconsistent placement. Shell-level host එකක් ඕනද කියන එක **ආපහු තීරණය කරන්න** (audit #7) | `TranzlateApp.kt`, `HomeScreen.kt` |
| B4 | ✅ [#33](https://github.com/sendtodilanka/tranzlate-app/pull/33) | 🔍 | nav3 `rememberViewModelStoreNavEntryDecorator()` නෑ → `lifecycle-viewmodel-navigation3` + decorator | `TranzlateApp.kt` |
| B5 | ✅ [#35](https://github.com/sendtodilanka/tranzlate-app/pull/35) | 🔍 | Custom "Claude-app" drawer push/scale/round motion → M3 `ModalNavigationDrawer` default (drawer-open status-bar band fix) | `TranzlateApp.kt` |
| B6 | ✅ [#39](https://github.com/sendtodilanka/tranzlate-app/pull/39) → **↩ reversed [#43](https://github.com/sendtodilanka/tranzlate-app/pull/43)** | 👤 | **Bottom `NavigationBar` (Home/Chat/Camera)** via `NavigationSuiteScaffold` (D-5 rev.2) · Chat=coming-soon · **hide-on-secondary** (`NavigationSuiteType.None`) · nav regression tests. **2026-07-26: bar එකම අයින් (D-5 rev.3)** — ඉතුරු වුණේ back-guard එකයි Chat destination එකයි විතරයි | `TranzlateApp.kt`, ~~`TopLevelDestination.kt`~~ (දැන් dead — B7) |

## 🅒 Screens — Text vertical ⏳ **දැන් කරන කණ්ඩායම** (🅐 · 🅑 ✅ ඉවරයි)

> **2026-07-26 (PR #43) නිසා line numbers මාරු වෙලා:** `HomeScreen.kt` සම්පූර්ණයෙන්ම ආපහු ලිව්වා (card stack), සහ `:core:ui/ComposerCard.kt` · `QuickActionButton.kt` · `PrimaryActionButton.kt` **තුනම දැන් dead code** — අලුත් Home එක input card එක inline හදනවා. C1/C6/C8 ආපහු **අලුත් `HomeScreen.kt`** එකේ verify කරන්න ඕන (defect එක තියෙනවද කියලා), පරණ line එකේ නෙවෙයි.

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| C1 | | 🔍 | අකුරු 500 ඉක්මවුණාම TalkBack ට උපදෙස ඇහෙන්නේ නෑ | ~~`HomeScreen.kt:228`~~ → අලුත් `HomeScreen.kt` `InputCard` (re-verify) |
| C2 | | 🔍 | Support නැති භාෂා යුගලයට වැඩක් නැති "Retry" → "භාෂාවක් තෝරන්න" | `ResultScreen.kt:154` |
| C3 | | 🔍 | භාෂාව මාරු කළාම ආයෙත් translate වෙන්නේ නෑ (D-1) | `TextViewModel.kt:157` |
| C4 | | 🔍 | Process death එකෙන් පස්සේ නොකියාම ආයෙත් translate කරනවා | `TextViewModel.kt:129` |
| C5 | | 🔍 | DataStore write guards (භාෂාව මාරු කරද්දී `IOException` → crash). *corruption handler එක 🅐 A2 ට ගියා* | `TextViewModel.kt:143` |
| C6 | | 🔍 | Text කොටුවේ placeholder දෙපාරක් කියවෙනවා + "වැරදියි" තත්ත්වය නෑ | ~~`ComposerCard.kt:131`~~ → අලුත් `HomeScreen.kt` `InputCard` `decorationBox` (re-verify; `ComposerCard.kt` දැන් dead) |
| C7 | | 🔍 | භාෂා ලැයිස්තුවේ පේළි → `selectable` + `selectableGroup` | `LanguagePickerScreen.kt:217` |
| C8 | | 🔍 | අකුරු 200% කළාම භාෂා pill සහ "Conversation" කැපෙනවා | ~~`ComposerCard.kt:257`~~ → අලුත් `LanguagePill` (`maxLines = 1` + `Ellipsis`) සහ `ToolCard` title/subtitle (දෙකම `maxLines = 1`) — 200% scale එකේදී **ආපහු මනින්න ඕන** |
| C9 | | 👤/🔍 | **අලුත් Home එකේ contentDescription නෑ** — `tt_text_input` · `tt_text_counter` · `tt_text_source_lang` · `tt_text_target_lang` (keys තියෙනවා: `cd_text_input`, `cd_text_counter`, `cd_text_source_lang`, `cd_text_target_lang`). TalkBack ට pill එකේ පේන label එකයි bare "12 / 500" එකයි විතරයි | `HomeScreen.kt` · TEST_A11Y §2.1 rows 1/4/5/7 |
| C10 | | 🔍 | **C-14 migration**: `HomeScreen.kt` එකේ private `dp`/`sp` ladder → `LocalSpacing` · `Dimensions` · `MaterialTheme.shapes` · `Elevation` · type scale. Scale එකට නැති value එකක් තියෙනවා නම් DESIGN_SYSTEM §4/§5/§6 එකේ **එක තැනකින්** amend කරන්න | `HomeScreen.kt:69-76` + inline |
| C11 | | 🔍 | Home එකේ primary action එක light/dark දෙකේම `primary` fill — UI_SPEC §1 නීතිය (dark = `primaryContainer`) `PrimaryActionButton` එකේ; Home ඒක පාවිච්චි කරන්නේ නෑ | `HomeScreen.kt` `InputCard` |

## ඊට පස්සේ

මේ තුන ඉවර වුණාම ඊළඟ කණ්ඩායම මෙතන **පේළියෙන් පේළියට ලියනවා** — "පස්සේ බලමු" කියලා
එකතු කරන එකෙන් තමයි A1/A5/A6 මුලින් නැති වුණේ (owner ලොග් එක බලන්න).

| කණ්ඩායම | ගණන | මොකක්ද | ලියලා තියෙන්නේ |
|---|---|---|---|
| 🟠 D — brains-prep | 12 | Cache-first read · Access/Usage API හැඩය · placeholders විවෘත පැත්තට වැරදීම · Room migration · භාෂා catalog seed · prefs race · **`UsageDataSource` එකට `.catch` නෑ** (PR #21 review එකෙන්; factory corruption handler එකෙන් ආවරණය වෙනවා, transient IO නෑ — Usage brain හදනකොට) | [audit P1](2026-07-23-deep-audit.md#p1--correctness-that-must-be-settled-before-the-brains-land) |
| 🔵 E — release readiness | 16 | R8 (#5) · localeConfig · backup rules · DAO `LIMIT` · **`material-icons-extended`** · nav3 decorator. **මනිනලද (2026-07-23):** icon class **10,820ක්** dex එකේ, අපි පාවිච්චි කරන්නේ **~25ක්**; AAR එක **34 MB**; release APK 13.5 MB — R8 නැති නිසා release එකේත් ඔක්කොම තියෙනවා. **2026-07-26 update:** Home දැන් `:core:designsystem` එකේ **Material Symbols Rounded vector drawable 17ක්** පාවිච්චි කරනවා (`ic_*.xml`) — `material-icons-extended` dependency එක තව ඉතුරු වෙලා තියෙන්නේ `:core:ui/ResultBlock.kt` සහ `:app` (`ComingSoonScreen` + dead drawer files) වල විතරයි. ඒවත් drawable වලට ගෙනාවම dependency එක සම්පූර්ණයෙන්ම අයින් කරන්න පුළුවන්. **+ `material3-adaptive-navigation-suite` දැන් unused** (D-5 rev.3) · **+ `roboto_flex.ttf` = 1.79 MB** APK එකට එකතු වුණා — R8/resource-shrink මනින්නෙ ඕකත් එක්ක | [audit P2](2026-07-23-deep-audit.md#p2--stock-material-3-and-platform-correctness) |
| ⚪ F — hygiene | 12 | Tests · Konsist gates · strings/C-3 · dead code · `.gitignore` · D-3 vs UI_SPEC · **`Konsist.scopeFromProject()` එකෙන් `.claude/worktrees/` එකත් scan වෙනවා** — worktree එකක් තියෙද්දී හැම source file එකක්ම දෙපාරක් පේනවා, `Translator` test එක `[Translator, Translator]` කියලා fail වෙනවා. Worktree එක තමයි මේ project එකේ ක්‍රමය, ඒ නිසා ඒක scope එකෙන් අයින් කරන්න ඕන (2026-07-23 හම්බුණා) | [audit P3](2026-07-23-deep-audit.md#p3--hygiene-tests-docs) |
| ⚡ G — performance | — | Detectors (LeakCanary · StrictMode · Compose metrics) → R8 (#5) → Macrobenchmark → Baseline Profiles. **පිළිවෙළ වැදගත්**: R8 නැතුව මනින්නේ අපි ship නොකරන build එකක් | issue [#22](https://github.com/sendtodilanka/tranzlate-app/issues/22) · roadmap Phase F |

---

## 👤 Owner හොයාගත්ත ඒවා — ලොග් එක

අලුත් එකක් ආවම මුලින්ම මෙතනට. ඊට පස්සේ මම පිළිවෙළට උඩ ලැයිස්තුවට දාලා, මෙතන "→ A*n*" කියලා ලකුණු කරනවා.

| දිනය | මොකක්ද | audit එකේ තිබුණද? | ගියේ |
|---|---|---|---|
| 2026-07-23 | `android:windowBackground` | ✅ තිබුණා (P2-11) — ඒත් queue එකට ගෙනාවේ නෑ | → **A1** ✅ |
| 2026-07-23 | `android:forceDarkAllowed=false` | ❌ **audit එකට හසු වුණේම නෑ** | → **A1** ✅ |
| 2026-07-23 | `isAppearanceLightStatusBars = !darkTheme` (පරණ app එකේ Theme.kt) | ✅ තිබුණා (P2-16) — ඒත් queue එකට ගෙනාවේ නෑ | → **A5** |
| 2026-07-23 | `isNavigationBarContrastEnforced = false` (පරණ app එකේ Theme.kt) | — | ~~❌ **ඕන නෑ** · API 36 එකේ deprecated (`android.jar` එකේ බැලුවා), `enableEdgeToEdge` ම manage කරනවා~~ → **2026-07-26 මේක වැරදියි කියලා පේනවා**: "manage කරනවා" කියන්නේ "off" කියන එක නෙවෙයි. ඒක apply වෙන API range එකේ platform එක තාමත් nav bar එක tint කරනවා (`#131314` page එකක් උඩ `#16171C`) — strip එක වෙනම පාටක් වගේ පේනවා. PR [#43](https://github.com/sendtodilanka/tranzlate-app/pull/43) එකෙන් `MainActivity` එකේ **API 29+ guard එකක් ඇතුළේ set කළා**. plan `issue-17-core-shell-theme.md` T-7 එකත් හදලා |
| 2026-07-26 | Home UI එක **Claude Design "Offline Translator M3"** export එකට හදමු — bottom nav එකයි drawer එකයි අයින් | ❌ (redesign) | → **issue [#42](https://github.com/sendtodilanka/tranzlate-app/issues/42)** · PR [#43](https://github.com/sendtodilanka/tranzlate-app/pull/43) · **D-5 rev.3** · docs reconcile = PR (this one) |
| 2026-07-26 | Home එකේ measurements design එකේ raw `dp`/`sp` ලෙස තියෙනවා (`ScreenMargin`, `CardRadius`, `15.sp` …) — token scale එකෙන් නෙවෙයි | ❌ audit-මිස් | → **C-14** (DECISIONS) · HomeScreen එක tracked exception එක; migration item |
| 2026-07-26 | අලුත් Home එකේ `cd_text_input` · `cd_text_counter` · `cd_text_source_lang` · `cd_text_target_lang` **attach වෙලා නෑ** (TalkBack ට "12 / 500" කියලා විතරයි ඇහෙන්නේ) | ❌ audit-මිස් | → **C9** (පහළ 🅒 batch එකට) · TEST_A11Y §2.1 rows 1/4/5/7 |
| 2026-07-23 | `installSplashScreen()` — splash screen එක | ⚠️ අඩක් (P2-11 එකේ සඳහන් වුණා, queue එකේ තිබුණේ නෑ) | → **A6** · *පාට* නම් A1 එකෙන්ම හැදුණා ([doc](https://developer.android.com/develop/ui/views/launch/splash-screen): splash එක `windowBackground` ගන්නවා) |
| 2026-07-23 | Screenshot harness එකට issue + roadmap එකක් ඕන | — | → **A8** · issue [#20](https://github.com/sendtodilanka/tranzlate-app/issues/20) + roadmap Phase F |
| 2026-07-23 | මේ file එකම stale වෙලා (පරණ අංක, හිස් "ඉවරයි" table) | — | → හදලා (මේ version එක) |
| 2026-07-24 | UI/UX පිට හැරෙන එක — **FT (Lingo French) app structure** එක reference කරමු | — | → **issue #26** · bottom nav D-5 rev.2 (B5/B6/#38/#37). Structure ගත්තා, code copy නෑ, GT/M3 skin රඳවා |
| 2026-07-24 | Bottom nav එක secondary/detail screens වලත් පේනවා | ❌ (redesign එකෙන් ආපු එකක්) | → **hide-on-secondary** (`NavigationSuiteType.None`, #39) — owner තෝරගත්තා |
| 2026-07-25 | androidTest suite එකම **API 36 එකේ fail** (Espresso `InputManager.getInstance`) | ❌ audit-මිස් (infra) | → **issue [#40](https://github.com/sendtodilanka/tranzlate-app/issues/40)** · CI unaffected (ubuntu, no emulator) |

---

## ඉවරයි

| # | මොකක්ද | PR | ඔප්පු කරපු විදිහ |
|---|---|---|---|
| A1 | Window background + force-dark opt-out | [#18](https://github.com/sendtodilanka/tranzlate-app/pull/18) merged | Launch එකේ frame එකින් එක pixel කියෙව්වා: dark `#131314` · light `#FAF9F8`, Android ගේ `#303030` phase එකක් **එකම frame එකකවත් නෑ**. APK එකේ style එක API bucket අනුව split වෙලා |
| A2 | Theme preference + corruption handler | [#21](https://github.com/sendtodilanka/tranzlate-app/pull/21) | Test 10ක්. Corruption catch එක තාවකාලිකව අයින් කරලා ඒ test එකම fail වෙනවා කියලා ඔප්පු කළා (vacuous නෙවෙයි). Device එකේ crash නෑ, prefs file ලියවෙනවා |
| A3 | Theme wiring — app එකේ තේරීම system එකට වඩා ඉහළින් | [#24](https://github.com/sendtodilanka/tranzlate-app/pull/24) | Refactor එකක් නිසා "පාට වෙනස් වුණේ නෑ" කියලා මැනලා ඔප්පු කළා: light `#0B57D0`/`#FFFFFF` · dark `#0842A0`/`#D3E3FD` — දෙකම කලින් වගේම. Test 5ක් (`ThemeMode.isDark`) |
| A5+A6 | Status/nav bar icon app එකේ theme එකට · splash එක preference එක එනකම් රඳවනවා | [#25](https://github.com/sendtodilanka/tranzlate-app/pull/25) | Stored default එක තාවකාලිකව DARK කරලා end-to-end ඔප්පු කළා: system **light**, app **dark** (`#131314`), status bar clock+icons **`#FFFFFF`** (light) — A5 නැත්නම් ඒවා කළු වෙලා නොපෙනේ. Splash එකේ background අපේ පාට, ඊට පස්සේ Home |
| A5+A6 | Bars app එකේ theme එකට · splash preference එක එනකම් රඳවනවා | [#25](https://github.com/sendtodilanka/tranzlate-app/pull/25) merged | Design-debate එකෙන් හැඩය තීරණය කළා. `enableEdgeToEdge` **එකයි**, live field එකක් කියවන detector එකක් එක්ක. Stale-listener probe **PASS** (system DARK→LIGHT flip කළත් bars app-dark). Early-call fallback එක ලියලා මැනලා **අයින් කළා** — වෙනසක් නෑ (frame 1ක්, දෙපාරක්) |
| A7 | Home + Result → `Scaffold` | [#19](https://github.com/sendtodilanka/tranzlate-app/pull/19) — CI green, co-verify ඉවරයි, merge එකට සූදානම් | Icon bounds ඇතුළේ luminance මැනලා: dark `#E3E3E3` · light `#1F1F1F` (කලින් දෙකේම `#000000`, ~1.1:1 → ~14.5:1). Snackbar nav-bar (`y=2337`) එකට උඩින්. Keyboard එකේදී composer `1925→1105`, menu `189-252` තැනේම |
| B5 · #35 | Drawer motion → M3 default | [#35](https://github.com/sendtodilanka/tranzlate-app/pull/35) merged · A1 co-verify PASS | Device (API 36): drawer content full-bleed + scrim-dim, **no** push/scale/round; status-bar band gone (`#0D0D0E` uniform; gesture-vs-3btn A/B) |
| B6 · #39 | Bottom nav (Home/Chat/Camera) + hide-on-secondary | [#39](https://github.com/sendtodilanka/tranzlate-app/pull/39) merged (replaced #36) · **A2 cross-model PASS** | insets/back/IME/toggle → material3 1.4.0 source එකට verify. Device: nav on Home, **hidden on Settings**, Chat=coming-soon. (Instrumentation run API 36 එකේ Espresso #40 නිසා block; tests compile) |
| B2 · #38 | `DrawerRow` → M3 `NavigationDrawerItem` | [#38](https://github.com/sendtodilanka/tranzlate-app/pull/38) merged · co-verify CLEAN | testTags preserved · touch-target 48→56dp · `selected=false` (secondary). *(2026-07-26: drawer එකත් එක්කම retired — D-5 rev.3)* |
| #37 | Docs → D-5 rev.2 (13 docs) | [#37](https://github.com/sendtodilanka/tranzlate-app/pull/37) merged · co-verify F1/F2 fixed | D-5 rev.2 + hide-on-secondary recorded · issue-15 supersede note · `onboarding_complete` pref added. *(2026-07-26: D-5 rev.3 එකෙන් ආපහු reconcile කළා)* |
| #43 | Home = Claude Design card stack · bottom nav + drawer අයින් (**D-5 rev.3**) | [#43](https://github.com/sendtodilanka/tranzlate-app/pull/43) merged (`10353ba`) · co-verify blockers හදලා (`46758b4`) | `NavShellSmokeTest` ආපහු ලිව්වා: card stack render වෙනවා · `tt_app_nav_home`/`tt_app_drawer` **assertDoesNotExist** · settings icon → Settings · camera tool card → Camera. `TextTranslationScreenTest`: counter `"12 / 500"` · blank state එකේ `tt_text_translate_btn` **නෑ**, `tt_text_mic` තියෙනවා. Roboto Flex Medium weight එක axis-instance එකෙන් හදලා. **⚠ androidTest suite එක API 35+ emulator වල run වෙන්නේ නෑ** (Espresso `InputManager.getInstance`, issue [#40](https://github.com/sendtodilanka/tranzlate-app/issues/40)) — API ≤34 image එකක් ඕන |
