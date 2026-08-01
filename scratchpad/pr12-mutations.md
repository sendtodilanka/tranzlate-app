# PR-12 (#185) mutations

Rule 11: every mutation below was written down BEFORE the test that catches it
existed. Each names the exact edit and the test that must turn red.

## Round 1 — the original build (M1–M11)

Recorded in the PR body's mutation table; not repeated here.

## Round 2 — the co-verify blocking defect (M12–M15)

The lens found two things: the voice legend never becomes visible when the
device's voice answer arrives after the picker's list has been laid out, and
`LanguagePickerScreen.kt:667`'s row-height decision is unreachable by any test
in the module.

### M12 — the row-height predicate loses `!voiceMark`

The lens's own mutation, moved somewhere a test can reach it. In
`pickerRowMinHeight` (LanguagePickerModel.kt), change

```kotlin
if (!hasSupportingText && !voiceMark) Dimensions.pickerRowHeight else Dimensions.pickerRowHeightTall
```

to

```kotlin
if (!hasSupportingText) Dimensions.pickerRowHeight else Dimensions.pickerRowHeightTall
```

which collapses the voice-but-no-pack row (17a's downloading Arabic, and every
`Downloadable`/`OnlineOnly` row that has a voice) to the 56dp single-line box
and loses the mark with it.

**Must turn red:** `voiceOnlyRowStaysTheTallRow` (PickerRowHeightTest).

### M13 — the two heights are swapped

In `pickerRowMinHeight`, exchange `Dimensions.pickerRowHeight` and
`Dimensions.pickerRowHeightTall`. M12 only pins the predicate; this pins the
mapping, which is the other half of the decision the composable used to own.

**Must turn red:** `plainRowIsTheShortRow` (PickerRowHeightTest).

### M14 — the voice answer arrives late and moves the list (THE regression)

This is the defect. Put the legend back where it was: a member of the
`LazyColumn`'s own item set, counted by `railOffset`. In `pickerListPlan`
(LanguagePickerModel.kt), restore the term

```kotlin
(if (showVoiceLegend) 1 else 0) +
```

to `railOffset`, and emit `item(key = "voice_legend") { VoiceLegend() }` inside
the `LazyColumn` again.

The arithmetic half of that is what a unit test can hold: if the legend is a
list item, then the item set BELOW the anchor changes the moment the device
answers, and `railOffset` differs between the two answers.

**Must turn red:** `theVoiceAnswerAddsNoItemToTheAnchoredList` (PickerListPlanTest)
— the plan for `anyVoiceMark = false` and for `anyVoiceMark = true` must agree on
`railOffset`, because the answer arriving is not allowed to add or remove a row
from a list whose scroll position is already anchored to a key.

### M15 — the legend goes back to depending on the answer having arrived

Not an edit to production code but to the invariant M14 states: assert only
`railOffset` for one value of `anyVoiceMark` and drop the cross-answer
comparison. Recorded because the cross-answer comparison is the whole test —
a single-value assertion passes under M14.

**Must turn red:** n/a — this is the shape the test must NOT take, written down
so the test is not quietly weakened later.

## Results

Each mutation was applied to the FINAL formatted code, run, and restored.
`:feature:language:testDebugUnitTest` is 102 tests, 0 failures unmutated.

| # | Applied | Tests that went red | Result |
|---|---|---|---|
| M12 | `pickerRowMinHeight` predicate drops `!voiceMark` | `voiceOnlyRowStaysTheTallRow`, `theVoiceButNoPackRowIsTallOnlyWhereTheMarkIsDrawn` | **RED** — 102 completed, 2 failed |
| M13 | the two `Dimensions` swapped | `plainRowIsTheShortRow`, `supportingTextAloneStillMakesTheTallRow`, + both M12 tests | **RED** — 102 completed, 4 failed |
| M14 | `railOffset` counts the legend again | `theVoiceAnswerAddsNoItemToTheAnchoredList`, `emptyTargetRecentsRemoveTheHeaderEntirely`, `an unrailed list needs no All-languages header`, `the export's own 16a arithmetic` | **RED** — 102 completed, 4 failed |

M14 is the one that matters: it is the defect itself put back (the legend
returns to the anchored item set), and the named regression test catches it.
Note that M14 turning four tests red rather than one is not slack — the other
three are the same arithmetic asserted at other section shapes, which is what
makes the rail land on the right row.

Before this round, the equivalent of M12 was run by the co-verify lens against
the code as it then stood and the whole module reported BUILD SUCCESSFUL with
zero failures. That is the measurement this round exists to change.
