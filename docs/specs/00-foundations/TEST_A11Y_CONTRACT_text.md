# Tranzlate — Text Translation: Test + Accessibility Contract (Foundation)

> **Feature:** TEXT TRANSLATION (`HomeScreen` + `ResultScreen` සහ ඒවායේ `TextInputCard`, `SourceCard`, `ResultCard`, `ErrorView` components).
> **Status:** Foundation contract — Phase 5 (Testing) + Adaptive/A11y සඳහා pre-condition එකකි. මෙය implement කරන කවුරුත් මේ contract එකට **conform** විය යුතුයි.
> **Verified grounding:** `data/model/TranslationModels.kt` (tags: `AUTO`, `ML2-mini`, `ML2 - ONLINE`, `NLP3.5 - ONLINE`), `util/QonversionManager.kt` (`EntitlementState = LOADING, UNKNOWN, PREMIUM, PLUS, NOT_SUBSCRIBED`), `ui/screen/home/HomeUiState.kt` (sealed `Loading | Ready`), usage limit `20/day` (CLAUDE.md, `Constants.Defaults.FEATURE_LIMIT_PER_DAY`).

---

## 0. Grounding rule — ඇයි Fake එකක් අනිවාර්යද?

සැබෑ translation engine තුනම (`OfflineTranslate` → MLKit, `GoogleTranslate`, `CloudTranslate` → NLP3.5) **non-deterministic** වේ: output එක network, model version, සහ downloaded pack මත රඳා පවතී. එම නිසා production engine එකකට එරෙහිව assert කිරීම **flaky** වේ.

එබැවින් මේ contract එක යටතේ සියලුම text-translation tests **`FakeTranslator`** එකක් inject කළ යුතුයි — එය golden fixture table එකකින් **fixed, deterministic** output ලබා දෙයි. `FeatureAccess`, `UsagePolicy`, සහ `Clock` යන තුනම **interface** ලෙස තබා, test වලදී fake implementation swap කරයි. සැබෑ engine එකකට network call එකක් test එකකින් සිදු වුවහොත් — එය **contract violation** එකකි (fail).

**මූලික නීතිය:** unit + Compose UI + Maestro යන තුනම `FakeTranslator` හරහා run විය යුතුයි. `real MLKit / Retrofit / Qonversion` කිසිදා test path එකක instantiate නොවිය යුතුයි.

---

# 1. TEST CONTRACT

## 1.1 Mandated injectable seam — `Translator` interface

සැබෑ `TranslateRepository` orchestrator එක මේ interface එක පිටුපස තිබිය යුතුයි, එවිට fake එකක් `@Binds` කළ හැක.

```kotlin
package com.codeboxlk.tranzlate.domain.translate

enum class Engine { AUTO, ML2_MINI, ML2_ONLINE, NLP35 }

sealed interface TranslationOutcome {
    data class Success(val text: String, val resolvedEngine: Engine) : TranslationOutcome
    data class Error(val reason: FailureReason) : TranslationOutcome
    data object LimitReached : TranslationOutcome
}

enum class FailureReason { NETWORK, ENGINE, UNSUPPORTED_PAIR, EMPTY_INPUT }

interface Translator {
    /** Deterministic in tests. srcLang/tgtLang are BCP-47 ("en","fr","zh"); "auto" = detect. */
    suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String,
        engine: Engine,
    ): TranslationOutcome
}
```

### `FakeTranslator` — mandated test double

```kotlin
class FakeTranslator(
    private val golden: Map<GoldenKey, TranslationOutcome> = defaultGolden,
    var forcedFailure: FailureReason? = null,   // test can force NETWORK/ENGINE
) : Translator {

    data class GoldenKey(val text: String, val src: String, val tgt: String, val engine: Engine)

    val calls = mutableListOf<GoldenKey>()      // spy: assert engine actually invoked

    override suspend fun translate(text, src, tgt, engine): TranslationOutcome {
        val key = GoldenKey(text.trim(), src, tgt, engine)
        calls += key
        forcedFailure?.let { return TranslationOutcome.Error(it) }
        if (text.isBlank()) return TranslationOutcome.Error(FailureReason.EMPTY_INPUT)
        return golden[key] ?: TranslationOutcome.Error(FailureReason.UNSUPPORTED_PAIR)
    }
}
```

