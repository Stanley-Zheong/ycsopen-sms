---
phase: 03-crypto-storage-bootstrap
plan: "03"
subsystem: security-integration-testing
tags: [softhsm, pkcs11, mysql, minio, java-21, aws-sdk, ruby, docker]

requires:
  - phase: 03-crypto-storage-bootstrap-01
    provides: Accepted protected-data inventory and locked cryptographic decisions
provides:
  - Pinned SoftHSM 2.7.0 source verification, isolated build, token initialization, native mechanism/key preflight, and exact cleanup
  - Real digest-locked MySQL and MinIO fixture lifecycle with authenticated functional probes
  - Opt-in Java 21 preflight across JDBC, S3-compatible object storage, and SunPKCS11 AES-GCM/HMAC
affects: [03-10-pkcs11-provider, 03-11-pkcs11-integration, 03-19-leak-proof, 03-22-evidence-production]

tech-stack:
  added: [AWS SDK for Java 2.54.7 test dependencies]
  patterns:
    - Immutable source and OCI digest admission before runtime use
    - Run-owned private PKCS11 configuration, token, PIN source, and cleanup
    - Fixed-argument bounded subprocess bridges with sanitized failure output

key-files:
  created:
    - scripts/lib/phase-03/softhsm-source.json
    - scripts/provision-phase-03-softhsm
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ServiceHarness.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03FixturePreflightTest.java
    - core/src/test/resources/application-phase03-integration.yml
  modified:
    - scripts/lib/phase-03/service_checks.rb
    - scripts/lib/phase-03/test_service_checks.rb
    - core/pom.xml

key-decisions:
  - "SoftHSM evidence requires a verified executable/library and initialized token; source-manifest validation alone cannot produce PASS."
  - "The provision branch accepts only SoftHSM 2.7.0 tag 2.7.0 / commit 13e6e86 / the locked codeload SHA-256, while the alternate branch requires explicit canonical existing CLI, library, and header paths."
  - "Java PKCS11 verification runs in a child Java 21 process whose environment names the private run-owned SoftHSM configuration, and consumes the stable slot ID observed after module reinitialization."
  - "Real MySQL and MinIO lanes use exact locally admitted image digests, ephemeral credentials, loopback ports, non-persistent storage, and owner-scoped cleanup."

patterns-established:
  - "Runtime prerequisite truth: BLOCKED and FAIL remain distinct, and unavailable prerequisites cannot be relabeled as passing evidence."
  - "Secret handling: PINs and service credentials are permission-restricted or process-local, never command-line arguments or durable result fields."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
metrics:
  tasks: 2
  files: 8
---

# Phase 03 Plan 03: Real Crypto and Storage Fixture Bootstrap Summary

**Digest-locked MySQL and MinIO services plus a source-verified SoftHSM 2.7.0 token now execute real JDBC, S3, native PKCS11, and Java SunPKCS11 preflights with fail-closed identity and cleanup.**

## Accomplishments

- Added a closed SoftHSM source manifest for tag `2.7.0`, commit `13e6e86`, archive SHA-256 `be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573`, and fixed CMake arguments.
- Built SoftHSM below a run-owned target, initialized a private token from a mode-0600 PIN source, verified four required mechanisms, created AES and HMAC keys with token/private/sensitive/nonextractable attributes, executed a real HMAC, and removed the complete build/token/config tree.
- Reused the exact admitted MySQL image and admitted the exact local MinIO image, then executed real JDBC and signed S3-compatible object operations. Anonymous object access was rejected with HTTP 403.
- Added a Java 21 SunPKCS11 probe that loads the provisioned native library, logs into the owned token, performs AES-GCM encrypt/decrypt and HMAC-SHA256, and proves generated secret keys are nonextractable through the provider boundary.
- Added destructive checks for source/version/schema/mechanism/attribute/token/path/cleanup failures and exact MinIO digest/platform/release and service run-ID admission.

## Task Commits

1. **Task 1: Implement pinned SoftHSM provision or verified-path admission** — `16fd939`
2. **Task 2: Wire executable MySQL, MinIO and SoftHSM fixture preflight** — `51ca62c`

