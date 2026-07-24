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

## 🅑 Core / Shell — Navigation shell ⏳ **දැන් කරන කණ්ඩායම**

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| B1 | 🔵 [#27](https://github.com/sendtodilanka/tranzlate-app/pull/27) **open** | 🔍 | Menu ඇරලා Back එබුවම app එක වැහෙනවා → `ModalDrawerSheet(drawerState)` | `DrawerContent.kt` |
| B2 | | 🔍 | Menu එකේ "දැන් ඉන්නේ කොහෙද" පේන්නේ නෑ → `NavigationDrawerItem` | `DrawerContent.kt:166` |
| B3 | | 🔍 | Shell එකට `Scaffold` + `SnackbarHostState` **එකක්** (දැන් තුනක්, එකිනෙක උඩ එනවා) | `TranzlateApp.kt:74,87` |
| B4 | ⏳ **දැන් LIVE** — A4 (#31) එකේ `SettingsViewModel` තමයි `entry<>` ඇතුළේ `hiltViewModel()` කරන පළවෙනි එක, ඒ නිසා Activity store එකට resolve වෙනවා (stateless නිසා functional හානියක් නෑ, ඒත් clear වෙන්නේ නෑ) | 🔍 | nav3 `rememberViewModelStoreNavEntryDecorator()` නෑ → `lifecycle-viewmodel-navigation3` + decorator | `TranzlateApp.kt` |

## 🅒 Screens — Text vertical

| # | තත්ත්වය | මූ | මොකක්ද | තැන |
|---|---|---|---|---|
| C1 | | 🔍 | අකුරු 500 ඉක්මවුණාම TalkBack ට උපදෙස ඇහෙන්නේ නෑ | `HomeScreen.kt:228` |
| C2 | | 🔍 | Support නැති භාෂා යුගලයට වැඩක් නැති "Retry" → "භාෂාවක් තෝරන්න" | `ResultScreen.kt:154` |
| C3 | | 🔍 | භාෂාව මාරු කළාම ආයෙත් translate වෙන්නේ නෑ (D-1) | `TextViewModel.kt:157` |
| C4 | | 🔍 | Process death එකෙන් පස්සේ නොකියාම ආයෙත් translate කරනවා | `TextViewModel.kt:129` |
| C5 | | 🔍 | DataStore write guards (භාෂාව මාරු කරද්දී `IOException` → crash). *corruption handler එක 🅐 A2 ට ගියා* | `TextViewModel.kt:143` |
| C6 | | 🔍 | Text කොටුවේ placeholder දෙපාරක් කියවෙනවා + "වැරදියි" තත්ත්වය නෑ | `ComposerCard.kt:131` |
| C7 | | 🔍 | භාෂා ලැයිස්තුවේ පේළි → `selectable` + `selectableGroup` | `LanguagePickerScreen.kt:217` |
| C8 | | 🔍 | අකුරු 200% කළාම භාෂා pill සහ "Conversation" කැපෙනවා | `ComposerCard.kt:257` |

## ඊට පස්සේ

මේ තුන ඉවර වුණාම ඊළඟ කණ්ඩායම මෙතන **පේළියෙන් පේළියට ලියනවා** — "පස්සේ බලමු" කියලා
එකතු කරන එකෙන් තමයි A1/A5/A6 මුලින් නැති වුණේ (owner ලොග් එක බලන්න).

| කණ්ඩායම | ගණන | මොකක්ද | ලියලා තියෙන්නේ |
|---|---|---|---|
| 🟠 D — brains-prep | 12 | Cache-first read · Access/Usage API හැඩය · placeholders විවෘත පැත්තට වැරදීම · Room migration · භාෂා catalog seed · prefs race · **`UsageDataSource` එකට `.catch` නෑ** (PR #21 review එකෙන්; factory corruption handler එකෙන් ආවරණය වෙනවා, transient IO නෑ — Usage brain හදනකොට) | [audit P1](2026-07-23-deep-audit.md#p1--correctness-that-must-be-settled-before-the-brains-land) |
| 🔵 E — release readiness | 16 | R8 (#5) · localeConfig · backup rules · DAO `LIMIT` · **`material-icons-extended`** · nav3 decorator. **මනිනලද (2026-07-23):** icon class **10,820ක්** dex එකේ, අපි පාවිච්චි කරන්නේ **~25ක්**; AAR එක **34 MB**; release APK 13.5 MB — R8 නැති නිසා release එකේත් ඔක්කොම තියෙනවා | [audit P2](2026-07-23-deep-audit.md#p2--stock-material-3-and-platform-correctness) |
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
| 2026-07-23 | `isNavigationBarContrastEnforced = false` (පරණ app එකේ Theme.kt) | — | ❌ **ඕන නෑ** · API 36 එකේ deprecated (`android.jar` එකේ බැලුවා), `enableEdgeToEdge` ම manage කරනවා |
| 2026-07-23 | `installSplashScreen()` — splash screen එක | ⚠️ අඩක් (P2-11 එකේ සඳහන් වුණා, queue එකේ තිබුණේ නෑ) | → **A6** · *පාට* නම් A1 එකෙන්ම හැදුණා ([doc](https://developer.android.com/develop/ui/views/launch/splash-screen): splash එක `windowBackground` ගන්නවා) |
| 2026-07-23 | Screenshot harness එකට issue + roadmap එකක් ඕන | — | → **A8** · issue [#20](https://github.com/sendtodilanka/tranzlate-app/issues/20) + roadmap Phase F |
| 2026-07-23 | මේ file එකම stale වෙලා (පරණ අංක, හිස් "ඉවරයි" table) | — | → හදලා (මේ version එක) |

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
