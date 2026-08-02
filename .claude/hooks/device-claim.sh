#!/usr/bin/env bash
# PreToolUse/Bash advisory for commands that DRIVE an emulator.
#
# Exists because two agents were put on one emulator at least THREE times in a
# session (#185, #187, #194) and it cost real evidence more than once. The count
# said "twice" here until 2026-08-02; #185 is chronologically the first and its
# own comment says so. A hook whose rationale undercounts its own incident is
# the fourth cause (verify documents against code) wearing the coat of the rule
# it enforces — so the number is now stated with the issues that back it.
#
#   - #187's lens could not attribute any capture to a build. The package's
#     lastUpdateTime kept changing and the screen navigated to states it had
#     not tapped toward. It reported "verified data නෑ" rather than guess,
#     which was correct and which the contention made necessary.
#   - #194's fix agent found `wm user-rotation lock 1` events it never issued
#     and a search box containing text it never typed. Before noticing, it had
#     installed a FAULT-INJECTED build over the device and run `pm clear`,
#     destroying the other agent's state mid-run.
#
# The session owner also compared a `fake` build against a `prod` build and
# reported the difference to the owner as a timing race — two builds, not two
# timings — because nothing recorded which build a capture came from.
#
# WHY THIS WARNS AND DOES NOT DENY, stated plainly rather than left as a gap:
# the hook sees a command, not a caller. It cannot tell the legitimate holder
# from an intruder, so a deny would block the holder as readily as the second
# agent — and a guard that blocks correct work gets switched off. What it CAN
# do is make the contention VISIBLE, which is exactly what was missing: `adb`
# serves both callers happily, nothing errors, and the symptom is a screenshot
# that disagrees with the code. That reads as a bug in the code, which is what
# wasted the time.
#
# Claim:   printf '%s\n' "<who> <why>" > .claude/device-claim
# Release: rm -f .claude/device-claim
#
# WHY THIS FIRES WHEN THERE IS NO CLAIM (added 2026-08-02, and the reason is the
# point): until today this hook returned silently unless a claim file already
# existed — and NOTHING in the repo ever created one. The protocol was written
# in this comment block, inside the hook, which no agent reads. So the first
# agent never claimed, and therefore the second agent was never warned: the hook
# could not fire, and had not, since the day it was written. It was checked by
# piping a payload at it, seeing a well-formed message, and never asking what
# had to be true for that message to be reached. That is rule 12 exactly — a
# presence check standing in for a behaviour check — committed while building
# the mechanism for rule 12. An unclaimed device now gets a short nudge naming
# the claim command, which is the only way the protocol teaches itself at the
# one moment it matters.
#
# AND WHY A BARE `adb` IS THE LOUDER CASE: a real phone sits on wireless adb
# beside the emulators. `adb shell …` with no `-s` targets whatever single
# device is attached — with one emulator up it silently drives the emulator,
# with only the phone up it silently drives the PHONE. Both read identically in
# the transcript. Until today this hook gave `adb shell` and
# `adb -s emulator-5556 shell` the same message, so the one rule that protects
# the owner's own handset was the one thing it did not check.
#
# KNOWN LIMIT (#207 lens): `head -c 300` follows a symlink, so a claim file that
# is a link surfaces the target's first line in the warning. Low severity —
# planting one needs the same `.claude/` write access a legitimate claim does,
# so there is no escalation — but it is disclosure, and it is named here rather
# than discovered later.
#
# FAIL OPEN, like every guard here: it NEVER blocks the tool call. Every path
# either warns or says nothing.
#
# Silent, enumerated from the code rather than from memory — the `exit 0`s
# before any message: no `jq` or a malformed payload (:75), an empty command
# (:76), a command with no `adb` (:80) or no driving verb (:81), and anything
# outside a git repo (:83, :85). Then, past the message branches: a claim file
# whose first line is EMPTY (:137-138) — verified with both a zero-byte file and
# a blank-lines one. Note the consequence, since the nudge tells the reader to
# create this file: `touch .claude/device-claim` silences the hook completely —
# no claim message and no nudge. Naming it here because the natural mistake and
# the total-silence path are the same keystroke.
#
# NOT silent, and this is where the comment was wrong TWICE:
#   - "no claim file"      — was the whole defect (#213); now the nudge at :119.
#   - "an unreadable one"  — `[ ! -r "$claim" ]` at :119 catches missing AND
#                            unreadable in ONE branch, so a `chmod 000` claim
#                            file produces the same nudge. Verified live.
#
# The #214 lens caught the first clause. I fixed that clause and left the one
# beside it in the same sentence unchecked — the identical code path, one
# comma away. It found that too, on re-attack. The lesson is not "be careful
# with comments": it is that a correction scoped to exactly what you were told
# is the same narrow search that caused the original error. This block is now
# derived from `grep -n "exit 0"` and a live run of each branch.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