## 1.2 Golden fixture table (source of truth)

සෑම row එකක්ම **exact** deterministic output එකකි. `(fake)` suffix එක සැබෑ engine එකකින් වෙනස බව සහතික කරයි. `AUTO` engine එක fake එකේදී `ML2_MINI` වෙත resolve වේ (offline-first), නමුත් network අවශ්‍ය pair වලදී `NLP35` වෙත.

| # | Input text | src | tgt | Engine (in) | → Output (exact) | resolvedEngine | Outcome |
|---|-----------|-----|-----|-------------|-------------------|----------------|---------|
| G1 | `Good morning` | en | fr | ML2_MINI | `Bonjour (fake)` | ML2_MINI | Success |
| G2 | `Good morning` | en | fr | AUTO | `Bonjour (fake)` | ML2_MINI | Success |
| G3 | `Good morning` | en | fr | NLP35 | `Bonjour, comment allez-vous (fake)` | NLP35 | Success |
| G4 | `Thank you` | en | es | ML2_ONLINE | `Gracias (fake)` | ML2_ONLINE | Success |
| G5 | `Hello world` | en | de | ML2_MINI | `Hallo Welt (fake)` | ML2_MINI | Success |
| G6 | `こんにちは` | ja | en | NLP35 | `Hello (fake)` | NLP35 | Success |
| G7 | `Good morning` | auto | fr | AUTO | `Bonjour (fake)` | ML2_MINI | Success (detect→en) |
| G8 | `நன்றி` | ta | en | ML2_MINI | *(no golden row)* | — | Error(UNSUPPORTED_PAIR) |
| G9 | `` (empty) | en | fr | any | — | — | Error(EMPTY_INPUT) |
| G10 | `Offline test` | en | fr | ML2_ONLINE | *(forcedFailure=NETWORK)* | — | Error(NETWORK) |
| G11 | `Quota text` | en | fr | NLP35 | — | — | LimitReached (via UsagePolicy, §1.4) |

> **Rule:** මේ table එකට row එකක් add කරන්නේ නම්, `defaultGolden` map එකට **identical** entry එකක් add කළ යුතුයි. Test එකකට tuple එකක් වෙනස් කරන්න බෑ — new row add කරන්න.

## 1.3 Fake `FeatureAccess` (Free / Plus / Premium)

```kotlin
interface FeatureAccess {
    val tier: Tier                        // FREE, PLUS, PREMIUM
    fun isEngineAllowed(engine: Engine): Boolean
    fun isPaid(): Boolean
}
enum class Tier { FREE, PLUS, PREMIUM }
```

Fake matrix (deterministic):

| Tier | maps to `EntitlementState` | AUTO | ML2_MINI | ML2_ONLINE | NLP35 | isPaid |
|------|----------------------------|:----:|:--------:|:----------:|:-----:|:------:|
| `FREE` | `NOT_SUBSCRIBED` | ✓ | ✓ | ✓ | ✓ (usage-limited 20/day) | false |
| `PLUS` | `PLUS` | ✓ | ✓ | ✓ | ✓ (unlimited) | true |
| `PREMIUM` | `PREMIUM` | ✓ | ✓ | ✓ | ✓ (unlimited) | true |

```kotlin
class FakeFeatureAccess(override var tier: Tier = Tier.FREE) : FeatureAccess {
    override fun isEngineAllowed(engine: Engine) = true          // all tiers see all engines
    override fun isPaid() = tier != Tier.FREE
}
```

## 1.4 Fake `UsagePolicy` (at-limit / under)

