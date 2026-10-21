#!/usr/bin/env python3
"""Export the guides for publishing on a website (any static-site stack, CMS or wiki).

    python3 docs/export.py [--out DIR] [--base-url /tutorials/network-monitor] [--ext md]
                           [--keep-h1] [--repo-url https://github.com/<owner>/<repo>]

Writes into DIR (default: docs/dist):
  <slug>.md                one file per guide, with its YAML front matter, links rewritten to
                           <base-url>/<slug>[#section]; the leading "# Title" heading is removed
                           because the site renders the title from front matter (use --keep-h1 to keep it)
  index.md                 the series landing page
  manifest.json            ordered list of pages (title, description, slug, order, tags, url) for navigation
  network-monitor-tutorial.md   everything in one document (no front matter, in-page anchors)
  skills.md                the setup runbook, copied as-is

Only the standard library is needed. See docs/PUBLISHING.md.
"""
import argparse, json, re, shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent


def parse(path: Path):
    text = path.read_text()
    m = re.match(r"---\n(.*?)\n---\n+(.*)", text, re.S)
    if not m:
        raise SystemExit(f"{path.name}: missing front matter")
    meta = {}
    for line in m.group(1).splitlines():
        k, _, v = line.partition(":")
        v = v.strip()
        if v.startswith("[") and v.endswith("]"):
            meta[k] = [t.strip() for t in v[1:-1].split(",") if t.strip()]
        else:
            meta[k] = v.strip('"')
    return meta, m.group(1), m.group(2)


def gh_slug(heading: str) -> str:
    s = re.sub(r"[^\w\s-]", "", heading.lower()).strip()
    return re.sub(r"\s", "-", s)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=str(HERE / "dist"))
    ap.add_argument("--base-url", default="/tutorials/network-monitor", help="URL prefix the pages will be served under")
    ap.add_argument("--ext", default="md", help="file extension for exported pages (md or mdx)")
    ap.add_argument("--keep-h1", action="store_true")
    ap.add_argument("--repo-url", default="https://github.com/nitinramachandran/network-monitor")
    a = ap.parse_args()

    out = Path(a.out)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    base = a.base_url.rstrip("/")

    files = {p.name: p for p in HERE.glob("*.md") if p.name == "README.md" or re.match(r"\d\d-", p.name)}
    pages = {}
    for name, p in files.items():
        meta, fm, body = parse(p)
        pages[name] = (meta, fm, body)
    slug_of = {name: meta["slug"] for name, (meta, _, _) in pages.items()}
    h1_anchor = {}
    for name, (meta, _, body) in pages.items():
        first = next((l for l in body.splitlines() if l.startswith("# ")), "# " + meta["title"])
        h1_anchor[name] = gh_slug(first[2:])

    def page_url(name, frag=""):
        slug = slug_of[name]
        return (f"{base}/" if slug == "index" else f"{base}/{slug}") + frag

    def rewrite(body: str) -> str:
        # guide-to-guide links
        body = re.sub(r"\]\(((?:\d\d-[a-z0-9-]+|README)\.md)(#[a-z0-9-]+)?\)",
                      lambda m: f"]({page_url(m.group(1), m.group(2) or '')})", body)
        # links out of docs/
        body = body.replace("](../README.md)", f"]({a.repo_url}#readme)")
        body = body.replace("](../skills.md)", f"]({base}/skills)")
        body = re.sub(r"\]\(\.\./([^)#]+)\)", lambda m: f"]({a.repo_url}/blob/main/{m.group(1)})", body)
        return body

    manifest = []
    for name, (meta, fm, body) in sorted(pages.items(), key=lambda kv: int(kv[1][0]["order"])):
        content = body
        if not a.keep_h1:
            content = re.sub(r"\A# .*\n+", "", content, count=1)
        content = rewrite(content)
        (out / f"{meta['slug']}.{a.ext}").write_text(f"---\n{fm}\n---\n\n{content.rstrip()}\n")
        manifest.append({"order": int(meta["order"]), "slug": meta["slug"], "title": meta["title"],
                         "description": meta["description"], "tags": meta.get("tags", []),
                         "updated": meta.get("updated"), "url": page_url(name).rstrip("/") or base})
    (out / "manifest.json").write_text(json.dumps({"series": "Home Network Monitor", "baseUrl": base, "pages": manifest}, indent=2) + "\n")

    # single combined document (guides only, in order, in-page anchors)
    parts = ["# Home Network Monitor: Complete Setup Tutorial\n"]
    for name, (meta, fm, body) in sorted(pages.items(), key=lambda kv: int(kv[1][0]["order"])):
        if int(meta["order"]) == 0:
            continue
        parts.append(body.rstrip() + "\n\n---\n")
    combined = "\n".join(parts)
    combined = re.sub(r"\]\((\d\d-[a-z0-9-]+\.md)(#[a-z0-9-]+)?\)",
                      lambda m: f"](#{m.group(2)[1:]})" if m.group(2) else f"](#{h1_anchor[m.group(1)]})", combined)
    combined = combined.replace("](../README.md)", f"]({a.repo_url}#readme)")
    (out / "network-monitor-tutorial.md").write_text(combined)

    shutil.copy(ROOT / "skills.md", out / "skills.md")
    print(f"Exported {len(manifest)} pages + manifest.json + combined tutorial + skills.md to {out}")


if __name__ == "__main__":
    main()