# Only commands that DRIVE a device. `adb devices` and `adb -l` list; listing is
# not driving, and warning on it would train the reader to ignore the message.
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])adb([[:space:]]|$)' || exit 0
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])adb[^;&|]*[[:space:]](shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot|reboot|kill-server|start-server|tcpip|usb|connect|disconnect|sideload|exec-out|bugreport|remount)([[:space:]]|$)' || exit 0

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
# Worktrees share a repo but not a checkout; the claim lives with the main one.
common=$(git rev-parse --git-common-dir 2>/dev/null) || exit 0
claim="$(dirname -- "$common")/.claude/device-claim"

# Is a device named? `-s <serial>` or `--serial`, or ANDROID_SERIAL in the same
# command. Anything else lets adb pick, and adb picks silently.
#
# Only the text BETWEEN `adb` and its subcommand counts. Searching the whole
# command would read `adb shell rm -s foo` as targeted — the `-s` there belongs
# to `rm`, on the device, and adb never saw it. Global options precede the verb;
# that is the only place a serial can legally appear.
# The second expression anchors on `(^|space)` and not on `space` alone: after
# the first strips `adb `, the verb is at position 0 for the common
# `adb shell …`, so a leading-space-only match never fires and the whole device
# side of the command stays in the prefix. That bug read `adb shell rm -s foo`
# as targeted on the first run of this test.
prefix=$(printf '%s' "$cmd" | sed -E 's/.*(^|[;&|[:space:]])adb[[:space:]]+//; s/(^|[[:space:]])(shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot|reboot|kill-server|start-server|tcpip|usb|connect|disconnect|sideload|exec-out|bugreport|remount)([[:space:]].*|$)//')
targeted=no
if printf '%s' "$prefix" | grep -qE '(^|[[:space:]])(-s([[:space:]]|$)|--serial)'; then
  targeted=yes
elif printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])ANDROID_SERIAL='; then
  targeted=yes
fi

if [ "$targeted" = no ]; then
  jq -nc --arg m "adb with no -s: this command targets whatever single device is
attached, and adb chooses without saying so.

A real phone is on wireless adb beside the emulators. One emulator up and this
drives the emulator; only the phone up and this drives the OWNER'S PHONE. The
transcript looks the same either way.

  adb devices                        # see what is actually attached
  adb -s emulator-5554 shell …       # name the one you mean, every time

Only Resizable_Experimental and Tranzlate_API24/28/29 may be used, and never
create an AVD — disk space is the reason the others were deleted." \
    '{systemMessage:$m}'
  exit 0
fi

if [ ! -r "$claim" ]; then
  # No claim, and nothing in the repo creates one unprompted — see the header.
  # This nudge is the only thing that ever teaches the protocol.
  jq -nc --arg m "No device claim recorded. If another agent is measuring on this
device, nothing will error and both of you will read each other's state.

  printf '%s\\n' '<who> <why>' > .claude/device-claim   # claim it
  rm -f .claude/device-claim                            # release when done" \
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