```kotlin
interface UsagePolicy {
    fun remaining(): Int          // for NLP35 text feature; -1 = unlimited
    fun isOver(): Boolean
    fun warningMessage(): String? // "You have N free NLP3.5 translations left today"
    suspend fun increment()
}
```

Fake states — test වලින් inject කරන deterministic scenarios:

| Fake name | `remaining()` | `isOver()` | `warningMessage()` | Use in |
|-----------|:-------------:|:----------:|--------------------|--------|
| `FakeUsageUnder` | `5` | false | `"5 free NLP3.5 translations left today"` | happy path, warning banner |
| `FakeUsageLast` | `1` | false | `"1 free NLP3.5 translation left today"` | pluralization test |
| `FakeUsageAtLimit` | `0` | true | `null` (paywall replaces) | G11 LimitReached, paywall route |
| `FakeUsageUnlimited` | `-1` | false | `null` | PLUS/PREMIUM tiers |

```kotlin
class FakeUsagePolicy(private var left: Int, private val cap: Int = 20) : UsagePolicy {
    override fun remaining() = left
    override fun isOver() = left == 0
    override fun warningMessage() =
        if (left in 1..3) "$left free NLP3.5 translation${if (left==1) "" else "s"} left today" else null
    override suspend fun increment() { if (left > 0) left-- }
}
```

## 1.5 Fake `Clock` (daily-reset determinism)

Daily usage reset (`resetUsageIfNeeded`) date-boundary logic fragile බැවින් time inject කළ යුතුයි.

```kotlin
interface AppClock { fun nowMillis(): Long; fun today(): LocalDate }

class FakeClock(var instant: Instant = Instant.parse("2026-07-21T09:00:00Z"),
                val zone: ZoneId = ZoneId.of("Asia/Colombo")) : AppClock {
    override fun nowMillis() = instant.toEpochMilli()
    override fun today() = instant.atZone(zone).toLocalDate()
    fun advanceDays(n: Long) { instant = instant.plus(n, ChronoUnit.DAYS) }
}
```

**Reset test invariant:** `FakeClock` එකේ දිනය last-reset දිනයට වඩා පසුව නම් → usage counter `0` වෙත reset විය යුතුයි; නැතිනම් නොවෙනස්ව පැවතිය යුතුයි.

## 1.6 Hilt test wiring (mandated)

```kotlin
@Module @TestInstallIn(components=[SingletonComponent::class], replaces=[TranslateModule::class])
object FakeTranslateModule {
    @Provides @Singleton fun translator(): Translator = FakeTranslator()
    @Provides @Singleton fun featureAccess(): FeatureAccess = FakeFeatureAccess(Tier.FREE)
    @Provides @Singleton fun usage(): UsagePolicy = FakeUsagePolicy(left = 5)
    @Provides @Singleton fun clock(): AppClock = FakeClock()
}
```

Unit tests (no Hilt) — constructor injection direct. Compose/Maestro — `@HiltAndroidTest` + `HiltTestApplication`.

## 1.7 Stable `testTag` / semantics identifier table (EVERY interactive control)

Namespace convention: `tt_text_*`. සෑම control එකකටම `Modifier.testTag(...)` + meaningful `semantics { }` දෙකම තිබිය යුතුයි. **empty `contentDescription = ""`** (currently `ResultCard.kt:117`) මේ contract යටතේ **fail** — §2 බලන්න.

