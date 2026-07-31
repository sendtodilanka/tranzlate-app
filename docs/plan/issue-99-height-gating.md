# Plan — issue #99: the short-window treatments key on MEASURED height, not width

status: accepted
(accepted basis: owner bug report from his own device — OnePlus 7 Pro GM1911,
prod build with the #97 fix installed, uiautomator dump attached to the issue;
owner standing rule = debate before implementation. The debate record is the
deep-research pass summarised under "Debate record" below, whose finding is
decisive rather than balanced: the axis we were gating on **cannot** carry the
signal we need, so there is no defensible "keep it as is" side.)

## The defect

Landscape on the owner's phone is **832dp × 384dp** — MEDIUM width (expanded
starts at 840dp) and compact height. Both short-window treatments are gated on

```kotlin
val splitResultOnly: Boolean get() = expandedWidth && compactHeight
```

so this phone falls **8dp short of the expanded breakpoint** and renders the
tall-window edit face inside a card that the IME has squeezed to 40dp. The
owner's dump: card node `[90,407-2820,557]` = 150px = 40dp tall at density 600,
while the field node is `[150,437-2255,617]` = 180px = 48dp — the field
overflows the card's clip and the typed text draws below the visible area. The
user sees an empty white bar. Counter / Paste / Translate are 30px slivers.

Note what is NOT broken: #97 holds (`mInputShown=true` on rotate and at +8s,
keystrokes reach the field). This is purely a layout-gating defect.

## Debate record (deep-research pass — binding, cited)

1. **WindowSizeClass HEIGHT breakpoints are 480dp / 900dp only.** A phone in
   landscape is compact-height with OR without the IME — the height class never
   changes when the keyboard opens. Keyboard-driven adaptation therefore
   **cannot** come from WindowSizeClass. (This is why `compactHeight` had to be
   paired with `isImeVisible` in #86 in the first place: the size class could
   not see the keyboard.)
2. **Google's adaptive documentation contains no IME guidance at all.** The
   community pattern for keyboard-aware layout is `WindowInsets.ime` /
   `BoxWithConstraints` over the MEASURED available height.
3. **Industry recommendation:** take *structure* (navigation, panes, margins)
   from WindowSizeClass; take *keyboard / short-window* adaptation from
   measured, inset-aware available height.

The ruling follows directly: the two concepts that were fused into
`splitResultOnly` are different questions on different axes.

| question | axis | signal |
|---|---|---|
| "can two panes sit side by side?" | width | `expandedWidth && compactHeight` — **unchanged** |
| "does the edit face physically fit?" | measured height incl. IME | **new** |

## The design

### 1. `splitResultOnly` keeps its meaning, loses its second job

`AdaptiveLayout.splitResultOnly` stays `expandedWidth && compactHeight` and is
read by exactly ONE call site — the side-by-side READ face (ComposerScreen.kt
`if (layout.splitResultOnly && !isEditing && showsResult)`). That face genuinely
needs width: two panes at 2:3 inside 832dp would be 320/480dp minus margins.
It is not what the owner reported and it does not move.

### 2. A measured fit signal, classified into the three arrangements that
already exist

`BoxWithConstraints` wraps the composer content, so `maxHeight` is the exact
height the Scaffold hands the pane **after** `WindowInsets.safeDrawing` — which
includes the IME. That number is branch-independent (it is the pane's incoming
constraint, not the card's leftover), so gating on it cannot oscillate; gating
on the card's own height could, because hiding the top row grows the card.

The classifier is a three-value enum because the edit face already has exactly
three arrangements — the enum simply names what the two booleans encoded:

| `ComposerFit` | edit face |
|---|---|
| `FULL` | label row · field · Paste · counter+action row (the portrait face) |
| `FOLDED_CHROME` | ONE chrome row (label+counter+clear+action) · field · Paste |
| `MINIMAL` | field + counter + action in a single row; top row hidden |

`ComposerEditBody`'s parameters (`compactLandscape`, `minimalIme`) are NOT
renamed or re-shaped — the enum maps onto them. This keeps the **#97 invariant
intact by construction: `ComposerField` still renders at exactly ONE source
position**, and the diff cannot reach it.

### 3. Thresholds — summed from the tokens that build the face

Both constants are the arithmetic of the arrangement they guard, not a device
measurement, so they stay true if a phone ships with different insets.

Outside the card, every arrangement pays: top row `8+48+16 = 72dp` + bottom
spacer `16dp` = **88dp**. Card interior padding `24 top + 16 bottom` = **40dp**.

**`FULL_CHROME_MIN_HEIGHT = 376dp`** — below this the FULL face cannot give the
field a usable multi-line area:

```
 48  SourceLabelRow (the ✕ IconButton sets the row height once there is text)
+ 8  field row top padding
+96  field — 3 lines of headlineSmall (lineHeight 32sp → 32dp/line)
+40  Paste chip (empty state; M3 TextButton min height)
+56  action row (8dp padding + 48dp Dimensions.touchTargetMin button)
=248 interior  → +40 card padding +88 chrome = 376dp
```

**`FOLDED_CHROME_MIN_HEIGHT = 272dp`** — below this even the folded face stops
fitting:

```
 48  chrome row (holds the 48dp action)
+ 8  field row top padding
+48  field — one comfortable line / the touch-target floor
+40  Paste chip (empty state)
=144 interior  → +40 card padding +88 chrome = 272dp
```

The 48dp terms are `Dimensions.touchTargetMin`, the a11y floor C-14 makes
authoritative; 96dp is the only judgement call in either sum and it is the one
the owner's complaint is about ("keyboard ආවම type කරන්න ඉඩ නෑ" — a field that
holds one clipped line is the bug, not the fix).

