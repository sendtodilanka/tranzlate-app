# ADVERSARIAL JUDGE RULING — Language screens rev3 architecture

දිනය 2026-07-31 · branch `main` (HEAD `7745e04`) · merged: **15a පමණයි** (PR #118+#121).
Inputs: `langrev3/code-map.md` · `spec-inventory.md` · `platform-audit.md` · `gap-matrix.md` + proposals 3 (incrementalist · consolidator · reliability). Cross-examination claims සියල්ල source-verify කළා (file:line පහත).

---

## 0 · Cross-examination record — proposal එකක් පාසා "most likely false" claim එක + verdict

### P1 (incrementalist) — checked claim: "duplication සාක්ෂි සියල්ල lift-able logic/strings; picker move එක churn-only, U-12 string single authority `:core:languageui` එකේ රැකෙයි"

**Verdict: WEAKENED — ruling against.** කරුණු තුනක්:
1. P1 ගේම layout එකේ 16a/17a/18a screen composables `feature:text` ඇතුළේ ඉතිරි වන අතර rev3 strings "`:core:languageui` single authority" කියයි. ඒක *තාක්ෂණිකව* කළ හැකි වුවත් (dependent module එකකට library strings resolve වේ), ප්‍රතිඵලය = language surface එක **ගෙවල් තුනක**: picker+16a+17+18 = `feature:text` · 20b–20e = `:feature:languages` · sheets+strings+shared rows = `:core:languageui`. CLAUDE.md "every big job has ONE home" ට කෙලින්ම එරෙහියි — 16a/20b share කරන row primitives (avatar, counter, row anatomy) ද ඒ shared module එකට accrete වී, P1 ගේ "no move" ස්ථාවරය ක්‍රමයෙන් P3 ගේ design එකටම converge වෙනවා — picker එක විතරක් stranded ව.
2. "Regression window shipped surface එක උඩ" cost claim එක **overstated** — measured coupling surface: picker files 4න් පිට `feature:text` එකේ `languageDisplayName`/`languageAvatarCode`/`DETECT_LANGUAGE_ID` භාවිතය **ශුන්‍යයි**; එකම shared symbol = `LanguagePickerTarget` (`feature/text/.../TextUiState.kt:8`) + screen param `viewModel: TextViewModel` (grep-verified මේ ruling එකේදීම). Logic-frozen `git mv` + moved tests unmodified-green = review surface paths+package lines පමණයි.
3. P1 ම PR-S rename (`languagepicker`→`languages`) කරනවා — "churn වළක්වනවා" කියූ position එකම churn එකක් admit කරයි.
**Surviving P1 ideas graft කර ඇත** (§7 බලන්න): usage-store-early, date-less-pack nudge exclusion, Retry-pill deviation fix, `Selected(inner)` wrapper, E-W1 naming, string-ledger batches.

### P2 (consolidator) — checked claim: "`TranslatePrefsRepository` core:domain එකේ දැනටමත් ඇත; අලුත් repository එකක්වත් නැතුව picker re-wire කළ හැක"

**Verdict: CONFIRMED TRUE.** `core/domain/src/main/kotlin/com/codeboxlk/tranzlate/domain/repository/TranslatePrefsRepository.kt:12-31` — `sourceLang`/`targetLang` Flows + `setSourceLang`/`setTargetLang`/`setLanguagePair` (atomic, KDoc :26). `TextViewModel` දැනටමත් inject කරයි (`feature/text/.../TextViewModel.kt:94`, stateIn Eagerly :108-112). Picker nav entry `viewModel = textViewModel` bind එකද verified (`app/.../TranzlateApp.kt` — `entry<LanguagePickerNavKey>`). **මේක code-map §1.5/§2 ("DataStore prefs direct") correct කරන source-level find එකක් — P2 ම source verify කර තිබීම proposal එකේ credibility ට ලොකුම සාක්ෂිය.** P2 ගේ P3-flaw catch එකද (පහත) නිවැරදියි.
**P2 හි ඇත්ත දුර්වලතා දෙකක් වෙනම හමු විය** — TTS engine process-lifetime hold (REJECT §7.3) + U-6 "migration එකකින් maps ×2 + Room දෙකම" muddle (§1 crisp split කර ඇත).

### P3 (reliability) — checked claims: (a) "sheets features වලට පහළින්ම — dependency-graph fact, style choice නොවේ"; (b) PR-C "Screen B honesty + `languageDisplayName`"

**Verdict: (a) FALSIFIED as necessity · (b) CONFIRMED FLAW.**
(a) `app` module එක සියලු features මත depend වන composition root නිසා composer-raised 19h sheet එක app-shell එකෙන් host කළ හැක (P2 §4) — feature→feature edge එකක් නැතුව. එනිසා "පහළින්ම ජීවත් විය *යුතුයි*" = false; ඒක choice එකක්.
(b) `feature/languagepicker/build.gradle.kts` = convention plugin (core modules පමණයි) + icons — **`feature:text` dep නෑ** (file කියවා verified). P3 ගේ PR-C (move එකට *කලින්*) `languageDisplayName` (`feature/text/LanguageNames.kt`) Screen B එකේ භාවිත කිරීම compile වන්නේම නෑ. P2 මේක නිවැරදිව කලින්ම caught ("cross-feature import ban; PR-7 merge එකෙන් free").
**P3 ගේ reliability substrate එක ruling එකේ හදවතට graft කර ඇත** — ඒක තමයි proposal තුනෙන් ශක්තිමත්ම state-flow/leak/perf discipline එක.

### RULING SUMMARY — skeleton = **P2 (consolidator)**, P3 ගේ reliability substrate + P1 ගේ sequencing/honesty grafts සමඟ.

---

## 1 · Module / unit design (final)

```
:feature:language                          ← NEW (feature convention plugin — 1 line)
  com.codeboxlk.tranzlate.feature.language
    picker/    LanguagePickerScreen/Model/ViewModel/LanguageNames (git-mv from feature:text)
               PickerDialogHost.kt (17c/17d — NEW)
    packs/     LanguagePacksScreen/ViewModel (git-mv of Screen B → පසුව 20b rewrite)
    sheets/    LanguageSheetRequest.kt (@Serializable sealed) + stateless sheet files 12
               (19a/b/d/f/g/h/j/m/n · 18a-confirm · 20c · 20e)
    firstrun/  FirstRunBlock.kt · SuggestedLanguages.kt (18a/18b)
    shared/    row primitives · avatar · counter · aggregate meter · failure-cause map ×1
  res/values{,-fil,-pt-rBR}/strings.xml    ← language-surface string SINGLE authority (U-12)

:core:designsystem  + TranzlateSheetScaffold · TranzlateListSheet (U-2 — string-free spec-§5 anatomy)
:core:ui            + WindowInfo fold-posture extension (17b/17c discriminator)
:core:model         + LanguageTagResolver (canonicalId lift — U-7, #119) · LanguageRole enum
                      (picker param + usage-store role + recents role — එක type එකක්)
:core:domain        + DownloadGate (U-9) · OfflineVoiceCatalog contract (U-3) ·
                      LanguageUsageRepository contract (U-6)
                      [TranslatePrefsRepository දැනටමත් ඇත — :12-31; අලුත් repo නෑ]
:core:database      + language_usage(lang_id, role, last_used_at) PK(lang_id,role) + DAO (#122)
                    + TranslationDao saved-count query (U-10)
:core:datastore     + recents role dimension (maps ×2 — 16a "Recently used as target" ordering)
:core:translate     RealOfflineModelManager: U-11 race fix · U-13 shared states · PackEvents (U-1)
:app                <queries> TTS_SERVICE (U-4) · AndroidOfflineVoiceCatalog (prod DI) ·
                      app SnackbarHost + DownloadEventsViewModel · 19h app-shell sheet host ·
                      PickerDialogHost gate wiring
DELETE :feature:languagepicker             (move ×2 පසු)
```

**Store split (P2 muddle නිරාකරණය — crisp semantics දෙකක්, store දෙකක්, documented):**
- **Recents (ordering, දින නොපෙන්වයි)** = selection-stamped, cap-10, DataStore — shipped semantics + **role dimension** (maps ×2). 15a Recent + 16a "Recently used as target" serve කරයි. GT parity.
- **Usage dates (20b/20d/20e nudge + relative dates)** = **translation-success-stamped**, engine-agnostic, resolved-id-never-`auto`, Room `language_usage` ≤194×2 rows. Selection එකකින් කවදාවත් date එකක් නොඑන බව test-pinned (brief §7b honesty rule).
- **Stamp semantics DECIDED (owner ට නොයවයි — engineering):** engine-agnostic — nudge copy "the languages you *use*" භාෂා-use ගැනයි; online-engine-served use එකක් නොගණන් කළොත් active-use භාෂාවක pack එක delete කරන්න nudge කිරීමේ worse-failure risk එක. Recorded — re-litigate නොකරන්න.

---

## 2 · State-flow contract (scope · lifetime · temperature · collector · process-death)

| Flow | Temp | Scope / lifetime | Collectors | Process death |
|---|---|---|---|---|
| manager `states` (U-13) | **hot** `stateIn(downloadScope, WhileSubscribed(5s), emptyMap())`; `capableTags` **memoized once** (අද emission එකක් පාසා `getAllLanguages()` — `RealOfflineModelManager.kt:114-117`) | `downloadScope` = process-life BY DESIGN (#82/#83, :107-108) — ruling touch නොකරයි | repo chain + picker VM + packs VM + events VM — upstream **1** | transient lost → restart `refreshDownloaded()` = disk truth |
| `modelStates()` API | cold wrapper `states.onSubscription { conflated-refresh }` → downloadScope single worker `refreshDownloaded()` | — | subscriber burst → ML Kit IPC **1** (අද picker open = chains 2, code-map §1.11); idle-expiry-පසු resubscribe ද refresh (staleness guard) | — |
| `PackEvents` (U-1) | hot `SharedFlow(replay=0, extraBufferCapacity=16, DROP_OLDEST)` | manager singleton; emit = ownership-checked publish sites (:134,:144) + delete outcome පමණයි | activity-scoped `DownloadEventsViewModel`, app shell, `repeatOnLifecycle(STARTED)` — **sanctioned screen-outliver #2 (අන්තිමයි)** | lost — backgrounded events deliberately dropped; ආපසු ආ විට state ම truth |
| `languages()` repo | **cold** combine ×3→×4 (voice overlay +`onStart emptySet`+`distinctUntilChanged`) | collector-scoped | picker VM · TextViewModel · packs VM — each `stateIn(WhileSubscribed(5s))` | catalog compile-time — nothing to lose |
| `offlineVoiceTags()` | hot cached `StateFlow<Set<String>>` | @Singleton **cache**; TTS engine = enumeration-window ONLY (`finally { shutdown() }`) — standing service connection **ශුන්‍යයි** | repo 4th combine source; rows O(1) `contains` | re-enumerate — init 1 + IPC 1 |
| `sourceLang`/`targetLang` | hot Eagerly | activity-life `TextViewModel` (leak නොවේ — VM scope) + picker VM `TranslatePrefsRepository` වෙතින් `WhileSubscribed(5s)` | chips + picker header — coherence = එකම DataStore keys atomicity | DataStore — survives |
| `language_usage` Room | cold | per-query | packs VM · 20e stale-query · 20d detail — `WhileSubscribed(5s)` | Room — survives |
| `LanguageSheetRequest` | hot | **`SavedStateHandle.getStateFlow`** raising-VM එකේ (අද plain `MutableStateFlow` ×2 — :70/:57 — fix) | raising screen | **survives** (pendingConsent loss class f fix) |
| query / scroll / 20e checks | — | `rememberSaveable` (:136 pattern) / SavedStateHandle | composition | survives |

**VM inventory (sheet-per-sheet VM තහනම්):** `LanguagePickerViewModel` · `LanguagePacksViewModel` · `TextViewModel` (untouched core role) · `DownloadEventsViewModel`. Sheets = stateless composables; request state raising-VM SavedStateHandle. **Sanctioned screen-outlivers = 2 යි** (downloadScope + events observer) — තුන්වැන්නක් එන ඕනෑම PR එකක් co-verify bounce (P3 rule adopted as standing review gate).

**Sheet framework:** anatomy = designsystem scaffold (string-free) · content = `:feature:language` stateless (strings/testTags `tt_lang_sheet_*`/a11y single authority) · hosting ස්ථර 2 — screen-local `ModalBottomSheet` (බහුතරය) + app-shell (19h composer hoist `onOfflinePackMissing(langId)` + snackbar-raised re-entries). Sheet chain (19f→19g) = request replace, stack නෑ. Back = dismiss (request→null), backstack pop නොවේ; dismiss සැම විටම state-machine action (consent dismiss → row Downloadable — no dead end).

**Adaptive (17a–17d):** hosts 2 — compact/compact-height/fold = nav-entry internal `WindowInfo` branches; medium/expanded no-fold = `PickerDialogHost` (`BasicAlertDialog` + `usePlatformDefaultWidth=false` — binary-verified audit §5) app-shell `rememberSaveable` flag එකකින්, Nav3 overlay-entry **නොවේ** (unverified — PP-4). 760/800 දෙකම width-MEDIUM නිසා discriminator = **fold posture** (WindowInfo extension). Host-agnostic saveable contract = query+pendingSheet+scroll, host දෙකේම instrumented restore test. "Manage packs" docked action ordering: `dialogVisible=false` **THEN** `push(LanguagesNavKey)`. Open-අතරතුර window-class change → keep-host-until-closed (P3). 20d = plain `Row` two-pane — `adaptive-layout` dep නොඑකතු කරයි.

---

## 3 · Phased PR plan

සෑම PR එකක්ම: issue-first + plan-doc (rule 3) · co-verify lens (rule 5; HIGH-RISK = adversarial trace + cross-model) · strings ×3 locales + #115 ledger line PR body එකේ · native-review **phase-boundary batches** · emulator ritual (Tranzlate_Resizable) UI PRs වලට.

**Phase 0 — docs/rulings (code නෑ)**
- **PR-0** · Scope: research record A-1 (detect pipeline — `MlKitLanguageIdentifier.kt:3,22` on-device fact) + A-2/E-W1 experiment record + plan-doc `docs/plan/issue-NN-language-rev3.md` + owner ruling-request list (§5) + spec doc-bug fixes 6 (spec-inventory list). Tests: — · Previews: — · Issues: #123.1 ruling request opens; A-1/A-2 records per rule 4.

**Phase 1 — shipped-truth stabilisation (UI වෙනසක් නෑ; each independently shippable)**
- **PR-1** · Scope: **#123.3** `delete` finally (:176-179) → `owns()` (:183-186) ownership guard + **#123.4** picker-VM silent-fake test fix (`LanguagePickerViewModelTest.kt:121-129` → `LanguageRepositoryImplTest:78` `SilentOfflineModelManager` template). **HIGH-RISK concurrency.** Tests: `deleteInFlight_thenRedownload_staysDownloading` (discriminating — pre-fix code එකට run කර FAIL වන බව verify), existing 8 green, genuinely-silent picker test. Previews: — · Issues: **closes #123.3 + #123.4**.
- **PR-2** · Scope: Screen B stall guard — `OfflineLanguagesViewModel.rows` (:49-55) combine එකට `onStart{emit(emptyMap())}` (repo :47 pattern). Localized-names fix **මෙතන නොකරයි** (cross-feature ban — §0 P3 flaw). Tests: silent-manager fake → rows emit. Previews: — · Issues: — (PP-5.a dead-end fix; 20b එනකම් user-facing).
- **PR-3** · Scope: U-13 manager `states` stateIn + onSubscription conflated refresh + memoized `capableTags`. **MEDIUM concurrency lens.** Tests: counting-fake `ModelStore` — subscriber burst → `downloadedTags()` calls == 1; two-collector coherence (turbine); WhileSubscribed stop→resubscribe re-refresh; repo `distinctUntilChanged` placement pin (unrelated pref write → no rebuild emission — PP-5.g regression test). Previews: — · Issues: — (PP-5.b/h; PR-16 dual-window prerequisite).
- **PR-4** · Scope: `LanguageTagResolver` lift (`BundledLanguageCatalog.kt:331-342` internal → core:model public; catalog delegates) + `LanguageRole` enum + `TranslatePrefsRepositoryImpl` write-side canonicalisation + picker decouple — screen signature `viewModel: TextViewModel` → `(target: LanguageRole, onDone)`; picker VM gains `TranslatePrefsRepository` selectedId flow + select-write (behaviour-preserving: එකම DataStore keys, picker VM දැනටත් selection නොලියයි — load-bearing fact #8). Tests: alias matrix (`iw→he`, `zh-CN→zh`), stored-raw-id selected-state, stamp+select order, atomic swap untouched, chips-coherence. Previews: unchanged (diff-proof). Issues: **closes #119 + #123.2**.
- **PR-5** · Scope: **#122** — Room `language_usage` table + DAO + translation-success stamper (engine-agnostic, resolved-never-`auto`, per-role) + DataStore recents role dimension (maps ×2). **HIGH-RISK data/migration: adversarial + cross-model.** මුල ලෑන්ඩ් වීම deliberate: dates accrue store එක ලෑන්ඩ් වූ දා සිට පමණයි (P1/P3 argument) — 20b එනකොට honest history already ඇත. Tests: DAO upsert/PK/stale-query, recents codec migration seed, **selection-alone-never-writes-usage** (R6 disconfirm), per-role stamps. Previews: — · Issues: **closes #122** (UI consumers PR-12/23/25/26).

**Phase 2 — the move (logic-edit ශුන්‍ය)**
- **PR-6** · Scope: `:feature:language` create + **git-mv picker verbatim** (prod 4: Screen/Model/ViewModel/LanguageNames + tests 4 (RowState/Search/ViewModel/LanguageNames) + `text_lang_*` strings ×3 locales) + app import updates. Logic edits **තහනම්**. Tests: moved suites 4 — **assertion එකක්වත් edit නොවී green** (R2 gate). Previews: moved intact. PR body: Tranzlate_Resizable 15a before/after screenshots (owner-visual). Issues: —.
- **PR-7** · Scope: git-mv packs screen/VM/test + `offline_*` strings → `:feature:language`; `:feature:languagepicker` **delete**; localized-names **one-line fix** (`languageDisplayName` දැන් same-module — shipped divergence bug). Tests: moved suite green + localized-row test. Previews: `OfflineRow*` states refresh. Issues: —.

**Phase 3 — seams + primitives (බොහෝ දුරට parallel)**
- **PR-8** · Scope: U-2 `TranzlateSheetScaffold`/`TranzlateListSheet` (designsystem — spec §5 anatomy; error colour = loss/stopping only). Tests: semantics/testTag contract. Previews: `SheetScaffold{Icon,NoIcon,TwoAction,ErrorFilled}Preview` + `ListSheetPreview` `@PreviewLightDark`. Issues: —.
- **PR-9** · Scope: U-9 `DownloadGate` (core:domain) — VM ×2 duplicate gate delete (`LanguagePickerViewModel.kt:85-107` ≈ old `OfflineLanguagesViewModel.kt:62-83`); `SUBSCRIBE_TIMEOUT_MS` shared const ride-along. Tests: gate matrix ×1 consolidated (Wi-Fi/metered/standing-pref/one-off/dismiss) replaces duplicated suites. Previews: — · Issues: —.
- **PR-10** · Scope: U-3 voice seam — contract core:domain · `AndroidOfflineVoiceCatalog` app/src/prod (mutex one-shot: construct → `onInit` await `withTimeout` → `getVoices()` null-guard (`TextToSpeech.java:1721-1726,853-854`) → `!isNetworkConnectionRequired` filter → locale→`LanguageTagResolver`→id → cache → **`finally { shutdown() }`**) · fake core:testing · **U-4 `<queries>` TTS_SERVICE** (audit P1 — නැත්නම් silently empty) · repo `hasOfflineVoice` overlay · **E-V1 executed, results PR body**. Tests: timeout→emptySet, null-voices guard, canonical mapping (es-ES→es), overlay-never-gates-list. Previews: — · Issues: — (depends #119 fix = PR-4).
- **PR-11** · Scope: U-5 storage — `AndroidStorageProbe.totalBytes` + models-dir walk (`Dispatchers.IO`), absent-dir → free-only degrade, **E-S1 instrumented pin**. Tests: temp-dir walk sum, degrade path. Previews: — · Issues: —.

**Phase 4 — 16a (depends: #123.1 ruling + PR-5 + PR-10)**
- **PR-12** · Scope: 16a target picker — voice legend + speaker marks (**tap live day-1** → 19j sheet — P1 ගේ dead-marks sequencing REJECT §7.6) + "Recently used as target" (per-role recents; empty → section **absent**, 18a pattern) + `Selected(inner: LanguageRowState)` wrapper (ruling දෙපැත්තටම accommodate — P1 graft) + 19j sheet. Tests: row voice-flag matrix, target-recent ordering, 19j trigger. Previews: `TargetPickerRow{Voice,NoVoice,Selected}Preview`, `VoiceLegendPreview`, `NoOfflineVoiceSheetPreview`. Issues: **implements #123.1 ruling**; consumes #122 store.

**Phase 5 — adaptive**
- **PR-13** · Scope: WindowInfo fold-posture extension + host-agnostic saveable contract (query+`LanguageSheetRequest`+scroll; shipped pendingConsent → SavedStateHandle මෙතනින්). LeakCanary debug-only dep rider (P2 suggestion — owner cost-free). Tests: instrumented restore host×2 harness. Previews: — · Issues: — (PP-4 state-loss class pin).
- **PR-14** · Scope: 17a landscape two-pane + Detect chip variant string ruling-key settle (C-conventions single key). Tests: window-gate unit. Previews: `PickerLandscape{From,To}Preview`. Emulator resizable ritual. Issues: —.
- **PR-15** · Scope: 17b foldable two-leaf (24dp crease gutter) + aggregate meter (U-5). Tests: meter formatting + degrade states. Previews: `OfflineLibraryMeter{Zero,Packs,Degraded}Preview`, `PickerFoldablePreview`. Issues: —.
- **PR-16** · Scope: 17c/17d `PickerDialogHost` + docked "Manage packs" ordering + **E-D1** (IME-in-dialog 800×1280 + 1280×800) + **measured jank budget pass** (`dumpsys gfxinfo`/JankStats — fling/scrub frame-drop clusters zero; fail = PR fail; named escape: `produceState(Default)` + Collator memoize). Tests: state-restore per host (instrumented), back-dismiss, ordering. Previews: `PickerDialog{Portrait,LandscapeTwoUp}Preview`. Issues: — (depends PR-3 shared upstream).

**Phase 6 — sheets + first-run + snackbars**
- **PR-17** · Scope: **19a** sheet — `MeteredConsentDialog` (:1137-1161) + Screen B inline dialog **දෙකම delete**, string sets ×2 delete (#115 net-negative start), toggle = standing pref, request → SavedStateHandle; E-W1 verified නම් drawn actions, නැත්නම් owner-approved interim ("Not now"/"Download now") + follow-up issue. Tests: gate→sheet flow, toggle persist, process-death restore. Previews: `MobileDataSheetPreview` (toggle ±). Issues: — (A-2/E-W1 gate).
- **PR-18** · Scope: 19d + 19b + failure-cause map ×2→×1 + **15a Retry-pill deviation fix** (:859-871 icon → spec filled pill — P1 graft). 19b trigger threshold = ඇති 150MB pre-flight const (:124-127) — DECIDED, owner Q නොවේ. "Free up space" button 20e එනකම් omit (single action "Manage packs" — no dead end). Tests: cause-map table, STORAGE pre-flight→19b. Previews: `InterruptedSheetPreview`, `NoSpaceSheetPreview`, `LanguageRowFailedPreview`. Issues: —.
- **PR-19** · Scope: 19f + 19g + U-10 saved-count DAO query + packs delete flow confirm-sheeted (unconfirmed 🗑 → confirmed — pre-20b UX win). Tests: count query (index-backed), in-use detection, fallback-target rule per ruling. Previews: `RemovePackSheetPreview`, `RemoveInUseSheetPreview`. Issues: — (depends 19g ruling ③).
- **PR-20** · Scope: 19h + 19m + app-shell sheet host (composer hoists `onOfflinePackMissing`; trigger = `ConnectivityMonitor.online` :15-28). Tests: offline trigger (FakeConnectivityMonitor), duplicate-selection guard (implement-time `TextViewModel` verify — spec-inventory 19m note), swap wiring (`onSwapLanguages` :301-314). Previews: `OfflinePackMissingSheetPreview`, `AlreadySourceSheetPreview`. Issues: —.
- **PR-21** · Scope: 18a/18b — `LocaleList.getAdjustedDefault()` primary suggestions (VERIFIED — audit §2; block කිසිදා හිස් නෑ) + **E-K1** (+`InputMethod` queries pass වුණොත් additive) + confirm sheet + "59 can be offline" counter variant + foldable/tablet first-run variants. Tests: locale-only fallback derivation, suggestion order, Get→confirm→gate chain. Previews: `FirstRunBlockPreview`, `SuggestedRowPreview`, `DownloadConfirmSheetPreview`. Issues: —.
- **PR-22** · Scope: U-1 `PackEvents` + app SnackbarHost + `DownloadEventsViewModel` + snackbars 20a-1..4 (View→`LanguagesNavKey` · Use→prefs write · Download again→gate · Retry→manager). 20a-5 = E-W1 gated. Tests: ownership-checked emission, DROP_OLDEST bound (17th event drops oldest), event→snackbar mapping, STARTED-only, pop-out survival. Previews: `SnackbarVariantsPreview` ×4. Issues: —.

**Phase 7 — Manage packs (now unblocked)**
- **PR-23** · Scope: **20b** rewrite behind same `LanguagesNavKey` + Home-row relabel (ruling ⑤ ×3 locales) — storage card · nudge (**date-less packs excluded**; copy "no recorded use yet" — DECIDED) · downloading/failed sections · dated rows · footer · `more_vert` · empty state (ruling ⑦). Tests: section-builder matrix (no-date honest case ඇතුළුව), nudge threshold, stop/retry post-PR-1. Previews: `ManagePacksScreen{Nudge,NoNudge,Empty,Downloading,Failed}`, `PackRow` ×6, `StorageCardPreview`, `NudgeCardPreview`. Issues: consumes #122, #123.3-safe.
- **PR-24** · Scope: 20c pack-actions list sheet ("Use as target now" via `TranslatePrefsRepository` · voice line · remove→19f/g). Tests: action routing. Previews: `PackActionsSheetPreview`. Issues: —.
- **PR-25** · Scope: 20e — stale Room query, pre-checked `rememberSaveable` boxes, batch remove + 19b "Free up space" completion. Tests: stale-selector, checkbox process-death restore, remove flow. Previews: `FreeUpSpaceSheetPreview` states. Issues: consumes #122.
- **PR-26** · Scope: 20d list-detail (plain two-pane; camera card OMIT · pair-share line OMIT — §7; per-role "source" line = usage store). Emulator pass. Previews: `ManagePacksListDetailPreview`. Issues: consumes #122.
- **PR-27** (conditional) · 19i + Detect chips per ruling ② — on-device නම්: 19i REJECT + shipped chips strip; online product-choice නම්: 19i build. **XOR — දෙකෙන් එකයි.**
- **PR-28** (conditional) · 19n per ruling ④ flavor-scoped copy.

---

## 4 · Reliability / memory / perf mechanisms — named risk එකකට බැඳ

| Mechanism | Named risk (register id) |
|---|---|
| Ownership-guard + discriminating test PR-1 **පළමුවෙන්ම** | R1 delete/download race (#123.3 — PP-5.c) |
| `onStart emptyMap` guard PR-2 + repo guard-placement pin test PR-3 | Screen B forever-Loading dead-end (PP-5.a) + guard regression (R10) |
| stateIn + onSubscription refresh + memoized capableTags (PR-3) | collector-multiplication IPC/alloc (PP-5.b/h) + dual-window cost (PP-5.k → R7) |
| Sanctioned-outliver count = 2, review-gate bounce rule | scope-leak class (PP-5.e/i → R11) |
| TTS enumerate→cache→`finally shutdown()`; standing connection ශුන්‍යයි | platform-documented TTS leak class (audit §1 → R4) |
| `<queries>` PR-10 + E-V1 + false-negative-safe fallback (mark නොපෙන්වයි) | voice silently-empty / false-positive lie (PP-5.j → R4) |
| `SavedStateHandle` sheet requests + 20e checks + host-agnostic contract | process-death state loss (PP-5.f/l → R12) + tablet host divergence (PP-4 → R5) |
| Bounded everything: event buffer 16 DROP_OLDEST · transient ≤59 · voice set ≤194 · usage ≤388 rows · recents 10 · request 1 | unbounded-growth class (R11) |
| Measured jank budget PR-16 (gfxinfo/JankStats) + named escape hatch — preemptive optimization REFUSED | 194-row/dual-window jank (PP-5.d/k → R7) — "felt fine" ≠ evidence |
| Storage walk `Dispatchers.IO` + absent-dir degrade + E-S1 pin | ML Kit dir-rename fragility (audit §3 caveat → R8) |
| Usage store selection-never-stamps test + date-less exclusion | invented-dates dishonesty (brief §7b → R6) |
| Move PRs logic-frozen + unmodified-tests-green + single-revert isolation | shipped-15a regression during migration (R2) |

> ⚠️ **AMENDED after this ruling was accepted** (2026-08-01, issue #149 · evidence `docs/research/issue-149-tts-lifetime.md`).
>
> ඉහත row එකේ **"standing connection ශුන්‍යයි"** rule එක **enumeration** ගැන — එය එසේම standing යි. එහෙත් ඒ wording එක literal ව මුළු app එකටම කියවූ විට **speaker** එකට වැරදි contract එකක් දෙයි, මන්ද utterance එකකට පසු shutdown කිරීම මනින ලද මිලක් ගෙවයි.
>
> නිවැරදි unit එක **"process-lifetime"** මිස "standing" නොවේ. එකම rule එක, unit දෙකකට:
>
> | surface | consumer | contract |
> |---|---|---|
> | `AndroidOfflineVoiceCatalog` (PR-10) | එක ප්‍රශ්නයක් — ඇසුවාට පස්සේ ඉවරයි | construct → ask → **`finally { shutdown() }`** (rev.3 හි ලියූ පරිදිම) |
> | `AndroidResultSpeaker` (#84 · #149) | result face එක — play/stop toggle + replay | `prepare()` @ Translating → **`release()`** face එක result නොවන හැම මොහොතක ම + `onCleared()` |
>
> මනින ලද කරුණු (API 37, `Resizable_Experimental`): engine එකක් bound ව තිබෙන තාක් TTS process එක `oom adj 100` + top-app sched group එකේ රැඳේ — **app එක background එකේ තිබියදීත්** — platform එක කිසිදා එය නොහරියි (`getAutoDisconnectTimeoutMs()` → `PERMANENT_BOUND_TIMEOUT_MS` = 0 = "do not unbind"). ඒ නිසා §7.3 REJECT එක **standing**: process-lifetime hold එකක් තහනම්මයි. එහෙත් rebind එකට ~500ms යයි, fresh engine එකක tap→audio **670ms** vs standing engine එකක **2-8ms** — ඒ නිසා utterance-per-shutdown එකද speaker එකට **REJECT**. දෙකට ම පොදු invariant: **engine එකක් තමන්ගේ consumer ට වඩා වැඩි කල් ජීවත් නොවේ.**
>
> Konsist gate: `no class holds a speech engine it cannot give back` (`app/src/test/.../KonsistArchitectureTest.kt`) — `@Singleton` engine holder එකක් හෝ `shutdown()` නැති holder එකක් RED කරයි.

---

## 5 · Owner rulings needed (taste / product ONLY — engineering questions මෙහි නෑ)

1. **#123.1 selected-row trailing** — 16a drawn evidence: selected ES row "On device" + voice + check. නිර්දේශය: marks+check (spec-conformant). PR-12 gate.
2. **A-1 Detect** — code fact: detection **on-device** (`MlKitLanguageIdentifier.kt:3,22`). Product ruling: on-device ම තියාගෙන 19i reject + shipped "ONLINE ONLY" chip strip කරනවාද (නිර්දේශය — private/free/offline), නැත්නම් detection online product-choice ද? Shipped 15a UI honesty ට බලපාන නිසා owner-visible.
3. **19g fallback-target** — in-use pack remove කළ විට target වන්නේ කුමන භාෂාවද? නිර්දේශය: device language catalog-capable නම් එය, නැත්නම් `en`.
4. **19n privacy copy scope** — "Nothing you type or say is uploaded" multi-engine flavors වල අසත්‍යයි; flavor-scoped strings ruling.
5. **20b Home-row new label** — entry point SETTLED (owner ruling in code-map); label candidate ×3 locales (en/fil/pt-BR) — නිර්දේශ candidate: "Manage language packs".
6. **Folded cover screen** — 17d postscript spec එකම අසයි: වෙනම treat කරනවාද?
7. **Manage packs empty state** — drawn නැත (verifier-corrected evidence: full-text scan of the spec — §6 draws only the populated "5 of 59 packs" state; no empty-state frame exists) — design output එකක් ඕන.
8. **19a interim actions approval** — E-W1 fail වුණොත් drawn "Wait for Wi-Fi" වෙනුවට "Not now"/"Download now" ship කිරීමේ deviation eka approve කරනවාද?

*(P1 ගේ ③ stamp-semantics · ⑥ 19b threshold · ⑧ date-less copy = engineering — §1/§3 හි DECIDED + recorded; owner ට නොයවයි.)*

---

## 6 · Risk register — each with the disconfirming experiment

| Id | Risk | Disconfirming experiment |
|---|---|---|
| R1 | Race fix wrong/incomplete | `deleteInFlight_thenRedownload_staysDownloading` — **pre-fix code එකට run කර FAIL වන බව** පෙන්වීම (discriminating proof); existing caller-death ×2 green |
| R2 | Move destabilises shipped 15a | Moved test suites 4 assertion-edit-ශුන්‍යව green; deviation එකක් = PR reject. Emulator before/after screenshot diff |
| R3 | shareIn staleness (idle-expiry → stale replay) | Turbine: collect → idle 5s+ → resubscribe → counting-fake `refreshDownloaded` invoked; නොවුණොත් design fail |
| R4 | Voice marks silently empty / false positive | **E-V1**: AOSP-no-GTTS image → empty set + marks absent + 19j copy coherent; `<queries>` නැතුව API 36 + Google TTS → empty ලැබෙන බව පෙන්වා block එකේ අවශ්‍යතාවය pin |
| R5 | Tablet dialog host state loss | Instrumented: dialog host එකේ query+sheet set → process death → restore assert; nav-host එකේම test එකම pass. **E-D1** IME insets දෙපැත්තෙම |
| R6 | Usage dates dishonest (selection-stamped) | Select-without-translate → `language_usage` row **නෑ** assert; translation success → row ඇත. Fail = store design reject |
| R7 | 194-row / dual-window jank | PR-16 gfxinfo/JankStats fling+scrub pass — frame-drop clusters > 0 නම් PR fail → named escape (produceState + Collator memoize) → re-measure |
| R8 | ML Kit models-dir rename breaks meter | **E-S1**: download → walk sum > 0 pin; dir rename/absent simulate → free-only degrade, no crash |
| R9 | Wi-Fi-defer promise false | **E-W1**: metered network + `DownloadConditions.requireWifi` request → start/completion observe කළ හැකිද මනින්න; unobservable නම් 20a-5 + "Wait for Wi-Fi" strings ship **නොවේ** |
| R10 | `distinctUntilChanged` placement regression | Repo test: unrelated pref write → list re-emission නෑ; fresh-install first-empty swallow නෑ |
| R11 | Events flood / observer leak | 17 events → oldest dropped assert; STARTED-only collection test; outliver count 2 gate at review |
| R12 | Sheet/checkbox process-death loss | SavedStateHandle restore tests per sheet class + 20e checks |
| R13 | Nav3 overlay-entry assumption creep | Dialog host = Nav-external composition ONLY; overlay-entry PR එකක් ආවොත් spike evidence නැතුව reject (P3 refuse #2 standing) |

---

## 7 · REJECT list (හේතු recorded — re-litigate නොකරන්න)

1. **P1 module stance** (picker `feature:text` + `:core:languageui` + rename-only) — surface ගෙවල් 3කට split; string/row-primitive accretion ඒ design එකම P3 shape එකට converge කරයි picker stranded ව; move cost measured-minimal (coupling = `LanguagePickerTarget` + constructor param — §0). 
2. **P3 `:core:language-ui` ring module** — necessity claim falsified (app-shell hosting covers 19h); module count ↓; product strings core ring එකක නොතබයි.
3. **P2 process-lifetime TTS engine hold** — enumerate→cache→shutdown adopted; rarely-changing data එකකට standing binder connection = owner leak-weight එකට එරෙහි, audit §1 cache-recompute ප්‍රමාණවත් කියයි. **(2026-08-01 · #149 amendment — §4 බලන්න:** මේ REJECT එක **process-lifetime** hold එකට පමණයි, "engine එකක් කිසිසේත් standing නොවිය යුතුය" කියා නොවේ. Speaker එකට utterance-per-shutdown = මනින ලද 670ms tap→audio — ඒ නිසා එය consumer-lifetime, `docs/research/issue-149-tts-lifetime.md`.**)**
4. **P3 PR-C names-fix placement** — cross-feature import violation (build.gradle verified §0); fix = PR-7 (same-module).
5. **P1 DataStore-only usage store** — Room adopted: queryable stale-list (20e), structured migration, දෙවැනි bespoke codec එකක් නෑ; 2-of-3 proposals convergent.
6. **P1 16a-with-dead-marks sequencing** — 19j = mark tap target drawn (spec 16a/19j); PR-12 එකේම දෙකම.
7. **Sheets as Nav3 entries / global sheet router** — overlay-entry behaviour unverified (PP-4) + host-module 2ක් වලට framework = over-architecture (unanimous).
8. **15b** — RULING'S OWN product decision (verifier corrected: the spec KEEPS 15b as "still the better answer as the library grows"; 15a is the carried-through design and is merged — rejecting 15b now is ours, and the spec's pro-filter-long-term signal is recorded for the owner in the epic); **20d camera card** — නොපවතින feature (#78/#112); **20d pair-share copy** — undocumented version-fragile layout (audit §3, P4); **per-pack size / % / pause / resume** — platform VERIFIED-NO; **invented dates** any form (brief §7b); **20a-5 + "Wait for Wi-Fi" pre-E-W1**; **19n unscoped copy** (P7); **gate/failure-map/string-set තුන්වැනි copy** — ඕනෑම PR එකක ආවොත් review bounce; **`adaptive-layout` dep**; **preemptive perf work** measured need නැතුව. (Proposal තුනම unanimous — standing.)
9. **P1 owner-Q ③⑥⑧** (stamp semantics / 19b threshold / date-less copy) — engineering choices dressed as questions; §1/§3 decided+recorded.

---

## 8 · Contribution credits

- **P2 (skeleton):** single `:feature:language` home · `TranslatePrefsRepository` re-wire (new-unit ශුන්‍ය — source-verified) · logic-frozen git-mv + unmodified-tests-green migration proof · app-shell hosting for cross-feature sheet · P3 sequencing-flaw catch · LeakCanary rider.
- **P3 (substrate):** state-flow contract discipline (stateIn+onSubscription refresh+memoized capableTags) · bounded `PackEvents` + `DownloadEventsViewModel` · sanctioned-outliver review gate · measured jank budgets + named escape hatch · process-death matrix · keep-host-until-closed · sheets-not-nav-entries refuse · anti-god-VM defence.
- **P1 (sequencing/honesty):** usage-store-early rationale (dates accrue — P3 ද) · date-less-pack nudge exclusion + honest copy · 15a Retry-pill deviation fix · `Selected(inner)` wrapper · E-W1 naming · #115 ledger + phase-boundary native-review batches · refuse-list rigor.

**Frame disposition 24/24:** build 20 · REJECT 1 (15b) · ruling-gated 2 (19i XOR chips-strip; 19n scope) · partial 1 (20d — camera card + pair-line omitted, ඉතිරිය build).
