#!/usr/bin/env bash
# PreToolUse/Bash advisory: IS SOMEONE ELSE ALREADY DRIVING THIS DEVICE?
#
# ── SCOPE, after the #223 split ───────────────────────────────────────────────
#
# This hook is AUTHORITATIVE for CLAIMING, and for nothing else.
#
# `hookify.adb-untargeted.local.md` is AUTHORITATIVE for TARGETING: it BLOCKS an
# `adb` that names no device, path-qualified forms included, with its serial
# check scoped to adb's own options. Until 2026-08-03 this hook also warned about
# an untargeted `adb` — a second, weaker copy of that rule, and worse, its
# untargeted branch returned early, so a bare `adb` never even reached the claim
# check. That branch is retired. Every driving command now gets exactly one
# question asked of it here: does somebody hold this device?
#
# ── #223: the gate could not see a path-qualified adb ─────────────────────────
#
#   $ANDROID_SDK_ROOT/platform-tools/adb shell ls        → silent, before today
#   /Users/…/Android/sdk/platform-tools/adb shell ls     → silent, before today
#
# The gate required `adb` to follow start-of-string, `;`, `&`, `|` or whitespace,
# and a `/` is none of those. `.claude/memory/emulator-device-verification`
# documents `$ANDROID_SDK_ROOT/emulator/emulator …` for launching, so the
# qualified-path habit is already established in this project — the form most
# likely to be typed was the form with no cover. Both gates and the claim path
# now accept an optional leading path.
#
# ── WHY IT EXISTS ─────────────────────────────────────────────────────────────
#
# Two agents were put on one emulator at least THREE times in a session (#185,
# #187, #194) and it cost real evidence more than once. The count said "twice"
# here until 2026-08-02; #185 is chronologically the first and its own comment
# says so. A hook whose rationale undercounts its own incident is the fourth
# cause (verify documents against code) wearing the coat of the rule it enforces.
#
#   - #187's lens could not attribute any capture to a build. The package's
#     lastUpdateTime kept changing and the screen navigated to states it had not
#     tapped toward. It reported "verified data නෑ" rather than guess, which was
#     correct and which the contention made necessary.
#   - #194's fix agent found `wm user-rotation lock 1` events it never issued and
#     a search box containing text it never typed. Before noticing, it had
#     installed a FAULT-INJECTED build over the device and run `pm clear`,
#     destroying the other agent's state mid-run.
#
# The session owner also compared a `fake` build against a `prod` build and
# reported the difference to the owner as a timing race — two builds, not two
# timings — because nothing recorded which build a capture came from.
#
# ── WHY IT WARNS AND DOES NOT DENY ────────────────────────────────────────────
#
# The hook sees a command, not a caller. It cannot tell the legitimate holder
# from an intruder, so a deny would block the holder as readily as the second
# agent — and a guard that blocks correct work gets switched off. What it CAN do
# is make the contention VISIBLE, which is exactly what was missing: `adb` serves
# both callers happily, nothing errors, and the symptom is a screenshot that
# disagrees with the code. That reads as a bug in the code, which is what wasted
# the time.
#
# Claim:   printf '%s\n' "<who> <why>" > .claude/device-claim
# Release: rm -f .claude/device-claim
#
# ── WHY IT FIRES WHEN THERE IS NO CLAIM (#213, and the reason is the point) ────
#
# Until 2026-08-02 this hook returned silently unless a claim file already
# existed — and NOTHING in the repo ever created one. The protocol was written in
# this comment block, inside the hook, which no agent reads. So the first agent
# never claimed, and therefore the second was never warned: the hook could not
# fire, and had not, since the day it was written. It was checked by piping a
# payload at it, seeing a well-formed message, and never asking what had to be
# true for that message to be reached — a presence check standing in for a
# behaviour check, committed while building the mechanism for rule 12. An
# unclaimed device now gets a short nudge naming the claim command, which is the
# only way the protocol teaches itself at the one moment it matters.
#
# ── KNOWN LIMITS ──────────────────────────────────────────────────────────────
#
#   - `head -c 300` follows a symlink, so a claim file that is a link surfaces
#     the target's first line in the warning (#207 lens). Low severity — planting
#     one needs the same `.claude/` write access a legitimate claim does, so
#     there is no escalation — but it is disclosure, and it is named here rather
#     than discovered later.
#   - A shell that reaches adb without naming it — `$ADB shell …`, `eval "$cmd"`,
#     a wrapper script — is invisible to a text gate. So is an adb driven from a
#     Gradle task or an MCP tool that never goes through Bash.
#   - **THE WIRING IS PART OF THE HOOK.** `.claude/settings.json` matches its
#     `if:` against the command BEFORE this file ever runs, so widening the regex
#     here does nothing the gate does not deliver. It was `Bash(adb *)`, which
#     rejects a path-qualified adb for exactly the reason the regex did — so the
#     #223 fix was, on its own, a fix that could not fire: #213's shape, in the
#     one place nobody had looked. It is now `Bash(*adb *)`, and both halves were
#     verified by typing the real command at the real tool with the wired hooks
#     instrumented, not by piping a payload at this file. If you widen this
#     regex again, change the gate in the same commit and re-run that proof.
#
# ── FAIL OPEN, like every guard here ──────────────────────────────────────────
#
# It NEVER blocks the tool call: every path either warns or says nothing.
#
# Silent, enumerated from the code rather than from memory — every `exit 0`
# before a message, named by its CONDITION and not by its line number. The
# previous version of this block cited lines; three of the six citations were
# already stale by the end of the edit that wrote them, because a comment that
# points at a line number is invalidated by the next insertion above it. Names
# are checkable with `grep`; numbers are checkable only until someone types.
#
#   - `jq` missing, or a payload it cannot parse   → `cmd=$(… jq …) || exit 0`
#   - an empty command                             → `[ -z "$cmd" ]`
#   - no `adb` in the command                      → gate 1, the adb-present grep
#   - `adb` but no driving verb (`adb devices`)    → gate 2, the verb grep
#   - outside a git repo                           → the two `git rev-parse` calls
#   - a claim file whose FIRST LINE is empty       → `[ -n "$held" ]`
#
# That last one has a consequence worth stating, since the nudge tells the reader
# to create this file: `touch .claude/device-claim` silences the hook completely
# — no claim message and no nudge. Named here because the natural mistake and the
# total-silence path are the same keystroke.
#
# NOT silent, and this is where the comment was wrong TWICE:
#   - "no claim file"     — was the whole defect (#213); it is now the nudge.
#   - "an unreadable one" — `[ ! -r "$claim" ]` catches missing AND unreadable in
#                           ONE branch, so a `chmod 000` claim file produces the
#                           same nudge. Verified live.
#
# The #214 lens caught the first clause. I fixed that clause and left the one
# beside it in the same sentence unchecked — the identical code path, one comma
# away. It found that too, on re-attack. The lesson is not "be careful with
# comments": it is that a correction scoped to exactly what you were told is the
# same narrow search that caused the original error. This block is derived from
# `grep -n "exit 0"` and a live run of each branch.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

