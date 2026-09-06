---
phase: 03-crypto-storage-bootstrap
plan: "02"
subsystem: security-evidence
tags: [ruby, json-schema, evidence, cryptographic-storage, fail-closed-validation]

requires:
  - phase: 03-crypto-storage-bootstrap-01
    provides: Accepted protected-data inventory, exact no-index targets, and current source-surface dispositions
provides:
  - Closed JSON contracts for exactly four Phase 03 obligation results and their manifest
  - Digest-bound validation of canonical tested inputs, accepted inventory, complete leak results, and external child results
  - OBL-001 rejection of unresolved current/migratable storage surfaces and missing no-index migration targets
affects: [03-19-leak-proof, 03-22-evidence-production, 03-23-evidence-finalization]

tech-stack:
  added: []
  patterns:
    - External child-result binding instead of self-authored PASS facts
    - Closed real-versus-deterministic adapter identity registry
    - Recursive prohibited-content rejection over durable evidence documents

key-files:
  created:
    - .planning/tools/phase3-crypto-evidence.rb
    - .planning/tools/validate-phase-03-crypto-evidence.rb
    - .planning/tools/test-phase-03-crypto-evidence.rb
    - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/schema/phase03-obligation-evidence.schema.json
    - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/schema/phase03-evidence-manifest.schema.json
  modified: []

key-decisions:
  - "A Phase 03 PASS must bind live-tested input hashes, the canonical inventory digest, a complete leak result, and fixed external child-result identities and digests."
  - "OBL-CRYPTO-STORAGE-001 revalidates inventory semantics and rejects unresolved current surfaces, forbidden deferral, capacity conflict, raw-URL writers, and loss or misclassification of either no-index mobile target."
  - "Durable evidence permits only closed adapter identities and sanitized digest/count facts; prohibited protected content and absolute paths fail closed."

patterns-established:
  - "Exact-four closure: catalog, TEST-MATRIX, manifest order, evidence path, behavior, Test ID/layer, and case ID must agree independently."
  - "Digest chain: manifest -> obligation evidence -> child result, inventory validator result, leak result, and canonical tested inputs."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
metrics:
  tasks: 1
  files: 5
---

# Phase 03 Plan 02: Exact-Four Cryptographic Evidence Contract Summary

**Closed, fail-closed evidence schemas and a Ruby validator now require four independently traced obligation results backed by canonical live inputs, accepted inventory, complete leak coverage, and fixed external child-result digests.**

## Accomplishments

- Defined obligation schema `phase03-obligation-evidence-v1` and manifest schema `phase03-evidence-manifest-v1`, with the manifest fixed to four ordered entries.
- Bound each obligation to its exact requirement, behavior, catalog Test ID/layer, case ID, and evidence target from both the catalog and Phase 03 TEST-MATRIX.
- Required live path/mode/SHA-256 validation for canonical tested inputs and same-subject binding for inventory, leak, and child results.
- Added closed REAL/DETERMINISTIC adapter identities for MySQL 8, MinIO, Java 21 SunPKCS11, SoftHSM 2.7.0, and deterministic validators/scanners.
- Added 33 contract cases: two positive exact-four fixtures and 31 destructive mutations covering trace, subject, adapter, inventory, leak, digest, schema, and prohibited-content forgery.

## Task Commits

1. **Task 1: Implement exact-four evidence validation** — `1afe481`

## Evidence Contract

| Binding | Fail-closed requirement |
| --- | --- |
| Exact-four trace | Catalog and TEST-MATRIX must independently equal the closed obligation registry. |
| Tested subject | Every sorted input has a live repository-relative regular file, exact mode, exact SHA-256, and canonical aggregate digest. |
| Inventory | Literal file checksum, canonical accepted digest, deterministic validator result digest, READY status, and zero blocking dispositions must agree. |
| Leak proof | Database cells, object bytes, logs, reports, and evidence all have positive scan counts, zero prohibited matches, and seeded-mutation sensitivity. |
| Child results | One fixed check ID/layer per obligation, same subject digest, PASS/zero exit, result digest, file checksum, and exact adapter identity set. |
| Sanitization | Plaintext canaries, key material, PINs, raw tokens/URLs, ciphertext bodies, provider text, phone-shaped plaintext, and absolute paths are rejected. |

