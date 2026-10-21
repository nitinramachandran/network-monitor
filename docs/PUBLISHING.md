# Publishing these guides on a website

This folder is written to be dropped into a documentation or blog site (for example, a "Tutorials" section) with little or no editing. This page is for the person doing that.

## What you get

| File | Purpose |
|---|---|
| `NN-*.md` and `README.md` | The guides. Each starts with YAML front matter (title, description, slug, series, order, tags, updated). |
| `export.py` | Produces a site-ready copy of everything in `docs/dist/` |
| `skills.md` (repository root) | A staged setup runbook, publishable as its own page |

Plain Markdown only: tables, fenced code blocks, headings, links. No images, no custom components, no JavaScript. The content has no site-specific syntax, so it renders in Jekyll, Hugo, Astro, Next.js (MDX or Markdown), Docusaurus, MkDocs, Ghost, WordPress (Markdown import) and similar.

## Quick start

```
$ python3 docs/export.py --base-url /tutorials/network-monitor
```
Needs Python 3.8+ and nothing else. Output in `docs/dist/`:

| Output | Use |
|---|---|
| `overview-and-planning.md`, `raspberry-pi-setup.md`, ... (13 files) | One page per guide, with front matter, internal links rewritten to `<base-url>/<slug>` |
| `index.md` | The series landing page |
| `manifest.json` | Ordered list of pages with title, description, tags and URL, for building navigation or a table of contents |
| `network-monitor-tutorial.md` | The whole series as a single long page (no front matter, in-page anchors) |
| `skills.md` | The setup runbook |

Options:

| Flag | Default | Meaning |
|---|---|---|
| `--base-url` | `/tutorials/network-monitor` | URL prefix the pages will be served under. Internal links use it. Use a full URL if you want absolute links. |
| `--ext` | `md` | Set `mdx` if your site needs that extension |
| `--keep-h1` | off | By default the leading `# Title` heading is removed, because sites render the title from front matter. Keep it if yours does not. |
| `--repo-url` | the project's GitHub URL | Where links to files outside `docs/` (such as the README) point |
| `--out` | `docs/dist` | Output folder |

## Front matter fields

```yaml
---
title: "Set up a Raspberry Pi as the monitor box"
description: "One sentence summary, suitable for a meta description or card."
slug: raspberry-pi-setup
series: "Home Network Monitor"
order: 2
tags: [raspberry-pi, linux, docker]
updated: 2026-10-22
---
```
Rename or drop fields to match your site's schema (for example `date` instead of `updated`, or `weight` instead of `order`); the exporter only needs `title`, `slug`, `order` and `description` to be present. If your site's front matter keys differ, a one-line `sed` over `docs/dist/*.md` is the easiest adapter.

## Integration patterns

**Copy and serve.** Copy `docs/dist/*.md` into your site's tutorials content folder and use `manifest.json` to render the list of guides in order.

**One page.** Publish only `network-monitor-tutorial.md` with your own front matter if you prefer a single long article.

**Keep it in sync.** Re-run the export when the repository's docs change. The source of truth is this repository; do not edit the exported copies.

## Things to check after importing

1. **Links**: click through the "Next" links at the bottom of each guide and the table of contents on the index page.
2. **Section anchors**: links such as `#43-configuring-your-isp-router` use GitHub-style heading ids. Most generators produce the same ids; some (MkDocs, Docusaurus) differ slightly for headings containing dots or punctuation. Spot-check the few cross-guide section links.
3. **Tables and code**: confirm that wide tables scroll rather than break your layout, and that `$ ` prompts in code blocks look right. They are for readers to skip when copying; add a `console` or `bash` language to the code fences if your site highlights syntax.
4. **Numbers in headings**: headings inside guides keep their section numbers (for example `## 2.5 Give the Pi a fixed IP`), which the cross-references rely on.

## Content notes for editors

- The provider guide ([ISP router guide](12-isp-router-guide.md)) is based on community forum reports, not official documentation, and says so. Keep that framing when editing, and keep the sources section. Provider firmware changes often, so review it periodically and note the review date in `updated`.
- Commands, ports and file paths were written for Raspberry Pi OS (Debian), Ubuntu Server and Debian. IP addresses are examples.
- The guides do not include licence text. Add the licence you want to publish under to your site or to the repository before reuse.

## Updating the series

1. Edit the Markdown in `docs/` (keep the front matter).
2. Update `updated:` in the files you changed.
3. Check links: `python3 docs/export.py --out /tmp/nwm-check` should complete without errors.
4. Re-export and re-publish.
