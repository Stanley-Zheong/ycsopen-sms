---
phase: 03-crypto-storage-bootstrap
plan: "12"
subsystem: database-security
tags: [migration, manifest, classification, preflight, zero-mutation]

requires:
  - phase: 03-crypto-storage-bootstrap/03-01
    provides: reviewed protected-data inventory and source acceptance fence
  - phase: 03-crypto-storage-bootstrap/03-04
    provides: strict YCSE/v1 parser and canonical capacity contract
  - phase: 03-crypto-storage-bootstrap/03-11
    provides: paired-admission, target, checkpoint and key metadata shapes
provides:
  - Typed allowlisted protected-data manifest with exact canonical digest binding
  - Magic-first legacy classifier with no malformed-YCSE fallback
  - Zero-mutation preflight for target, schema, capacity, checkpoint, binding, writer and key readiness
  - Inseparable writer/snapshot port contract with no role-only acceptance operation
affects: [03-13-migration-runner, 03-14-real-migration, 03-27-signed-pair-admission]

tech-stack:
  added: []
  patterns:
    - Exact reviewed SQL-identifier allowlist
    - Static checks before paired admission
    - Five-counter zero-mutation rejection observation

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataTarget.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataManifest.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/LegacyValueClassifier.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationPreflight.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/WriterFencePort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/migration/EncryptedSnapshotVerifier.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataManifestTest.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/migration/LegacyValueClassifierTest.java
  modified: []

key-decisions:
  - "Bind migration targets to the exact reviewed inventory byte digest and closed target/candidate/source-surface identifier sets."
  - "Expose writer and snapshot verification only through one PairedBoundary method; neither role has an independently accepted state."
  - "Run manifest, target, schema, capacity, row-binding, checkpoint and key checks before the paired boundary can execute."

patterns-established:
  - "Magic-first classification: any YCSE-prefixed invalid byte string is CORRUPT and cannot fall back to legacy."
  - "Preflight rejection checks pair-admission, lease, checkpoint, event and business-row counters for exact zero change."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 12: Typed Migration Preflight Summary

**A digest-bound typed inventory, strict magic-first classifier and inseparable writer/snapshot admission seam now reject every unresolved migration prerequisite before row mutation.**

## Accomplishments

- Loaded the reviewed inventory only from bounded canonical UTF-8 JSON whose exact SHA-256 digest is supplied by the caller; duplicate JSON keys, line-ending drift, digest drift, unknown identifiers, missing identifiers and disposition/capacity changes fail closed.
- Materialized 17 protected database fields, seven protected object references and five legacy digest targets as immutable typed records with explicit legacy, null, row-binding and blind-index rules.
- Classified only `VALID_ENVELOPE`, `APPROVED_LEGACY`, `CORRUPT`, `AMBIGUOUS` and `NULL_ALLOWED`; malformed magic-prefixed bytes are always corrupt.
- Required the exact target set, canonical storage bounds, runtime schema, context, primary key, checkpoint transition, legacy fallback state, deployed writer, row binding, orphan count and ACTIVE key set before paired admission.
- Preserved `bulk_sending_items.mobile_encrypted` and `uplink_records.mobile_encrypted` as mandatory no-index field targets and rejected any equality-index schema or row invention.
- Preserved `third_party_risk_check_logs.mobile_hash` as schema-only migratable with its V1 index, no current Java reader/writer and a resolved deployed-writer fence.

## Task Commit

1. **Task 1: Implement manifest, strict classifier and migration preflight** — `81fbdac` (`feat`)

## Typed Preflight Matrix

| Boundary | Accepted state | Rejected state | Mutation boundary |
| --- | --- | --- | --- |
| Manifest | Exact reviewed target/candidate/surface sets and supplied canonical digest | Digest drift, duplicate/unknown/missing ID, `REVIEW_REQUIRED`, current/migratable deferral, blocking surface | Pair boundary not invoked |
| Classification | Strict YCSE/v1, reviewed nonmagic rule, or explicit nullable target | Magic-prefixed invalid bytes, over-bound bytes, invalid UTF-8/legacy grammar, forbidden null | No migration collaborator invoked |
| Schema/capacity | Every target present with canonical capacity, context and primary key | Schema drift, over-capacity value, missing context/PK | Pair boundary not invoked |
| Blind index/checkpoint | Complete row binding, zero orphans, valid adjacent transition, no fallback after `COMPLETE` | Missing index, orphan, checkpoint jump/regression, completed fallback | Pair boundary not invoked |
| No-index fields | Both mandatory fields present with no equality-index schema or rows | Either target missing or any invented equality index | Pair boundary not invoked |
| Risk-log digest | V1 schema/index present, schema-only migration, no Java surface, deployed writer known | Missing index, invented Java reader/writer, unknown deployed writer | Pair boundary not invoked |
| Keys | Provider available and exactly one ACTIVE field, mobile-index and snapshot-recovery version | Provider outage, missing/duplicate ACTIVE, duplicate version | Pair boundary not invoked |
| Writer/snapshot | One successful `PairedBoundary.verifyAndAdmit` result | Any writer, snapshot, signature, subject, replay or atomic-admission rejection | Rejection must retain all five counters |

