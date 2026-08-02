---
name: block-adb-without-serial
enabled: true
event: bash
action: block
conditions:
  - field: command
    operator: regex_match
    pattern: (^|[;&|]|\s)(\S*/)?adb(?:\s+(?!-s(?:\s|$))(?!--serial)(?!-{0,2}$)[^\s;&|]+)*?\s+(?:shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot|reboot|tcpip|sideload|exec-out|bugreport|remount)(\s|$)
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

**The serial check is scoped to `adb`'s OWN options, and that is the whole point.** The
first version of this rule used a `not_contains: "-s "` over the entire command, and the
#233 co-verify lens broke it three ways: `ls -s && adb shell pm list packages`,
`echo 'use -s next time' && adb shell ls`, and `touch '/tmp/notes-s .txt'; adb shell ls`
all went **unblocked** — a `-s` belonging to some other program on the same line read as
"this adb names a device". Whether the owner's phone was protected came down to incidental
whitespace elsewhere in the line.

Now only the tokens **between `adb` and its subcommand** count. Consequences worth stating:

- **`adb shell rm -s foo` is BLOCKED**, and that is correct. An earlier version allowed it
  as a "decoy false positive". That reasoning was wrong: the `-s` belongs to `rm`, running
  on the device, so the `adb` call itself still names no device and can still reach the
  handset. It is not a false positive; it is the exact hazard.
- `--serial` is handled inside the pattern. `ANDROID_SERIAL` stays a whole-command check
  because it is an environment assignment and legitimately appears before the command.
