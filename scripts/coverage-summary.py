#!/usr/bin/env python3
# EV Supervision Lite — built by Kevin Vavelin, Codetics
# https://codetics.fr
# Copyright (c) 2026 Kevin Vavelin, Codetics
# SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path


def jacoco(path: Path) -> dict:
    root = ElementTree.parse(path).getroot()
    counters = {}
    for counter in root.findall("counter"):
        missed = int(counter.get("missed", "0"))
        covered = int(counter.get("covered", "0"))
        counters[counter.get("type", "").lower()] = (covered, covered + missed)
    return counters


def vitest(path: Path) -> dict:
    total = json.loads(path.read_text())["total"]
    return {
        name: (int(values["covered"]), int(values["total"]))
        for name, values in total.items()
        if name in ("lines", "statements", "branches", "functions")
    }


def xccov(path: Path) -> dict:
    report = json.loads(path.read_text())
    targets = [
        target
        for target in report.get("targets", [])
        if not target.get("name", "").endswith((".xctest", "Tests"))
    ]
    covered = sum(int(target.get("coveredLines", 0)) for target in targets)
    executable = sum(int(target.get("executableLines", 0)) for target in targets)
    return {"line": (covered, executable)}


READERS = {"jacoco": jacoco, "vitest": vitest, "xccov": xccov}


def percent(pair) -> float:
    covered, total = pair
    return 0.0 if total == 0 else round(100.0 * covered / total, 1)


def colour(value: float) -> str:
    if value >= 80:
        return "brightgreen"
    if value >= 60:
        return "green"
    if value >= 40:
        return "yellowgreen"
    if value >= 20:
        return "yellow"
    return "orange"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("format", choices=sorted(READERS))
    parser.add_argument("report", type=Path)
    parser.add_argument("--name", required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    if not arguments.report.is_file():
        print(f"Coverage report not found: {arguments.report}", file=sys.stderr)
        return 1

    counters = READERS[arguments.format](arguments.report)
    main_metric = next((key for key in ("line", "lines", "instruction") if key in counters), None)
    if main_metric is None:
        print("No line coverage found in the report.", file=sys.stderr)
        return 1
    value = percent(counters[main_metric])

    arguments.output.mkdir(parents=True, exist_ok=True)
    badge = {
        "schemaVersion": 1,
        "label": arguments.label,
        "message": f"{value:.1f}%",
        "color": colour(value),
    }
    (arguments.output / f"{arguments.name}.json").write_text(json.dumps(badge) + "\n")
    details = {key: {"covered": pair[0], "total": pair[1], "percent": percent(pair)} for key, pair in counters.items()}
    (arguments.output / f"{arguments.name}-details.json").write_text(
        json.dumps({"name": arguments.name, "label": arguments.label, "percent": value, "counters": details}, indent=2)
        + "\n"
    )

    rows = "\n".join(
        f"| {key} | {pair[0]} / {pair[1]} | {percent(pair):.1f}% |" for key, pair in sorted(counters.items())
    )
    summary = f"### {arguments.label}: {value:.1f}%\n\n| Metric | Covered | % |\n| --- | --- | --- |\n{rows}\n"
    print(summary)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as handle:
            handle.write(summary + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