## Exact Runtime Identities

| Boundary | Admitted identity | Real operation |
| --- | --- | --- |
| SoftHSM source | `2.7.0`, tag `2.7.0`, commit `13e6e86`, archive SHA-256 `be14a582...91573` | Verified-before-extract CMake build under `core/target/phase03`, native PKCS11 token/key/HMAC preflight, Java SunPKCS11 AES-GCM/HMAC |
| SoftHSM verification-path runtime | CLI SHA-256 `a7184db1...e412`, library SHA-256 `34e645f5...42b0` | Executed from the provision branch; the run-owned path and all products were deleted after verification |
| MySQL | `mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` | JDBC connection plus `SELECT 1`, exact version/database/current-user assertions |
| MinIO | `minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`, release `RELEASE.2025-09-07T16-13-09Z` | Authenticated bucket/object create-put-get-delete and anonymous-read rejection |

SoftHSM runtime binaries are built with run-owned absolute paths, so integration-run binary hashes vary with the isolated destination. Each run recomputes and validates its executable/library hashes; no deleted runtime path is presented as a durable installed prerequisite.

## Mechanism and Attribute Facts

- Required mechanisms observed from the real token: `CKM_AES_KEY_GEN`, `CKM_AES_GCM`, `CKM_GENERIC_SECRET_KEY_GEN`, and `CKM_SHA256_HMAC`.
- The native preflight generated exactly one AES key and one generic-secret HMAC key.
- Both keys were asserted as `CKA_TOKEN=true`, `CKA_PRIVATE=true`, `CKA_SENSITIVE=true`, and `CKA_EXTRACTABLE=false` through PKCS11 attribute reads.
- The Java provider probe additionally proved token-backed AES-GCM and HMAC operations and returned no encoded key bytes.
- Durable output contains hashes, versions, counts, and fixed verdict identifiers only; PINs, generated keys, service credentials, absolute runtime paths, and object bodies are absent.

## Verification

- `/usr/bin/env ruby scripts/lib/phase-03/test_service_checks.rb` — PASS, 13 runs and 53 assertions, including destructive identity and cleanup cases.
- `scripts/provision-phase-03-softhsm --manifest scripts/lib/phase-03/softhsm-source.json --destination core/target/phase03/softhsm --provision --initialize-token --preflight --cleanup` — PASS with provision branch, exact source identity, four mechanisms, two nonextractable keys, and cleanup PASS.
- `mvn -f core/pom.xml -Pphase03-integration -Dtest=Phase03FixturePreflightTest test` — PASS, one real three-boundary integration test with no failures, errors, or skips.
- `/usr/bin/env ruby scripts/lib/phase-03/service_checks.rb assert-clean --all` — PASS after real integration and again after the ordinary backend suite.
- `mvn -f core/pom.xml test` — PASS, 36 tests with zero failures/errors; seven opt-in integration tests skipped as configured, including the Phase 03 real lane outside its profile.
- Ruby syntax checks and `git diff --check` — PASS.

## Decisions Made

- The real service profile is opt-in and ordinary Maven runs skip it explicitly. No mock or deterministic substitute is wired under the real test name.
- MinIO credentials are passed through a private ephemeral environment file, while the SoftHSM SO/user PIN values remain in a private run-owned file and are cleared from Java character storage during teardown.
- SoftHSM token initialization is followed by module reinitialization before recording the slot. This records the stable slot ID seen by independent consumer processes rather than the initialization process's transient slot.
- Java launches only a fixed classpath/main-mode PKCS11 probe in a child JVM so the native module observes the correct private `SOFTHSM2_CONF` from process start.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Accepted only the legal codeload PAX metadata record**

- **Found during:** Task 1 source extraction.
- **Issue:** The immutable GitHub codeload archive begins with a POSIX PAX global metadata record, which the initial regular-file-only extractor rejected.
- **Fix:** Skipped exactly that metadata-only record while preserving locked-root, traversal, link, and non-regular-entry rejection for all extracted content.
- **Files modified:** `scripts/provision-phase-03-softhsm`.
- **Committed in:** `16fd939`.

