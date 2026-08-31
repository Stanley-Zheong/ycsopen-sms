---
phase: 03-crypto-storage-bootstrap
plan: "01"
subsystem: database-security
tags: [flyway, mysql, protected-data, envelope-encryption, validation]

requires:
  - phase: 01-engineering-verification-foundation
    provides: Real MySQL harness, planning validators, and immutable baseline verification
provides:
  - Owner-scoped Flyway selection constrained to the registered V1200-V1299 range
  - Exact V1 checksum and declared-versus-applied migration-set verification
  - Fail-closed inventory for protected database fields, object references, legacy digests, candidates, and executable source surfaces
affects: [03-02-evidence-contract, phase03-migrations, protected-persistence, protected-object-storage]

tech-stack:
  added: []
  patterns:
    - Registry-driven migration namespace allocation
    - Capacity-computed protected-data inventory with destructive mutation tests
    - Explicit blocked readiness until every current plaintext surface adopts the protected boundary

key-files:
  created:
    - core/src/main/resources/security/protected-data-inventory.json
    - .planning/tools/phase3-protected-inventory.rb
    - .planning/tools/validate-phase-03-protected-inventory.rb
    - .planning/tools/test-phase-03-protected-inventory.rb
    - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/schema/protected-data-inventory.schema.json
  modified:
    - skills/flyway-migration/scripts/next_flyway_version.py
    - skills/flyway-migration/tests/test_next_flyway_version.py
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java

key-decisions:
  - "Resolve migration versions only through the exact schema-owner registry and reject malformed, overlapping, colliding, or exhausted namespaces."
  - "Treat inventory completeness separately from obligation readiness: the inventory may pass while current plaintext surfaces keep OBL-CRYPTO-STORAGE-001 blocked."
  - "Keep bulk and uplink mobile columns protected while recording EXCLUDED_NO_EQUALITY_CONTRACT rather than inventing blind indexes without a V1 digest or current equality owner."

patterns-established:
  - "Schema/source reconciliation: every discovered protected target, digest, candidate, and executable surface must have one exact manifest disposition."
  - "Fail-closed evidence readiness: unresolved current implementation facts remain blockers and cannot coexist with a ready status."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
metrics:
  tasks: 2
  files: 8
---

# Phase 03 Plan 01: Namespace and Protected Inventory Summary

**Owner-bound V1200 Flyway allocation plus a capacity-computed inventory that reconciles 17 inline fields, seven protected object references, five legacy digests, 15 candidates, and six current source surfaces without authorizing premature obligation evidence.**

## Accomplishments

- Constrained `crypto-storage-bootstrap` migrations to the registered `V1200-V1299` namespace, selecting `V1200` as the first globally unused version and rejecting malformed registry rows, overlaps, filename errors, duplicate versions, wrong checks, and exhaustion.
- Preserved immutable V1 verification at SHA-256 `fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9` and compared successful real-MySQL Flyway history against the complete declared source-version set.
- Added a canonical manifest and JSON Schema covering 24 protected targets, five legacy digest targets, 15 reviewed candidates, and six executable source surfaces.
- Added a fail-closed Ruby validator and 17-case destructive suite for target disappearance, unresolved disposition, capacity drift, digest drift, source/runtime drift, unknown writers/candidates, raw URL handling, and false readiness.

## Task Commits

1. **Task 1: Enforce owner-range Flyway selection and immutable V1** — `f817d59`
2. **Task 2: Build the capacity-computed fail-closed inventory** — `c53b74a`

## Inventory Contract

| Inventory set | Count | Accepted disposition |
| --- | ---: | --- |
| Inline protected database fields | 17 | `PROTECTED` with computed YCSE/v1 capacity |
| Protected object references | 7 | Opaque protected-object ID, ceiling 64 bytes |
| Legacy digest migration targets | 5 | Exact legacy SHA-256 migration union |
| Reviewed candidates | 15 | Explicit exclusion, non-protected rationale, or non-executable future owner |
| Current executable source surfaces | 6 | Explicitly blocking until protected-boundary adoption |

YCSE/v1 maximum overhead is 145 bytes. The in-place `VARBINARY(255)` plaintext ceiling is 110 bytes. The deterministic capacity table digest is `sha256:1db7943a5eec3d29209346d191fc8b2ab1582b90c9cc86f3f3a85cd7f80499d0`; the complete manifest digest is `sha256:bdc5fe83278ff9da9c3f16f84da8aa7955b0ed49d72a7d3c0727f587b7d6939a`.