### 4. The top row keeps its IME term

`hideTopRow = isEditing && fit.minimal && WindowInsets.isImeVisible`. The
measured term replaces `splitResultOnly`; the IME term stays for #86's explicit
requirement — dismissing the keyboard must bring back the **only** back
affordance — and because `&&` short-circuits, no window above MINIMAL
subscribes to per-frame IME inset invalidation (the #56 draw-failure suspect,
narrowed deliberately in #87).

### 5. Two-pane is pinned to FULL

`permanentTwoPane` windows skip the classifier entirely
(`if (layout.permanentTwoPane) ComposerFit.FULL else …`). A tablet sitting at
exactly the 480dp height bound with a tall IME could otherwise dip under
272dp and newly hide its top row — a tablet behaviour change this fix has no
business making.

## Regression guards (must be byte-identical to today)

| shape | today | after |
|---|---|---|
| phone portrait (any phone, IME up or down) | FULL | FULL |
| tablet / unfolded two-pane | two panes, top row always shown | unchanged (pinned FULL) |
| 914dp phone landscape, no IME | FOLDED | FOLDED |
| 914dp phone landscape, IME up | MINIMAL, top row hidden | MINIMAL, top row hidden |
| phone landscape read face (result showing) | side-by-side split | unchanged |
| **832dp phone landscape, IME up** | **FULL — the bug** | **MINIMAL** |
| 832dp phone landscape, no IME | FULL | FOLDED (intended: 384dp − insets is a short window whichever side of 840dp it falls) |

## Verification

`:feature:text:testDebugUnitTest` · `spotlessApply` · `spotlessCheck detekt
--rerun-tasks` · `:app:assembleTranzlateProdDebug`, plus a Compose preview for
the newly-reachable short-window-with-IME body (CLAUDE.md rule 7 — every
Compose UI element ships `@PreviewLightDark`), plus a measured calibration run
on `Tranzlate_Resizable` confirming the 914dp landscape band assignments above
still hold against the real numbers.

## Calibration outcome (2026-07-31, Tranzlate_Resizable, prod debug)

Measured `paneHeight` logged from the running app (temporary log, removed
before commit). The owner's window was reproduced exactly by overriding the
emulator to his device's geometry — `wm size 1440x3120` + `wm density 600`,
which yields 832×384dp in landscape, confirmed by
`dumpsys window displays … cur=3120x1440`.

| shape | window | IME | measured | band | vs today |
|---|---|---|---|---|---|
| phone portrait | 411×914dp | down | **862.1dp** | FULL | unchanged ✓ |
| phone portrait | 411×914dp | **up** | **549.7dp** | FULL | unchanged ✓ |
| phone landscape | 914×411dp | down | **359.2dp** | FOLDED_CHROME | unchanged ✓ |
| phone landscape | 914×411dp | **up** | **121.9dp** | MINIMAL | unchanged ✓ |
| **owner's landscape** | **832×384dp** | down | **332.0dp** | FOLDED_CHROME | was FULL — now folds |
| **owner's landscape** | **832×384dp** | **up** | **107.7dp** | MINIMAL | **was FULL — THE BUG** |
| tablet portrait | 800×1280dp | up | (FULL chrome rendered) | FULL | unchanged ✓ |

The 914dp landscape regression guard — the one that had to stay identical —
holds on both sides: 359.2dp folds (16.8dp under the 376dp threshold) and
121.9dp goes minimal. The margin is small in dp but it is not arbitrary: at
359.2dp the interior is 359.2 − 128 = 231.2dp against a 248dp full-chrome
stack, so the full face genuinely does not fit there. The threshold is the fit
boundary, which is exactly where a fold should happen.

Portrait clears `FULL_CHROME_MIN_HEIGHT` by 173.7dp even with the keyboard up,
so no portrait phone in the fleet folds.

**Fix confirmed on the owner's geometry** (`i99-op7-land.png`,
`i99-op7-typed.png`): with the IME up the composer renders the minimal body —
top row hidden, field + `0 / 500` counter + mic in one row — and typing "Good
morning" leaves the text FULLY VISIBLE inside the card with a full-size
Translate button. That is the exact symptom the issue reported as an empty
white bar.

### Not verified

Tablet **landscape** two-pane could not be captured: the resizable AVD in
tablet mode refused `user_rotation`, `cmd window set-user-rotation` and a
landscape `wm size` override, staying at 1200×1920. The guard is structural
rather than measured — `layout.permanentTwoPane -> ComposerFit.FULL` pins the
band before any measurement is consulted, and the two-pane branch calls
`ComposerEditBody` with the default (false) chrome flags, untouched by this
change. Tablet **portrait** with the IME up was verified as FULL.
