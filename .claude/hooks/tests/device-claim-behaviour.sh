#!/usr/bin/env bash
# Behaviour test for `device-claim.sh`.
#
# ── WHY THIS FILE EXISTS ──────────────────────────────────────────────────────
#
# #213: this hook had never fired and could not have. It was "verified" by piping
# a payload at it with a claim file the test itself planted, and nobody asked
# what had to be true in the real repo to reach that branch. The check lived in a
# transcript, so nothing re-ran it and nothing noticed.
#
# So this file asserts the two things that transcript could not:
#   1. every DRIVING form reaches the claim question, including the
#      path-qualified ones (#223) that the old word-boundary gate could not see;
#   2. every non-driving form stays silent, because a guard that cries on
#      `adb devices` is a guard people learn to ignore.
#
# It cannot test the `if:` gate in `.claude/settings.json` — that is matched by
# the harness BEFORE this hook runs, so it has to be checked by typing a real
# command at the real tool. That limit is named here rather than papered over,
# because it is where #223 actually lived: the regex and the gate rejected the
# same commands for the same reason, and fixing only one would have changed
# nothing. `settings.json` must keep `if: "Bash(*adb *)"`, asserted below.
#
#   .claude/hooks/tests/device-claim-behaviour.sh
#
# Exit 0 = pass. Exit 1 = a real finding. Needs bash, git, jq.
set -uo pipefail

here=$(cd -- "$(dirname -- "$0")" && pwd)
HOOK=${DEVICE_CLAIM_HOOK:-$here/../device-claim.sh}
SETTINGS=${DEVICE_CLAIM_SETTINGS:-$here/../../settings.json}
TMP=$(mktemp -d "${TMPDIR:-/tmp}/device-claim-behaviour.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT

pass=0; fail=0
ok()  { pass=$((pass+1)); }
bad() { fail=$((fail+1)); printf '  FAIL  %s\n' "$1"; }

for tool in git jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "SKIP: no $tool on PATH"; exit 0; }
done
[ -r "$HOOK" ] || { echo "SKIP: hook not readable at $HOOK"; exit 0; }

# A throwaway repo, so the claim file is ours and never the real one — planting a
# claim in the actual repo would mislead an agent genuinely driving a device.
REPO="$TMP/repo"; mkdir -p "$REPO/.claude"
( cd "$REPO" && git init -q . && git config user.email t@example.invalid \
  && git config user.name t && git commit -qm init --allow-empty ) >/dev/null 2>&1 \
  || { echo "SKIP: could not build the fixture repo"; exit 0; }
CLAIM="$REPO/.claude/device-claim"

kind() { # kind <command> -> silent | nudge | held | held+stale | untargeted | other
  local out
  out=$(cd "$REPO" && printf '%s' "$1" | jq -Rc '{tool_input:{command:.}}' | bash "$HOOK" 2>/dev/null)
  case "$out" in
    "")                  echo silent ;;
    *"No device claim"*) echo nudge ;;
    *STALE*)             echo held+stale ;;
    *"Device claimed"*)  echo held ;;
    *"adb with no -s"*)  echo untargeted ;;
    *)                   echo other ;;
  esac
}
check() { # check <expected> <command>
  local got; got=$(kind "$2")
  if [ "$got" = "$1" ]; then ok; else bad "want $1, got $got: $2"; fi
}

printf '=== 1. DRIVING commands reach the claim question ===\n'
rm -f "$CLAIM"
while IFS='|' read -r want cmd; do
  case "$want" in ''|'#'*) continue ;; esac
  check "${want// /}" "${cmd/#" "/}"
