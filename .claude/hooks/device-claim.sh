#!/usr/bin/env bash
# PreToolUse/Bash advisory for commands that DRIVE an emulator.
#
# Exists because two agents were put on `emulator-5554` twice in one session
# (#187, #194) and it cost real evidence both times:
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
# FAIL OPEN, like every guard here: no claim file, an unreadable one, a stale
# one, or a command this cannot resolve → silent. A message is always a real
# finding.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null) || exit 0
[ -z "$cmd" ] && exit 0

# Only commands that DRIVE a device. `adb devices` and `adb -l` list; listing is
# not driving, and warning on it would train the reader to ignore the message.
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])adb([[:space:]]|$)' || exit 0
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])adb[^;&|]*[[:space:]](shell|install|uninstall|emu|push|pull|logcat|screencap|uiautomator|forward|reverse|root|unroot)([[:space:]]|$)' || exit 0

root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
# Worktrees share a repo but not a checkout; the claim lives with the main one.
common=$(git rev-parse --git-common-dir 2>/dev/null) || exit 0
claim="$(dirname -- "$common")/.claude/device-claim"
[ -r "$claim" ] || exit 0

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