| # | Control | `testTag` | Screen / component | Node type | Notes |
|---|---------|-----------|--------------------|-----------|-------|
| 1 | Input text field | `tt_text_input` | `TextInputCard` | `TextField` | editable, multi-line |
| 2 | Translate button | `tt_text_translate_btn` | `HomeScreen` | `Button` | disabled when input blank |
| 3 | Swap languages | `tt_text_swap` | `MiddleContent` | `IconButton` | src↔tgt |
| 4 | Source language selector | `tt_text_source_lang` | `MiddleContent` | `Button`/chip | opens `LanguageScreen` |
| 5 | Target language selector | `tt_text_target_lang` | `MiddleContent` | `Button`/chip | opens `LanguageScreen` |
| 6 | Mode chip (container) | `tt_text_mode_chip` | `TopAppBar`/sheet | `Button` | opens `TranslationModels` sheet |
| 6a | Mode chip — per model | `tt_text_mode_chip_{tag}` | bottomSheet | `RadioButton` | `{tag}`∈`AUTO,ML2_MINI,ML2_ONLINE,NLP35` |
| 7 | Character counter | `tt_text_counter` | `TextInputCard` | text (non-interactive) | `"12/500"` |
| 8 | Copy result | `tt_text_copy` | `ResultCard` | `IconButton` | |
| 9 | Speak result (TTS) | `tt_text_speak` | `ResultCard` | `IconButton` (toggle) | play/stop state |
| 10 | Reverse translation | `tt_text_reverse` | `ResultCard`/`SourceCard` | `IconButton` | swaps result→input |
| 11 | Star / favourite | `tt_text_star` | `ResultCard` | `IconButton` (toggle) | selected state |
| 12 | More menu | `tt_text_more_menu` | `ResultCard` | `IconButton` | opens dropdown |
| 13 | Retry | `tt_text_retry` | `ErrorView` | `Button` | re-runs last request |
| 14 | Error view (container) | `tt_text_error_view` | `ErrorView` | container (`liveRegion`) | title + retry |
| 15 | Result text | `tt_text_result` | `ResultCard` | text (selectable) | golden output rendered here |
| — | Loading indicator | `tt_text_loading` | `LoadingView` | progress (`liveRegion`) | translating spinner |
| — | Usage warning banner | `tt_text_usage_warning` | `HomeScreen` | text (`liveRegion` polite) | from `warningMessage()` |

> **Contract:** මේ tag string 17 වෙනස් නොවිය යුතුයි (Maestro + Compose test දෙකම මේවා reference කරයි). tag rename එකක් = breaking change = doc update + PR.

## 1.8 State machine + example unit test

Text-translation UI state machine (ViewModel-level):

```
Idle ──type(nonblank)──▶ Ready ──tapTranslate──▶ Translating
Translating ──Success──▶ Result(text)
Translating ──Error(reason)──▶ Error(reason) ──tapRetry──▶ Translating
Translating ──LimitReached──▶ Paywall
Result ──tapReverse──▶ Ready (result→input, langs swapped)
Ready ──tapSwap──▶ Ready (src↔tgt)
any ──clearInput──▶ Idle
```

```kotlin
@get:Rule val dispatcher = TestDispatcherRule()   // sets Dispatchers.Main = UnconfinedTestDispatcher

@Test fun `translate transitions Ready to Result with golden output`() = runTest {
    val translator = FakeTranslator()                       // default golden
    val vm = TextTranslationViewModel(
        translator, FakeFeatureAccess(Tier.FREE),
        FakeUsagePolicy(left = 5), FakeClock(), src = "en", tgt = "fr",
    )
    vm.onInput("Good morning")
    assertThat(vm.state.value).isInstanceOf(State.Ready::class.java)

    vm.onTranslate(Engine.ML2_MINI)                          // G1

    vm.state.test {                                          // turbine
        assertThat(awaitItem()).isInstanceOf(State.Translating::class.java)
        val done = awaitItem() as State.Result
        assertThat(done.text).isEqualTo("Bonjour (fake)")    // exact golden
        assertThat(done.engine).isEqualTo(Engine.ML2_MINI)
    }
    assertThat(translator.calls.last().engine).isEqualTo(Engine.ML2_MINI)  // spy
}

@Test fun `NLP35 at limit goes to Paywall not Result`() = runTest {
    val vm = viewModel(usage = FakeUsagePolicy(left = 0), tier = Tier.FREE)
    vm.onInput("Quota text"); vm.onTranslate(Engine.NLP35)    // G11
    assertThat(vm.state.value).isEqualTo(State.LimitSheet)
}

@Test fun `network failure emits Error(NETWORK) and retry replays`() = runTest {
    val t = FakeTranslator().apply { forcedFailure = FailureReason.NETWORK }
    val vm = viewModel(translator = t)
    vm.onInput("Offline test"); vm.onTranslate(Engine.ML2_ONLINE)
    assertThat((vm.state.value as State.Error).reason).isEqualTo(FailureReason.NETWORK)
    t.forcedFailure = null                                    // network back
    vm.onRetry()
    assertThat(vm.state.value).isInstanceOf(State.Result::class.java)
}
```