**2. [Rule 1 / Rule 3 - Runtime correctness] Kept build state and dynamic libraries inside the executable boundary**

- **Found during:** Task 1 real build and preflight.
- **Issue:** Relative CMake state directories could escape to a repository-level path; the documented header location differed from the built source tree; the native helper also needed explicit SoftHSM/OpenSSL runtime search paths.
- **Fix:** Used absolute run-owned CMake sysconf/localstate directories, discovered the actual locked-source header, and linked the helper with exact runtime paths. The transient repository-level config from the failed attempt was removed and the clean rerun proved no residue.
- **Files modified:** `scripts/provision-phase-03-softhsm`.
- **Committed in:** `16fd939`.

**3. [Rule 1 - Bug] Made MinIO and SoftHSM probes reflect independent consumers**

- **Found during:** Task 2 real integration.
- **Issue:** MinIO's current `mc stat --json` output did not match the initial size parser, and SoftHSM's initialization process reported a transient slot ID that a separate Java process could not open.
- **Fix:** Compared the actual authenticated object bytes, reinitialized the PKCS11 module before recording the owned stable slot, and ran SunPKCS11 in a child JVM with the private configuration in its startup environment.
- **Files modified:** `scripts/lib/phase-03/service_checks.rb`, `scripts/provision-phase-03-softhsm`, `core/src/test/java/com/ycsopen/sms/core/verification/Phase03FixturePreflightTest.java`.
- **Committed in:** `51ca62c`.

**4. [Rule 2 - Missing critical cleanup and handoff validation] Closed post-start failure paths**

- **Found during:** Task 2 fixture hardening.
- **Issue:** A Java identity/handoff failure after a service had started could bypass the returned session owner, and the initial handoff reader did not independently close every field and private-file permission.
- **Fix:** Added per-service cleanup after post-start failures, exact handoff field/source/slot checks, contained regular-file checks, private config/PIN permissions, and guaranteed PIN character clearing even when stop fails.
- **Files modified:** `core/src/test/java/com/ycsopen/sms/core/verification/Phase03ServiceHarness.java`.
- **Committed in:** `51ca62c`.

**Total deviations:** Four auto-fixed correctness/security groups. All were required for the planned real boundary and fail-closed cleanup behavior; no production endpoint, schema, or mock substitute was added.

## Issues Encountered

- No admissible local SoftHSM runtime or CMake executable was present. The exact official SoftHSM archive was fetched only from the locked URL and verified before extraction. CMake 4.4.3 was installed from the verified Homebrew core formula to execute the required provision branch; Homebrew cleanup also removed `ripgrep`, which was immediately restored with cleanup disabled. No SoftHSM package, alternate image, branch, tag, or latest-version fallback was used.
- The first integrated MinIO probe and two SunPKCS11 attempts failed closed. Each failure ran owner-scoped cleanup and `assert-clean --all` passed before the correction and rerun.

## Known Stubs

None. The profile is intentionally disabled outside `phase03-integration`; the enabled lane reaches all three real boundaries and has no mock substitution.

## Threat Surface Review

The plan adds test-only loopback MySQL/MinIO network fixtures, native library loading, and run-owned file access. Exact image/source identities, canonical containment, private permission checks, bounded output, secret/path redaction, non-persistent MinIO storage, fixed arguments, and cleanup on success/failure implement the plan's spoofing, disclosure, tampering, repudiation, and denial-of-service mitigations. No production network endpoint, authentication path, database schema, persistent service volume, production credential, or production data was added.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan establishes executable prerequisites and real integration preflight only; it does not produce the canonical obligation evidence manifest, close storage/migration/leak obligations, or claim Phase 03 completion.

## Self-Check: PASSED

- All eight planned implementation/configuration files and this summary exist.
- Task commits `16fd939` and `51ca62c` exist on `phase/03-crypto-storage-bootstrap` and contain no tracked deletion.
- The destructive Ruby suite, real provision/preflight/cleanup command, real three-service Java profile, ordinary backend suite, and final owner-scoped cleanup assertion pass against the committed implementation.
- `core/target/phase03/softhsm` and every Phase 03 owned service resource are absent after verification.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no progress, percentage, duration, or estimate field was added.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
