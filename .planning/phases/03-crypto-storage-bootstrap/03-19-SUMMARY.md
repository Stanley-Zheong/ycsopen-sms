---
phase: 03-crypto-storage-bootstrap
plan: "19"
subsystem: multi-surface-leak-proof
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 19: Multi-surface leak proof

Implemented a fail-closed synthetic-canary scanner across the exact database, log, private-object, evidence and generated-report surface union.

## Result

- Java owns bounded runtime reads for `database-cells`, `logs` and `object-bytes`; Ruby independently owns repository evidence and generated reports.
- The only public combination command accepts the exact three runtime surfaces plus the strict-digest two-target artifact report and emits one sanitized five-target result.
- Missing, duplicate, reordered, unreadable, escaped, linked, oversized, direct, encoded, split or digest-drifted inputs fail closed. Reports retain only identities, counts and digests.
- The real integration lane uses production MySQL, MinIO, SunPKCS11/SoftHSM, protected-field and private-object adapters, proves isolated seeded sensitivity, and cleans its run-owned table, objects and bucket.
- An optional strict `PHASE03_TESTED_SUBJECT_DIGEST` binds the same real scan to the final root-runner subject without rewriting the report.

## Verification

- Java focused scanner suite: 8 tests passed.
- Ruby artifact-scanner destructive suite: 22 cases passed.
- Exact-four evidence destructive suite: 35 cases passed.
- Real MySQL/MinIO/SoftHSM leak integration: 1 test passed; exact five targets reported zero prohibited matches and seeded sensitivity was detected.
- Ruby syntax and whitespace audits passed.

## Boundary

Plan 22 still owns the post-run artifact scan, same-subject root composition and exact-four producer. No Phase 03 obligation TODO is closed by this plan alone.
