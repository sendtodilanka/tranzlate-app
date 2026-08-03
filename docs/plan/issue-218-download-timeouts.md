# Plan — issues #218 / #237: bound every `Task.await()` in the offline-model manager

status: accepted
(accepted basis: both issues carry measured evidence and a settled fix
direction. #218's own comment names the strongest option — *"Checking
connectivity before starting turns an unbounded wait into sheet 19d, which
already exists"* — and #237 states its own honest limit. Nothing here is an open
design question the owner has to answer; the one judgement call, the timeout
value, is derived below from measurement rather than chosen.)

## The one defect, in two places

`RealOfflineModelManager` reaches Play Services through three `Task.await()`
calls and **none of them is bounded**:

| call | what it does | issue |
|------|--------------|-------|
| `RemoteModelManager.download(...).await()` | the pack transfer | **#218** |
| `RemoteModelManager.deleteDownloadedModel(...).await()` | remove a pack | **#237** |
| `RemoteModelManager.getDownloadedModels(...).await()` | read what is on disk | **#237** (the `finally`'s second unbounded wait) |

An `await()` that never resolves is not a slow path — it is a coroutine parked
forever. Everything written *below* it to handle failure is therefore dead code:
`download()`'s `catch` blocks are reached only when the Task *fails*, and a Task
that never settles never fails. The row keeps the state it was put into, and
`EDGE_CASES.md` §7's no-dead-end rule is broken in the strongest possible way —
not a state that guides nowhere, but a state that cannot be left.

## #218 — reproduced on this branch before any code was written

Genuine airplane mode on `emulator-5554`, verified rather than assumed (the
unrooted broadcast silently no-ops):

```
$ adb -s emulator-5554 shell settings get global airplane_mode_on   → 1
$ adb -s emulator-5554 shell dumpsys connectivity | grep -c "NetworkAgentInfo{" → 0
$ adb -s emulator-5554 shell ping -c 2 -W 2 8.8.8.8   → connect: Network is unreachable
```

The mechanism, dumped from the device on this branch:

```
JOB #u0a105/15: DownloadManager:com.android.providers.downloads
  Source: uid=u0a229 pkg=com.codeboxlk.tranzlate.offlinetranslator
  Required constraints:    CONNECTIVITY FLEXIBILITY
  Satisfied constraints:   FLEXIBILITY DEVICE_NOT_DOZING BACKGROUND_NOT_RESTRICTED WITHIN_QUOTA
```

The transfer is a system `DownloadManager` `JobScheduler` job on our uid, gated
on `CONNECTIVITY` and **event-driven, not timed**. Airplane mode never sends the
broadcast that would satisfy it, so the job is not slow — it is parked, and
nothing in the chain will ever time it out.

A second thing this run found, not in either issue: **the mobile-data consent
sheet (19a) opens when there is no radio at all.** `isActiveNetworkMetered()`
answers `true` when there is no active network — AOSP's documented "best guess"
— so the app asks *"Download over mobile data? Your plan may charge for it"* on
a device that has no data connection to charge. Filed separately; the
connectivity pre-flight below does not fix it, because the gate runs first.

## #237 — the same shape, a worse exit

`delete()` sets `Deleting`, launches, and `join()`s. The transient is cleared
only in the job's `finally`, which cannot run while `store.delete(tag)` is
unresolved. The screen renders `Deleting` as a bare `CircularProgressIndicator`
with **nothing tappable** (`OfflineLanguagesScreen.kt:294-299`), `transient`
lives in a `@Singleton` so leaving and returning shows the same spinner, and
`confirmRemove` launches on `appScope` so the coroutine is pinned for the
process lifetime.

**Honest limit, carried from the issue and not softened here: no reproduction
exists.** `deleteDownloadedModel` has no connectivity gate, so #218's trigger
does not apply to it. What was attempted, and what each attempt showed, is
recorded in `docs/research/issue-237-delete-hang.md` rather than asserted here.
This plan does **not** claim the delete hangs. It claims something weaker and
sufficient: **nothing bounds the call, and the state it leaves behind has no
exit.** Both halves are read directly off the source.

## What ships

