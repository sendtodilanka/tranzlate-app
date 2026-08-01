# Research — issue #149: what does a live `TextToSpeech` actually cost, and what does giving it back cost?

Read-only record (Rule 4). Experiments run 2026-08-01 on `Resizable_Experimental`
(emulator-5556, API **37**, `google_apis_ps16k`, Google TTS at
`/product/app/GoogleTTS/GoogleTTS.apk`), prod debug build of this branch.

## Question

`ResultSpeaker` built a `TextToSpeech` in a `@Singleton`'s field initializer and
never shut it down. The #130 ruling says the opposite for the voice work — "TTS =
enumerate→cache→shutdown, never a standing engine" — so the app was about to hold
two TTS surfaces under contradictory contracts (#147's `AndroidOfflineVoiceCatalog`
releases on every path).

The issue asks for a judgement, not a mechanical alignment, because enumeration
and speaking are not the same job. Three things had to be measured or cited before
choosing:

1. What a live `TextToSpeech` costs while idle.
2. Whether `shutdown()`-then-reconstruct has a real latency cost, and how much.
3. What Google's own guidance says about instance lifetime.

Ruling text does not settle any of them — Mandatory Rule 1 applies to the ruling
as much as to the old app: nothing is correct because it is written down.

## What the sources say (checked first)

**Google's guidance sets the lifetime at a component boundary.** The
`TextToSpeech.shutdown()` reference is one of the few places Android states an
instance-lifetime policy at all, and it names neither extreme:

> Releases the resources used by the TextToSpeech engine. It is good practice for
> instance to call this method in the onDestroy() method of an Activity so the
> TextToSpeech engine can be cleanly stopped.

(developer.android.com/reference/android/speech/tts/TextToSpeech — verbatim.)
Not per-utterance, and not never: at the end of the thing that was speaking.

**AOSP says the platform will not clean up after you.** Since the TTS manager
service was introduced the client no longer binds the engine itself; it asks
`system_server` to hold the binding
(`TextToSpeech.java` `SystemConnection.connect` → `ITextToSpeechManager.createSession`).
The system binds it with

```java
Context.BIND_AUTO_CREATE | Context.BIND_SCHEDULE_LIKE_TOP_APP     // TextToSpeechManagerPerUserService.java:98
```

and then explicitly disables its own idle unbind:

```java
protected long getAutoDisconnectTimeoutMs() { return PERMANENT_BOUND_TIMEOUT_MS; }   // :164-166
```

`ServiceConnector.Impl.getAutoDisconnectTimeoutMs()` documents that return value as
"non-positive (<=0) value to disable automatic unbinding", and
`AbstractRemoteService.PERMANENT_BOUND_TIMEOUT_MS = 0`. The only other end is
client-process death, which the service links to
(`mCallback.asBinder().linkToDeath(mUnbindOnDeathHandler, 0)`, :123). So a held
engine is held until `shutdown()` or until our process dies. There is no timeout
that quietly saves a careless app.

## Experiment E-149a — the idle cost, measured on the app itself

Probe: `app/src/androidTest/kotlin/com/codeboxlk/tranzlate/TtsEngineLifetimeProbe.kt`
for the latencies; plain `adb` sampling for the process state. The app runs were
driven through the real UI (`input tap` / `input text`), not through a test seam.

```
adb -s emulator-5556 shell am force-stop com.google.android.tts
adb -s emulator-5556 shell monkey -p com.codeboxlk.tranzlate.offlinetranslator -c android.intent.category.LAUNCHER 1
adb -s emulator-5556 shell "ps -A -o PID,RSS,NAME | grep -w com.google.android.tts"
adb -s emulator-5556 shell "dumpsys activity processes com.google.android.tts | grep -E 'oom adj:|mCurSchedGroup=|curProcState=|cached='"
```

**Before the fix**, on the shipped `@Singleton`:

| moment | TTS engine process |
|---|---|
| app not running | **does not exist** |
| app launched, Home screen, nothing tapped | created; `oom adj cur=100`, `mCurSchedGroup=3`, `curProcState=5`, `cached=false empty=false`, RSS ~62MB |
| app sent to background (`KEYCODE_HOME`), +25s / +50s / +75s | **unchanged**: still `adj 100`, still `mCurSchedGroup=3` — while our own process sat at `adj 700` |
| our process force-stopped | `adj 905`, `mCurSchedGroup=0`, `cached=true empty=true`, RSS ~44MB |

Two things in that table decided the issue.

The first is that **reaching Home was enough**. Hilt builds the singleton when the
first `TextViewModel` is constructed, the field initializer binds the engine, and
so an app that had translated nothing and spoken nothing was already holding
another process open.

The second is the background row, which **disconfirmed the hypothesis I went in
with**. I expected the cost to decay when the app left the foreground — service
importance normally follows its client. It does not: `BIND_SCHEDULE_LIKE_TOP_APP`
kept the engine at visible-app importance and in the top-app scheduling group for
as long as the binding existed, with our app in the background. The cost is not
bounded by our foreground time; it is bounded only by our process's death.

`adj 100` is `VISIBLE_APP_ADJ` — the engine is not merely resident, it is held
above the tier the low-memory killer reclaims from. Released, it becomes a cached,
empty process (`adj 905`) and is first in line to be reclaimed.

## Experiment E-149b — the rebind cost

`TtsEngineLifetimeProbe`, same device, engine already warm (`logcat -s TtsLifetimeProbe`):

```
rebind#0 signalled=true status=0 initMs=601      rebind#0 shutdownMs=1
rebind#1 signalled=true status=0 initMs=495      rebind#1 shutdownMs=0
rebind#2 signalled=true status=0 initMs=514      rebind#2 shutdownMs=1
rebind#3 signalled=true status=0 initMs=501      rebind#3 shutdownMs=1
rebind#4 signalled=true status=0 initMs=478      rebind#4 shutdownMs=1
```

Construct → `onInit` = **478-601ms** (median 501). `shutdown()` itself is free (≤1ms).

The number that matters to a user is not init, though — it is tap→audio, which the
probe measures against `UtteranceProgressListener.onStart`:

```
fresh:      tap→audio 670ms (init included)
standing#0: tap→audio 8ms
standing#1: tap→audio 2ms
standing#2: tap→audio 4ms
standing#3: tap→audio 7ms
standing#4: tap→audio 1ms
```

**~0.7s of silence on a fresh engine against ~2-8ms on one already bound**, and a
play/stop/play retoggle would pay it every time. This emulator runs on an M-series
host; a low-end phone is slower, not faster, so 670ms reads as a floor. What that
number is on real low-end hardware: **verified data නෑ** — no such device here.

## What this rules out

