# Research/design record — design-conformance infrastructure (issue #349)

Read-only research + adversarial design record (rules 4, 8). The plan it justifies is
`docs/plan/issue-349-design-conformance.md`.

## The problem, stated as a falsifiable claim

**Claim:** manual build-to-spec + human/LLM review cannot reliably catch design divergences,
so an objective machine gate is required.

**Evidence (not assertion):** 20d (PR #338, `4c088b0`) was built to spec, reviewed, "measured
~1-4px matching", and approved by a co-verify lens — and the owner then found three classes
of divergence it missed: font size/weight, error-card colour, screen-edge spacing. A prose
colour-disclosure was even written in the PR body and the screen still got APPROVE-WITH-NOTES.
Human review has structural blind spots (position was checked; font metrics and exact colour
were not). This is the disconfirmation the claim needed to survive.

## The reshaping correction (owner, 2026-08-07) — golden is STATED, not measured

Initial design: extract the mockup's computed layout (`extract-specs.mjs`) and treat it as the
golden. **Disconfirmed by the owner and by measurement.** The SSOT STATES the design values
(README §Tokens); the mockup is a drawing that deviates from them.

`reconcile.mjs` (measured `specs/*.json` vs the full stated palette + metrics + type in
`stated-spec.json`) — the experiment that would have falsified "measure everything":

```
Divergences: 129 font, 53 colour, 78 row-height  across 26 frame(s).
```

Every frame diverges. That count is a **role-blind diagnostic** and over-states real divergence:
it includes legitimate unstated roles (a detail-pane headline is 28sp with no stated value) and
the row-height heuristic is blind on list-detail panes — the gate, not the count, does the
role-precise check. The genuine, clean divergences: 20d's app-bar drawn 24sp where the SSOT
states 22sp; body at 14.5/12.5sp where the scale is 13.5/12; 20d's capability-card icon ink
`#072711` off the `onTertiary` token. **Frames measure at 1:1 device dp** (412×892, 1280×800,
800×1280, 760×812, 892×412 — all clean), so these are drawing imprecisions, not a scale
artifact — measurement is spatially trustworthy but the mockup's *values* are not the spec. A
pure-measurement golden would have enforced 24sp on the 20d app-bar, moving a *correct* 22sp
build AWAY from the SSOT. Hence golden = STATED (authoritative, for colour/type/metrics) ⊕
MEASURED (only the unstated: per-element positions, headline/display sizes). Conflict → stated wins.

> The first draft cited 165/115/78 with a `#ffffff`-vs-`#f8fafd` example. The #350 co-verify
> corrected it: `#ffffff` is `surfaceContainerLowest` (a valid token), the inflation came from a
> README-subset palette + icon glyphs counted as text. Fixed — full reconciled palette + `ic`
> flag — giving 129/53/78. See the "Co-verify #350 findings — status" section in the plan doc.

## Red-team verdict (rule 8): ADOPT-WITH-CONDITIONS

Full transcript is the #349 red-team agent run (2026-08-07). It investigated the real repo,
not the design's self-description, and ran a numeric/pixel prototype against the committed 20d
artifacts. Findings that shaped the plan:

- **Feasibility (SURVIVES).** At pinned bytecode: `getBoundsInRoot()`→DpRect,
  `SemanticsActions.GetTextLayoutResult`→fontSize/weight, `captureToImage()`→per-node
  ImageBitmap all present + accurate. The module already walks the same semantics tree
  (200+ uses of `onNodeWithTag`). Numeric extraction of the built screen is real, not a leap.
- **Scale (SURVIVES, empirical).** Roborazzi goldens render at exactly their dp qualifiers
  (1280×800, 411×891) → `1px = 1dp`, zero conversion. A golden-coordinate sample hit the
  built PNG exactly, confirming origin alignment too.
- **Component 1 not durable (NEEDS-CHANGE → this PR).** `extract-specs.mjs` + `specs/*.json`
  had zero commits on any branch; `git clean` deletes them. Committed here.
- **Mapping (NEEDS-CHANGE → curated).** 38 testTags file-wide vs 81 meaningful elements in
  one frame: no 1:1 map exists, and text-matching is rule-1-unsafe (copy is rewritten). Fix:
  ~15–20 hand-mapped landmark testTags per frame, PR-reviewable.
- **Completeness (NEEDS-CHANGE → group counts).** Per-tag matching can't catch a missing
  card (the original 20d defect). Fix: `assertCountEquals` on repeated structures.
- **Manifest design REJECTED.** A `Conforms-to-frame:` PR-body marker is a presence-check a
  lens can lie in (rule 12: `Enumerated by: …this is a lie` was allowed by guard-pr). The gate
  must be hard JVM assertions with no field to lie in.
- **Bypass ranked.** Cheapest: edit the SSOT (`language-screens-spec.html`, zero branch
  protection) to match a wrong build. Mitigation: co-verify flags any PR touching both
  `feature/language/**` and the spec/specs.

**Where the red-team's own findings shift under the stated-primary correction:** its finding
(a) "built 22sp ≠ golden 24sp" inverts — 22sp is the STATED value and is correct; the
measured 24sp golden was the artifact. The gate must therefore compare against stated, not
measured, or it reproduces exactly the error this record documents. Findings (b) colour
(fixed by #348) and (c) position (unstated → measured is the reference) stand.

## Disconfirmation discipline (rule 4)

- *Hypothesis:* the numeric gate catches what review misses. *Experiment that would refute
  it:* run a prototype against the already-rejected 20d; if it can't flag the owner-named
  divergences, the approach is dead. *Result:* the prototype flagged them. Survives (≤70%
  until Component 2's real gate reproduces it in-tree, red-before-green).
- *Hypothesis:* measure everything. *Experiment:* reconcile measured vs stated. *Result:*
  26/26 frames diverge; refuted. Golden is stated-primary.
- *Open risk that would invalidate the plan, not complete it:* if the allowlist for "unstated
  roles/colours" grows to cover the divergences instead of a genuine short list, the lint
  catches nothing (a rule-12 presence-vs-truth failure). The co-verify lens must check the
  allowlist is minimal and each entry is tied to a frame element + reason.