done <<'TABLE'
# bare adb — the form that always worked
nudge  | adb -s emulator-5554 shell ls
nudge  | adb shell ls
# #223: a path in front of adb. `/` is not a shell-word boundary, so the old
# gate could not see any of these.
nudge  | $ANDROID_SDK_ROOT/platform-tools/adb shell ls
nudge  | /Users/someone/Library/Android/sdk/platform-tools/adb shell ls
nudge  | ./platform-tools/adb -s emulator-5554 install app.apk
nudge  | /opt/sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p
nudge  | cd /tmp && $ANDROID_SDK_ROOT/platform-tools/adb -s emulator-5554 screencap -p /sdcard/s.png
nudge  | ANDROID_SERIAL=emulator-5554 adb shell ls
TABLE

printf '\n=== 2. NON-driving commands stay silent ===\n'
while IFS='|' read -r want cmd; do
  case "$want" in ''|'#'*) continue ;; esac
  check "${want// /}" "${cmd/#" "/}"
done <<'TABLE'
# listing is not driving
silent | adb devices
silent | adb -l
# must not match a word merely ENDING in adb, or CONTAINING it
silent | gradlew adbtest
silent | fooadb shell ls
silent | /usr/bin/adb-fake shell ls
silent | echo /usr/bin/adb
silent | echo "run adb later"
# degenerate
silent | adb
silent | git status
TABLE

printf '\n=== 3. The claim file decides which message ===\n'
rm -f "$CLAIM"
check nudge '$ANDROID_SDK_ROOT/platform-tools/adb shell ls'
printf 'agent-A measuring #222\n' > "$CLAIM"
check held  '$ANDROID_SDK_ROOT/platform-tools/adb shell ls'
check held  'adb -s emulator-5554 shell ls'
# The untargeted branch is RETIRED — hookify.adb-untargeted is authoritative for
# targeting. Before that, an untargeted adb returned early and never revealed
# that someone else held the device, which is this hook's entire job.
check held  'adb shell input tap 1 1'
: > "$CLAIM";            check silent 'adb -s emulator-5554 shell ls'   # empty first line
printf '\n\n' > "$CLAIM"; check silent 'adb -s emulator-5554 shell ls'
printf 'agent-A\n' > "$CLAIM"; chmod 000 "$CLAIM"
check nudge 'adb -s emulator-5554 shell ls'                             # unreadable == missing
chmod 644 "$CLAIM"
printf 'agent-A\n' > "$CLAIM"; touch -t 202001010000 "$CLAIM"
check held+stale 'adb -s emulator-5554 shell ls'
rm -f "$CLAIM"

printf '\n=== 4. FAIL OPEN ===\n'
for probe in '{"tool_input":{"comma' '{"tool_input":{}}' '{}'; do
  out=$(cd "$REPO" && printf '%s' "$probe" | bash "$HOOK" 2>/dev/null); rc=$?
  { [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open on payload: $probe (rc=$rc)"
done
out=$(cd "$TMP" && printf 'adb -s emulator-5554 shell ls' | jq -Rc '{tool_input:{command:.}}' | bash "$HOOK" 2>/dev/null); rc=$?
{ [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open outside a git repo (rc=$rc)"
out=$(cd "$REPO" && printf 'adb -s emulator-5554 shell ls' | jq -Rc '{tool_input:{command:.}}' | PATH=/nonexistent /bin/bash "$HOOK" 2>/dev/null); rc=$?
{ [ -z "$out" ] && [ "$rc" -eq 0 ]; } && ok || bad "not fail-open with an empty PATH (rc=$rc)"

printf '\n=== 5. THE WIRING — the gate must still deliver path-qualified adb ===\n'
# Not a behaviour test: `if:` is matched before this hook runs, so this only
# asserts the setting has not been narrowed back. `Bash(adb *)` would silently
# undo #223 without changing one line of the hook.
if [ -r "$SETTINGS" ]; then
  if grep -q '"if": "Bash(\*adb \*)"' "$SETTINGS"; then ok
  else bad "settings.json no longer gates device-claim.sh with Bash(*adb *) — #223 regresses invisibly"
  fi
else
  printf '        n/a  settings.json not readable at %s\n' "$SETTINGS"
fi

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
