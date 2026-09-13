# Issue 90 Release Tenant Status Action Verification

## Executed passes

| Surface | Command | Result |
| --- | --- | --- |
| Regression before the seed fix | `mvn -f core/pom.xml -Dtest=ReleaseAcceptanceSeedMigrationTest test` | Expected failure: 2 tests ran and both found zero `DEV-TENANT` account rows. |
| Regression after the seed fix | `mvn -f core/pom.xml -Dtest=ReleaseAcceptanceSeedMigrationTest test` | PASS: 2 tests, 0 failures. Fresh creation and repeatability with operator-managed balance, frozen amount, status, and version are covered. |
| Frontend install | `npm --prefix web ci` | PASS. npm reported 7 existing dependency advisories; no dependency changed. |
| Frontend unit suite | `npm --prefix web test` | PASS: 51 files, 157 tests. The existing tenant-qualification suite passed 15 tests. |
| Frontend production build | `npm --prefix web run build` | PASS. Vite emitted its existing large-chunk advisory. |
| Docker acceptance discovery | `npm --prefix web run test:docker-release -- --list` | PASS: 2 tests discovered, including `pw-issue-90-release-tenant-status-action`. |
| Changed acceptance lint | From `web/`: `npx eslint test/docker-release/release-acceptance.spec.ts --max-warnings 0` | PASS. |
| Release script syntax | `bash -n scripts/verify-docker-release` | PASS. |
| Diff whitespace | `git diff --check` | PASS. |

## Verification boundaries

- `mvn -f core/pom.xml test` was executed. Three unrelated suites failed on
  host prerequisites: production migration configuration was unavailable,
  `/usr/bin/env ruby` was absent, and the container's PID 1 did not reap owned
  process-test descendants. The issue-owned migration regression passed in the
  same workspace.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` could not
  start because this host has no Ruby executable.
- The local Docker daemon did not return server information and the release
  script's `/usr/bin/google-chrome` executable was absent, so the MySQL/Chrome
  acceptance was not claimed locally. Changes under `scripts/` and `web/test/`
  route the pull request through the repository's `Docker release / Google
  Chrome` check, which owns that result.
- The repository code-review precheck passed its diff whitespace step and then
  stopped on direct stdout calls already present in unchanged Java test files.
- Claude Code 2.1.220 was installed, but its read-only review could not start
  because the CLI was not logged in. The independent agent review found no
  BLOCKER or HIGH findings; pull-request checks remain authoritative for merge.

## Pull-request correction

The first Docker release run proved the Issue 90 MySQL row and Chrome action,
but the pre-existing release-identity case found that the Web artifact embedded
the pull-request merge SHA instead of the checked-out head SHA. The release
script now rebuilds `web/dist` with `VITE_BUILD_COMMIT` set to its validated
`BUILD_COMMIT` before composing either environment. A later pull-request run
must pass the complete Docker release job before merge.

The next run passed both Chrome cases and the new tenant-account SQL assertion,
then exposed an existing prefix check that counted `1380013` across both the
development and release fixture versions. That check now joins
`number_prefix_versions` and scopes the count to `DEV-PREFIX-2026-09`. A later
run must still pass fresh, upgrade, and restart acceptance before merge.

The complete Docker release job subsequently passed. The remaining Phase 03
real-integration failure also occurs on the latest `main` run that exercised
that job (`34732724573`): its Phase 3-only fixtures allowed Flyway to apply later
V1400-V5800 migrations despite asserting the V1/V1200/V1201 boundary. The two
owning Phase 3 test classes now pin both Spring-managed and programmatic Flyway
entrypoints to target `1201`; a fresh CI run must prove the correction against
real MySQL, MinIO, and SoftHSM before merge.

That run passed both Phase 3 migration tests, including the production snapshot
proof. The protected-persistence child then revealed that the base `dev`
profile still expanded its Spring-managed Flyway locations to include release
repeatables, which are not bounded by a version target. Its command-line test
configuration now restricts locations to `classpath:db/migration` as well as
target `1201`; the real-integration suite must pass on the next run.

The location restriction then let protected persistence reach its real lookup
proof, where the V1201 target omitted `blacklist_entries.effective_at` and
`expires_at`, columns required by the current lookup service and added by
V2500. The migration/snapshot proof remains pinned to its V1201 boundary; the
protected-persistence proof now applies all production versioned migrations
from the isolated `db/migration` location while still excluding dev and release
repeatables. That exposed a timestamp-precision defect in the current metadata
lookup: a row inserted with the microsecond `effective_at` default could compare
as later than second-precision `CURRENT_TIMESTAMP` for the rest of that second.
The production predicate now compares against `CURRENT_TIMESTAMP(6)` and the
fixture retains the real default-timestamp write semantics, so the
metadata-only assertion proves immediate activation. A fresh real-integration
run remains required.
