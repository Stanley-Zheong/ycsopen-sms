---
phase: 03
slug: crypto-storage-bootstrap
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-31
completion_metric: scoped_todo_empty
---

# Phase 03 — Validation Strategy

> Phase 3 的逐任务反馈与最终证据合同。完成只由 `crypto-storage-bootstrap` 权威 TODO 是否为空决定，不使用时间、工期或百分比指标。

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Unit/framework tests** | JUnit 5 + AssertJ + Mockito through Maven Surefire |
| **Database integration** | Existing digest-pinned MySQL 8.4 service harness and Flyway |
| **PKCS#11 integration** | Java 21 SunPKCS11 against an isolated SoftHSM token/config; deterministic adapter is unit evidence only |
| **Object integration** | AWS SDK v2 S3 client against the already-present digest-pinned MinIO image |
| **Quick run command** | `mvn -f core/pom.xml test` with the task-specific `-Dtest=...` selector when available |
| **Full suite command** | `mvn -f core/pom.xml test` plus Phase 3 MySQL, PKCS#11, MinIO and evidence-validator lanes |
| **Completion signal** | All four owner obligations traced, evidence valid, reviews clear, and scoped TODO query empty |

No validation command may pull a moving browser, container, native package, Maven version, or service artifact. Missing real adapter prerequisites remain explicit open TODOs; mocks cannot close them.

## Sampling Rules

- After every behavior-changing task commit, run its narrow automated test and all directly affected regression tests.
- After each plan wave, run all Phase 3 tests whose prerequisites exist and record any unavailable real-service lane as an open TODO.
- Before phase verification, run the complete Maven suite, real MySQL lane, real SoftHSM lane, real MinIO lane, evidence validators and leak scans.
- Never allow three consecutive implementation tasks without an automated verification command.
- A passing deterministic adapter test does not change the status of the PKCS#11 production-semantics TODO.

## Per-Plan Verification Map

| Plan | Requirement / obligation | Threat reference | Secure behavior | Automated evidence | Initial status |
|------|---------------------------|------------------|-----------------|--------------------|----------------|
| 03-01 inventory-and-contract | OBL-CRYPTO-STORAGE-001..004 | P03-T01 inventory omission | Every protected DB field, object reference and log class has one reviewed manifest disposition | Manifest schema/unit tests plus repository/V1 coverage validator | pending |
| 03-02 envelope-and-key-ports | OBL-CRYPTO-STORAGE-001, 003 | P03-T02 nonce/AAD/key confusion | `YCSE/v1` rejects malformed, swapped-context, tampered, oversized and unknown-version input; no key bytes escape ports | JUnit crypto vectors, property/fault tests and secret-canary scan | pending |
| 03-03 pkcs11-production-adapter | OBL-CRYPTO-STORAGE-003 | P03-T03 master-key extraction or fallback | Production profile uses opaque PKCS#11 KEK/HMAC keys, fails closed and supports active/retiring aliases | SunPKCS11 + isolated SoftHSM integration; provider/key-attribute preflight | pending / real prerequisite missing |
| 03-04 persistence-boundary | OBL-CRYPTO-STORAGE-001 | P03-T04 plaintext bypass and row swap | Message and protected repositories accept domain values but persist only bound envelopes and versioned blind indexes | Unit/service tests plus real MySQL raw-row assertions and AAD row-swap rejection | pending |
| 03-05 migration-bootstrap | OBL-CRYPTO-STORAGE-004 | P03-T05 ambiguous or partial migration | Manifest runner classifies safely, checkpoints atomically, resumes idempotently and verifies before commit | Real MySQL mixed fixtures, interruption/restart/concurrency/integrity/rollback cases | pending |
| 03-06 protected-object-storage | OBL-CRYPTO-STORAGE-002 | P03-T06 public/direct object disclosure | Private storage holds application ciphertext; only authorized, expiring application capabilities return plaintext | Real MinIO anonymous-denial/raw-canary/capability expiry-revocation-tamper tests | pending |
| 03-07 redaction-and-leak-scan | OBL-CRYPTO-STORAGE-001..003 | P03-T07 sensitive log/evidence leakage | Sensitive values, URLs, ciphertext and key material do not appear in logs, DB metadata, object metadata or evidence | Captured-log tests and seeded canary scans across repository-owned output surfaces | pending |
| 03-08 rotation-recovery-closure | OBL-CRYPTO-STORAGE-001..004 | P03-T08 rotation loss or false closure | Interrupted rotation resumes, retiring keys remain readable until safe retirement, evidence and TODO closure are truthful | Full fault suite, real adapters, obligation/evidence/schema validators, independent reviews | pending |

## Wave 0 Requirements

- [ ] Extend `skills/flyway-migration/scripts/next_flyway_version.py` with owner-range selection and tests proving Phase 3 resolves to `V1200` rather than `V2`.
- [ ] Add Phase 3 test profiles and exact-path fixtures for real MySQL, MinIO and SoftHSM without moving downloads.
- [ ] Provision or locate an approved SoftHSM executable/library and verify its exact version before running the PKCS#11 lane.
- [ ] Define the protected-data inventory manifest schema, validator and seeded canaries before application integration begins.
- [ ] Replace Phase 1's “latest Flyway version is 1” assertion with an immutable-V1 plus declared-migration-set assertion.
- [ ] Create Phase 3 evidence schema/validator stubs tied one-to-one to the four authoritative obligations.

## Real-Service Evidence Boundary

| Boundary | Required proof | What does not count |
|----------|----------------|---------------------|
| MySQL/Flyway | Digest-pinned MySQL applies V1 and the registered V1200+ set; raw-row, checkpoint, restart and integrity assertions pass | H2, SQL parsing, or migration filename inspection alone |
| PKCS#11/HSM semantics | SunPKCS11 loads an isolated SoftHSM token; keys are opaque; wrap/unwrap, HMAC, alias rotation and failure cases pass | Mockito or deterministic in-memory adapter |
| Private S3 semantics | AWS S3 client talks to digest-pinned MinIO; anonymous access fails and stored bytes contain no plaintext canary | In-memory maps or mocked S3 client |
| Production deployment | Deployment configuration identifies its actual KMS/HSM product, key aliases, access policy and private object-store policy | SoftHSM being described as a certified physical HSM |

## Manual-Only Verifications

No Phase 3 behavior is accepted as manual-only. Production hardware certification and deployment IAM policy are deployment evidence, not substitutes for automated code/adapter conformance tests and not claims made by this phase.

## Validation Sign-Off

- [ ] Every implementation task has a narrow automated verification command or an explicit Wave 0 prerequisite.
- [ ] Sampling continuity has no three consecutive tasks without automated verification.
- [ ] Wave 0 prerequisites are resolved or remain represented by non-empty scoped TODOs.
- [ ] No watch-mode, moving download or implicit network-fetch command exists.
- [ ] Real MySQL, SoftHSM and MinIO results remain separately labeled from deterministic unit evidence.
- [ ] All four obligation evidence targets pass integrity and traceability validators.
- [ ] Independent code review, goal verification and Claude review contain no unresolved BLOCKER/HIGH findings.
- [ ] `nyquist_compliant: true` is set only after every item above is satisfied.
- [ ] Owner-scoped TODO query returns empty.

**Approval:** pending
