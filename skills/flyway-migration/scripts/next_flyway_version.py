#!/usr/bin/env python3
"""Print or validate the next sequential ycsopen-sms Flyway version."""

from __future__ import annotations

import argparse
from collections import defaultdict
import re
import sys
from pathlib import Path


VERSION_RE = re.compile(r"^V(?P<version>\d+)__.+\.sql$")
REPEATABLE_RE = re.compile(r"^R__.+\.sql$")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--check", help="candidate prefix such as V2")
    args = parser.parse_args()

    migration_dir = args.repo.resolve() / "core/src/main/resources/db/migration"
    if not migration_dir.is_dir():
        parser.error(f"migration directory not found: {migration_dir}")

    version_files: dict[int, list[str]] = defaultdict(list)
    repeatable_count = 0
    invalid_names: list[str] = []
    for path in sorted(migration_dir.iterdir()):
        if not path.is_file() or path.suffix.lower() != ".sql":
            continue
        match = VERSION_RE.match(path.name)
        if match:
            version_files[int(match.group("version"))].append(path.name)
        elif REPEATABLE_RE.match(path.name):
            repeatable_count += 1
        else:
            invalid_names.append(path.name)

    if invalid_names:
        print("ERROR=unsupported Flyway SQL filename(s): " + ",".join(invalid_names), file=sys.stderr)
        return 2

    duplicates = {version: files for version, files in version_files.items() if len(files) > 1}
    if duplicates:
        detail = ";".join(f"V{version}={','.join(files)}" for version, files in sorted(duplicates.items()))
        print(f"ERROR=duplicate Flyway version(s): {detail}", file=sys.stderr)
        return 2

    current_max = max(version_files, default=0)
    next_prefix = f"V{current_max + 1}"
    print(f"MIGRATION_DIR={migration_dir}")
    print(f"CURRENT_MAX={current_max}")
    print(f"REPEATABLE_COUNT={repeatable_count}")
    print(f"NEXT={next_prefix}")

    if args.check is not None:
        accepted = args.check == next_prefix
        print(f"CHECK_RESULT={'PASS' if accepted else 'FAIL'}")
        return 0 if accepted else 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
