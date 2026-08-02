---
name: block-adb-without-serial
enabled: true
event: bash
action: block
conditions:
  - field: command
    operator: regex_match
    pattern: (^|[;&|]|\s)(\S*/)?adb\s+([^;&|]*\s)?(shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot|reboot|tcpip|sideload|exec-out|bugreport|remount)(\s|$)
  - field: command
    operator: not_contains
    pattern: "-s "
  - field: command
    operator: not_contains
    pattern: --serial
  - field: command
    operator: not_contains
    pattern: ANDROID_SERIAL
---

🛑 **BLOCKED — this `adb` command names no device, and `adb` will pick one silently.**

**A real phone — the owner's own handset — sits on wireless adb beside the emulators.**

With one emulator up, an untargeted `adb` drives the emulator. With only the phone up, it
drives **the phone**. Both read identically in the transcript, and nothing errors either
way. `adb` only complains when *two or more* devices are attached; with exactly one it just
uses it.

**Always name the device:**

```
adb devices                              # see what is actually attached
adb -s emulator-5554 shell …             # name the one you mean, every time
```

**Permitted emulators only:** `Resizable_Experimental`, `Tranzlate_API24`,
`Tranzlate_API28`, `Tranzlate_API29`. **Never create an AVD** — disk space is the reason
the others were deleted.

**Claim the device before driving it** (two agents on one emulator has silently corrupted
evidence at least three times — #185, #187, #194):

```
printf '%s\n' "<who> <why>" > .claude/device-claim    # claim
rm -f .claude/device-claim                            # release
```

---

**Why blocked and not warned:** `device-claim.sh` already warns on the bare form, but an
`adb` invoked by absolute or `$ANDROID_SDK_ROOT` path **slips its gate entirely** (issue
#223) — its pattern requires `adb` to follow a shell-word boundary, and `/` is not one.
This rule matches the path-qualified form too.