- **Per-utterance shutdown (the catalog's contract, applied to the speaker).**
  Rejected: it puts a measured ~0.5-0.7s of nothing in front of every play tap,
  with no progress affordance on a speak button, for a saving the user only
  benefits from while they are looking at a result they could speak. Enumeration
  can pay a bind once per process because it asks one question and is done; a
  toggle asks repeatedly.
- **Leaving the singleton and amending the ruling to exempt the speaker.**
  Rejected: the idle cost is real and measured, and it is paid from launch by
  users who never tap speak — including while the app is in the background. The
  ruling's rule is not wrong about speaking; it is just stated for the wrong unit
  ("never standing" rather than "never past its consumer").

## What was adopted

The engine lives exactly as long as its consumer has something to say:
`ResultSpeaker` gains `prepare()`/`release()`, `AndroidResultSpeaker` stops being a
`@Singleton`, and `TextViewModel` holds one from the moment a translation is asked
for (`Translating`) until the face stops being a result — plus `onCleared()`, for
the result left on screen when the host goes away.

`prepare()` fires at `Translating`, not at `Result`, deliberately: the ~500ms bind
then runs alongside the translation instead of in front of the audio, so the first
tap is warm.

> **CORRECTED after co-verify (2026-08-01, PR #159 lens).** This section first
> claimed that a tap beating the bind "is still answered honestly — `speak()`
> returns false and the UI shows 'unavailable' … the same window the pre-fix code
> had on a cold start". **Both halves were wrong**, and the lens reproduced it on
> the device: after a **cache hit** the busy floor is cancelled and `Result`
> renders immediately, so the tap lands mid-bind and got *"Speech isn't available
> for this language on this device"* — a statement about the language and the
> device that was false, on a button that worked seconds later. And the window was
> **new**, not inherited: pre-fix the singleton bound at Home and was long ready by
> the time any result existed. A window this fix creates cannot be excused by the
> code it replaced.
>
> Fixed by making the wait a wait: `ResultSpeaker.speak` suspends until the bind
> reports, so the tap plays. Only outcomes that are true are ever said out loud —
> `NO_VOICE` (the engine bound, this language has no voice) and
> `ENGINE_UNAVAILABLE` (no usable engine at all, with the system-settings
> guidance EDGE_CASES requires). "Still binding" is not one of them.

## Verification on the device (post-fix, real UI)

| step | TTS engine process |
|---|---|
| launch, Home, nothing tapped | **does not exist** (pre-fix: created and pinned at `adj 100`) |
| composer open, "Good morning" typed | still does not exist |
| Translate tapped, result on screen | bound: `adj 100`, `cached=false empty=false` |
| one BACK to Home, app **still** top-resumed | released: `adj 905`, `cached=true empty=true` |

The last row is the one the singleton could never produce.

## The first fix did not close the harm — co-verify re-measurement (PR #159)

Everything above survived the lens. The **fix** did not: it released on `onCleared`
and on the state funnel, and `TextViewModel` is hoisted OUTSIDE the NavDisplay
entries (`TranzlateApp.kt`), so it resolves to the Activity's ViewModelStore and
`onCleared()` runs only when the Activity finishes. Backgrounding changes no state
at all, so with a result on screen the funnel never fired either. Re-measured,
result held, `KEYCODE_HOME`:

```
+25s : com.google.android.tts → adj 100  schedGroup 3  cached=false
       our app                → adj 700
+65s : com.google.android.tts → adj 100  schedGroup 3  cached=false
       our app                → adj 900  cached=true
```

That is this record's own **"before the fix"** row, reproduced against the fix.
Only the BACK path — the one the acceptance table happened to test — was ever
closed.

The lifetime now has a second input: the shell's own `ON_START`/`ON_STOP`
(`LifecycleStartEffect` beside the hoist, since the composer is not composed while
the picker or Settings is on top). An engine is held only when a face that can
speak is showing **and** the app is on screen; coming back re-binds, so the first
tap after a return is still warm. Re-verification of that is in the PR body.

## Limits of this record

- One device, one engine (`com.google.android.tts`, API 37 emulator). A
  third-party engine (Samsung, Acapela, eSpeak) may bind at a different cost;
  none is installed here. **verified data නෑ** for those.
- The pre-API-31 `DirectConnection` path (the app binds the engine itself rather
  than through `TextToSpeechManagerService`) was not measured. It uses plain
  `BIND_AUTO_CREATE` without `BIND_SCHEDULE_LIKE_TOP_APP`, so the scheduling-group
  half of the finding may not hold there; the "nothing unbinds it but you" half is
  structural and does. **Which path the `release()` guard is for** was corrected by
  the co-verify lens: it cited `DirectConnection.disconnect` (`TextToSpeech.java`
  :2430) as the unguarded `unbindService` — a path AOSP marks legacy — while API
  31+ ends the session through `SystemConnection.disconnect` (:2488-2500), which
  unbinds nothing itself. Independently confirmed here only as far as
  `connectToEngine()` choosing `SystemConnection` or `DirectConnection` from
  `mIsSystem`; the two `disconnect()` bodies at those line numbers are the lens's
  reading, not re-verified in this session (the file truncates in the fetch tool)
  — **verified data නෑ** for the exact bodies. The guard stays either way: it costs
  nothing, and the legacy path is still reachable.
- Nothing here measures battery. The claim is about process importance, scheduling
  group and reclaim eligibility — all directly observed — not about mAh.
