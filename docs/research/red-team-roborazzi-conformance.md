# Red-team record — Roborazzi visual-diff mechanism (PR #337, rule 8)

**Red-team-verdict: ADOPT-WITH-CONDITIONS, model=claude-sonnet-5**
Posted: https://github.com/sendtodilanka/tranzlate-app/pull/337#issuecomment-5204309215
Date: 2026-08-06.

Method: read the plugin source, the pinned dependency's actual upstream source at its exact
pinned tag (never the PR's description of it), ran the mechanism with a forced fresh execution,
and opened the committed golden PNGs as pixels (not `git diff`). Everything sourced to a command
or file:line (rule 12).

## What SURVIVES (verified, not assumed)
- **A. `check`→`build` wiring is real** — `ComposeTestConventionPlugin.kt:142-159` wires
  `verifyRoborazzi<Variant>` onto `check`, debug-filtered. Ran `:feature:language:verifyRoborazziDebug
  --rerun-tasks` → BUILD SUCCESSFUL, 78/78 executed (not cached) — a real comparison.
- **B. Server-side tier is real** — `gh api .../branches/main/protection` → `required_status_checks
  {strict:true, contexts:["build"]}`, `enforce_admins:true`. Genuinely not reducible to a client promise.
- **C. `preflight`/`build` split is intentional + accurately cited** — Roborazzi.kt:45 at tag 1.70.0 is
  exactly `if (!roborazziOptions.taskType.isEnabled()) return`; `preflight` (plain `test`) gives zero
  Roborazzi signal by design.

## BLOCKING before #337 merges
### 1. The golden locks the WRONG (broken) state — highest priority, CONFIRMED LIVE
The committed `ManagePacksTwoPane_expanded_w1280dp_h800dp.png` is **pixel-identical to the SHIPPED,
pre-fix 20d** (avatar+name only, "Usage" header, duplicated storage card — `git show
main:OfflineLanguagesScreen.kt` lines 678-734 = the pre-fix `ManagePacksDetailPane`; matches PR #338's
own "Before" description). The incident that commissioned this mechanism is being canonized as "correct"
by its own PoC golden. Nothing enforces "record a golden only after a conformance co-verify" — that rule
lived only in an uncommitted scratchpad.
→ **FIX: drop the two-pane golden from #337; ship only the compact golden (verified conformant, visually
plain). The two-pane (20d) golden lands with #338, recorded against the FIXED + conformance-verified 20d.**

### 2. The cross-OS threshold claim is FALSE (mis-cited premise in a rigor mechanism)
PR body: "I use Roborazzi's **default** `changeThreshold` (1%)." Actual, at pinned 1.70.0:
`RoborazziOptions()` → `CompareOptions()` → `DefaultResultValidator = ThresholdValidator(0F)` — the
default is **0%**, not 1%. The cited authority roborazzi#180 is titled "Default Should Be 0" and its
thread is users hitting CI-only failures **at threshold 0 on identical macOS-record/Linux-verify setups**;
the mitigation people used was "record in CI too," never "raise the threshold." No `changeThreshold` is
configured anywhere in the PR (grep: 0 hits).
→ **FIX: make a deliberate, documented threshold decision** (an explicit non-zero value to absorb cross-OS
font-delta, OR 0 + a record-on-Linux path). Do not ship an assumed default described as something it isn't.
Roborazzi here is the REGRESSION lock; initial design-conformance is the cross-model co-verify's job.

## NEEDS-CHANGE — not blocking #337 alone, but must land before more goldens accrue (#338's)
### 3. Coverage (2 of 25) + re-record laundering + lens blindness
- Coverage: `git grep -lE "captureRoboImage|RoborazziRule"` → 1 file, 2 `@Test`s vs 25 frame-groups. One
  of the two is the broken golden above. Nothing floors/counts coverage.
- `co-verify-lens.md` has **zero** golden/PNG/Roborazzi awareness; its prescribed `git diff` step renders a
  PNG as `GIT binary patch` — a lens literally cannot see what a golden shows, and nothing tells it to `Read`
  the PNG. `recordRoborazzi` is the only correction path and is undifferentiated: a legit re-record and a
  regression-laundering re-record are the identical command + identical (invisible) diff.
→ **FIX (small, in #337): one line in `co-verify-lens.md` — a golden PNG add/change MUST be opened with
  `Read` and visually compared to the SSOT frame, because `git diff` cannot show it.** Coverage-visibility
  (a CI-printed, twice-enumerated count) is a tracked follow-up.

## Non-blocking follow-ups
4. **CLAUDE.md rule 6** "what a green `preflight` does NOT mean" list needs a 4th bullet: screenshot/design-
   conformance locks are checked only by `build`, never `preflight`.
5. **Systemic (pre-existing, repo-wide):** no `required_pull_request_reviews`, no `CODEOWNERS` → `.github/
   workflows/ci.yml` (which defines what "build" checks) is editable by the same PR it gates. Caps how much
   "build is required" alone should buy. Not introduced by #337; recorded, not blocking.

## Explicitly NOT recommended
A new merge-blocking hook that greps PR bodies for a "golden-conformance" marker — that is one more presence
check (like `Enumerated by:`/`Call sites:`) whose documented limit is that it catches "absent," never "false."
The real defense is a real re-verification (open the pixels, compare to the frame) by a non-author reviewer,
recorded as a citation — not a script (per `red-team-gate-enforcement.md`).

## Corroboration
An earlier cross-model co-verify (BLOCK) independently found the same root compile failure
(`ManagePacksScreenshotTest.kt:68`, the #334-dropped `nowMillis`) via a different method — two independent
paths on one cause (rule 12). It did not test golden-validity-vs-frame or the threshold claim; those are this
pass's additions.
