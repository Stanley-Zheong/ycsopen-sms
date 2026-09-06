# Authoritative Phase TODO

Every item is open at entry. A checkbox may close only with the cited executable evidence. The phase is complete only when the scoped query is empty.

## Entry gate

- [x] Dependency Phase 1 live delivery attestation and empty effective TODO are revalidated — Evidence: `ENTRY-EVIDENCE.md` and Gate-D subject `f97cf61d399b7e48269ac1a1dc2c54f685f81e8e`.
- [x] Required phase artifacts, 30 executable plans and V1200 schema claim pass machine validation — Evidence: `ENTRY-EVIDENCE.md`, `ENTRY-REVIEW.md`, and `03-VALIDATION.md`.
- [x] Independent entry reviewer records criterion-level PASS with no blocker — Evidence: `ENTRY-REVIEW.md` has 12/12 PASS.
- [x] Exact execution-entry command exits zero after independent review — Evidence: `ENTRY-EVIDENCE.md` digest `8ac1a83125bc2fb36f143e2d64f824b74414899fffa0f7775a9949d8e3c4d4bb` and `CLAUDE-REVIEW.md` Attempt 5.

## Execution prerequisites

- [x] Owner-range selector, protected-data inventory and exact-four evidence schemas pass destructive fixtures — Evidence: corrected fixed-subject production reachability and deterministic fixtures are now part of the canonical run.
- [x] Real MySQL, MinIO and SoftHSM prerequisites are admitted by exact identity and cleanup checks — Evidence: corrected fixed-subject production reachability and fixture cleanup pass.
- [x] Production configuration contains no direct key setting or deterministic fallback — Evidence: `ObjectStorageConfiguration` + runtime crypto bean composition and registry-backed `ActiveFieldKeyReference` are now in canonical object/crypto path.
- [x] API and user-manual registration-object contracts match runtime constants — Evidence: production object controller/service graph is now composed under `ycsopen.object-store.enabled` and covered by route-level fixtures.

## Owned obligation closure

- [x] One explicit persistence boundary protects every current database reader/writer and accepted migration target — Evidence: `EVIDENCE/OBL-CRYPTO-STORAGE-001.json` is generated and validated against the fixed subject.
- [x] Private encrypted object storage, staged registration objects and authorized expiring access pass real boundary cases — Evidence: `EVIDENCE/OBL-CRYPTO-STORAGE-002.json` is generated and validated against the fixed subject.
- [x] Opaque PKCS#11 key separation, multi-version index rotation, rewrap and recovery pass without persisted root-key material — Evidence: `EVIDENCE/OBL-CRYPTO-STORAGE-003.json` is generated and validated against the fixed subject.
- [x] Signed-preflight migration is idempotent, integrity checked, resumable, auditable and failure safe — Evidence: `EVIDENCE/OBL-CRYPTO-STORAGE-004.json` is generated and validated against the fixed subject.

## Verification and review

- [x] Default Maven suite passes — Evidence: fixed-phase mandatory lane included in canonical `verify-phase-03` run.
- [x] Complete fixed Phase 03 runner passes every deterministic and real-service lane — Evidence: current-subject `core/target/phase03/results/aggregate.json` records 14/14 PASS with result digest `6fa4a45c604071b3f5e8118c7334071c281275c02c54dbc729a66e8a6a8fd1b1`.
- [x] Inventory, leak, schema, exact-four evidence and cleanup validators pass — Evidence: current `EVIDENCE/evidence-manifest.json`, 59 evidence fixtures, exact-four 4/4 and committed 21-file sanitized result closure all PASS.
- [x] GSD goal verification has no unresolved blocking finding — Evidence: `03-VERIFICATION.md` verifies 4/4 must-haves against tested subject `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`.
- [x] GSD code review has no unresolved blocking or high finding — Evidence: `03-REVIEW.md` Round 15 PASS with BLOCKER 0 / HIGH 0.
- [x] Claude convergence review has no unresolved blocking or high finding — Evidence: `CLAUDE-REVIEW.md` Attempt 12 PASS with BLOCKER 0 / HIGH 0, session `ac52aa94-c613-4702-83a2-ed207bb75a41`.

## Delivery

- [x] Final verification and summary bind the canonical tested subject and evidence manifest — Evidence: `03-VERIFICATION.md` and `SUMMARY.md` bind subject manifest `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`, tested subject `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a` and evidence manifest `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`.
- [x] Historical pre-Plan-31 closure used a reserved external-delivery item — Evidence: retained only as history; it is superseded by the current physical-empty rule and is not current completion evidence.
- [x] One atomic phase commit is visible on the configured GitHub branch and pull request — Evidence: phase commit `9e6240a` is visible on `refs/heads/phase/03-crypto-storage-bootstrap` and PR 15; the Linux CI portability correction is an additive follow-up commit on the same phase branch.
- [x] Corrective commit is pushed to PR 15 and synthetic-merge backend, real-service, and Web checks pass — Evidence: corrective head `a7039ed245bb33167a9e72e39fefcc8c0daea89e`; synthetic merge `f841497bf3635316c2cd58c86f9c296e5756ab5f`; run `34010664879` passed all five jobs.

## PR 15 review remediation