## Exact Current Source Surfaces

1. `message-submit-persistence` — `MessageSubmitService`, `MessageTask`, and `MessageTaskRepository`.
2. `tenant-registration-persistence` — `TenantController`, `TenantRegistrationRequest`, `TenantService`, and `TenantRepository`.
3. `auth-user-hydration-save` — `AuthService`, `User`, and `UserRepository`.
4. `hmac-api-key-hydration` — `HmacAuthInterceptor`, `TenantApiKey`, and `TenantApiKeyRepository`.
5. `blacklist-lookup-hydration` — `BlacklistChecker`, `BlacklistEntry`, and `BlacklistEntryRepository`.
6. `tenant-lifecycle-analytics-hydration-save` — `TenantService`, `ComplaintRatioService`, `Tenant`, and `TenantRepository`.

## Verification

- `python3 skills/flyway-migration/tests/test_next_flyway_version.py` — PASS, six cases.
- `python3 skills/flyway-migration/scripts/next_flyway_version.py --owner crypto-storage-bootstrap --check V1200` — PASS, owner and range resolved exactly.
- `/usr/bin/env ruby .planning/tools/test-phase-03-protected-inventory.rb` — PASS, 17 destructive cases.
- `/usr/bin/env ruby .planning/tools/validate-phase-03-protected-inventory.rb ... --acceptance` — PASS, with six implementation blockers retained.
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner crypto-storage-bootstrap --assert-unique --assert-traced` — PASS, four owned obligations selected.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — PASS.
- `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 03 ... --entry-evidence ...` — PASS, 30 plans and one dependency validated after the roadmap position update.
- `mvn -f core/pom.xml -Pphase01-integration -Dtest=Phase01MySqlIntegrationTest test` — PASS, two real-MySQL tests.
- `mvn -f core/pom.xml test` — PASS, 35 tests with six expected profile-gated skips and no failures or errors.

## Decisions Made

- Inventory acceptance proves completeness and consistency only. It does not close storage obligations while `obligation_readiness` is `BLOCKED_BY_CURRENT_IMPLEMENTATION`.
- Schema-only protected columns stay mandatory migration targets even when current Java has no mapping. Empty reader/writer arrays on those rows are deliberate facts verified against source discovery.
- `bulk_sending_items.mobile_encrypted` and `uplink_records.mobile_encrypted` remain in the protected union with `EXCLUDED_NO_EQUALITY_CONTRACT`; later work must not silently drop either row.

## Deviations from Plan

None — plan implementation stayed within the declared files and behavior.

## Known Stubs and Blocking Implementation Facts

The inventory itself contains no completion-blocking stub. It intentionally records the following existing implementation gaps, which later Phase 03 plans must replace before OBL-CRYPTO-STORAGE-001 can pass:

- Message submission still persists a plaintext phone value and unkeyed digest.
- Tenant registration drops protected identity/contact inputs and persists raw proof URLs.
- User hydration/save crosses the protected `phone_encrypted` mapping without a persistence boundary.
- HMAC API-key lookup hydrates the protected secret instead of a secret-excluding projection.
- Blacklist equality lookup hydrates the protected mobile field and uses the legacy digest.
- Tenant lifecycle and analytics paths hydrate or save the full protected entity without exact-byte preservation/projections.

## Threat Surface Review

No new network endpoint, authentication path, production file-access path, or database schema was introduced. The new repository-local validator implements the plan's declared registry-tampering, V1 repudiation, metadata-only inventory, source reconciliation, and fail-closed deferral mitigations.

## Remaining Scoped TODO State

All Phase 03 TODO rows remain open. In particular, OBL-CRYPTO-STORAGE-001 and OBL-CRYPTO-STORAGE-004 are not complete: this plan supplies namespace and inventory prerequisites, while cryptographic persistence, migration execution, evidence production, independent verification, review, and delivery attestation remain owned by later plans.

## Self-Check: PASSED

- All eight planned implementation/test artifacts exist.
- Task commits `f817d59` and `c53b74a` exist on `phase/03-crypto-storage-bootstrap`.
- Both task verification commands and the complete plan verification set pass.
- The authoritative Phase 03 TODO file retains every obligation row unchecked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` with no progress or percent field.