## 1.9 Compose UI test (type → translate → assert golden)

```kotlin
@HiltAndroidTest
class TextTranslationScreenTest {
    @get:Rule(order=0) val hilt = HiltAndroidRule(this)
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun typeThenTranslate_showsGoldenResult() {
        compose.onNodeWithTag("tt_text_source_lang").assertTextContains("English")
        compose.onNodeWithTag("tt_text_target_lang").assertTextContains("French")

        compose.onNodeWithTag("tt_text_input").performTextInput("Good morning")
        compose.onNodeWithTag("tt_text_counter").assertTextEquals("12/500")
        compose.onNodeWithTag("tt_text_translate_btn").assertIsEnabled().performClick()

        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("tt_text_result").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tt_text_result").assertTextEquals("Bonjour (fake)")  // G1
    }

    @Test fun blankInput_translateDisabled() {
        compose.onNodeWithTag("tt_text_translate_btn").assertIsNotEnabled()
    }

    @Test fun errorView_retry_recovers() {
        // FakeTranslator forcedFailure=NETWORK injected via test module override
        compose.onNodeWithTag("tt_text_input").performTextInput("Offline test")
        compose.onNodeWithTag("tt_text_translate_btn").performClick()
        compose.onNodeWithTag("tt_text_error_view").assertIsDisplayed()
        compose.onNodeWithTag("tt_text_retry").assertHasClickAction()
    }

    @Test fun modeChip_selectsNlp35() {
        compose.onNodeWithTag("tt_text_mode_chip").performClick()
        compose.onNodeWithTag("tt_text_mode_chip_NLP35").assertIsSelected().performClick()
    }
}
```

## 1.10 Maestro E2E flow outline

`.maestro/text_translation.yaml` — testTags only (locale-agnostic, deterministic against fake build variant).

```yaml
appId: com.codeboxlk.tranzlate.offlinetranslator
# Runs on a build flavor wired to FakeTranslateModule (staging/fakeEngine).
---
- launchApp: { clearState: true }
# 1. happy path — golden result
- tapOn: { id: "tt_text_source_lang" }
- tapOn: "English"
- tapOn: { id: "tt_text_target_lang" }
- tapOn: "French"
- tapOn: { id: "tt_text_input" }
- inputText: "Good morning"
- assertVisible: { id: "tt_text_counter", text: "12/500" }
- tapOn: { id: "tt_text_translate_btn" }
- assertVisible: { id: "tt_text_result", text: "Bonjour (fake)" }   # G1
# 2. copy + speak + star
- tapOn: { id: "tt_text_copy" }
- tapOn: { id: "tt_text_star" }
- assertVisible: { id: "tt_text_star", enabled: true }
- tapOn: { id: "tt_text_speak" }
# 3. swap + reverse
- tapOn: { id: "tt_text_reverse" }
- assertVisible: { id: "tt_text_input", text: "Bonjour (fake)" }
# 4. NLP3.5 at limit → paywall (fake usage=AtLimit variant)
- tapOn: { id: "tt_text_mode_chip" }
- tapOn: { id: "tt_text_mode_chip_NLP35" }
- inputText: "Quota text"
- tapOn: { id: "tt_text_translate_btn" }
- assertVisible: { id: "tt_text_limit_sheet" }                          # LimitReached route
# 5. error + retry (fake forcedFailure variant)
- tapOn: { id: "tt_text_retry" }
```

---

# 2. ACCESSIBILITY CONTRACT

