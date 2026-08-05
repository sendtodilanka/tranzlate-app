#!/usr/bin/env python3
"""
enumerate-frames.py -- structural frame enumeration for the rev5 language-screens spec.

WHY THIS EXISTS (issue #184)
    The rev5 spec's frames were counted by their `data-screen-label` attribute, and
    three docs then asserted "all 54 frames" were covered. But two fully-drawn
    frames -- 20c (pack-actions sheet) and 20e (free-up-space sheet), LIGHT and DARK
    each -- carry no `data-screen-label` at all. A label count reports 54 and
    silently omits those four drawings. This script enumerates frames by STRUCTURE,
    independent of the label, so a missing label can never hide a frame again. It is
    the committed, checkable script the issue asked for ("Commit the script this
    time so the next claim of coverage is checkable").

THE ENCODING TRAP
    The frame markup does not live in the raw HTML. It is inside a single
    <script type="__bundler/template"> block, HTML-entity-escaped around a
    JSON string literal. A raw grep of the 9 MB file finds 0 frame spans but 55
    `data-screen-label` hits (54 attributes + 1 prose <code> mention) -- which is
    how the false "54" was taken. Decode first: extract the template block,
    html.unescape(), then json.loads() to get the real markup string.

THE FRAME SIGNAL (label-independent)
    Every drawing is a wrapper <div> identified by EITHER
      (a) a monospace caption div as its first child
          (font:500 10px/1 ui-monospace,Menlo,monospace ...  e.g. "LIGHT" / "DARK"
          / "TO . LANDSCAPE"), OR
      (b) a `data-screen-label` attribute on the wrapper tag itself.
    20c/20e's four unlabelled frames use (a) with no label; 21c's two "refused
    placement" frames use (b) with no caption. The UNION of the two signals is the
    complete frame set. Signal (a) ALONE already catches every unlabelled frame,
    which is the exact property #184 needs and the property the mutation test pins.
    Neither signal is the direct-child-of-the-row heuristic, which miscounts: 20a
    nests its ten drawings inside sub-columns, so counting a row's direct children
    returns columns, not drawings.

MUTATION TEST (decided BEFORE this script was written -- rule 11)
    Remove ONE `data-screen-label` from a frame that has one (here: the first
    "15a picker light"). The STRUCTURAL enumerator must STILL find that frame
    (via its "LIGHT" caption); a LABEL-based enumerator loses it. `--self-test`
    performs exactly this mutation in memory and asserts:
      * structural total is UNCHANGED (58 -> 58), and the mutated frame is now
        reported among the label-less frames (4 -> 5);
      * the label-only total DROPS (54 -> 53), and the mutated frame is ABSENT
        from the label set.
    If the structural count ever moved under this mutation, the enumerator would be
    secretly label-based, and the self-test fails loudly (non-zero exit).

USAGE
    python3 enumerate-frames.py              # enumerate; print the report
    python3 enumerate-frames.py --self-test  # run the mutation test; exit != 0 on fail
"""
from __future__ import annotations

import html
import json
import os
import re
import sys

SPEC = os.path.normpath(
    os.path.join(os.path.dirname(__file__), os.pardir, "language-screens-spec.html")
)

# --- signals -------------------------------------------------------------------
# The caption is a <div> whose first child it is NOT -- it is itself a child of the
# frame wrapper. Anchor the match at that caption <div> so the wrapper is the <div>
# immediately before it (the caption is always the wrapper's first child).
CAPTION = re.compile(
    r'<div style="font:500 10px/1 ui-monospace,Menlo,monospace;letter-spacing:\.8px;'
    r'color:[^"]*">([^<]*)</div>'
)
LABEL_ATTR = re.compile(r'data-screen-label="([^"]*)"')
DVOPT_OPEN = re.compile(r'<div class="dv-opt" id="([^"]*)">')
DIV_TAG = re.compile(r'<(/?)div\b')


def decode_spec(path: str = SPEC) -> str:
    """Extract the __bundler/template block and decode it to the real markup string."""
    raw = open(path, encoding="utf-8").read()
    m = re.search(r'<script type="__bundler/template">(.*?)</script>', raw, re.DOTALL)
    if not m:
        raise SystemExit("template block not found -- spec format changed")
    return json.loads(html.unescape(m.group(1)))


def _div_end(s: str, start: int) -> int:
    """Index just past the </div> that closes the <div> opening at `start`."""
    i, depth = start, 0
    while i < len(s):
        m = DIV_TAG.search(s, i)
        if not m:
            return len(s)
        depth += 1 if m.group(1) == "" else -1
        if depth == 0:
            return m.end()
        i = m.end()
    return len(s)


def _dvopt_bounds(markup: str):
    """List of (id, start, end) for every <div class="dv-opt" id="...">."""
    out = []
    for m in DVOPT_OPEN.finditer(markup):
        out.append((m.group(1), m.start(), _div_end(markup, m.start())))
    return out


def _owner(bounds, pos: int):
    for oid, s, e in bounds:
        if s <= pos < e:
            return oid
    return None


