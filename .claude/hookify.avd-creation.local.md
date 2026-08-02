---
name: block-avd-creation
enabled: true
event: bash
action: block
pattern: (^|[;&|]|\s)(\S*/)?avdmanager\s+.*\bcreate\b
---

🛑 **BLOCKED — never create an AVD. This is a standing owner rule.**

> *"Use only Resizable (Experimental) and Tranzlate API 24, 28, 29 emulators only for
> testing. I will delete others hereafter for **saving space on laptop**."*
> — owner, 2026-08-02

**Disk space is the whole reason** the other AVDs were deleted. Creating a replacement
defeats the rule exactly, which is why this blocks rather than warns — there is no
legitimate case to let through.

**The four permitted AVDs, and nothing else:**

| | |
|---|---|
| `Resizable_Experimental` | the adaptive one — phone / unfolded / tablet |
| `Tranzlate_API24` | min-SDK floor |
| `Tranzlate_API28` | |
| `Tranzlate_API29` | |

**If none of them can do what you need, that is the finding — report it.** Say
"verified data නෑ" and name what would settle it. Do not quietly build a device to make a
claim provable.

Two things already established this way rather than faked:

- **TABLETOP posture is unreachable** on `Resizable_Experimental` —
  `hw.sensor.posture_list=1, 2, 3` is CLOSED / HALF_OPENED / OPENED only. A TABLETOP claim
  can be unit-tested and nothing more, and saying so was the correct answer.
- **The Play Store does not work** there — `com.android.vending` is a stub with no launch
  activity. Anything needing a real Play Store has no AVD, and that is stated rather than
  worked around.

`avdmanager delete` is not blocked — only `create`.
