#!/usr/bin/env python3
"""Regression tests for the owner-aware Flyway version selector."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
SELECTOR = REPOSITORY_ROOT / "skills/flyway-migration/scripts/next_flyway_version.py"


class NextFlywayVersionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary_directory.name)
        self.migration_dir = self.repo / "core/src/main/resources/db/migration"
        self.migration_dir.mkdir(parents=True)
        planning = self.repo / ".planning"
        planning.mkdir()
        (planning / "SCHEMA-OWNERSHIP.md").write_text(valid_registry(), encoding="utf-8")
        self.write_migration("V1__init_schema.sql")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_migration(self, name: str) -> None:
        (self.migration_dir / name).write_text("SELECT 1;\n", encoding="utf-8")

    def run_selector(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(SELECTOR), "--repo", str(self.repo), *arguments],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_selects_first_globally_unused_version_inside_owner_range(self) -> None:
        result = self.run_selector("--owner", "crypto-storage-bootstrap", "--check", "V1200")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("OWNER=crypto-storage-bootstrap", result.stdout)
        self.assertIn("RANGE=V1200-V1299", result.stdout)
        self.assertIn("NEXT=V1200", result.stdout)
        self.assertIn("CHECK_RESULT=PASS", result.stdout)

        self.write_migration("V1200__crypto_metadata.sql")
        self.write_migration("V1202__reserved_gap.sql")
        result = self.run_selector("--owner", "crypto-storage-bootstrap")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("NEXT=V1201", result.stdout)

    def test_rejects_check_outside_selected_owner_range(self) -> None:
        result = self.run_selector("--owner", "crypto-storage-bootstrap", "--check", "V2")

        self.assertEqual(2, result.returncode)
        self.assertIn("CHECK_RESULT=FAIL", result.stdout)

    def test_rejects_unknown_owner_and_malformed_registry(self) -> None:
        unknown = self.run_selector("--owner", "not-registered")
        self.assertEqual(2, unknown.returncode)
        self.assertIn("unregistered schema owner", unknown.stderr)

        registry = self.repo / ".planning/SCHEMA-OWNERSHIP.md"
        registry.write_text(valid_registry().replace("V1200-V1299", "V1200..V1299"), encoding="utf-8")
        malformed = self.run_selector("--owner", "crypto-storage-bootstrap")

        self.assertEqual(2, malformed.returncode)
        self.assertIn("malformed migration namespace", malformed.stderr)

    def test_rejects_overlapping_registry_ranges(self) -> None:
        registry = self.repo / ".planning/SCHEMA-OWNERSHIP.md"
        registry.write_text(
            valid_registry().replace("V1300-V1399", "V1299-V1399"),
            encoding="utf-8",
        )

        result = self.run_selector("--owner", "crypto-storage-bootstrap")

        self.assertEqual(2, result.returncode)
        self.assertIn("overlapping migration namespaces", result.stderr)

    def test_rejects_unsupported_names_and_duplicate_versions_globally(self) -> None:
        self.write_migration("V2_1__dotted.sql")
        malformed = self.run_selector("--owner", "crypto-storage-bootstrap")
        self.assertEqual(2, malformed.returncode)
        self.assertIn("unsupported Flyway SQL filename", malformed.stderr)

        (self.migration_dir / "V2_1__dotted.sql").unlink()
        self.write_migration("V1__duplicate.sql")
        duplicate = self.run_selector("--owner", "crypto-storage-bootstrap")

        self.assertEqual(2, duplicate.returncode)
        self.assertIn("duplicate Flyway version", duplicate.stderr)

    def test_rejects_exhausted_owner_range(self) -> None:
        for version in range(1200, 1300):
            self.write_migration(f"V{version}__occupied.sql")

        result = self.run_selector("--owner", "crypto-storage-bootstrap")

        self.assertEqual(2, result.returncode)
        self.assertIn("migration namespace exhausted", result.stderr)


def valid_registry() -> str:
    return """# Schema Ownership and Migration Conflict Contract

| Schema ID | PRD data domain | Schema object/prefix | Owner package | Migration namespace | Dependencies | Compatibility | Rollback | Cross-owner protocol |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SCHEMA-LEGACY-BASELINE | Legacy | legacy.* | engineering-verification-foundation | V0001-V0999 | - | expand-migrate-contract | rollback=forward-fix | approval |
| SCHEMA-P03 | Crypto | ycs.sms.crypto.* | crypto-storage-bootstrap | V1200-V1299 | engineering-verification-foundation | expand-migrate-contract | rollback=forward-fix | approval |
| SCHEMA-P04 | Messages | ycs.sms.messages.* | platform-system-message-bootstrap | V1300-V1399 | crypto-storage-bootstrap | expand-migrate-contract | rollback=forward-fix | approval |
"""


if __name__ == "__main__":
    unittest.main()
