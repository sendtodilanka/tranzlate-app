# Plan — the `Exception`/`Throwable` split, and three unguarded doors beside it

status: accepted
(accepted basis: owner-directed autonomous task; issues #236 (S0/P1) and #238
(S2/P1) were opened by the owner and handed to this session as one PR, on the
grounds that both are "which `Throwable` reaches the process handler, from
where". Scope fixed below and not widened past it.)

Issues: **#236** (S0) · **#238** (S2). Related, deliberately NOT fixed here: **#242**.

## 1. The decision this PR had to make

#236 states it as a fork and refuses to let it be dodged:

> Either widen the five, or narrow the four that were widened and retract #195's
> rationale — but the same JNI failure cannot be fatal on five paths and
> survivable on three by accident.

**Ruling: WIDEN.** The four `Throwable` sites stay; the `Exception` sites on the
same concept are widened to match. #195's rationale is upheld, and extended to
two sites #236's own table does not list (see §3).

### 1.1 The premise is verified here, not inherited

`TextViewModel.kt:768-779` is this project's written rationale. Its type claim was
re-checked on this machine rather than believed:

```
$ "$JAVA_HOME/bin/java" Hier.java
java.lang.UnsatisfiedLinkError -> java.lang.LinkageError -> java.lang.Error -> java.lang.Throwable -> java.lang.Object
   isException=false  isThrowable=true
```

So `catch (e: Exception)` provably does not cover the class the KDoc names, and
`catch (t: Throwable)` does. The premise holds.

The escape route holds too:

```
$ grep -rn 'CoroutineExceptionHandler' --include='*.kt' . | grep '/src/main/'
feature/language/.../LanguagePickerViewModel.kt:390:  (KDoc prose only)
feature/history/.../HistoryViewModel.kt:146:          (KDoc prose only)
feature/text/.../TextViewModel.kt:215:                  (KDoc prose only)
```

Three mentions, all prose. **No handler is installed anywhere**, and
`DataModule.kt:69-71` is `CoroutineScope(SupervisorJob() + dispatchers.io)` —
`SupervisorJob` stops sibling cancellation, it does not swallow. An escape reaches
`Thread.defaultUncaughtExceptionHandler`: process death, no dialog.

(#236 cites `DataModule.kt:68-70`; the provider is at **:69-71**. Immaterial to
the argument, recorded because rule 11 asks that documents be checked against
code rather than quoted from.)

### 1.2 Why widening and not narrowing — the argument, not the assumption

**a. Narrowing has no advocate in the codebase.** Every one of these sites already
carries a written degrade: the star stays unfilled, Recents misses a row, Manage
packs misses a date, the saved-count line is absent. Not one of them argues
anywhere that process death is the right outcome for the call it guards.
Narrowing would mean choosing, deliberately, that on the *same* call with the
*same* degrade already built and tested, one failure class is fatal instead.

**b. The costs are not symmetric.** If widening turns out to be unnecessary —
no `Error` ever arrives from Room — the runtime behaviour is byte-identical,
because a catch clause that never matches costs nothing. If narrowing turns out
to be wrong, the cost is process death on the star tap, on **every translation**,
and on **every language selection**. One direction risks nothing; the other risks
the S0. With the premise verified rather than speculative, that settles it.

**c. The honest counter, addressed rather than buried.** `catch (Throwable)` also
catches `OutOfMemoryError` and `StackOverflowError`, and the usual guidance is not
to swallow those. Two facts make it acceptable *here specifically*, and they are
conditions this PR must not exceed:

  1. Every guarded region is **one bounded best-effort persistence call**, never a
     region of arbitrary work. By the time the catch runs, the frame that
     overflowed has unwound and the allocation that failed has been released.
  2. The alternative is not "the app handles OOM gracefully". It is
     `Thread.defaultUncaughtExceptionHandler` — the process death we would have
     had anyway. Swallowing cannot be worse than the default here, and on the
     JNI case it is much better.

  So the rule this PR establishes is narrow on purpose: **widen a catch that wraps
  a single best-effort call with a defined degrade; never widen one that wraps a
  region.**

**d. `CancellationException` still rethrows, first and by name, everywhere.** It is
an `Exception`, so widening protects it not at all — only the rethrow does. #197
was bitten exactly here, and rule 12 records a brief of mine that was wrong about
it. No rethrow is touched by this PR.

## 2. What #236's table got right, and where it stopped

The table is correct on every row it has. It is **incomplete**, and the direction
of the error is under-fixing.

Two independent enumerations (rule 12 — searches that could not both miss the same
thing):

```
$ grep -rEn 'catch[[:space:]]*\(' --include='*.kt' . | grep '/src/main/'    # form
$ grep -rn  'catch'              --include='*.kt' . | grep '/src/main/'    # token
```

The first is blind to `.catch {`; the second finds it (`TranzlatePreferencesDataSource.kt:42`)
and also to KDoc prose, which the first excludes. Neither can miss what the other
sees. Resolved through a script that pairs each site with its caught type (many are
line-wrapped by spotless, so the type is not on the `catch` line):

**50 raw hits · 3 are prose inside comments · 47 real catch sites.**
By type: `CancellationException` 21 · `Exception` 20 · `Throwable` 4 ·
`InterruptedIOException` 2 · `RuntimeException` 1 (`??`/`the` in the raw dump were
the two whose type sits past an interleaved comment).

Of the 20 `Exception` sites, **7** are on the concept #236 is about — a best-effort
persistence call whose catch performs a silent degrade, on a scope with no handler.
#236 lists 5 of them. The two it does not:

| Site | Guards | Degrades to |
|---|---|---|
| `UsageDataSource.kt:51` `readUsage` | DataStore read | `emptyPreferences()` |
| `RealUsagePolicy.kt:107` `persist` | usage counter save | fail-open, in-memory decision stands |

Both are the identical shape, both were written before this was settled, and
leaving them would recreate exactly the "un-migrated half" #236 exists to end.

## 3. Scope — the line, and what is deliberately outside it

**In:** the 7 persistence-degrade sites.

**Out, and filed rather than done:** the engine and SDK-boundary catches
(`GotEngine`, `GctEngine`, `MlKitEngine`, `MlKitLanguageIdentifier`,
`RealOfflineModelManager` download/delete, `FirebaseRemoteConfigSource`,
`QonversionSubscriptionGateway`, `ResultSpeaker`). These are a different concept:
their catch converts a failure into a **typed, user-visible outcome with a Retry**,
not a silent degrade. Widening them changes what the user sees, which is a
behaviour decision with its own EDGE_CASES row, and neither issue asks for it.

## 4. #238 — three doors, three different fixes

Grouped with #236 because they share the sentence, but only one of them is about
catch width. Recording that distinction is the point:

### 4.1 `DownloadGate` — no catch of any width

`StatFs` throws `IllegalArgumentException`, verified from the SDK source on this
machine:

```
$ grep -n -A6 'private static StructStatVfs doStat' \
    "$ANDROID_HOME/sources/android-36.1/android/os/StatFs.java"
53:            throw new IllegalArgumentException("Invalid path: " + path, e);
```

That is an `Exception`. **Widening would have fixed nothing here** — the bug is
that there is no catch at all. Two homes, two degrades:

- `DownloadGate.requestDownload` — the metered decision (prefs read + `isMetered()`).
  If we cannot determine the answer, **raise the consent question.** Issue #90's
  ruling is that a metered download is a consent question; when the answer is
  unknown, the safe side is to ask, never to spend the user's data plan silently.
  The sheet already offers "Download once" and "Wait for Wi-Fi", so it is not a
  dead end.
- `RealOfflineModelManager.download` — the free-space pre-flight. If the probe
  cannot answer, **proceed.** Refusing on an unknown would strand a user whose disk
  is fine, and `StorageProbe`'s own contract already says `null` means "unknown,
  degrade honestly", never "zero". ML Kit reports a genuine out-of-space itself.

### 4.2 Two blocking probes on `Dispatchers.Main.immediate`

`isMetered()` (binder IPC) and `freeBytes()` (`statvfs`) run on the caller's
coroutine, which on the download path is the main thread. The same file already
wraps the same two calls at `LanguagePickerViewModel.kt:230` and `:549` and says
why. Fixed by following that established idiom at the call sites that miss it —
one `withContext(dispatchers.io)` covers both probes, since it applies to the whole
call tree beneath it.

### 4.3 DataStore reads guarded, writes not

The read guard at `TranzlatePreferencesDataSource.kt:42` covers every read flow and
swallows `IOException` only. **Every `dataStore.edit` setter has none** (12 sites).
The write guard goes in the same home as the read guard, with the same narrow rule:
`IOException` only, because that is what `edit` documents. Anything else stays loud.

### 4.4 The backstop — a handler on `@ApplicationScope`

`SupervisorJob()` without a `CoroutineExceptionHandler` is half the idiom: it stops
one child's failure cancelling its siblings, and still lets it kill the process.
Every local guard above is a *specific* degrade; the handler is the *general* one,
and it is what stops the next `appScope.launch` someone writes from recreating this
S0 in a file nobody thought to check.

This is the difference the project has already paid for twice: a KDoc saying
"appScope has no handler, so guard your writes" is a rule, and rule 8's lesson is
that writing a rule down is not enforcing it. The handler is the enforcement.

It does not make the local guards redundant. A handler catches the throw but the
launch's remaining work is already dead — at `LanguagePickerViewModel.kt:376` a
failing `setSourceLang` would take the `stampSafely` below it down too. The local
catch keeps the degrade fine-grained; the handler keeps the process alive.

## 5. Verification

Mutation-first per rule 11 — the mutations were written to a scratchpad file
**before** any test or production line was changed, and are reproduced in the PR
body. The one that matters: **throw a `LinkageError` (not an `Exception`) on each
path and assert the app survives.** Each is shown failing on unfixed code first;
`Reproduced:` in the PR body is that run, not a claim.

Gate: `./gradlew test :app:assembleTranzlateProdDebug :app:assembleTranzlateFakeDebug spotlessCheck detekt verifyStringKeyDocs`.

## 6. Left for #242, declared rather than silently changed

#242 records that `savedCountOf`'s `CancellationException` rethrow arm is entered by
no test, and that the fixture cannot express it.

This PR changes that function's **second** arm (`Exception` → `Throwable`).
`CancellationException` is caught by the **first** arm and rethrown, so the rethrow
arm's behaviour is untouched. It remains untested. The fixture work
(`MutableSharedFlow` target + a call counter) belongs to #242 and is not done here.