> **Baseline:** Android `Modifier.semantics`, TalkBack, WCAG 2.1 AA. සියලුම `contentDescription` **`strings.xml`** හරහාම (CLAUDE.md නීතිය — hardcoded UI text තහනම්). දැනට `ResultCard.kt` හි `contentDescription = ""` (line 117), `"Like"`, `"Copy"`, `"More"` hardcoded — මේ contract යටතේ **fail**; string keys වෙත migrate කළ යුතුයි.

## 2.1 Per-control accessibility table

| # | Control (`testTag`) | `contentDescription` (en) | String key | Role | State exposure | Min target |
|---|---------------------|----------------------------|------------|------|----------------|:----------:|
| 1 | `tt_text_input` | `Text to translate` | `cd_text_input` | `EditableText` | none (editable) | 48dp height |
| 2 | `tt_text_translate_btn` | `Translate` | `cd_text_translate` | `Button` | `disabled` when blank | 48×48dp |
| 3 | `tt_text_swap` | `Swap source and target languages` | `cd_text_swap` | `Button` | — | 48×48dp |
| 4 | `tt_text_source_lang` | `Source language, %1$s` | `cd_text_source_lang` | `Button` | `stateDescription`= lang name | 48dp |
| 5 | `tt_text_target_lang` | `Target language, %1$s` | `cd_text_target_lang` | `Button` | `stateDescription`= lang name | 48dp |
| 6 | `tt_text_mode_chip` | `Translation model, %1$s` | `cd_text_mode_chip` | `Button` | `stateDescription`= model title | 48dp |
| 6a | `tt_text_mode_chip_{tag}` | model title (e.g. `Tranzlate NLP3.5 (Online)`) | `cd_text_model_{tag}` | `RadioButton` | `selected` true/false | 48dp row |
| 7 | `tt_text_counter` | `%1$d of %2$d characters used` | `cd_text_counter` | text | `liveRegion=polite` on change | n/a |
| 8 | `tt_text_copy` | `Copy translation` | `cd_text_copy` (reuse `button_copy`) | `Button` | — | 48×48dp |
| 9 | `tt_text_speak` | play: `Speak translation` / active: `Stop speaking` | `cd_text_speak` / `cd_text_speak_stop` | `Button` | `stateDescription`= Playing/Idle | 48×48dp |
| 10 | `tt_text_reverse` | `Reverse translation direction` | `cd_text_reverse` | `Button` | — | 48×48dp |
| 11 | `tt_text_star` | on: `Remove from favourites` / off: `Save to favourites` | `cd_text_star_on` / `cd_text_star_off` | `Button` | `selected` = starred | 48×48dp |
| 12 | `tt_text_more_menu` | `More options` | `cd_text_more` | `Button` | `expanded` when open | 48×48dp |
| 13 | `tt_text_retry` | `Retry translation` | `cd_text_retry` | `Button` | — | 48×48dp |
| 14 | `tt_text_error_view` | (heading text) `home_result_error_view_title` | existing key | container | `liveRegion=assertive` | n/a |
| 15 | `tt_text_result` | (the translated text itself) | dynamic | text | `selectable`; no cd override | n/a |

**Rules that make each a11y row pass/fail-checkable:**
- සෑම `IconButton` එකකම `contentDescription` **non-empty** විය යුතුයි — automated test: `onAllNodes(hasClickAction()).assertAll(SemanticsMatcher.keyIsDefined(ContentDescription))`. (empty `""` = fail.)
- Toggle controls (`star`, `speak`, `mode radio`) **state** එක expose කළ යුතුයි — `assertIsSelected()` / `assertIsOn()` හෝ `stateDescription`. Icon වෙනසක් **පමණක්** ප්‍රමාණවත් නොවේ (TalkBack කියවන්නේ නෑ).
- Min touch target **48×48dp** — `Modifier.sizeIn(minWidth=48.dp, minHeight=48.dp)` හෝ `minimumInteractiveComponentSize()`. Layout test එකකින් bounds verify කරන්න.
- `fil` (`values-fil`) සහ `pt-rBR` (`values-pt-rBR`) සඳහා **සියලුම** `cd_text_*` key translate කළ යුතුයි — default `en` string පමණක් තිබීම = incomplete (lint `MissingTranslation`).

