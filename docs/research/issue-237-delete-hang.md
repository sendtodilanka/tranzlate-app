# Research — issue #237: can `deleteDownloadedModel` actually hang?

status: read-only investigation · **result: NOT REPRODUCED**
device: `emulator-5554` (claimed exclusively, `.claude/device-claim`)
build: prod-debug from `fix/issue-218-download-timeouts` base `5cbcfe8`,
APK md5 `1781a50d21d59f8352c0bdb90683741f`, installed 2026-08-03 06:28:21
(recorded because two captures were once compared across two different
flavours — CLAUDE.md rule 12, third shape)

## Why this record exists

Rule 4: an unknown root cause gets a read-only research record before a fix,
with every hypothesis paired with an experiment that would prove it wrong.

#237 states its own limit plainly and this investigation agrees with it:

> **It is not proved that `deleteDownloadedModel` hangs.** Unlike `download()`,
> it has no connectivity gate, so the radio-off trigger does not apply and no
> reproduction exists.

The fix that ships on this branch therefore rests on **two facts read off the
source**, not on a measured hang:

1. `RealOfflineModelManager.delete()` awaits a Play Services `Task` with nothing
   bounding it, and its `finally` awaits a second one.
2. The state it leaves behind, `Deleting`, is drawn as a bare
   `CircularProgressIndicator` with nothing tappable
   (`OfflineLanguagesScreen.kt:294-299`), lives in a `@Singleton`, and is
   launched on `appScope` — so it survives leaving the screen and is pinned for
   the process lifetime.

Both are verifiable by reading, and together they are sufficient: **nothing
bounds the call, and the state has no exit.** That is what the fix addresses.
Nothing below should be read as evidence that the delete hangs.

## What the sibling issue established, for contrast

#218's harm IS reproducible, and was reproduced on this branch before any code
was written: 975 s in `Downloading…`, unchanged, with the radio off. The
mechanism is a system `DownloadManager` `JobScheduler` job on our uid whose
`CONNECTIVITY` constraint is event-driven rather than timed.

The decisive confirmation is the release, not the hang: **12 s after airplane
mode was switched off the row was still `Downloading`, and 12 s after that it
read `Delete Afrikaans`.** A transfer that had produced nothing for sixteen
minutes finished in under half a minute the moment the constraint was satisfied.
It was never slow — it was gated.

`delete()` has no equivalent gate, which is exactly why the same trigger cannot
be pointed at it.

## Hypotheses and the experiments that would have disproved them

Method note, stated up front because it bounds every number below: state is read
with a `uiautomator` dump, whose round-trip floor was measured at **2.03 s**
(five samples: 2.07 / 2.04 / 2.06 / 2.07 / 2.03). "Completed within 2.1 s"
therefore means *inside a single observation* — an upper bound, not a resolved
duration. No claim here is finer than that.

### H1 — the delete needs the network, like the download does

If `deleteDownloadedModel` reached out to Play Services' servers (to release a
lease, report a deletion, check for an update), the radio-off trigger that pins
`download()` would pin it too.

**Disconfirming experiment.** Download a pack for real; enable airplane mode;
verify genuinely offline; delete.

```
NetworkAgentInfo entries: 0
ping 8.8.8.8:            Network is unreachable
result:                  row returned to "Download Afrikaans" within 2.10 s
```

**H1 disconfirmed.** The delete is a local operation and completes with no
network at all.

### H2 — the delete hangs when Play Services is not running

Every one of these calls is an IPC into `com.google.android.gms`. If the delete
were issued while that process was dead, the `Task` might never be delivered.

**Disconfirming experiment.** Re-download the pack, then
`am force-stop com.google.android.gms` immediately before confirming the remove.

```
result: row returned to "Download Afrikaans" within 2.06 s
```

**H2 disconfirmed.** Play Services restarts on demand and the Task settles
normally.

### What was NOT tried, and why it is not a gap being papered over

- **A device with no Play Services at all** (an AOSP or de-Googled image). Only
  `Resizable_Experimental` and `Tranzlate_API24/28/29` may be used and no AVD may
  be created, so this is out of reach here rather than skipped.
- **Storage failure mid-delete** (read-only volume, disk full during removal).
  No supported way to induce it on these AVDs without root-level damage to the
  emulator another session may need.
- **A `Task` that resolves neither way.** This is what the unit tests do instead,
  with a fake store that parks forever — which is the honest place to test a
  condition that could not be induced on real hardware. It proves the *handling*
  is correct, and deliberately does not claim the condition occurs.

## Conclusion

Single-hypothesis confidence is capped at 70% by rule 4, and this does not need
the cap: **there is no hypothesis being asserted.** Two plausible triggers were
tested and both came back negative, so the honest statement is that
`deleteDownloadedModel` was **not observed to hang under any condition
reachable here**, and #237's own wording is correct as filed.

What ships is not a fix for a hang. It is a bound on a wait that had none, and
an exit for a state that had none — both justified by reading the code, and
neither claiming a reproduction that does not exist.