### 1. A bounded wait on all three `Task.await()` calls

On the **seam**, not inside `MlKitModelStore`: every suspending call the manager
makes into `ModelStore` goes through one `bounded(...)` helper. `ModelStore` is
an interface with more than one implementation, so a bound placed inside the ML
Kit adapter would hold for that one only and would have to be remembered again
by the next. It also puts the timeout on the side of the file's own boundary
where tests driving a fake store can reach it — which is not a detail: the trap
below is an interaction between the timeout and the manager's `catch` ordering,
and a bound inside the adapter would be untestable exactly where it matters.

**A timeout must not surface as a cancellation.**
`TimeoutCancellationException` *is* a `CancellationException`, and
`RealOfflineModelManager.download()` catches cancellation FIRST and rethrows it
(that catch means "the user pressed Stop"). A bare `withTimeout` would therefore
be read as a Stop, the row would never reach `Failed`, and the bound would look
correct while changing nothing. The seam converts a timeout to an `IOException`,
which the existing `toFailure()` already maps to `OfflineModelFailure.NETWORK` —
the cause that raises sheet 19d.

### 2. Two constants, not one — and why one number cannot serve

The brief asks for the timeout "stated once as a named constant with its
reasoning". It is stated once *per kind of call*, because the three calls are
two different kinds of thing and a single number is provably wrong for one of
them:

- a **46 MB network transfer** needs tens of minutes on a poor connection;
- a **local file delete** and a **local disk query** should settle in
  milliseconds.

A single transfer-sized number would leave #237's exitless spinner pinned for
half an hour, which does not fix #237. A single local-sized number would kill
every genuine slow download. So: one constant for the transfer, one for the
local operations, both in one place with the arithmetic that produced them.

**`MODEL_TRANSFER_TIMEOUT_MILLIS = 1_800_000` (30 minutes).** Derived, not
picked:

- Largest pack measured on device is **45.7 MB** (#219 — the same issue records
  that four user-facing strings state the size and two are wrong, so the budget
  is taken against 50 MB rather than the measured figure).
- The slowest link this is willing to support is **256 kbit/s**, the ITU/OECD
  broadband floor — below every modern mobile bearer.
- `50 MB ÷ 256 kbit/s` = 52,428,800 B ÷ 32,000 B/s = **1638 s ≈ 27.3 min** → 30
  minutes.
- Read the other way, the number states its own promise: at 30 minutes a 50 MB
  pack needs 29.1 kB/s = **233 kbit/s**, so anything at or above that finishes
  rather than being cut off.

Thirty minutes is deliberately generous, because **the timeout is the backstop,
not the user experience.** The pre-flight below answers #218's actual scenario
in milliseconds; the timeout only catches the case the `CONNECTIVITY` constraint
cannot — a connection that exists and then stalls. ML Kit reports no progress at
all, so "slow" and "stalled" are indistinguishable to us, and the asymmetry is
plain: cutting off a real download destroys work the user waited for, while
waiting too long costs a spinner the user can always dismiss with the ✕.

**`MODEL_LOCAL_TIMEOUT_MILLIS = 30_000` (30 seconds).** These calls do no
network work, but they are not free either: each is an IPC round-trip into Play
Services, which can be cold-starting or updating.

Measured on device, with the measurement's own limit stated beside it rather
than rounded away: a real delete finished **inside a single observation window
in every run** — with the radio off, and with Play Services force-stopped
immediately beforehand. The observation method is a `uiautomator` dump whose
round-trip floor was measured at **2.03 s** (five samples:
2.07/2.04/2.06/2.07/2.03), so what this establishes is an **upper bound of
~2.1 s**, not a finer figure, and it is not written as one. Thirty seconds is
roughly fourteen times that bound — long enough that a busy device is never cut
off, short enough that a spinner with no control on it is a spinner and not a
dead end. The house already bounds a Play-Services-family Task this way at 8 s
(`RemoteConfigDefaults.FIRST_FETCH_TIMEOUT_MS`) on a path a UI tap sits on; this
one is not on a tap's critical path, so it can afford to be kinder.

### 3. The connectivity pre-flight — the higher-value half of #218

