# Plan — no radio is not metered: the 19a consent sheet must not open in airplane mode

status: accepted
(accepted basis: owner-directed rev5 completion objective, rule 13; issue **#248**
(S2/P2) is classified in `docs/plan/issue-130-rev5-completion.md` wave **1d** —
"behaviour defects on the shipped screens". Scope fixed in §5 and not widened.)

Issue: **#248** (S2). Related, deliberately NOT changed here: **#90** (the ruling
that created sheet 19a), **#218**/**#247** (the manager pre-flight this now sits in
front of), **#238** (the unanswerable-decision guard this fix must preserve).

## 1. The defect — the input is wrong, not the logic

Tap ⬇ on any offline-languages row with the radio completely off. Sheet **19a**
opens:

> **Download over mobile data?** … Your plan may charge for it. [Not now] [Download now]

There is no mobile data. There is no network of any kind. The app asks the user to
accept a charge on a data plan that cannot be used, and "Download now" starts
nothing — it hands to `RealOfflineModelManager.download`, whose #218/#247 pre-flight
refuses with `Failed(NETWORK)` and raises sheet 19d. One truthful answer needed one
sheet; the user is shown two.

The chain, verified against the code at tip `40af56a`:

- `AndroidConnectivityMonitor.isMetered()` (`app/src/prod/.../AndroidConnectivityMonitor.kt:65`)
  is `manager.isActiveNetworkMetered`. With **no active network** AOSP's
  `ConnectivityManager` falls through to a documented best-guess and returns
  **`true`**.
- `DownloadGate.requestDownload` (`core/domain/.../DownloadGate.kt:106`) computes
  `mustAsk = connectivity.isMetered() && !allowed`. With the brand default
  `DEFAULT_ALLOW_MOBILE_DATA = false`, `allowed = false`, so `mustAsk = true`.
- → `consentQuestion.raise(id)`; the download does not start.

The gate is doing exactly what #90 designed. The metered *snapshot* is a wrong
input, because "no network" and "metered network" are being collapsed into one.

## 2. The fix — gate the consent question on there being a network to charge

`ConnectivityMonitor` already carries the seam that tells the two apart:
`isOnline()` returns **false** when there is no active validated network
(`AndroidConnectivityMonitor.isOnline():57-61` — `getNetworkCapabilities(activeNetwork)
?: return false`), whereas `isMetered()` best-guesses **true** there. So a metered
answer is only *meaningful* when `isOnline()` is true.

The one-line change, in `DownloadGate.requestDownload`, inside the existing #238
try/catch:

```
-                connectivity.isMetered() && !allowed
+                connectivity.isOnline() && connectivity.isMetered() && !allowed
```

Only a metered link that actually **exists** is a consent question. No network
short-circuits `mustAsk` to false, so the gate hands off to
`modelManager.download(id)` — whose own pre-flight owns the no-network outcome
(§3).

### 2.1 Why the gate, not `isMetered()` itself

The issue's fix direction names the gate, and it is right. `isActiveNetworkMetered`
returning true-when-offline is documented platform behaviour and is the honest
platform snapshot; rewriting `AndroidConnectivityMonitor.isMetered()` to return
false-when-offline would change what a general seam means for any future caller,
for a decision that is specific to this one consent gate. `isMetered()` has exactly
**one** production caller (§4), and it is the gate. The gate is where #90's consent
decision lives, so the "is this decision even meaningful?" guard lives there too.

### 2.2 Why `isOnline()` is the FIRST conjunct, and why the read order is preserved

Order is load-bearing for the #238 guarantees, which four existing tests pin:

- `isOnline()` first means a positively-offline device (`isOnline() == false`)
  short-circuits before `isMetered()` is consulted at all — the best-guess-true is
  never reached, which is the whole point.
- The `val allowed = downloadPrefs.allowMobileData.first()` read stays a **separate
  statement above** the boolean expression, exactly as today. It must still throw
  where it throws now: the test `an unreadable standing permission asks rather than
  assuming consent` drives an online, **un-metered** connection with a throwing prefs
  read and asserts the gate ASKS. If the prefs read were inlined into the `&&`,
  `isMetered() == false` would short-circuit past it and the throw would never
  happen — the test would go from asking to downloading. Kept as a statement, it
  throws first and is caught by the existing widened catch → ask.
- If `isOnline()` itself throws, the existing `catch (Throwable)` converts it to
  `mustAsk = true` (ask). That is the safe side: the harm #238/#90 guard against is
  spending the data plan silently, and asking never does that. On a genuinely
  offline device this reproduces today's two-sheet annoyance rather than a
  regression, and on an online-metered device it is correct.

