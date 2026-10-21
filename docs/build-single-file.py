#!/usr/bin/env python3
"""Concatenate the numbered guides into one Markdown file (e.g. to publish the series as a single tutorial).

Usage: python3 docs/build-single-file.py [output.md]

Links between guides (`04-network-placement.md#section`) are rewritten to in-page anchors.
"""
import re, sys
from pathlib import Path

here = Path(__file__).parent
out = here / (sys.argv[1] if len(sys.argv) > 1 else "network-monitor-tutorial.md")
files = sorted(p for p in here.glob("[0-9][0-9]-*.md"))


def slug(heading: str) -> str:
    s = re.sub(r"[^\w\s-]", "", heading.lower()).strip()
    return re.sub(r"\s", "-", s)


anchor = {p.name: slug(re.match(r"#\s+(.*)", p.read_text().splitlines()[0]).group(1)) for p in files}
body = ["# Home Network Monitor: Complete Setup Tutorial\n"]
for p in files:
    body.append(p.read_text().rstrip() + "\n\n---\n")
text = "\n".join(body)


def fix(m):
    name, frag = m.group(1), m.group(2) or ""
    return f"](#{frag[1:]})" if frag else f"](#{anchor[name]})"


text = re.sub(r"\]\((\d\d-[a-z0-9-]+\.md)(#[a-z0-9-]+)?\)", fix, text)
out.write_text(text)
print(f"Wrote {out} ({text.count(chr(10))} lines)")
