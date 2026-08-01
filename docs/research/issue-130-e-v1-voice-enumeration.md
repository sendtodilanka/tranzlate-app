# Research — issue #130 E-V1: does offline-voice enumeration actually work on a device?

status: executed 2026-08-01 — one half pinned, one half NOT pinnable on the hardware available
read-only record (Rule 4) — the code it informs is PR-10 (`AndroidOfflineVoiceCatalog`)

## 0. What E-V1 was supposed to disconfirm

Risk **R4** in the rev.3 ruling (§6): *"Voice marks silently empty / false
positive"*. The ruling names two experiments:

1. On an image **without Google TTS**, the enumerated set is empty, no marks
   appear, and the 19j copy stays coherent.
2. **Without** the `<queries>` block on API 36 + Google TTS, the enumerated set
   comes back empty — which is what pins the block's necessity.

Result in one line: **(1) is pinned, and its evidence is stronger than expected.
(2) could NOT be pinned on any emulator image available on this machine, and the
reason why is measured, not guessed.** Details below.

## 1. What was run

Probe: `app/src/androidTestProd/kotlin/com/codeboxlk/tranzlate/OfflineVoiceEnumerationProbe.kt`
— a plain instrumented test (no Hilt, no Compose, no Espresso) that logs the raw
platform answer under the tag `OfflineVoiceProbe`.

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SERIAL=emulator-5556
adb -s emulator-5556 logcat -c
./gradlew :app:connectedTranzlateProdDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.codeboxlk.tranzlate.OfflineVoiceEnumerationProbe
adb -s emulator-5556 logcat -d -s OfflineVoiceProbe:I
```

Device: the AVD that was booted, **not** the `Tranzlate_Resizable` the ruling
assumed. Recorded exactly, because it changes what the run can claim:

| | value | how |
|---|---|---|
| AVD | `Resizable_Experimental` | `adb -s emulator-5556 emu avd name` |
| API | **37** (not 36) | `adb shell getprop ro.build.version.sdk` |
| image | `android-37.1/google_apis_ps16k` | `~/.android/avd/Resizable_Experimental.avd/config.ini` |
| build type | `userdebug`, `ro.debuggable=1` | `adb shell getprop` |
| TTS engine | `com.google.android.tts` at `/product/app/GoogleTTS/GoogleTTS.apk` | `adb shell pm path` |

App `targetSdk` is 36, so package-visibility filtering is in force for this app
(it applies from targetSdk 30).

## 2. Finding A — the enumeration works, and the platform's tags are BCP-47

With the `<queries>` block present and Google TTS installed:

```
visible TTS engines: 1 -> [com.google.android.tts]
onInit signalled=true status=0 (SUCCESS=0)
getVoices() -> 473
offline voice ids (68): [ar, as, bg, bn, bs, ca, cs, cy, da, de, doi, el, en, es,
  et, fi, fr-CA, fr-FR, gu, he, hi, hr, hu, id, is, it, ja, jv, km, kn, ko, lt,
  lv, mai, ml, mr, ms, ne, nl, no, or, pa, pl, pt-BR, pt-PT, ro, ru, sa, sd, si,
  sk, sl, sq, sr, su, sv, sw, ta, te, th, tl, tr, uk, ur, vi, yue, zh, zh-TW]
```

The risk this run was really checking — that `Voice.getLocale()` might hand back
ISO3-shaped locales (`Locale("eng","USA")`) that no catalog id could match — did
**not** materialise on this engine. Every locale converted cleanly:

```
voice=ca-ES-language      locale=ca_ES   tag=ca-ES    network=false canonical=ca
voice=cmn-tw-x-ctc-local  locale=zh_TW   tag=zh-TW    network=false canonical=zh-TW
voice=fil-ph-x-fic-local  locale=fil_PH  tag=fil-PH   network=false canonical=tl
voice=he-il-x-heb-local   locale=he_IL   tag=he-IL    network=false canonical=he
voice=ar-xa-x-arc-network locale=ar      tag=ar       network=true  canonical=ar
```

Three things worth keeping from that dump:

- **The resolver is load-bearing on real data, not just on test data.** Google's
  Filipino voices report `fil-PH`; a naive `locale.language` read yields `fil`,
  which is not a catalog id, and Filipino would silently lose its mark on every
  device. `LanguageTagResolver`'s `fil → tl` alias is what saves it.
- **The `-network` voices are real and numerous** — nearly half of the 473. The
  `!isNetworkConnectionRequired` filter is doing visible work, not defending
  against a hypothetical.
- **Some voices are honestly dropped.** `brx-IN`, `kok-IN`, `ks-IN`, `mni-IN`,
  `sat-IN` resolve to `null` because the 194-language catalog has no row for
  them (`mni-Mtei` exists but `mni-IN` does not truncate to it). These are
  false negatives — the safe direction — and they are recorded here rather than
  papered over.

## 3. Finding B — a device with no TTS engine, and the null return is REAL

The ruling's "AOSP image without Google TTS" is not available (§5). What was
run instead removes the engine from the app's view on the image we have:

```
adb -s emulator-5556 root
adb -s emulator-5556 shell pm uninstall --user 0 com.google.android.tts
adb -s emulator-5556 shell cmd package query-services -a android.intent.action.TTS_SERVICE
  -> No services found
