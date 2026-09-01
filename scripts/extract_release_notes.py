#!/usr/bin/env python3
"""Print one VERSION section from CHANGELOG.md for release tooling."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SEMVER = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?")


def extract_release_notes(changelog: Path, version: str) -> str:
    if not SEMVER.fullmatch(version):
        raise ValueError(f"invalid semantic version: {version}")

    lines = changelog.read_text(encoding="utf-8").splitlines()
    heading = f"## {version}"
    try:
        start = lines.index(heading) + 1
    except ValueError as error:
        raise ValueError(f"{changelog} does not contain an exact '{heading}' section") from error

    end = next(
        (index for index in range(start, len(lines)) if lines[index].startswith("## ")),
        len(lines),
    )
    section = lines[start:end]
    while section and not section[0].strip():
        section.pop(0)
    while section and not section[-1].strip():
        section.pop()
    if not section or not any(line.startswith("- ") for line in section):
        raise ValueError(f"{heading} must contain at least one release-note bullet")
    return "\n".join(section) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--changelog", type=Path, default=Path("CHANGELOG.md"))
    arguments = parser.parse_args()
    print(extract_release_notes(arguments.changelog, arguments.version), end="")


if __name__ == "__main__":
    main()
