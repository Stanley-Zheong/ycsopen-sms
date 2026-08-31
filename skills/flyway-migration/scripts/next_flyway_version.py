#!/usr/bin/env python3
"""Print or validate the next owner-scoped ycsopen-sms Flyway version."""

from __future__ import annotations

import argparse
from collections import defaultdict
import re
import sys
from pathlib import Path


VERSION_RE = re.compile(r"^V(?P<version>\d+)__.+\.sql$")
REPEATABLE_RE = re.compile(r"^R__.+\.sql$")
NAMESPACE_RE = re.compile(r"^V(?P<start>\d+)-V(?P<end>\d+)$")


def load_owner_ranges(registry: Path) -> dict[str, list[tuple[int, int]]]:
    if not registry.is_file():
        raise ValueError(f"schema ownership registry not found: {registry}")

    ranges: dict[str, list[tuple[int, int]]] = defaultdict(list)
    schema_ids: set[str] = set()
    for line_number, line in enumerate(registry.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.startswith("| SCHEMA-"):
            continue
        fields = [field.strip() for field in line.strip().strip("|").split("|")]
        if len(fields) != 9:
            raise ValueError(f"malformed schema registry row at line {line_number}: expected 9 fields")
        schema_id, owner, namespace = fields[0], fields[3], fields[4]
        if schema_id in schema_ids:
            raise ValueError(f"duplicate schema registry id: {schema_id}")
        match = NAMESPACE_RE.fullmatch(namespace)
        if not match:
            raise ValueError(f"malformed migration namespace for {owner}: {namespace}")
        start = int(match.group("start"))
        end = int(match.group("end"))
        if start < 1 or start > end:
            raise ValueError(f"invalid migration namespace for {owner}: {namespace}")
        schema_ids.add(schema_id)
        ranges[owner].append((start, end))

    if not ranges:
        raise ValueError("schema ownership registry contains no machine-readable rows")

    ordered = sorted(
        (start, end, owner)
        for owner, owner_namespaces in ranges.items()
        for start, end in owner_namespaces
    )
    for previous, current in zip(ordered, ordered[1:]):
        previous_start, previous_end, previous_owner = previous
        current_start, current_end, current_owner = current
        if current_start <= previous_end:
            raise ValueError(
                "overlapping migration namespaces: "
                f"{previous_owner}=V{previous_start}-V{previous_end},"
                f"{current_owner}=V{current_start}-V{current_end}"
            )
    return ranges


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--owner", required=True, help="owner package from .planning/SCHEMA-OWNERSHIP.md")
    parser.add_argument("--check", help="candidate prefix such as V2")
    args = parser.parse_args()

    repository = args.repo.resolve()
    migration_dir = repository / "core/src/main/resources/db/migration"
    if not migration_dir.is_dir():
        parser.error(f"migration directory not found: {migration_dir}")

    try:
        owner_ranges = load_owner_ranges(repository / ".planning/SCHEMA-OWNERSHIP.md")
    except (OSError, UnicodeError, ValueError) as error:
        print(f"ERROR={error}", file=sys.stderr)
        return 2
    selected_ranges = owner_ranges.get(args.owner, [])
    if not selected_ranges:
        print(f"ERROR=unregistered schema owner: {args.owner}", file=sys.stderr)
        return 2
    if len(selected_ranges) != 1:
        namespaces = ",".join(f"V{start}-V{end}" for start, end in selected_ranges)
        print(f"ERROR=schema owner has multiple migration namespaces: {args.owner}={namespaces}", file=sys.stderr)
        return 2

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

    range_start, range_end = selected_ranges[0]
    next_version = next((version for version in range(range_start, range_end + 1)
                         if version not in version_files), None)
    if next_version is None:
        print(
            f"ERROR=migration namespace exhausted: {args.owner}=V{range_start}-V{range_end}",
            file=sys.stderr,
        )
        return 2

    current_max = max(version_files, default=0)
    next_prefix = f"V{next_version}"
    print(f"MIGRATION_DIR={migration_dir}")
    print(f"OWNER={args.owner}")
    print(f"RANGE=V{range_start}-V{range_end}")
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