# …re-run the probe…
adb -s emulator-5556 shell cmd package install-existing --user 0 com.google.android.tts   # restore
```

Probe output, `<queries>` block PRESENT, no engine available to the user:

```
visible TTS engines: 0 -> []
onInit signalled=true status=-1 (SUCCESS=0)
getVoices() -> null
offline voice ids (0): []
```

This is the strongest single result of the experiment:

1. **`getVoices()` returned literally `null`** on a real Android build. The null
   guard cited from AOSP `TextToSpeech.java` is not a defensive reading of the
   source — it is the observed return, and Kotlin sees the SDK's un-annotated
   type as a platform type, so nothing would have caught the NPE.
2. **`onInit` fired promptly with `ERROR (-1)`.** The no-engine case does NOT
   reach the timeout; it fails fast. The timeout defends a different case (an
   engine that binds and then goes silent), which is why both are handled.
3. **The catalog answered the empty set and threw nothing** — the seam's
   false-negative-safe contract, on the device.

What this run does **not** cover: "no marks appear and the 19j copy stays
coherent". No voice UI exists yet — PR-10 builds the seam only, and the marks
land in PR-12. That half of the ruling's clause is deferred to PR-12, where
there is something to look at.

## 4. Finding C — the `<queries>` half could NOT be pinned here, and here is why

Removing the block from `app/src/main/AndroidManifest.xml`, rebuilding and
re-running produced an **identical** result: `visible TTS engines: 1`,
`getVoices() -> 473`, 68 ids. Taken alone that would read as "the block is
unnecessary". It is not what happened. Two measurements say so.

First, the merged manifest genuinely lost the block (the one left is Play
Billing's, from a library, and mentions no TTS action):

```
$ python3 - <<'PY'   # app/build/intermediates/packaged_manifests/tranzlateProdDebug/…/AndroidManifest.xml
<queries>
    <intent><action android:name="com.android.vending.billing.InAppBillingService.BIND" /></intent>
    <intent><action android:name="com.google.android.apps.play.billingtestcompanion.BillingOverrideService.BIND" /></intent>
</queries>
PY
```

Second, the reason the engine stayed visible:

```
$ adb -s emulator-5556 shell dumpsys package com.google.android.tts | grep forceQueryable
    forceQueryable=true

$ adb -s emulator-5556 shell dumpsys package queries | head -3
  system apps queryable: false          # NOT the blanket debug-build exemption
  queries via forceQueryable:
  forceQueryable:
    … com.google.android.tts …          # listed individually
```

`com.google.android.tts` is marked **force-queryable** on this image, which makes
it visible to every app regardless of `<queries>`. The blanket "all system apps
are queryable on debug builds" exemption is explicitly OFF here, so this is a
property of that one package, not of the build type.

**Conclusion: this image cannot discriminate the hypothesis.** The experiment
ran, and it returned "no difference" for a measured reason that has nothing to
do with whether the block is needed. It is evidence about the image, not about
the code.

The block stays, for reasons that survive this null result:

- Google's own package-visibility guidance names `INTENT_ACTION_TTS_SERVICE` as
  a `<queries>` case for apps that use `TextToSpeech`.
- Force-queryable is a per-package property. A **third-party** engine (Samsung
  TTS, Acapela, Vocalizer, eSpeak — engines real users set as their default)
  carries no such marking, and without the block those are invisible on API 30+.
  On such a device the app would report "no offline voices" while the user is
  listening to that very engine read their screen aloud.
- Removing it can only ever narrow what the app can see. The cost of keeping it
  is one manifest block; the cost of being wrong is the feature failing closed
  and silently, which is exactly risk R4.

## 5. What would be needed to pin Finding C

Not available on this machine — stated plainly rather than worked around:

- **An AOSP (`default` tag) system image.** Installed images are all
  `google_apis` or `google_apis_playstore`: `android-24`, `android-28`,
  `android-29`, `android-36.1` (×2), `android-37.1`. `sdkmanager
  "system-images;android-36;default;arm64-v8a"` would provide one.
- **Or a TTS engine that is not force-queryable** — a normally-installed (not
  `/system`, not `/product`) engine APK, or a physical phone whose default
  engine is a third-party one. No third-party engine APK is present here, and
  the physical device attached to this machine was deliberately not touched.

Either would turn Finding C from "not pinnable" into a real answer. Until then
the block's justification is documentation plus the force-queryable argument
above, and this record says so rather than claiming a pass.

## 6. Incidental finding (not part of E-V1)

`app/src/androidTestProd` did not compile at all on `origin/main`:

```
Default_HiltComponents.java:163: error: [Dagger/MissingBinding]
  com.codeboxlk.tranzlate.domain.access.PurchaseFlow cannot be provided
  without an @Provides-annotated method.
```

`@TestInstallIn(replaces = [TranslateModule::class])` removed the `PurchaseFlow`
binding when billing moved into `TranslateModule` (commit `fabb214`) and nothing
re-supplied it. Verified pre-existing by stashing PR-10's changes and rebuilding.
CI runs `build`, `test`, the classpath guards and `detekt spotlessCheck` — none
of which compiles androidTest — so the whole instrumented suite has been
unbuildable, invisibly, since that commit. PR-10 re-supplies the binding because
E-V1 could not otherwise run; **the missing CI step is the real finding** and
belongs to its own issue.
