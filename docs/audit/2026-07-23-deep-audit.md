---
status: record
date: 2026-07-23
scope: whole repo at `5693c47` (post-PR#16 UI reset)
method: 4 independent read-only audit lenses (stock-M3 · androidx/deprecations · a11y+contract · architecture/tests), then owner-session re-verification of every P0 against library sources and a running device
device: Pixel 7 AVD `Tranzlate_Play`, Android 36.1, `tranzlateFakeDebug`
---

# Deep audit — Tranzlate rebuild

Four lenses were run independently over `core/**`, `feature/**`, `app/**`, `build-logic/**`, `lib/**`
plus the governing docs. Every claim below that carries **[verified]** was re-checked in this
session against the resolved library source in the Gradle cache, or reproduced on the device —
not taken on the auditing agent's word.

Totals: **8 P0** (user-visible now) · **11 P1** (correctness, must land before the brains) ·
**16 P2** (stock-M3 / platform) · **11 P3** (hygiene, tests, docs).

---

## P0 — defects a user hits on today's build

### P0-1 · Dark mode: every result-screen action icon renders black-on-black [verified — device + source]

`ResultScreen.kt:117-121` roots the screen in a plain `Box(...).background(colorScheme.surface)` —
not a `Surface`, not a `Scaffold`. material3 declares
`val LocalContentColor = compositionLocalOf { Color.Black }` (`ContentColor.kt:33`, "Defaults to
`Color.Black` if no color has been explicitly set"), and only `Surface` re-provides it from the
container colour. `IconButton` takes its content colour from `LocalContentColor.current`, so every
icon in the body — speak, copy, reverse, 👍, 👎 (`ResultScreen.kt:231-314` via `ActionIconButton`
`:319-333`) — paints **pure black in both themes**.

In light mode black on `#FAF9F8` looks deliberate. In dark mode it is black on `#131314`:
contrast ≈ 1.1:1 against a required 4.5:1 — the controls are effectively invisible.
Screenshot: `docs/design/screenshots/ui3-result-dark.png`.

Text is unaffected because every `Text` on that screen passes an explicit colour; only the
icons inherit. The top bar is correct because `CenterAlignedTopAppBar` wraps itself in a `Surface`.

**Fix:** adopt `Scaffold` (P2-2) — it wraps content in a `Surface` and sets the content colour
correctly — or, minimally, `CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface)`.
Add a dark-theme screenshot test so this cannot regress.

> Not caught by: unit tests, Compose previews (previews render both themes but nobody
> compared the icon colour), cross-model review, or the palette audit — the palette itself is
> correct; the defect is that the palette is never applied to these nodes. Only the device
> screenshot caught it, exactly like the IME-pan bug in PR #14.

### P0-2 · System back exits the app instead of closing the drawer [verified — device + source]

Reproduced: open the drawer, press Back → the app closes to the launcher
(`topResumedActivity=…NexusLauncherActivity`), the drawer never closes.

Cause: back handling for the drawer lives **only** inside the `ModalDrawerSheet(drawerState = …)`
overload, which calls `DrawerPredictiveBackHandler` (`NavigationDrawer.kt:643`). `ModalNavigationDrawer`
itself (`:332`) installs none — its own contribution is the scrim plus `paneTitle`/`dismiss` semantics.
`DrawerContent.kt:79-87` passes a raw `Surface` as `drawerContent`, so nothing handles back.

**Consequence observed twice:** because the process was killed with `drawerState` saved as *open*
(`rememberDrawerState` is `rememberSaveable`), the next launch **restored the drawer open** — the
app cold-starts showing the drawer. One root cause, two symptoms.

**Fix:** use `ModalDrawerSheet(drawerState = drawerState, …)` (P2-1). Note the collision with the
hand-rolled push/scale motion at `TranzlateApp.kt:104-122`: M3's predictive-back also scales the
sheet, so the two must be reconciled, not stacked.

### P0-3 · Over-limit guidance is invisible to TalkBack [verified]

`HomeScreen.kt:228-229` passes `counterContentDescription = stringResource(R.string.cd_text_counter, …)`
unconditionally, while the *visible* counter text switches to `text_over_char_limit` at `:220-222`.
A screen-reader user typing 501 characters hears "501 of 500 characters used" and is never told
what to do. Violates EDGE_CASES §7 no-dead-end and TEST_A11Y §2.1 row 7.

**Fix:** compute the content description in the same `when` as the visible text.

### P0-4 · Snackbars render under the gesture navigation bar [verified — source]

`ResultScreen.kt:170-173` and `TranzlateApp.kt:170-173` place a bare `SnackbarHost` with
`Modifier.align(BottomCenter)` in a `Box` that carries no insets — the `safeDrawing` padding is on a
*sibling* `Column` (`ResultScreen.kt:123-127`). `SnackbarHost` applies no insets of its own;
`Scaffold` is what offsets it by `contentWindowInsets.getBottom()` (`Scaffold.kt:242-248, 282-286`).
`HomeScreen.kt:246` happens to be correct because it sits inside the padded box.

**Fix:** `Scaffold(snackbarHost = …)` (P2-2), or `windowInsetsPadding(WindowInsets.safeDrawing)`.

### P0-5 · Corrupt preferences crash the app on launch

`DataStoreModule.kt:23` builds the store with **no `corruptionHandler`**, and every
`dataStore.data.map { }` (`TranzlatePreferencesDataSource.kt:25-33`) has no `.catch`. It is consumed
by `stateIn(…, Eagerly)` in `TextViewModel.kt:70-76`, so a `CorruptionException`/`IOException`
propagates into `viewModelScope` — hard crash, no recovery path except clearing app data.
Per the DataStore docs, recovery is the app's responsibility.

**Fix:** `ReplaceFileCorruptionHandler { emptyPreferences() }` on the factory **and**
`.catch { if (it is IOException) emit(emptyPreferences()) else throw it }` on each read.

### P0-6 · Changing or swapping languages does not re-translate [verified]

`TextViewModel.kt:157-171` — `onSwapLanguages`, `onSelectSourceLanguage`, `onSelectTargetLanguage`
each write prefs and stop there. With a result on screen, the result and its language labels go
stale. Violates **D-1** and spec-01 §4 (`RESULT ─lang change→ VALIDATING`) / US-2 / US-4.

**Fix:** after the pref write, `if (_uiState.value is Result) startTranslation(...)` —
cache-first, no meter charge (needs P1-1).

### P0-7 · Process-death restore silently re-runs the translation [verified]

`TextViewModel.kt:129-131`: `restoreResultIfNeeded()` → `onRetry()` → `startTranslation(request)`.
Every restore of the Result screen fires a fresh engine call — a second network request today, and a
second quota charge the day metering is real.

**Fix:** persist the last *outcome* (SavedStateHandle or the Room row id) and rehydrate it;
re-run only on an explicit Retry.

### P0-8 · Unsupported language pair offers only a Retry that must fail [verified — device]

Reproduced with a fake-variant input outside the golden table: the error card offers **Retry**,
which deterministically fails again, and the Result screen has no language control to change the
pair. EDGE_CASES §4 requires the action to match the reason ("Pick language").

**Fix:** map `FailureReason` → action label + target; `UNSUPPORTED_PAIR` navigates to the picker.

---

## P1 — correctness that must be settled before the brains land

The 4 brains are placeholders today. Exact starting state:

| Brain | File | Behaviour today |
|---|---|---|
| Translation | `core/translate/RealTranslator.kt:24-29` | always `Error(ENGINE)` — confirmed on device with the prod variant |
| Offline models | `RealOfflineModelManager.kt:20-24` | `emptyMap()`; download/delete silently succeed |
| Access | `RealFeatureAccess.kt:22-26` | tier hard-`FREE`, **every engine allowed**, `isPaid()=false` |
| Access | `SubscriptionPurchaseFlow.kt:24-51` | NoOp gateway; unknown tier string maps to **PLUS** |
| Usage | `RealUsagePolicy.kt:24-30` | `remaining()=20` tier-blind, `isOver()=false`, `increment()` **no-op** |
| Ads | `RealAdsCoordinator.kt:27` | `Unit` |
| Connectivity | `ConnectivityMonitor.kt:25` | `flowOf(true)` — permanently "online", zero consumers |
| Config | `StaticRemoteConfigSource` | D-2/D-4 constants, zero consumers |

Every one of these **fails open**. The moment `ModeId.NLP35` becomes selectable, free users get
unmetered paid Cloud Translation.

- **P1-1 · No cache-first read** [verified] — `TranslateTextUseCase.kt:56-67` calls the engine first;
  `cached()` is consulted only inside `saveToHistory` afterwards. C-8's "no meter charge on a cache
  hit" is therefore unimplementable at the only place that sequences the flow. Fix before metering.
- **P1-2 · `FeatureAccess` cannot express Loading** — `val tier: Tier` + sync `isPaid()`
  (`FeatureAccess.kt:11-17`) while `:lib:subscription` correctly emits `Entitlement.Loading`.
  Gating on a not-yet-resolved entitlement is the old app's exact bug. Move to `Flow<Entitlement>`.
- **P1-3 · `UsagePolicy` is a sync API over an async source** — `remaining(): Int`, `isOver(): Boolean`
  (`UsagePolicy.kt:9-18`) over DataStore + midnight reset, with no atomicity contract on `increment()`.
  The real implementation must `runBlocking` or serve stale data, and two concurrent metered
  translations can double-spend. High-risk per CLAUDE.md Rule 5.
- **P1-4 · `LimitReached` renders as a generic engine error** [verified] — `TextViewModel.kt:211-213`
  maps it to `Error(ENGINE)` ⇒ "Something went wrong… check your connection". C-11 wants a
  dismissible `tt_text_limit_sheet`. Unreachable today (C-10 holds), wrong on day 1 of metering.
- **P1-5 · Entitlement denial and quota exhaustion are the same outcome** —
  `TranslateTextUseCase.kt:57-59`; `TranslationOutcome` has no `NotEntitled`.
- **P1-6 · Non-unique C-8 index + read-then-insert** [verified] —
  `TranslationEntity.kt:16` is a plain `Index`, dedupe is a use-case-level check, `@Insert` defaults to
  ABORT ⇒ concurrent identical translations duplicate. Make it `unique = true` + `onConflict = IGNORE`.
- **P1-7 · No Room migration policy** — `DatabaseModule.kt:20-22`: no migrations, no
  `fallbackToDestructiveMigration`, no migration test; schema v1 is exported. The next schema change
  crashes every upgrading install.
- **P1-8 · Language catalog never seeds; fallback is all-or-nothing** —
  `LanguageRepositoryImpl.kt:28-35` returns the bundled minimal list only while the table is empty, so
  the first row ever written collapses the picker to that row; `LanguageDao.setLastUsed` updates an
  empty table (permanent no-op ⇒ "Recent" can never work); `upsertAll` has zero callers.
- **P1-9 · Preferences race can overwrite the user's stored language pair** —
  `TextViewModel.kt:69-76` uses `stateIn(Eagerly, "en"/"fr")`, so `.value` is the fallback until
  DataStore's first emission. A swap tapped in that window writes the fallback pair over the stored one.
- **P1-10 · Wrong dispatcher for I/O** — `TextViewModel.kt:189` wraps the use case in
  `withContext(dispatchers.default)`; `Default` is CPU-sized. Room suspend DAOs are already main-safe,
  so the wrapper is both wrong-pool and unnecessary.
- **P1-11 · Unguarded DataStore writes** — `TextViewModel.kt:143,160,166,170`: an `IOException`
  from `edit` is uncaught in `viewModelScope` ⇒ crash on a language swap, with no user feedback.

---

## P2 — stock Material 3 and platform correctness

The owner's rule is "stock M3 wherever one exists". Sweep result: `ModalNavigationDrawer`,
`NavigationSuiteScaffold`, `CenterAlignedTopAppBar`, `ListItem`, `HorizontalDivider`, `AssistChip`,
`FilledIconButton`, `FilledTonalIconButton`, `IconButton`, `TextButton`, `Card`, `Surface`,
`SelectionContainer` are all stock and correctly used. Remaining gaps:

1. **Drawer sheet → `ModalDrawerSheet(drawerState)`** (`DrawerContent.kt:79-87`). Fixes P0-2, plus
   predictive-back animation, the 16 dp end-corner shape, the spec width range (ours is a fixed
   300 dp), and the `horizontalScaleUp/Down` gap fix.
2. **`Scaffold` on Home + Result + shell** (`HomeScreen.kt:152-251`, `ResultScreen.kt:117-174`,
   `TranzlateApp.kt:87`). Fixes P0-1 and P0-4 at the root, and provides the `nestedScroll` seam for #4.
3. **`DrawerRow` → `NavigationDrawerItem`** (`DrawerContent.kt:166-203`). Today there is **no selected
   state anywhere in the drawer** — the user cannot see which destination they are on. Also missing:
   `Role.Tab` semantics (a bare `Surface(onClick)` sets no role — `Surface.kt:227-231`), the 56 dp
   active-indicator height, 16/24 dp padding, the 12 dp icon-label gap, and the `badge` slot.
4. **Top-bar `scrollBehavior`** — none of the three bars has one, so the scrolled-container colour can
   never apply (`AppBar.kt:2523` reads `overlappedFraction`, pinned at 0 without a behavior). Result and
   picker scroll content straight under a transparent bar. *This one is a design call: the flat
   transparent bar is deliberate (UI_SPEC §1) — the owner should decide whether scrolled content gets
   a separation tint.*
5. **Composer text field → `TextFieldDefaults.DecorationBox`** (`ComposerCard.kt:131-179`). Real losses:
   the placeholder has no `clearAndSetSemantics` (TalkBack reads "Enter text" *and* "Text to translate");
   over-limit is not exposed as `isError`, so there is no `SemanticsProperties.Error` and no error colour;
   no `supportingText` slot for the counter.
6. **Picker rows → `Modifier.selectable(...)` + `LazyColumn(Modifier.selectableGroup())`**
   (`LanguagePickerScreen.kt:141,217-222`) — the split `clickable(role=…)` + `semantics { selected }`
   can drift, and without a selectable group TalkBack announces no "x of y" position in a ~100-item
   single-choice list.
7. **Drawer recents → `ListItem`** (`DrawerContent.kt:206-229`); **section headers need
   `Modifier.semantics { heading() }`** (`DrawerContent.kt:138`, `LanguagePickerScreen.kt:160`) — M3
   has no header component, but heading navigation currently skips both.
8. **Account row is not clickable at all** (`DrawerContent.kt:231-275`) — a dead area. (M3 1.4 ships no
   Avatar component, so the hand-rolled circle is justified; `Badge` defaults to the *error* container
   and means "notification count", so reusing it as a plan-tier pill would be a misuse —
   `NavigationDrawerItem`'s `badge` slot is the honest home.)
9. **Three independent `SnackbarHostState`s** (`TranzlateApp.kt:74`, `HomeScreen.kt:142`,
   `ResultScreen.kt:111`) — shell and screen snackbars can overlap; hoist one.
10. **nav3 is missing the ViewModelStore decorator** — `NavDisplay` at `TranzlateApp.kt:204` passes no
    `entryDecorators`; the default is `rememberSaveableStateHolderNavEntryDecorator()` only
    (`NavDisplay.kt:260-261`). It works today purely because both ViewModels are Activity-scoped; the
    first `hiltViewModel()` *inside* an `entry<>` will silently get the Activity store and never be
    cleared. Add `lifecycle-viewmodel-navigation3` + `rememberViewModelStoreNavEntryDecorator()`.
11. **No `android:windowBackground`** (`themes.xml:5`, `values-night/themes.xml:3`) — the framework
    default is `?colorBackground` = `#FAFAFA`/`#303030`, our surfaces are `#FAF9F8`/`#131314` ⇒ a grey
    flash on cold start, and that colour is also the Android 12+ splash background. Add it, and adopt
    `core-splashscreen` (`MainActivity.kt:18-26`) which also gives a `setKeepOnScreenCondition` seam
    for the first prefs/entitlement read. The comment in `values/themes.xml:3-4` already claims
    "Light/dark window backgrounds via values-night" — **neither file sets one**, so the comment
    documents an intent that was never implemented. `android:forceDarkAllowed=false` is missing too:
    the app declares its own night theme so force-dark should not engage, but stating it explicitly is
    the documented belt-and-braces and matters as soon as any View-based content (WebView, ads SDK)
    enters the window. *(Both were raised in the 2026-07-22 theme Q&A and were dropped from the audit
    lenses — added back here.)*
12. **No per-app language support** — `values-fil` and `values-pt-rBR` ship, but without
    `generateLocaleConfig = true` + `resources.properties` the user cannot pick the app's language in
    system settings; the translations are reachable only by changing the whole device locale.
13. **Backup rules are untouched templates** with `allowBackup="true"` ⇒ the full translation history
    (`tranzlate.db`) is uploaded to Google cloud backup, and a restored DB can collide with the schema.
14. **No `buildTypes` block anywhere** ⇒ release is un-minified, un-shrunk, un-obfuscated (issue #5),
    with no keep-rule baseline before Hilt/Room/serialization/AdMob land. AGP 9 also requires the
    `proguard-android-optimize.txt` default file.
15. **Recents query has no `LIMIT`** — `TranslationDao.kt:10-11` selects the whole table and
    `DrawerViewModel.kt:29-33` takes 4 in Kotlin; every insert re-emits the entire history. Paging for
    the History screen.
16. **`enableEdgeToEdge()` follows the *system* theme, not the app's** — `SystemBarStyle.auto`'s
    `detectDarkMode` reads `resources.configuration.uiMode` (`EdgeToEdge.kt:167-170`). Harmless today,
    breaks the moment the in-app light/dark switch ships (already flagged `TODO(#7)` in
    `PrimaryActionButton.kt:48`, which likewise branches on `isSystemInDarkTheme()` at the component —
    the exact pattern `LocalFloatingSurface` exists to eliminate).

---

## P3 — hygiene, tests, docs

- **Tests vs TEST_A11Y §3 Definition of Done.** Met: fake seam, golden table, state machine, Compose
  golden. Missing, in priority order: **(a)** no `.maestro/` directory at all; **(b)** no automated a11y
  assertion (every clickable has a content description) and no 48 dp bounds test; **(c)** no contrast,
  RTL or `fontScale = 2f` test — P0-1 would have been caught by (c)'s sibling, a dark-theme screenshot
  test; **(d)** no Room or DataStore instrumented tests, and **no repository unit tests at all**
  (`LanguageRepositoryImpl` fallback, unknown-mode degrade, `normalizeSourceText`); **(e)** no ViewModel
  test for LimitReached, restore-refire, or the prefs race (P0-7, P1-4, P1-9).
- **Konsist gates are weaker than they read** (`KonsistArchitectureTest.kt:36-65`) — all four inspect
  **import strings only**, so any fully-qualified reference bypasses them (and FQN usage is already
  idiomatic here, `SubscriptionPurchaseFlow.kt:30-51`). The ring-2 allowlist permits
  `com.codeboxlk.tranzlate.*` wholesale, so `:core:domain` importing `:core:data` would pass. In
  practice the Gradle graph is what enforces the rings. Assert on module dependency sets and add a
  meta-test that *should* fail.
- **C-8 normalization is implemented twice** — `FakeTranslationRepository.kt:60` and
  `TranslationRepositoryImpl.kt:19`. The dedupe test proves the fake's copy; the production regex has no
  direct test and drift keeps the test green. Move to one pure home and unit-test it.
- **D-3 contradicts UI_SPEC** — DECISIONS D-3 removed 👍/👎 as feedback-ambiguous; `ResultScreen.kt:303-314`
  implements them per UI_SPEC §2.4. DECISIONS is the declared tie-breaker. Resolve in one line, then
  either wire the star to the existing `setFavourite`/`favourites()`/`favourite` column (all zero
  callers today) or drop the affordances.
- **Strings.** Six keys ship outside the STRINGS catalogue, breaking C-3's "catalogue is the only
  authority" (`text_over_char_limit`, `text_lang_detect`, `text_lang_all_header`, `cd_text_lang_search`,
  `text_guided_lang_search`, `cd_text_reverse`). `tools:ignore="MissingTranslation"` is applied
  **blanket at the `<resources>` root** in four files, so every future key is silently exempt (fil and
  pt-rBR are 5/57 translated). Unused: `home_greeting_named`, `home_tile_conversation_sub`,
  `home_tile_camera_sub`, `cd_text_retry` (that last one is an a11y contract row — Retry's accessible
  name is currently just "Retry"). The greeting separator `", "` is hardcoded at `HomeScreen.kt:371-377`
  while `home_greeting_named` sits unused.
- **200 % text scale.** `ComposerCard.kt:257-263` gives the language pills a **fixed** 40 dp height —
  at `fontScale 2.0` labelLarge needs ~40 dp of line box alone, so the label clips; the following
  `heightIn(min = 48.dp)` is dead code under a fixed height (the 48 dp target actually comes from M3's
  `minimumInteractiveComponentSize()` inside `Surface`). `QuickActionButton.kt:71-79` is
  `maxLines = 1` + ellipsis, so "Conversation" truncates. The mode chip lives in a fixed-height
  `CenterAlignedTopAppBar` title slot.
- **No initial focus** — zero `FocusRequester` in the repo; TEST_A11Y §2.2 row 1 wants the composer
  focused on open (also GT parity).
- **White-label gaps** — `AppConfig.defaultSourceLang/defaultTargetLang` have **no consumer** while
  `TranzlatePreferencesDataSource.kt:68-71` hardcodes `en`/`fr`, so a new brand cannot set its default
  pair. Same shape: `RemoteConfigSource.textLimit()` is unconsumed while `TextViewModel.kt:32` hardcodes
  `TEXT_CHAR_LIMIT = 500`.
- **Module hygiene** — `:app` compiles against `:feature:paywall`, an empty shell with no NavKey and no
  caller; `:feature:languagepicker`'s `LanguagePickerScreen` collides by name with the real one in
  `:feature:text`, forcing `import … as OfflineLanguagesScreen` (`TranzlateApp.kt:47`).
- **Dead weight** — `UsageDataSource` (all 5 keys) unused while `:core:usage` declares
  `implementation(projects.core.datastore)`; `core/model/Availability.kt` (the whole EDGE_CASES §1
  model) unused while the UI hand-rolls `translateEnabled` booleans; `ConnectivityMonitor` unused;
  `LanguageDao.upsertAll`, `AppResult.getOrNull`, `ModeId.resolvedEngineOrNull`, all 6 `Elevation`
  levels, `TranzlateShapeNone`, most `Motion` easings. `room-ktx` is a blank artifact merged into
  `room-runtime` (Room 2.8+ advises removing it). Five catalog entries have zero consumers.
  `android.useAndroidX`, `android.nonTransitiveRClass` and `org.gradle.tooling.parallel` in
  `gradle.properties` are all no-ops on AGP 9 / Gradle 9.6.
- **`material-icons-extended` is pinned to 1.7.8** outside the BOM (the rest of Compose resolves
  1.11.4/1.4.0) — a frozen line shipping thousands of vectors for the ~25 icons actually used.
- **`:core:ui` uses `androidx.window.core.layout.WindowSizeClass` without declaring the dependency**
  (`WindowInfo.kt:7`) — it arrives transitively through `material3-adaptive`.
- **Fake offline-model states are unobservable** — `FakeOfflineModelManager.kt:24-47` applies two
  `MutableStateFlow` updates with no suspension between them, so a conflating collector never sees
  `Downloading`/`Deleting`; two of the six states the fake exists to demo are only visible to the
  turbine test.
- **Repo hygiene** — an untracked 4.6 MB `bugreport-*.zip` sits in the project root and `.gitignore`
  covers neither it nor `.claude/worktrees/`.

---

## Verified correct — no action

Recorded so the next session does not re-audit them.

- Clipboard uses the current `LocalClipboard` + `ClipEntry` API; `collectAsStateWithLifecycle`
  everywhere with zero `collectAsState()`; no `GlobalScope`/`runBlocking`/`Thread.sleep` in the repo;
  `CancellationException` correctly rethrown.
- `currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint` is the current adaptive API,
  and `DrawerState.currentOffset` is the non-deprecated member.
- `SavedStateHandle.getStateFlow` + `@Serializable` NavKeys + `rememberNavBackStack` give a complete
  process-death story for the stack and the input (the *outcome* is the gap — P0-7).
- AGP 9 readiness: untyped `CommonExtension`, `androidComponents.beforeVariants`, and an explicit
  `buildFeatures.resValues = true` (AGP 9 flipped that default to `false` and white-label `app_name`
  depends on it).
- `windowSoftInputMode="adjustResize"` + `WindowInsets.safeDrawing` + `enableEdgeToEdge()` are the
  correct edge-to-edge trio; `java.time` on minSdk 24 is covered by core-library desugaring;
  `@OptIn(ExperimentalMaterial3Api::class)` on `CenterAlignedTopAppBar` is still required in 1.4.0.
- Variant graphs are complete and symmetric — no binding exists in only one of prod/fake.
- `:lib:subscription`, `:lib:ads`, `:lib:consent` are genuinely reusable: zero project dependencies,
  own namespaces, SDK-free NoOp stand-ins, and consent starts `UNKNOWN` (the old app's auto-grant bug
  is fixed structurally).
- Ring discipline holds in the build graph: pure-JVM ring 2 has no Android on its classpath, features
  depend only on contracts, brains carry no Hilt modules.
- `TranslateTextUseCase` sequences Access → translate → meter-on-success-only → history → ads in one
  place, with 9 tests including C-10 free-engine-no-charge and best-effort history.
- testTags: 14 of the 17 contract tags are present with exact spelling on the right controls; the 3
  missing belong to unbuilt features (mode sheet, usage warning, limit sheet).
- Live regions are correct: Polite on loading/result/counter, **Assertive only on error**.
- RTL: zero `left`/`right`/absolute alignments; `AutoMirrored` used for back/forward/volume;
  `SwapHoriz` has no auto-mirrored variant so the manual `scaleX = -1f` is the only route.
- ShimmerResult stays custom: material3 1.4.0 has no skeleton API, and `LoadingIndicator` does **not**
  exist in 1.4.0 (only its tokens class ships).
- Every composable ships `@PreviewLightDark`; CI guards are non-vacuous; Detekt and Spotless are wired
  repo-wide.

---

## Suggested batches

| Batch | Contents | Why together |
|---|---|---|
| **A — "the app is wrong on the device"** | P0-1, P0-2, P0-4, plus P2-1/P2-2/P2-3 | All three P0s are fixed by the same two structural moves (`Scaffold`, `ModalDrawerSheet`), and the drawer work brings the selected state with it. Ship with a dark-theme screenshot test. |
| **B — text-vertical contract** | P0-3, P0-6, P0-7, P0-8, P2-5, P2-6, plus the 200 % and strings items | One feature, one spec, one test pass. |
| **C — before the brains** | all of P1 | These are API-shape decisions; retrofitting them after the engines land costs far more. |
| **D — platform + release** | P2-11 … P2-16, issue #5 | Manifest/theme/build-type work that belongs in one release-readiness PR. |
| **E — hygiene** | P3 dead weight, catalog, `.gitignore`, docs contradictions | Low risk, do opportunistically. |
