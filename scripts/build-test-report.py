#!/usr/bin/env python3
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

import argparse
import html
import json
import os
import shutil
import sys
import xml.etree.ElementTree as ElementTree
from datetime import datetime, timezone
from pathlib import Path

COMPONENTS = [
    ("server", "Server", "Kotlin · Spring Boot · OCPP 1.6 and 2.0.1"),
    ("dashboard", "Web dashboard", "Next.js · TypeScript"),
    ("android", "Android app", "Kotlin · Jetpack Compose"),
    ("ios", "iOS app", "Swift · SwiftUI"),
]


def suites(folder: Path):
    for path in sorted(folder.rglob("*.xml")):
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError:
            continue
        if root.tag == "testsuite":
            yield root
        elif root.tag == "testsuites":
            yield from root.iter("testsuite")


def results(folder: Path) -> dict:
    summary = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0, "failed": []}
    if not folder.is_dir():
        return summary
    for suite in suites(folder):
        for case in suite.iter("testcase"):
            summary["tests"] += 1
            summary["time"] += float(case.get("time") or 0)
            name = f"{case.get('classname') or suite.get('name') or ''} › {case.get('name') or ''}"
            if case.find("failure") is not None:
                summary["failures"] += 1
                summary["failed"].append(name)
            elif case.find("error") is not None:
                summary["errors"] += 1
                summary["failed"].append(name)
            elif case.find("skipped") is not None:
                summary["skipped"] += 1
    return summary


def coverage(folder: Path, name: str):
    details = folder / "badges" / f"{name}-coverage-details.json"
    if not details.is_file():
        return None
    return json.loads(details.read_text()).get("percent")


def badge(path: Path, label: str, message: str, colour: str):
    path.write_text(json.dumps({"schemaVersion": 1, "label": label, "message": message, "color": colour}) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("collected", type=Path)
    parser.add_argument("site", type=Path)
    arguments = parser.parse_args()

    reports = arguments.site / "reports"
    badges = arguments.site / "badges"
    reports.mkdir(parents=True, exist_ok=True)
    badges.mkdir(parents=True, exist_ok=True)

    rows = []
    failures = []
    total = {"tests": 0, "failed": 0}
    for key, title, stack in COMPONENTS:
        folder = arguments.collected / f"report-{key}"
        summary = results(folder / "junit")
        percent = coverage(folder, key)
        for source in (folder / "badges").glob("*.json") if (folder / "badges").is_dir() else []:
            if not source.name.endswith("-details.json"):
                shutil.copy(source, badges / source.name)
        link = ""
        if (folder / "coverage").is_dir():
            shutil.copytree(folder / "coverage", reports / key, dirs_exist_ok=True)
            link = f'<a href="{key}/index.html">Coverage report</a>'
        failed = summary["failures"] + summary["errors"]
        total["tests"] += summary["tests"]
        total["failed"] += failed
        passed = summary["tests"] - failed - summary["skipped"]
        state = "missing" if summary["tests"] == 0 else ("failed" if failed else "passed")
        rows.append(
            f"<tr><th scope=\"row\">{html.escape(title)}<span>{html.escape(stack)}</span></th>"
            f"<td class=\"{state}\">{state}</td><td>{passed}</td><td>{failed}</td><td>{summary['skipped']}</td>"
            f"<td>{summary['time']:.1f} s</td><td>{'—' if percent is None else f'{percent:.1f}%'}</td><td>{link}</td></tr>"
        )
        failures += [f"<li><strong>{html.escape(title)}</strong> {html.escape(name)}</li>" for name in summary["failed"]]

    if total["tests"]:
        colour = "brightgreen" if total["failed"] == 0 else "red"
        message = f"{total['tests'] - total['failed']} passed" + (f", {total['failed']} failed" if total["failed"] else "")
        badge(badges / "tests.json", "tests", message, colour)

    run = os.environ.get("GITHUB_SERVER_URL", "https://github.com") + "/" + os.environ.get("GITHUB_REPOSITORY", "") + "/actions/runs/" + os.environ.get("GITHUB_RUN_ID", "")
    commit = os.environ.get("GITHUB_SHA", "")[:7]
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    failed_block = (
        f"<section><h2>Failed tests</h2><ul class=\"failed-list\">{''.join(failures)}</ul></section>" if failures else ""
    )
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Test report · EV Supervision Lite</title>
<meta name="description" content="Automated test results and code coverage of EV Supervision Lite, the open-source OCPP 1.6 and OCPP 2.0.1 EV charging supervision platform (CSMS) by Codetics.">
<link rel="canonical" href="https://codetics-software.github.io/ev-server-lite/reports/">
<style>
:root {{ --bg: #f7f7f5; --fg: #1c1c1a; --muted: #5d5d58; --line: #dcdcd6; --card: #ffffff; --ok: #1d7a46; --ko: #b42318; --warn: #8a6100; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #141413; --fg: #ededea; --muted: #a3a39c; --line: #33332f; --card: #1d1d1b; --ok: #4cc287; --ko: #ff8a7a; --warn: #e3b341; }} }}
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }}
main {{ max-width: 1040px; margin: 0 auto; padding: 40px 16px 64px; }}
h1 {{ font-size: 1.75rem; margin: 0 0 4px; }}
p.meta {{ color: var(--muted); margin: 0 0 32px; }}
.table {{ overflow-x: auto; background: var(--card); border: 1px solid var(--line); border-radius: 12px; }}
table {{ border-collapse: collapse; width: 100%; min-width: 720px; }}
th, td {{ text-align: left; padding: 12px 16px; border-bottom: 1px solid var(--line); vertical-align: top; }}
thead th {{ font-size: .8rem; color: var(--muted); font-weight: 600; }}
tbody tr:last-child th, tbody tr:last-child td {{ border-bottom: 0; }}
th span {{ display: block; font-weight: 400; font-size: .85rem; color: var(--muted); }}
td.passed {{ color: var(--ok); font-weight: 600; }}
td.failed {{ color: var(--ko); font-weight: 600; }}
td.missing {{ color: var(--warn); font-weight: 600; }}
a {{ color: inherit; }}
section {{ margin-top: 32px; }}
.failed-list li {{ margin: 4px 0; font-family: ui-monospace, monospace; font-size: .9rem; }}
footer {{ margin-top: 48px; color: var(--muted); font-size: .9rem; }}
</style>
</head>
<body>
<main>
<h1>Test report</h1>
<p class="meta">Commit {html.escape(commit or "local")} · generated {generated} · <a href="{html.escape(run)}">workflow run</a> · <a href="../">project home</a></p>
<div class="table"><table>
<thead><tr><th scope="col">Component</th><th scope="col">Status</th><th scope="col">Passed</th><th scope="col">Failed</th><th scope="col">Skipped</th><th scope="col">Duration</th><th scope="col">Line coverage</th><th scope="col">Details</th></tr></thead>
<tbody>{''.join(rows)}</tbody>
</table></div>
{failed_block}
<footer>EV Supervision Lite — built by Kevin Vavelin, Codetics · <a href="https://codetics.fr">https://codetics.fr</a></footer>
</main>
</body>
</html>
"""
    (reports / "index.html").write_text(page, encoding="utf-8")
    print(f"Report written to {reports / 'index.html'} ({total['tests']} tests, {total['failed']} failed)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
