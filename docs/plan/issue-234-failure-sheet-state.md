# Plan — issues #234, #235, #239: the pack-failure sheet's state is wrong in three ways

status: accepted
lands as: **PR #246** — 👁 open. Co-verify **BLOCKED** it once, on a race inside the raise's own
suspension point (§3.5); fixed and re-pushed, awaiting the next pass (rule 5; the author cannot
be the lens). Tracker row: `ROADMAP.md`, "Any time" section. The number goes in when the PR is
opened, not when it merges — deferring the tick is the failure mode that broke the tracker
three times in one session.

(accepted basis: all three are owner-filed, all three carry the harm, the file:line and the
mutation-shaped evidence. #234 and #235 name their own fix direction; #239 names two
candidate answers and asks for one to be chosen and justified. This plan settles what the
issues left open: WHERE the pre-flight answer travels, which of #239's two answers ships and
why, and how each claim is pinned so the next reader cannot re-open it by accident.)

Refs: #130 PR-18 (`docs/plan/issue-130-language-rev3.md` ruling 9 — **reversed here, see §7**)
· #175 · `docs/specs/00-foundations/EDGE_CASES.md` §7 · and `Fixes: #234`, `Fixes: #235`,
`Fixes: #239`.

## 1. Why one PR

Three defects, one path: the user taps a download, it fails, and the app has to say so
honestly. They share `LanguagePickerViewModel.reportFailure`, `PackFailureSheetHost` and
`PackFailureRequest`; #235's own text says the two exits from the storage-failure state "are
both a lie and a no-op", and the no-op is #234. Fixing either alone leaves the user in the
same place.

## 2. The harm, in the order the user meets it

A device with 12 MB free. The picker's ⬇ on Spanish.

| Step | Shipped behaviour | Issue |
|---|---|---|
| tap ⬇ | 19b opens: *"There is 12 MB free on this device."* | — (correct) |
| **Manage packs** | the picker's ViewModel survives the push, so 19b is still raised | #235 |
| free 130 MB, press Back | 19b re-opens **still saying 12 MB**, bar still at 96% | #235 |
| tap **Retry** on the row | **nothing at all** — no spinner, no sheet, no toast | #234 |
| tap ⬇ on a second language while a sheet is up | the sheet **swaps** — different title, different action under the thumb | #239 |

## 3. The mechanism, and where the fix has to live

### 3.1 #234 — a conflating channel cannot carry "again"

`RealOfflineModelManager.kt:249-252` refuses before enqueueing and writes
`Failed(STORAGE)`. `OfflineModelState.Failed` is a `data class`, `transient` is a
`MutableStateFlow<Map<…>>`, and `MutableStateFlow` conflates on `equals`. A retry therefore
writes a map **equal** to the current one: no emission, `combine` does not re-run,
`reportFailure`'s `dropWhile { it == before }` never passes. Two consequences, both in the
shipped KDoc at `LanguagePickerViewModel.kt:486-496`: no second sheet, **and one coroutine
left suspended on a shared flow until the picker closes.**

Three ways to get an emission were considered and two rejected:

1. **Clear the transient, then re-set it** (the first of #234's own two suggestions).
   Rejected: the two writes go through a `combine` on another scope and a `stateIn` that
   conflates, so the intermediate value may be dropped and the final map is equal to the
   first again — a race, not a fix. It would also flash the row back to "not downloaded".
2. **Give `Failed` an attempt discriminator.** Rejected: a nonce in a domain model that
   every row's equality and every `remember` key reads, to signal something no renderer
   draws.
3. **Return the pre-flight answer from the call the caller already awaits.** Shipped.
   `RealOfflineModelManager.download` *knows* synchronously — it writes the state and
   returns — and `DownloadGate.requestDownload` is a `suspend` call the ViewModel already
   awaits. This is not the "second outcome channel" REJECT §7.8 bounces and PR-22's `PackEvents`
   is reserved for: it is a return value on an existing call, and it carries only the half
   that IS synchronous. The asynchronous half still arrives through the state map, exactly
   as ruling 9 requires.

```kotlin
sealed interface DownloadAttempt {
    data object Started : DownloadAttempt                          // watch the map
    data class Refused(val cause: OfflineModelFailure) : DownloadAttempt   // pre-flight said no
    data object Ignored : DownloadAttempt                          // not capable / already in flight
}
```

`Ignored` is not decoration: `download()` has two early returns (`!store.isCapable`,
`activeDownloads.containsKey`) that write nothing, and today each of them strands a watcher
on a flow that will never move. Naming them is what lets the ViewModel stop watching.

### 3.2 #235 — one host dismissed, the other did not

`PackFailureSheets.kt:239-244`'s `NoSpaceSheet` calls `onManagePacks` and never
`onDismiss`. In the dialog host the shell's `manageLanguagePacks` dismisses the card first,
which clears the child `ViewModelStore` and takes `raisedFailure` with it; in the nav host
`onManagePacks` is a **push**, and `rememberViewModelStoreNavEntryDecorator` clears on pop.
So `PickerDialogHost.kt:140-145`'s *"Two ways in, one behaviour"* was true of the lambda and
false of the behaviour — **and only the smaller screen was right.**

The fix is at the HOST, not in the sheet: `PackFailureSheetHost`'s NoSpace branch composes
`{ onDismiss(); onManagePacks() }`, which is the shape the Interrupted branch one case below
it already uses for Retry (`LanguagePickerScreen.kt:419-422`). `NoSpaceSheet` stays a
drawing that calls exactly what it is handed — its own test asserts that Manage packs
dismisses nothing, and that test stays green.

### 3.3 #234 and #235 are one fix between them

They are not two fixes that happen to ride together. `packFailureRequest` reads
`freeBytes()`/`totalBytes()` **at raise time**, so the figures are only ever stale because a
raise SURVIVES the user's trip to Manage packs. §3.2 stops the sheet surviving; §3.1 makes
the next Retry raise a *new* one. Together: **every 19b on screen quotes a figure read at the
moment it was raised.** Neither half delivers that alone — with only §3.2 the user comes back
to a picker whose Retry is still dead, and with only §3.1 the stale sheet is still the first
thing they meet.

And the arithmetic makes it more than a nicety. `REQUIRED_FREE_BYTES` is 150 MB; #235's user
frees 130 MB from 12 MB and has 142 MB. **The retry is still refused** — so the honest
outcome is 19b again, reading *"142 MB free"*, which tells the user their action counted and
was not yet enough. Silence tells them it did not count.

### 3.4 #239 — the decision, and what was rejected

`raisedFailure` is a single slot and `download()` starts an independent watcher per tap, so
the last failure to conclude wins — including over a sheet the user is reading, with a
different action arriving under a thumb already moving. `PackFailureSheetHost`'s KDoc claims
"the ViewModel already guarantees one request at a time", which is true and is not the
property that matters.

#239 names two coherent answers.

**Rejected — queue the later failure.** It re-arms the same harm one beat later: dismissing
Hindi's sheet pops Tamil's into the space the thumb is already travelling to, and N taps
means N modal interruptions. Worse, it re-creates #235 by design — a queued 19b quotes
figures measured before the user did anything about them, and there is no honest answer to
"how long is a queued failure still worth interrupting for". EDGE_CASES §7's requirement is
that no state is a dead end; it does not ask for every fact to arrive as a modal.

**Shipped — the sheet on screen holds the slot, and a failure that arrives while it is held
is dropped to the row.** The row is not a consolation prize here: it is where this codebase
already puts a failure the user is not owed an interruption about
(`PackFailureRequest`'s KDoc, and `PackFailureSheetRaisingTest.a failure this screen did not
ask for opens no sheet`). The dropped failure keeps its red row, its cause line and its
Retry — Retry that now *works*, because of §3.1. So the no-dead-end rule is satisfied by the
surface the user is looking at, and the interruption budget is spent on the thing they are
currently reading.

**Dropped means dropped.** The watcher reports once, at the moment it concludes; if the slot
is held then, it does not come back later. That is what kills #239's mirror case — the user
closes Hindi's sheet and nothing pops up behind it.

### 3.5 The suspension point inside the raise — co-verify BLOCK on PR #246

The first version of the claim was the CAS alone,
`raisedFailure.compareAndSet(null, packFailureRequest(id, cause))`. **Kotlin evaluates the
argument first**, so the suspending disk read ran and the slot was inspected only afterwards —
the check sat on the wrong side of the gap it was meant to cover.

The lens drove a dismiss through that gap: Hindi's 19d on screen, Tamil refused for space (so
owed nothing but its row), the user taps Close while Tamil's read is in flight, Tamil's CAS
finds the slot free **because of the dismiss's timing**, and Tamil's sheet lands on top of it.
Answer one interruption, receive another — §3.4's harm through a new door. It also inverted
§3.4's own reasoning *for this hazard*: a plain check placed **before** the read would have
closed it.

**Why no round-1 test could see it:** `TestDispatcherProvider` puts `io` on the same
`TestDispatcher` as main, and `withContext` to the dispatcher you are already on does not park.
In that fixture the disk read is not a suspension point and the window does not exist. The
reproduction needs `io` on a second scheduler — and the two-failure case needs a scheduler
**each**, so the reads can be released one at a time.

What ships is two conditions, with the user-facing rule *derived* from them rather than asserted
beside them:

```kotlin
val answeredBefore = sheetsAnswered.get()
val request = packFailureRequest(id, cause)                    // SUSPENDS
if (sheetsAnswered.get() != answeredBefore) return             // nothing answered meanwhile
raisedFailure.compareAndSet(expect = null, update = request)   // and the slot is still free
```

If a sheet was up when a failure concluded, then either it is still up when that failure's
request is ready (the CAS refuses) or it was answered in the meantime (the counter refuses).
There is no third way for it to have gone.

**A third check was written and the mutation run deleted it.** "Is the slot free right now",
asked before the read, looks like the natural first line — and every case it rejects is rejected
again by one of the two above, so no mutation could redden a test by removing it. It is gone
rather than demoted to an optimisation: a check that cannot fail a test is one the next reader
mistakes for the load-bearing one, and may then simplify a real guard against it. It had also
been **shadowing the CAS** — with it present, reverting the CAS to a plain assignment broke
nothing — which is why `a request built while another sheet won does not replace it` now exists
to isolate it.

## 4. What ships

| File | Change |
|---|---|
| `core/domain/…/OfflineModelManager.kt` | `DownloadAttempt` + `download()` returns it |
| `core/domain/…/DownloadGate.kt` | `requestDownload` returns `DownloadAttempt?` (null = the question was raised); `downloadConsented` returns `DownloadAttempt` |
| `core/translate/…/RealOfflineModelManager.kt` | answers `Ignored` / `Refused(STORAGE)` / `Started` |
| `core/translate-fake/…/FakeOfflineModelManager.kt` | answers the same three |
| `feature/language/…/LanguagePickerViewModel.kt` | `Refused` raises at once; `Started` watches; `Ignored` returns; the raise is a CAS |
| `feature/language/…/LanguagePickerScreen.kt` | `PackFailureSheetHost`'s NoSpace branch dismisses before it navigates; both KDocs corrected |
| `feature/language/…/PickerDialogHost.kt` | the "two ways in, one behaviour" comment now describes what the code does |
| tests | see §5 |
| this plan · `docs/plan/issue-130-language-rev3.md` ruling 9 · `docs/plan/ROADMAP.md` | §7 |

**No new composable and no new drawn state**, so rule 7's preview set is unchanged: 19b and
19d already ship `@PreviewLightDark` per state in `PackFailureSheets.kt`. The change is which
lambda a host composes and when a request is raised.

## 5. Tests (each aimed at a mutation decided before it — register in the PR body)

- `RealOfflineModelManagerTest` — the pre-flight answers `Refused(STORAGE)`; a second tap on
  a downloading tag answers `Ignored`; a started download answers `Started`.
- `PackFailureSheetRaisingTest` — a retry refused for the same reason **raises again**
  (this REVERSES the suite's `a retry refused for the same reason does not interrupt twice`,
  see §7); the re-raised sheet carries the space freed since; **two ids**: a second failure
  does not swap the sheet being read, and does not come back after it is closed.
- `PackFailureSheetsTest` — 19b's Manage packs clears the sheet **before** it navigates,
  asserted as an ORDER so that calling both in the wrong order still fails.
- `PackFailureSheetRaisingTest`, round 2 (§3.5) — four more, three of which need `io` on its
  own scheduler because the shared-dispatcher fixture cannot express the window at all: the
  lens's dismiss-through-the-gap; the loser landing on the dismiss that frees the winner; the
  loser landing while the winner's sheet is still up (the CAS in isolation); and `Ignored`
  starting no watcher, asserted on the manager's `subscriptionCount` because "no sheet" is
  true with and without that bug.

**Every failure test in the repo drove one language id** (seven `"hi"` in
`PackFailureSheetRaisingTest`, four `"fr"` in `LanguagePickerViewModelTest`) and that single
fixture habit is what hid #239. The new #239 tests drive two.

## 6. Deliberately out of scope

**Screen B's Retry is dead for the same reason and stays that way in this PR.** #234 says
"same for `tt_offline_retry` in Settings", and it is: `OfflineLanguagesViewModel.download`
routes the tap and watches nothing. The reason it is not fixed here is not effort — it is
that 19b's single action is **Manage packs**, and on Settings → Offline languages that is a
button to the screen the user is standing on. The accepted plan already ruled this
(`issue-130-language-rev3.md` ruling 8: *"raising it FROM that manager would be a button to
where the user already is"*), and named PR-23 as the rewrite of that screen. What Screen B
should show instead is a design question, not a bug fix; it is recorded as a finding for the
owner to file rather than decided here. **The seam this PR adds is what a later answer will
be built on** — `DownloadAttempt` reaches that ViewModel through the same gate.

## 7. Documents this PR must correct (rule 11, fourth cause)

`docs/plan/issue-130-language-rev3.md` ruling 9 states the un-reported retry as *"left as the
better behaviour rather than papered over"* and cites the test that pins it. #234 overrules
that: the argument was *"the sheet they dismissed a second ago"*, and a second ago the user
was told 12 MB free and has since had the chance to change it. Leaving the plan saying the
opposite of the code is the drift rule 11's fourth cause exists to stop, so the ruling gains
a dated reversal note pointing here — the reasoning is not deleted, it is answered.