# Only commands that DRIVE a device. `adb devices` and `adb -l` list; listing is
# not driving, and warning on it would train the reader to ignore the message.
# `([^[:space:];&|]*/)?` is #223: an absolute, relative or $ANDROID_SDK_ROOT path
# in front of `adb` still names adb. It cannot match a word merely ENDING in adb
# (`fooadb`), because the group must end in `/` or be empty.
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])([^[:space:];&|]*/)?adb([[:space:]]|$)' || exit 0
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])([^[:space:];&|]*/)?adb[^;&|]*[[:space:]](shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot|reboot|kill-server|start-server|tcpip|usb|connect|disconnect|sideload|exec-out|bugreport|remount)([[:space:]]|$)' || exit 0

# Not in a work tree → nothing to claim against. (The result is deliberately not
# captured: it was an unused variable here since the file was written, which is
# the shape shellcheck exists to catch.)
git rev-parse --show-toplevel >/dev/null 2>&1 || exit 0
# Worktrees share a repo but not a checkout; the claim lives with the main one.
common=$(git rev-parse --git-common-dir 2>/dev/null) || exit 0
claim="$(dirname -- "$common")/.claude/device-claim"

if [ ! -r "$claim" ]; then
  # No claim, and nothing in the repo creates one unprompted — see the header.
  # This nudge is the only thing that ever teaches the protocol.
  jq -nc --arg m "No device claim recorded. If another agent is measuring on this
device, nothing will error and both of you will read each other's state.

  printf '%s\\n' '<who> <why>' > .claude/device-claim   # claim it
  rm -f .claude/device-claim                            # release when done

Only Resizable_Experimental and Tranzlate_API24/28/29 may be used, and never
create an AVD — disk space is the reason the others were deleted." \
    '{systemMessage:$m}'
  exit 0
fi

held=$(head -c 300 -- "$claim" 2>/dev/null | tr -d '\r' | head -1) || exit 0
[ -n "$held" ] || exit 0

# A claim older than 6 hours is almost certainly a crashed agent's leftover.
# Warning forever on a dead claim is how a signal becomes noise.
if [ -n "$(find "$claim" -mmin +360 2>/dev/null)" ]; then
  held="$held  (STALE — claimed over 6h ago; delete .claude/device-claim if that agent is gone)"
fi

jq -nc --arg m "Device claimed: ${held}

If that is you, carry on. If it is not, STOP — you are about to drive a device
someone else is measuring on, and nothing will error when you do.

This cost two pieces of evidence in one session (#187, #194): adb serves both
callers, so the symptom is a screenshot that disagrees with the code.

Boot your own instead:  emulator -avd Resizable_Experimental   (comes up as 5556)
Only Resizable_Experimental and Tranzlate_API24/28/29 may be used. Never create
an AVD, and never target the physical device." \
  '{systemMessage:$m}'
exit 0
