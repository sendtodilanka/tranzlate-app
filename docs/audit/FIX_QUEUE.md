# Fix queue — එකින් එක

වැඩ කරන ක්‍රමය: **එකක් = එක issue = එක පොඩි PR = emulator එකේ බලනවා → ඊළඟ එක.**
Bulk edit නෑ.

**මූලාශ්‍රය තීරුව:** 🔍 = audit එකෙන් · 👤 = **owner හොයාගත්තා**.
එකම ලැයිස්තුවක් තියාගන්නේ පිළිවෙළ නැති නොවෙන්න — ඒත් කොහෙන් ආවද කියලා පේනවා.
👤 ගණන වැඩි වෙනවා කියන්නේ මගේ audit එකේ හිඩැස් තියෙනවා කියන එකයි — ඒක බලාගෙන ඉන්න ඕන දෙයක්.

විස්තර: [`2026-07-23-deep-audit.md`](2026-07-23-deep-audit.md) · Sinhala පිටුව: `docs/plan/review/audit-2026-07-23.html`

---

**පිළිවෙළ (owner, 2026-07-23):** මුලින්ම **core / shell** — theme, `MainActivity`, window, shell.
ඊට පස්සේ තමයි screens. යටින්ම තියෙන ස්ථරය කලින්.

---

## 🅐 Core / Shell — Theme ⏳ දැන් කරන කණ්ඩායම