`ConnectivityMonitor.isOnline()` already exists and is already the app's
synchronous pre-flight seam: the translation waterfall pre-flights its online
tiers against it, and `DownloadGate` reads the same monitor's `isMetered()` for
the #209/PR-17 consent gate. **No second seam is added.** The manager takes the
monitor it did not previously have and asks the question one layer lower, so
every caller is covered — the gate's `requestDownload`, its `downloadConsented`
follow-through, and any future one.

**The check is synchronous and sits with the other pre-flights**, immediately
before the free-space probe and before anything is launched. It refuses in the
same shape the storage pre-flight already refuses: write `Failed(NETWORK)`,
return, enqueue nothing. `store.download()` is never called, so no
`DownloadManager` job is ever created — verifiable on device by the absence of
the job the dump above shows.

It goes **before** the storage probe. An offline device cannot download whatever
its free space is, and of the two refusals the connectivity one is the *exact*
answer while the storage one is a 3× headroom estimate (150 MB required against
a 45.7 MB pack) that can refuse a download which would have succeeded. The
certain refusal should be the one the user is given first.

**A limitation this inherits, stated here rather than left for a lens to find.**
The picker decides whether a failure earns sheet 19d by watching the shared
state map for a *change* (`LanguagePickerViewModel.reportFailure`'s
`dropWhile { it == before }`). On a **first** attempt the row moves
`NotDownloaded → Failed(NETWORK)`, the watcher passes, and 19d opens — that is
#218's ask, and it is what this change delivers. On a **retry while still
offline** the refusal writes a value equal to the one already on the row,
`MutableStateFlow` conflates equal values, and no second sheet opens. That is
**#234**, exactly and only: a filed, owned, sibling defect in this same file,
currently scoped to `STORAGE` and being fixed on its own branch. This change
neither fixes it nor worsens it — it routes a second cause onto the same
pre-existing path, and #234's fix will cover both at once. The row is not a dead
end meanwhile: it is red, it names the cause, and it carries a Retry.

An alternative placement — the check *inside* the launched job, so the row
passes through `Downloading` first — was designed and rejected. It buys nothing:
`StateFlow` conflation means the collector is not guaranteed to observe the
intermediate `Downloading` either, so the retry case is identical, while the
first-attempt case gains a spinner flash and a `DownloadManager` job that has to
be reasoned about. It would also sit outside the reach of #234's fix.

### 4. `Deleting` ends somewhere tappable

With `store.delete()` and the `finally`'s `refreshDownloaded()` both bounded,
`delete()` always returns and the `finally` always runs. The transient clears
and the row lands on whatever the refresh says — `Downloaded` (🗑 again) or
`NotDownloaded` (⬇). Both are tappable; the no-dead-end rule is satisfied by the
state the row ENDS on, which is what the rule asks for.

**No control is added to `Deleting` itself, deliberately.** A Stop on a delete
would be a lie: ML Kit exposes no cancel, and a half-removed model is a worse
outcome than a bounded wait. A `Failed` landing was also rejected — the failed
row's only control is a Retry wired to `onDownload`, so a failed *delete* would
offer the user a *download*. The truthful refresh gives a better answer than any
new state would.

## Tests

Mutation-first: every case below was written down with the source edit that must
turn it red **before** any test code existed —
`scratchpad/mutations-issue-218-237.md`, twelve mutations, reproduced in the PR
body. The two that matter most are M3/M4 (moving the connectivity check above
`takeTransient` must break the retry-raises-the-sheet test — that is #234's bug)
and M7 (letting `TimeoutCancellationException` escape must break the
timeout-becomes-Failed test — that is the trap this change could most easily
walk into).

JVM unit tests, virtual time, against the production constants rather than
copies of them.

## Out of scope, named rather than left to be found

- **#234** (`Failed(STORAGE)` conflation) is a sibling defect in this file being
  fixed on its own branch. This branch does not touch the synchronous storage
  pre-flight and adds no new instance of the conflation.
- **#219** (the pack size stated in four places, two wrong) supplies the 45.7 MB
  input above and is otherwise untouched.
- **19a opening with no radio** — found by this branch's reproduction, filed
  separately, not fixed here.
