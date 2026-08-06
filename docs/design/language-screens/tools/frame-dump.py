#!/usr/bin/env python3
"""
frame-dump.py -- emit the CANONICAL element list of one rev5 design frame.

WHY THIS EXISTS
    The 20d Manage-packs detail pane shipped missing most of its design because
    every brief and review compared code to a DERIVED summary, never to the SSOT
    frame itself. This tool makes "what the design shows" a COMPUTED fact: given a
    dv-opt id, it decodes the spec (same encoding trap enumerate-frames.py documents
    -- escaped JSON inside a <script type="__bundler/template"> block) and prints,
    for that group, every visible text node and every Material Symbols icon name it
    draws. An auditor then accounts for each element against the implementation:
    present@file:line | ruled-omit(<ruling line>) | ABSENT. This is TRIAGE only --
    the enforceable lock is the Roborazzi golden-diff in CI's `build` (#337). Text
    extraction has a known limit (pure-visual elements -- a bar, a layout, a colour
    state -- have no text node), which is precisely why it is not the lock.

USAGE
    python3 frame-dump.py 20d          # elements of every frame in group 20d
    python3 frame-dump.py 20d --icons  # icon names only
    python3 frame-dump.py --list       # every dv-opt id
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

DVOPT_OPEN = re.compile(r'<div class="dv-opt" id="([^"]*)">')
DIV_TAG = re.compile(r"<(/?)div\b")
# Material Symbols render as <span class="material-symbols-...">icon_name</span>
ICON = re.compile(r'<span[^>]*material-symbols[^>]*>([a-z0-9_]+)</span>')
# a visible text node: >text< that is not pure markup/whitespace
TEXT = re.compile(r">([^<>]+)<")
# noise to drop from text nodes
NOISE = re.compile(r"^[\s·•|/–—-]*$")


def decode_spec(path: str = SPEC) -> str:
    raw = open(path, encoding="utf-8").read()
    m = re.search(r'<script type="__bundler/template">(.*?)</script>', raw, re.DOTALL)
    if not m:
        raise SystemExit("template block not found -- spec format changed")
    return json.loads(html.unescape(m.group(1)))


def _div_end(s: str, start: int) -> int:
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


def groups(markup: str):
    return [(m.group(1), m.start(), _div_end(markup, m.start()))
            for m in DVOPT_OPEN.finditer(markup)]


def elements(markup: str, gid: str):
    hits = [(oid, s, e) for oid, s, e in groups(markup) if oid == gid]
    if not hits:
        raise SystemExit(f"no dv-opt group id={gid!r}; try --list")
    _, s, e = hits[0]
    seg = markup[s:e]
    icons = []
    for m in ICON.finditer(seg):
        icons.append(m.group(1))
    texts = []
    for m in TEXT.finditer(seg):
        t = html.unescape(m.group(1)).strip()
        if t and not NOISE.match(t) and not t.startswith("material-symbols"):
            texts.append(t)
    # de-dup preserving order
    def dedup(xs):
        seen, out = set(), []
        for x in xs:
            if x not in seen:
                seen.add(x); out.append(x)
        return out
    return dedup(texts), dedup(icons)


if __name__ == "__main__":
    markup = decode_spec()
    args = sys.argv[1:]
    if not args or "--list" in args:
        for oid, _, _ in groups(markup):
            print(oid)
        sys.exit(0)
    gid = args[0]
    texts, icons = elements(markup, gid)
    icons_only = "--icons" in args
    if icons_only:
        print(f"# icons in {gid}: {len(icons)}")
        for ic in icons:
            print(ic)
        sys.exit(0)
    print(f"=== FRAME GROUP {gid} ===")
    print(f"\nICONS ({len(icons)}):")
    for ic in icons:
        print(f"  {ic}")
    print(f"\nTEXT LABELS ({len(texts)}):")
    for t in texts:
        print(f"  {t!r}")