Issue [#17](https://github.com/sendtodilanka/tranzlate-app/issues/17) · plan [`issue-17-core-shell-theme.md`](../plan/issue-17-core-shell-theme.md) (accepted)

| # | මූ | මොකක්ද | තැන |
|---|---|---|---|
| A1 | 👤 | ✅ **ඉවරයි** — Window theme: `android:windowBackground` + `android:forceDarkAllowed=false`. Device එකේ ඔප්පු කළා: light `#FAF9F8` · dark `#131314` (කලින් Android ගේ `#FAFAFA`/`#303030`) | `values/themes.xml`, `values-night/themes.xml`, `values*/colors.xml` |
| A2 | 🔍 | Theme preference එක: DataStore keys + repository (`SYSTEM`/`LIGHT`/`DARK` + dynamic on/off). **+ corruption handler** — A6 මේ pref එක එනකම් splash එක රඳවන නිසා, file එක හැදුණොත් crash එකක් වෙනුවට **splash එකේම හිරවෙනවා**. ඒ නිසා ඒක මෙතනට ගෙනාවා (කලින් C5) | `TranzlatePreferencesDataSource.kt`, `DataStoreModule.kt:23` |
| A3 | 🔍 | `TranzlateTheme(darkTheme, dynamicColor)` ඒ preference එකෙන් run කරන එක + `PrimaryActionButton` එකේ `isSystemInDarkTheme()` branch එක අයින් කිරීම (`TODO(#7)`) | `Theme.kt`, `PrimaryActionButton.kt:48` |
| A4 | 🔍 | **Settings screen** — light / dark / system + dynamic colour toggle (දැන් 32 පේළියේ placeholder එකක්) | `feature/settings` |
| A5 | 👤 | `MainActivity`: `DisposableEffect(darkTheme)` + `enableEdgeToEdge(detectDarkMode = { darkTheme })` — status/nav bar icon පාට app එකේ theme එකට. (`Activity` cast + `SideEffect` ක්‍රමය පාවිච්චි කරන්නේ නෑ.) | `MainActivity.kt:20` |
| A6 | 👤 | `core-splashscreen` + `setKeepOnScreenCondition` — theme preference එක එනකම් රඳවගන්න (නැත්නම් dark තෝරලා තියෙන කෙනෙකුට **light flash**). API 23+ backport. **සීමාව 1000ms** (Google) | `MainActivity.kt:19` |
| A7 | 🔍 | ✅ **ඉවරයි** — `ResultScreen` + `HomeScreen` → `Scaffold`. Device එකේ ඔප්පු කළා: icon දැන් dark `#E3E3E3` / light `#1F1F1F` (කලින් දෙකේම `#000000`); snackbar nav-bar එකට උඩින්; keyboard එකේදී composer උඩට, top bar තැනේම | `ResultScreen.kt`, `HomeScreen.kt` |
| A8 | 🔍 | **Screenshot-test harness** (Robolectric + Roborazzi). දැන් repo එකේ JVM screenshot test එකක් හදන්න ක්‍රමයක් නෑ — ඒ නිසා A7 එකේ bug එක වගේ එකක් ආපහු ආවොත් CI එකෙන් අහුවෙන්නේ නෑ. මේකෙන් dark mode විතරක් නෙවෙයි, contrast · RTL · අකුරු 200% ඔක්කොම ආවරණය වෙනවා | නව infra |

## 🅑 Core / Shell — Navigation shell

| # | මූ | මොකක්ද | තැන |
|---|---|---|---|
| B1 | 🔍 | Menu ඇරලා Back එබුවම app එක වැහෙනවා → `ModalDrawerSheet(drawerState)` | `DrawerContent.kt:79` |
| B2 | 🔍 | Menu එකේ "දැන් ඉන්නේ කොහෙද" පේන්නේ නෑ → `NavigationDrawerItem` | `DrawerContent.kt:166` |
| B3 | 🔍 | Shell එකට `Scaffold` + `SnackbarHostState` **එකක්** (දැන් තුනක්, එකිනෙක උඩ එනවා) | `TranzlateApp.kt:74,87` |
| B4 | 🔍 | nav3 `rememberViewModelStoreNavEntryDecorator()` නෑ — අලුත් screen එකකට තමන්ගේම ViewModel දැම්මොත් clear වෙන්නේ නෑ | `TranzlateApp.kt:204` |

## 🅒 Screens — Text vertical

| # | මූ | මොකක්ද | තැන |
|---|---|---|---|
| C1 | 🔍 | අකුරු 500 ඉක්මවුණාම TalkBack ට උපදෙස ඇහෙන්නේ නෑ | `HomeScreen.kt:228` |
| C2 | 🔍 | Support නැති භාෂා යුගලයට වැඩක් නැති "Retry" → "භාෂාවක් තෝරන්න" | `ResultScreen.kt:154` |
| C3 | 🔍 | භාෂාව මාරු කළාම ආයෙත් translate වෙන්නේ නෑ (D-1) | `TextViewModel.kt:157` |
| C4 | 🔍 | Process death එකෙන් පස්සේ නොකියාම ආයෙත් translate කරනවා | `TextViewModel.kt:129` |
| C5 | 🔍 | DataStore write guards (භාෂාව මාරු කරද්දී `IOException` → crash). *corruption handler එක 🅐 A2 ට ගියා* | `TextViewModel.kt:143` |
| C6 | 🔍 | Text කොටුවේ placeholder දෙපාරක් කියවෙනවා + "වැරදියි" තත්ත්වය නෑ | `ComposerCard.kt:131` |
| C7 | 🔍 | භාෂා ලැයිස්තුවේ පේළි → `selectable` + `selectableGroup` | `LanguagePickerScreen.kt:217` |
| C8 | 🔍 | අකුරු 200% කළාම භාෂා pill සහ "Conversation" කැපෙනවා | `ComposerCard.kt:257` |

ඊට පස්සේ: 🟠 brains-prep (11) → 🔵 release readiness → ⚪ පිරිසිදු කිරීම්.
ඒවා [audit report](2026-07-23-deep-audit.md) එකේ P1 / P2 / P3 කොටස් වල.

---

## 👤 Owner හොයාගත්ත ඒවා — ලොග් එක

අලුත් එකක් ආවම මුලින්ම මෙතනට. ඊට පස්සේ මම පිළිවෙළට උඩ ලැයිස්තුවට දාලා, මෙතන "→ #n" කියලා ලකුණු කරනවා.

| දිනය | මොකක්ද | audit එකේ තිබුණද? | ගියේ |
|---|---|---|---|
| 2026-07-23 | `android:windowBackground` | ✅ තිබුණා (P2-11) — ඒත් queue එකට ගෙනාවේ නෑ | → #2 |
| 2026-07-23 | `android:forceDarkAllowed=false` | ❌ **audit එකට හසු වුණේම නෑ** | → #2 |
| 2026-07-23 | `isAppearanceLightStatusBars = !darkTheme` (පරණ app එකේ Theme.kt) | ✅ තිබුණා (P2-16) — ඒත් queue එකට ගෙනාවේ නෑ | → #14 |
| 2026-07-23 | `isNavigationBarContrastEnforced = false` (පරණ app එකේ Theme.kt) | — | ❌ **ඕන නෑ** · API 36 එකේ deprecated (`android.jar` එකේ බැලුවා), `enableEdgeToEdge` ම manage කරනවා |
| 2026-07-23 | `installSplashScreen()` — splash screen එක | ⚠️ අඩක් (P2-11 එකේ සඳහන් වුණා, queue එකේ තිබුණේ නෑ) | → #15 · *පාට* නම් #2 එකෙන්ම හැදෙනවා ([doc](https://developer.android.com/develop/ui/views/launch/splash-screen): splash එක `windowBackground` ගන්නවා) |

---

## ඉවරයි

| # | මොකක්ද | PR |
|---|---|---|
| — | — | — |
