# Phase 03 Linux temporary-root CI failure

Status: resolved locally; remote replay pending

## Symptom

GitHub Actions run `33997060968` passed the Phase 01 portable job but failed the required `Phase 03 portable registry` job in four `ProductionMigrationCommandServicesFactoryTest` cases. The expected migration-domain result was replaced by `IllegalStateException: migration configuration is unavailable`.

## Investigation

- The production factory deliberately rejects a configuration or snapshot path when any ancestor is group- or world-writable.
- The test relied on JUnit `@TempDir`. On the Linux runner that directory is rooted under `/tmp`, whose permissions include group/world write; on the local macOS runner JUnit selected a private user-owned root.
- A Linux Java probe reproduced the exact precondition: the generated path had a writable `/tmp` ancestor and was rejected by the production rule.
- No production policy was changed. The test now creates its fixture beneath the canonical real user home and removes it after each test.

## Verification

- Focused macOS replay: 11 tests, 0 failures, 0 errors.
- Full local Maven replay: 365 tests, 0 failures, 0 errors, 17 integration-gated skips.
- Focused Linux Java 21 container replay with the repository mounted read-only: 11 tests, 0 failures, 0 errors.
- Canonical fixed-subject and remote required-check results remain pending and are tracked by the authoritative Phase 03 TODO.

## Root cause and resolution

The test fixture accidentally inherited a platform-specific, world-writable ancestor that production correctly distrusts. The minimal correction is to place the fixture under a trusted user-owned directory. This preserves the shipped trust boundary and makes the test represent an admissible production configuration on both macOS and Linux.

Files changed by this correction:

- `core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProductionMigrationCommandServicesFactoryTest.java`