The obligation schema SHA-256 is `ebdd74e243467c2d4ba0ce5caeac1bfa16b92eca745b54d4c57c527ab690ae9a`. The manifest schema SHA-256 is `e4fb6d52ff4e55de232b4edec7b55c5a2a8625ddd4dcf2db93cc6ec8ed705985`.

## OBL-001 Inventory Gate

OBL-CRYPTO-STORAGE-001 cannot validate when any of these facts is present:

- inventory readiness is not `READY` or blocking surface IDs remain;
- a protected/digest target is `DEFERRED_OWNER`;
- a deferred candidate is executable or migratable;
- `REVIEW_REQUIRED` occurs anywhere in the inventory;
- a protected target has a capacity conflict;
- a required current source surface is missing, unknown, stale, or not `PROTECTED_BOUNDARY_ADOPTED`;
- a declared current writer retains a raw URL setter/input;
- `bulk_sending_items.mobile_encrypted` or `uplink_records.mobile_encrypted` is absent or lacks `EXCLUDED_NO_EQUALITY_CONTRACT`.

## Verification

- `/usr/bin/env ruby .planning/tools/test-phase-03-crypto-evidence.rb` — PASS, 33 cases: two positive fixtures and 31 rejected destructive mutations; no repository PASS target produced.
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner crypto-storage-bootstrap --assert-unique --assert-traced` — PASS, exactly four selected obligations across 522 valid catalog rows.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — PASS.
- Ruby warning-enabled syntax checks for all three new tools — PASS.
- JSON parsing for both schemas and `git diff --check` — PASS.

## Decisions Made

- Obligation JSON cannot supply its own authority. It references checked files whose byte checksum and canonical result digest are recomputed by the validator.
- Real adapter claims are closed to exact identities and modes rather than arbitrary labels. Deterministic validators and scanners cannot impersonate real service adapters.
- The inventory validator result is necessary but not sufficient: the evidence validator independently checks readiness, deferral, capacity, current source-surface closure, raw URL writers, and both no-index targets.
- Digest-shaped fields are syntax-checked and excluded from plaintext phone-pattern scanning, preventing valid hashes from being mistaken for protected phone values.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected closed-field set initialization**

- **Found during:** Task 1 positive fixture execution.
- **Issue:** Initial Ruby `Set` construction treated each field list as one array member, causing every valid document field to appear both missing and unknown.
- **Fix:** Constructed every field and target set from individual string members.
- **Files modified:** `.planning/tools/phase3-crypto-evidence.rb`.
- **Verification:** Both positive fixtures and all destructive fixtures pass their expected verdicts.
- **Committed in:** `1afe481`.

**2. [Rule 1 - Bug] Excluded validated digest fields from phone-pattern scanning**

- **Found during:** Task 1 positive fixture execution.
- **Issue:** A legitimate SHA-256 result digest could contain an incidental 11-digit substring and be rejected as phone-shaped plaintext.
- **Fix:** Kept strict SHA/digest validation and skipped protected-value pattern matching only for those validated digest fields.
- **Files modified:** `.planning/tools/phase3-crypto-evidence.rb`.
- **Verification:** Positive fixtures validate while an explicit phone-shaped non-digest fact remains prohibited.
- **Committed in:** `1afe481`.

**Total deviations:** 2 auto-fixed Rule 1 implementation bugs. **Impact:** Both fixes were necessary for correct fail-closed behavior and did not expand plan scope.

## Issues Encountered

None remain after the two test-discovered validator corrections.

## Known Stubs

None. The plan intentionally defines contracts and destructive fixtures; real result production remains assigned to later Phase 03 plans and is not represented as complete here.

## Threat Surface Review

The repository-local validator adds bounded evidence file reads, exact path containment, regular-file/link checks, byte checksums, canonical digests, closed adapter identities, and recursive prohibited-content scanning. These surfaces implement the plan's repudiation, spoofing, disclosure, and tampering mitigations. No production network endpoint, authentication path, database schema, secret, production data, or external service configuration was added.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan produced schema and validation contracts only; it produced no `EVIDENCE/evidence-manifest.json` and no `EVIDENCE/OBL-CRYPTO-STORAGE-00*.json` PASS target. Runtime crypto, storage, migration, leak evidence, independent review, and delivery attestation remain later-plan work.

## Self-Check: PASSED

- All five planned implementation/test/schema files exist.
- Task commit `1afe481` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- The complete task verification and planning-validator self-test pass after the committed implementation.
- The Phase 03 evidence manifest and all four obligation PASS targets remain absent from the repository.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no progress or percent field was added.