class Frame:
    __slots__ = ("wrapper_pos", "dvopt", "caption", "label")

    def __init__(self, wrapper_pos, dvopt, caption, label):
        self.wrapper_pos = wrapper_pos
        self.dvopt = dvopt
        self.caption = caption
        self.label = label

    def name(self) -> str:
        cap = self.caption or "?"
        return f"{self.dvopt} / {cap}"


def enumerate_frames(markup: str):
    """Structural frame list. A frame = one wrapper <div>, found via caption OR label."""
    bounds = _dvopt_bounds(markup)
    wrappers = {}  # wrapper_pos -> Frame

    def wrapper_start_before(pos: int) -> int:
        return markup.rfind("<div", 0, pos)

    # signal (a): every monospace caption -> its wrapper is the <div> just before it
    for m in CAPTION.finditer(markup):
        wpos = wrapper_start_before(m.start())
        open_tag = markup[wpos:markup.find(">", wpos) + 1]
        lab = LABEL_ATTR.search(open_tag)
        wrappers[wpos] = Frame(wpos, _owner(bounds, wpos), m.group(1),
                               lab.group(1) if lab else None)

    # signal (b): every data-screen-label attribute -> the <div> it sits on
    for m in LABEL_ATTR.finditer(markup):
        wpos = wrapper_start_before(m.end())
        if wpos in wrappers:
            wrappers[wpos].label = m.group(1)
            continue
        # caption-less labelled frame (21c). Its caption, if any, is its first child.
        inner = markup[markup.find(">", wpos) + 1: _div_end(markup, wpos)]
        cap = CAPTION.search(inner)
        wrappers[wpos] = Frame(wpos, _owner(bounds, wpos),
                               cap.group(1) if cap else None, m.group(1))

    return sorted(wrappers.values(), key=lambda f: f.wrapper_pos)


def label_only_frames(markup: str):
    """The NAIVE method the docs used: enumerate by label attribute alone."""
    return LABEL_ATTR.findall(markup)


def report(markup: str) -> None:
    frames = enumerate_frames(markup)
    labelless = [f for f in frames if f.label is None]
    labels = label_only_frames(markup)
    empty = [oid for oid, s, e in _dvopt_bounds(markup)
             if not any(f.dvopt == oid for f in frames)]

    print("STRUCTURAL FRAME ENUMERATION -- language-screens rev5 spec")
    print("=" * 62)
    print(f"dv-opt groups (id-level)        : {len(_dvopt_bounds(markup))}")
    print(f"  empty groups (no drawings)    : {len(empty)}  {empty}")
    print(f"TRUE FRAME (drawing) COUNT      : {len(frames)}")
    print(f"  carrying a data-screen-label  : {len(frames) - len(labelless)}")
    print(f"  carrying NO label             : {len(labelless)}")
    print(f"label-attribute count (claimed) : {len(labels)}")
    print()
    print("Frames carrying NO data-screen-label (a label-count misses these):")
    for f in labelless:
        print(f"    {f.name()}")
    twentyc_e = {f.dvopt for f in labelless}
    print()
    print(f"20c present among label-less : {'20c' in twentyc_e}")
    print(f"20e present among label-less : {'20e' in twentyc_e}")


def self_test(markup: str) -> int:
    """Mutation test -- see the module docstring. Returns process exit code."""
    victim = "15a picker light"
    assert f'data-screen-label="{victim}"' in markup, "victim label not present"
    mutant = markup.replace(f' data-screen-label="{victim}"', "", 1)

    base = enumerate_frames(markup)
    base_labelless = [f for f in base if f.label is None]
    mut = enumerate_frames(mutant)
    mut_labelless = [f for f in mut if f.label is None]

    base_labels = label_only_frames(markup)
    mut_labels = label_only_frames(mutant)

    ok = True

    def check(desc, cond):
        nonlocal ok
        ok = ok and cond
        print(f"  [{'PASS' if cond else 'FAIL'}] {desc}")

    print("MUTATION TEST -- remove data-screen-label", repr(victim))
    print("-" * 62)
    check(f"structural total unchanged ({len(base)} -> {len(mut)})",
          len(base) == len(mut))
    check(f"label-less frames grew by one ({len(base_labelless)} -> {len(mut_labelless)})",
          len(mut_labelless) == len(base_labelless) + 1)
    check("mutated frame still enumerated, now label-less",
          any(f.caption == "LIGHT" and f.dvopt == "15a" and f.label is None
              for f in mut_labelless))
    check(f"label-only total dropped ({len(base_labels)} -> {len(mut_labels)})",
          len(mut_labels) == len(base_labels) - 1)
    check("mutated frame ABSENT from the label-only set (this is the bug it hides)",
          victim in base_labels and victim not in mut_labels)
    print()
    print("RESULT:", "PASS -- structural survives label removal; label-only does not"
          if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    markup = decode_spec()
    if "--self-test" in sys.argv[1:]:
        sys.exit(self_test(markup))
    report(markup)