## 2.2 Initial focus after key state changes

| State change | Focus lands on | Verify (Compose) |
|--------------|----------------|------------------|
| Screen open | `tt_text_input` (soft-keyboard ready) | `assertIsFocused()` |
| Result appears (Translating→Result) | `tt_text_result` (not back to input) | `requestFocus()` on result node |
| Error appears (Translating→Error) | `tt_text_error_view` title, then `tt_text_retry` reachable next | assertive live-region moves focus |
| Paywall (LimitReached) | paywall root heading `tt_text_limit_sheet` | new screen focus |
| Reverse tapped | `tt_text_input` (now holds prior result) | `assertIsFocused()` |

## 2.3 TalkBack live-region announcements

| Event | Live-region host (`testTag`) | `liveRegion` | Announced text (en, string key) |
|-------|------------------------------|--------------|----------------------------------|
| Translate started | `tt_text_loading` | `Polite` | `Translating…` (`a11y_translating`) |
| Result ready | `tt_text_result` | `Polite` | `Translation ready: %1$s` (`a11y_result_ready`) — result text |
| Error | `tt_text_error_view` | `Assertive` | `Translation failed. %1$s` (`a11y_error`) — reason (`Network unavailable` / `Translation error`) |
| Usage warning | `tt_text_usage_warning` | `Polite` | `%1$s` (`warningMessage()` — e.g. `1 free NLP3.5 translation left today`) |
| At limit | (paywall) | `Assertive` | `Daily free limit reached` (`a11y_limit_reached`) |

Implement via `Modifier.semantics { liveRegion = LiveRegionMode.Polite/Assertive }`. Assertive **only** for error/limit (interrupts); everything else Polite.

## 2.4 Focus / traversal order (top → bottom, LTR)

`Modifier.semantics { traversalIndex = n }` හෝ visual order එකට match වන layout order:

```
1  tt_text_mode_chip         (top app bar)
2  tt_text_source_lang
3  tt_text_swap
4  tt_text_target_lang
5  tt_text_input
6  tt_text_counter
7  tt_text_translate_btn
── after result ──
8  tt_text_result
9  tt_text_reverse
10 tt_text_star
11 tt_text_copy
12 tt_text_speak
13 tt_text_more_menu
── error path ──
   tt_text_error_view (title) → tt_text_retry
```

Verify: TalkBack swipe-right sequence මේ පිළිවෙලට යා යුතුයි; decorative icons `Modifier.clearAndSetSemantics {}` වලින් skip කරන්න.

## 2.5 Contrast requirement

- සියලුම text + icon-on-background **≥ 4.5:1** (normal text), large text (≥18sp/14sp-bold) **≥ 3:1** (WCAG AA).
- `tt_text_result`, `tt_text_counter`, usage-warning, mode-chip label — measured against actual surface (light **සහ** dark theme දෙකේම, `Material You` dynamic palette ඇතුළත්ව).
- Interactive icon (`copy/speak/star/more`) tint **≥ 3:1** vs background.
- Verify: Accessibility Scanner / `Espresso` `matches(hasMinimumContrastRatio(4.5f))` හෝ manual token audit (Phase 4 `Color.kt` brand palette වලට). **`Color.Cyan/Blue/Red/Magenta`** (currently `MiddleContent.kt`) contrast-guaranteed නොවේ → fail; gradient tokens වෙත.

## 2.6 RTL (right-to-left) expectations

