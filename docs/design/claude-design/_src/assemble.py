#!/usr/bin/env python3
"""Assemble standalone Claude Design cards.

Each cards_src/<name>.body.html must start with an @dsCard comment line.
Output cards/<name>.html = that marker line + full standalone HTML with
tokens.css + components.css inlined (cards must render with no external refs).
"""
import pathlib
import re
import sys

SRC = pathlib.Path(__file__).resolve().parent
ROOT = SRC.parent
CARDS_SRC = ROOT / "cards_src"
OUT = ROOT / "cards"

css = (SRC / "tokens.css").read_text() + "\n" + (SRC / "components.css").read_text()
OUT.mkdir(exist_ok=True)

errors = []
for body_file in sorted(CARDS_SRC.glob("*.body.html")):
    text = body_file.read_text()
    first, _, rest = text.partition("\n")
    if not re.match(r"^<!--\s*@dsCard\s+group=\"[^\"]+\"", first):
        errors.append(f"{body_file.name}: first line is not an @dsCard marker: {first[:80]}")
        continue
    hexes = re.findall(r"#[0-9a-fA-F]{3,8}\b", rest)
    if hexes:
        errors.append(f"{body_file.name}: raw hex colors in body: {sorted(set(hexes))[:6]}")
        continue
    name = body_file.name.replace(".body.html", ".html")
    out = (
        f"{first}\n<!doctype html>\n<html lang=\"si\"><head><meta charset=\"utf-8\">"
        f"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        f"<title>{name}</title><style>\n{css}\n</style></head><body>\n{rest}\n</body></html>\n"
    )
    (OUT / name).write_text(out)
    print(f"ok  {name}")

if errors:
    print("\nFAILURES:", file=sys.stderr)
    for e in errors:
        print(" -", e, file=sys.stderr)
    sys.exit(1)