- [x] Aggregate-review CR-01: applicable portable CI gates remain active alongside synthetic-merge Backend, Phase 03 real-integration, and Web jobs — Evidence: `.github/workflows/ci.yml` parses as YAML, contains the five scoped jobs, and each job asserts `HEAD == GITHUB_SHA`; Round 4 review PASS.
- [x] Aggregate-review CR-02: an expired `OPEN` registration session cannot publish `STAGED` metadata — Evidence: `ProtectedObjectMetadataRepositoryJdbcTest` 8/8 PASS and Round 4 review PASS.
- [x] Aggregate-review CR-03: real MySQL proves close/claim versus publication in both winner orders — Evidence: `Phase03ObjectStorageIntegrationTest` PASS with zero skip and deterministic `CLOSED`/`CLAIMED` publish-first/terminal-first interleavings.
- [x] Aggregate-review CR-04: any integrity-valid 11-digit legacy mobile can migrate without widening the online writer validator — Evidence: `ProtectedDataMigrationRunnerTest` 11/11 PASS and real `Phase03MigrationIntegrationTest` 2/2 PASS with zero skip.
- [x] Aggregate-review CR-05: unknown message locators or unbound YCSE-shaped rows cannot bypass VERIFIED/COMPLETE — Evidence: focused destructive migration tests 11/11 PASS, real migration 2/2 PASS, and Round 4 review PASS.
- [x] Aggregate-review CR-06: legacy message plaintext/hash mismatch cannot pass BACKFILLED or VERIFIED — Evidence: focused and real destructive migration state tests PASS; Round 4 review traces exact ASCII/SHA-256 predicates.
- [x] Aggregate-review CR-07: a magic-only or wrong-length YCSE value cannot pass as a current message envelope — Evidence: focused and real destructive migration state tests PASS; Round 4 review traces `EnvelopeCodec` and fixed ciphertext-length validation.
- [x] Claude-review CR-08: whole-target message state validation does not take an unbounded `FOR UPDATE` lock — Evidence: SQL-shape regression and migration tests 11/11 PASS; Round 4 review confirms the non-locking scan and retained bounded row-lock/CAS fences.
- [x] CI replay CR-09: a clean runner prepares the locked MySQL and MinIO platform images before the real fixture starts — Evidence: run `34008949254` passed the explicit digest-pinned image preparation step before Maven.
- [x] CI replay CR-10: MinIO validation distinguishes the immutable manifest/repository digest from the OCI config/image digest on classic Linux Docker — Evidence: 15 service-contract cases / 70 assertions PASS; independent reviewer and Claude report BLOCKER/HIGH 0/0; run `34010664879` passed image preparation, the real Maven step and all seven zero-skip suites on classic Linux Docker.
- [x] CI replay CR-11: a clean generated-output tree supplies the artifact scanner with a validated current-run real proof before the combined leak scan — Evidence: empty-input scanner fixtures 24/24 PASS; clean local real leak suite 1/1 PASS with zero skip and no retained temporary input; independent and Claude reviews BLOCKER/HIGH 0/0; run `34010664879` executed the seven real suites with 8 tests and zero failures, errors, or skips.
- [ ] Closure-review CR-12: the active Phase 03 validation contract matches the final executable test topology and contains no draft, pending, obsolete-class, or unchecked sign-off state — Evidence required: corrected `03-VALIDATION.md`, planning-validator PASS, independent review with `BLOCKER 0 / HIGH 0`, and latest-head PR CI PASS.

- [x] `message_tasks.mobile_encrypted` legacy plaintext is migrated before its target can reach COMPLETE — Evidence: unit migration 11/11 and real migration 2/2 PASS; COMPLETE rejects every non-current or invalidly bound row.
- [x] System and tenant blacklist blind-index scopes are both queried with whitelist precedence intact — Evidence: `BlindIndexLookupServiceTest` 7/7 PASS and real protected-persistence lookup regression PASS.
- [x] `SNAPSHOT_RECOVERY` remains readable after the rotation-required threshold and restart — Evidence: `SnapshotEnvelopeInventoryTest` 4/4 plus PKCS#11 lifecycle regressions PASS.
- [x] A routing rejection performs no FIELD KEK wrap reservation — Evidence: `MessageTaskProtectionAdapterTest` 8/8 and `MessageSubmitServiceTest` 6/6 PASS, covering blacklist/content/frequency rejection and accepted-once wrapping.
- [x] Concurrent registration-session close/claim cannot publish a new STAGED object — Evidence: JDBC repository 8/8 and real object-storage race suite PASS with zero skip.
- [x] ACTIVE MOBILE frequency rules fail closed without Redis or FIELD-wrap mutation until Phase 18 supplies a stable identity — Evidence: `FrequencyCheckerTest` 3/3 and message-submit regressions PASS.
- [x] Pull-request CI tests the synthetic merge tree — Evidence: run `34010664879` checkout fetched and tested `refs/pull/15/merge` at `f841497bf3635316c2cd58c86f9c296e5756ab5f`; every job's `HEAD == GITHUB_SHA` assertion passed.
- [x] Phase 03 real-service evidence is generated in CI and no `core/target` result is tracked — Evidence: run `34010664879` real integration and seven-suite zero-skip proof passed, cleanup and Surefire upload passed, and `git ls-files 'core/target/**'` returns no path.
- [x] CI runs frontend install, unit tests, and production build — Evidence: run `34010664879` Web job passed `npm --prefix web ci`, `npm --prefix web test`, and `npm --prefix web run build`.
- [x] README and user manual describe the actual disabled-default and PKCS#11 crypto-storage bootstrap — Evidence: source/document query finds zero `FIELD_ENCRYPTION_KEY` references; configuration and bootstrap instructions match the production PKCS#11/object-store properties.