- Arabic/Hebrew/Urdu target/UI locale වලදී layout **mirror** විය යුතුයි: source/target chips, swap, back-arrow, traversal order — සියල්ල `start/end` (never `left/right`) padding වලින්.
- Result text directionality **content** අනුව (`TextDirection.Content`) — en→ar result RTL ලෙස render විය යුතුයි, UI en වුවත්.
- Swap/reverse icons RTL හි `Modifier.scale(-1f,1f)` හෝ `autoMirrored` icon variants.
- Verify: `adb shell settings put global debug.force_rtl 1` + `values-ar` pseudo-locale; controls overlap/clip නොවිය යුතුයි.

## 2.7 Text scaling (200%) expectations

- Font scale **2.0** (`fontScale = 2f`) දී: `tt_text_input`, `tt_text_result`, mode-chip label, counter, translate-button label — **clip / truncate / overlap නොවිය යුතුයි**.
- Text `sp` (never `dp`) වලින්; containers `wrapContentHeight` + scroll (`verticalScroll`) — fixed-height card එකක result clip නොවිය යුතුයි.
- `autoSizeText/` composable භාවිත වන තැන min font floor එකක් තිබිය යුතුයි (illegible→fail).
- Touch targets 48dp scale-independent (dp) ලෙසම පවතී.
- Verify: `createComposeRule` `fontScale=2f` + `assertIsDisplayed()` සියලු §1.7 tags; Maestro `- setDeviceLocale`/font-scale run; screenshot diff clip නැත.

---

# 3. Definition of Done (pass/fail checklist)

| # | Gate | Pass criterion |
|---|------|----------------|
| 1 | Fake seam | `Translator`, `FeatureAccess`, `UsagePolicy`, `AppClock` interfaces exist + fakes provided; no real engine on any test path |
| 2 | Golden fixtures | `defaultGolden` ≡ §1.2 table exactly; G1 `en→fr ML2_MINI` = `"Bonjour (fake)"` |
| 3 | testTags | සියලු §1.7 tags 17 source code එකේ present; Compose + Maestro reference කරයි |
| 4 | Unit tests | state-machine transitions (Ready→Translating→Result/Error/Paywall) green |
| 5 | Compose test | type→translate→`assertTextEquals("Bonjour (fake)")` green |
| 6 | Maestro | `.maestro/text_translation.yaml` full flow green on fake variant |
| 7 | a11y desc | සියලු interactive nodes non-empty `contentDescription` (empty `""` = fail) |
| 8 | a11y state | toggles `selected`/`stateDescription` expose; scanner 0 errors |
| 9 | Touch target | සියලු IconButton ≥ 48×48dp |
| 10 | Live regions | translating/result/error announcements verified with TalkBack |
| 11 | Contrast | ≥4.5:1 light+dark+MaterialYou; `Color.Cyan/Blue/Red/Magenta` removed |
| 12 | RTL + 200% | mirror + no clip at fontScale 2.0; `values-fil` + `values-pt-rBR` `cd_text_*` complete |

---

## ⚖️ CONFORMANCE OVERRIDE (DECISIONS C-1..C-13 win over anything above)

Where this contract conflicts with `DECISIONS.md` canonical conventions, **C-n wins.** Specifically:
- **C-2 (button vs live):** free-engine happy-path tests assert **wait-for-result after debounce**, NOT a button tap. `tt_text_translate_action` exists **only** for the metered Advanced-AI path.
- **C-1 testTags:** the `tt_text_*` set here is authoritative; the feature spec references it.
- **C-5 char counter:** exact rendered value = **`12/500`** (no spaces) — fix any `12/500` assertion.
- **C-6 counter:** `text_metered_counter` = used/limit ("15/20 today").
- **C-7 Reverse:** post-condition = result text moved to input + languages swapped + re-translated (NOT verbatim-to-input only).
- **C-11 at-limit:** dismissible **bottom-sheet** (`tt_text_limit_sheet`), NOT a navigated paywall screen; AUTO keeps working.
- **C-3 string keys:** use the STRINGS catalogue keys; do not invent `a11y_*`/`cd_*` (they now live in STRINGS, C-4).
- **C-10:** AUTO never resolves to metered NLP35, so no golden case charges quota via AUTO.