`CancellationException` still rethrows first and by name (#197). This PR does not
touch the rethrow.

## 3. The open question — the no-network outcome — is ALREADY SETTLED

The one design call #248 flags is: *what does the user see when they tap ⬇ with no
radio?* It is settled, not new, and the fix invents no UX. Four independent facts:

1. **The manager already owns it.** `RealOfflineModelManager.download:354-358`
   already does `if (!connectivity.isOnline())` → `takeTransient(Failed(NETWORK))`
   (row turns red, names the cause, offers Retry) **and** returns
   `DownloadAttempt.Refused(NETWORK)`. Pre-existing, merged (#218/#247).
2. **The picker already raises 19d from it.** `LanguagePickerViewModel.reportOutcome:557`
   maps `is DownloadAttempt.Refused -> raise(id, attempt.cause)`; `NETWORK` → sheet
   19d. Pre-existing.
3. **The offline-manager screen already surfaces it** via the shared state map
   (`Failed(NETWORK)` → red row + Retry). `OfflineLanguagesViewModel.download:153`
   discards the return, but the row still turns red. Pre-existing.
4. **The correct behaviour already ships for half the offline cases.** With the
   radio off **and** the standing "always download on mobile data" preference ON,
   the gate's `mustAsk` is *already* false today (`isMetered()[true] &&
   !allowed[!true=false]`), so it *already* calls `modelManager.download()` →
   `Refused(NETWORK)` → 19d, with **no** 19a. This fix makes the permission-OFF
   case behave the same as the permission-ON case already does. It is not a new
   outcome — it is the outcome the app already produces, extended to the other half.

This satisfies `EDGE_CASES.md` §7 (NO-DEAD-END: red row + Retry + a guided sheet)
and its readiness matrix row for "offline + not downloaded" (a guided
connect-to-download path). **No owner decision is required.**

## 4. Enumeration (rule 11)

Two independent searches, run at tip `40af56a` (rule 12 — could not both miss the
same site):

```
$ grep -rn 'isMetered'      --include='*.kt' .   # token
$ grep -rn 'connectivity\.isMetered()' --include='*.kt' .   # qualified call
```

- **`isMetered()` production callers: 1** — `DownloadGate.kt:110`. (Others: the
  interface decl `ConnectivityMonitor.kt:27`, the prod impl
  `AndroidConnectivityMonitor.kt:65`, the fake `FakeConnectivityMonitor.kt:30`, and
  KDoc mentions in the two ViewModels + the test.) The metered decision lives in
  one place by #90's design, so the fix is one call site.
- **`requestDownload` production callers: 2** — `LanguagePickerViewModel.kt:481`
  and `OfflineLanguagesViewModel.kt:153`. Both are consumers I do **not** own; both
  already handle the new return (see §3.2/§3.3): the picker via `reportOutcome`, the
  offline screen via the state map. No consumer change is needed — verified, not
  assumed.

**Call sites: `isMetered()` 1 found, 1 changed. `requestDownload` 2 found, 0 need
changing (both already handle `Refused(NETWORK)`).**

## 5. Scope — file ownership

**In (owned):**
- `core/domain/.../DownloadGate.kt` — the one-line guard + KDoc note.
- `core/testing/.../DownloadGateTest.kt` — the #248 regression test.
- `docs/plan/issue-248-no-radio-metered.md` — this doc.

**Out, deliberately untouched:**
- `AndroidConnectivityMonitor.isMetered()` — honest platform snapshot, §2.1.
- `RealOfflineModelManager` — already owns the no-network outcome (§3.1); reordering
  the gate and its pre-flight is explicitly #248's own note against #90's surface,
  and it is not needed: the gate now simply defers to it.
- `LanguagePickerViewModel` / `OfflineLanguagesViewModel` — owned by other agents;
  no change needed (§4).
- `docs/plan/issue-130-language-rev3.md` tracker — the orchestrator moves that row
  at land.

**No composable is added or changed.** The fix is domain logic + a unit test;
sheet 19a (`MobileDataSheet.kt`) is unchanged — it is simply not raised in airplane
mode. So no `@PreviewLightDark` is due (rule 7 applies to composables added/changed).

## 6. Verification — mutation-first (rule 11)

The mutation is decided **before** the test is written: *the gate omits the
`isOnline()` guard* — i.e. the production code exactly as it stands today. The harm
is "no network → 19a raised".

New test in `DownloadGateTest` (the gate's home suite): a fake connectivity monitor
with `state.value = false` (offline) and `metered = true` (the AOSP best-guess),
prefs `false` (brand default). Asserts:
- `gate.pendingConsent.value` is **null** — 19a is NOT raised (the #248 guard);
- `manager.downloads == ["de"]` — the gate hands off to the manager (not a silent
  no-op dead end), so the manager can produce `Failed(NETWORK)` → 19d.

Expected RED→GREEN, both captured in the PR body:
- **RED** against current code: `pendingConsent == "de"`, `downloads` empty → the
  two assertions fail.
- **GREEN** after the one-line fix.

The existing #238 suite (unanswerable metered, unreadable prefs, `Error`,
`CancellationException` propagation) and the #90 matrix must stay green — all use
`initiallyOnline = true`, so prepending `isOnline() &&` is a no-op for them (§2.2).

Gate: `./gradlew preflight` (rule 6), plus `:core:testing:test` for the suite.