## Paired-Port Contract

`WriterFencePort` and `EncryptedSnapshotVerifier` deliberately expose no standalone verification or accepted result. The only executable method is `WriterFencePort.PairedBoundary.verifyAndAdmit(PairedAdmissionRequest)`, which receives all four manifest/signature paths and the expected deployment subject in one request. A returned `PairedAdmission` represents the complete combined result only. Plan 27 owns bounded schema/signature verification and the production atomic compare-and-set behind this seam.

## Zero-Mutation Results

The deterministic rejection matrix observes these mutable counts before and after every failed preflight:

| Counter | Rejected result |
| --- | ---: |
| Pair-admission writes | 0 |
| Lease writes | 0 |
| Checkpoint writes | 0 |
| Event writes | 0 |
| Business-row writes | 0 |

Static rejection cases also assert that the paired boundary was never called. Covered cases include unresolved manifest, missing target, missing context/PK, capacity overflow, schema drift, missing ACTIVE key, key-provider outage, blind-index orphan/missing schema, no-index invention, risk-log unknown writer, invalid checkpoint transition and legacy fallback after `COMPLETE`. A paired-boundary rejection separately proves all five mutation counts remain unchanged.

## Verification

- `mvn -f core/pom.xml -Dtest='ProtectedDataManifestTest,LegacyValueClassifierTest' test` — PASS, nine tests with no failure, error or skip.
- `/usr/bin/env ruby .planning/tools/validate-phase-03-protected-inventory.rb --manifest core/src/main/resources/security/protected-data-inventory.json --schema core/src/main/resources/db/migration/V1__init_schema.sql --source-root core/src/main/java --acceptance` — PASS; the existing tenant-registration implementation blocker remains explicit.
- `mvn -f core/pom.xml test` — PASS, 168 tests with no failure or error; 11 real-service/profile-gated tests were intentionally skipped by the ordinary lane.
- `git diff --check` — PASS.
- Canonical checked-in manifest digest observed by this plan: `sha256:1f84db570676823dc979e43611d31b3abea4a36607b77a9fe7f31766a25c77fd`.

## Acceptance Criteria

- **PASS — explicit accepted classes:** the classifier enum is closed and every legacy acceptance depends on the reviewed target rule.
- **PASS — complete fail-closed admission:** unresolved target, capacity, writer, snapshot and key failures occur before any SQL-update collaborator can run.
- **PASS — source inventory acceptance:** all 17 inline, seven object, five digest and six current source-surface records remain reconciled.

## Decisions Made

- Exact manifest bytes, not a caller-reformatted JSON tree, are the canonical reviewed artifact; digest comparison is constant-time and the reader is bounded before parsing.
- SQL identifiers live only in the compiled reviewed allowlist and the typed manifest. Runtime requests provide observations keyed by those identifiers, never dynamic identifiers for SQL construction.
- The paired boundary owns successful atomic admission. Preflight performs no post-admission role check that could turn a committed pair into a failed overall result.

## Deviations from Plan

None — implementation stayed within the eight declared task files and preserved the Plan 27 production ownership boundary.

## Issues Encountered and Verification Boundary

- The first focused invocation exposed an invalid exception-constructor call at compilation. It was corrected before the task commit; all focused and full backend tests then passed.
- The post-plan Wave 6 gate found an older source-drift fixture that attempted to replace `setMobileEncrypted`, a token removed by Plan 26. The gate replaced it with the current reviewed `messageTaskProtectionAdapter.save` token and added an explicit mutation assertion. The complete 17-case destructive inventory suite and real source-inventory acceptance command both pass; this repair is recorded separately from the Plan 03-12 task commit.

## Known Stubs

None in this plan. The production Ed25519/schema/atomic-CAS paired adapter is an explicit later-plan implementation owned by Plan 27, not a fallback or independently accepted stub.

## Threat Surface Review

- The new bounded regular-file manifest loader is the declared P03-12-T1 file-access surface. It rejects symlinks, noncanonical UTF-8, over-limit input, duplicate keys and digest drift.
- No network endpoint, authentication route, secret, production data, schema change, dynamic SQL execution or external configuration was added.

## Remaining Scoped TODO State

The authoritative Phase 03 TODO file contains 22 open rows. All four Phase 03 obligation rows remain unchecked, `requirements-completed` remains empty, and this plan makes no phase-completion claim.

## Self-Check: PASSED

- All eight declared implementation/test artifacts exist.
- Task commit `81fbdac` exists in git history and contains no tracked deletion.
- Focused tests, source-inventory acceptance, full backend regression and diff checks pass at the recorded boundary.
- `STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`.
- The scoped TODO query remains nonempty with 22 open rows; no obligation or requirement was closed.
