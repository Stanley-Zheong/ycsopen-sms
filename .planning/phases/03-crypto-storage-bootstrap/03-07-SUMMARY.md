---
phase: 03-crypto-storage-bootstrap
plan: "07"
subsystem: production-pkcs11-conformance
tags: [java-21, sunpkcs11, softhsm, mysql, aes-gcm, hmac-sha256]

requires:
  - phase: 03-crypto-storage-bootstrap-03
    provides: Source-locked SoftHSM 2.7.0 provisioner and real-service lifecycle
  - phase: 03-crypto-storage-bootstrap-06
    provides: Production SunPKCS11 provider, opaque-key adapter and durable wrap reservations
  - phase: 03-crypto-storage-bootstrap-15
    provides: Real-service harness composition and strict cleanup patterns
provides:
  - Reference-only fail-closed production crypto-storage startup
  - Real SoftHSM and MySQL proof of the production SunPkcs11KeyAdapter
  - Source/hash-locked SoftHSM 2.7.0 fixture capability declaration for Java 21
affects: [03-09-protected-persistence, 03-10-connector-writer, 03-16-object-capability, 03-20-key-lifecycle]

tech-stack:
  added: []
  patterns:
    - Enabled production startup admits only the exact SunPKCS11 adapter and closed configuration contract
    - Real crypto evidence uses a child process, source-locked native runtime, real MySQL and hashes/counts-only output
    - Test-fixture source corrections require exact archive, patch, target preimage and target result hashes

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageConfiguration.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageStartupVerifier.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/config/CryptoStorageStartupVerifierTest.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03Pkcs11IntegrationTest.java
  modified:
    - core/src/main/resources/application.yml
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactory.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactoryTest.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ServiceHarness.java
    - core/src/test/resources/application-phase03-integration.yml
    - scripts/provision-phase-03-softhsm
    - scripts/lib/phase-03/service_checks.rb
    - scripts/lib/phase-03/test_service_checks.rb

key-decisions:
  - "Production startup is reference-only and rejects adapter, provider, module, slot, credential source, mechanism, attribute, alias or wrap-ceiling drift without fallback."
  - "Java 21 SunPKCS11 receives the exact five-entry mechanism closure: PCKM_KEYSTORE, AES and generic-secret key generation, AES-GCM, and SHA-256 HMAC."
  - "SoftHSM 2.7.0 test-fixture declarations are corrected to PKCS#11 legacy version macros in exactly two hash-locked upstream files; this is not a production-HSM fallback or physical-HSM certification."

patterns-established:
  - "Real adapter proof: provision exact fixture, create nonextractable purpose-separated token keys, run the production adapter over real V1200 MySQL state, emit only hashes/counts, then assert strict cleanup."
  - "Java 21 key inspection: treat P11KeyStore CKA_VALUE_LEN text as bytes and convert with checked multiplication before enforcing 256-bit key size."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 07: Production PKCS11 Startup and Real Conformance Summary

**Reference-only production startup now fails closed, while the same production `SunPkcs11KeyAdapter` passes real source-locked SoftHSM AES-GCM/HMAC behavior and real MySQL reservation boundaries without deterministic fallback.**

## Accomplishments

- Replaced raw field-key defaults with provider/module/slot/token/credential references and exact canonical aliases; disabled mode exposes denying ports, while enabled non-test mode accepts only the production adapter.
- Added startup rejection for missing or wrong adapter/provider/module/credential settings, mechanism and key-attribute drift, alias drift, provider unavailability, and rotation/ceiling drift.
- Built an opt-in child-process proof using the Plan 03 provisioner, purpose-separated nonextractable token keys, the production provider/session/adapter, and real MySQL V1200 metadata.
- Proved AES-GCM wrap/unwrap and AAD tamper rejection, ACTIVE/RETIRING HMAC behavior, token-domain separation, missing/wrong key failures, restart continuity, provider-failure count burn, threshold transition, exact hard ceiling, and competing-caller admission.
- Locked the SoftHSM 2.7.0 fixture capability correction by archive hash, whole-patch hash, and per-file preimage/result hashes, with destructive rejection tests and mandatory cleanup.

## Task Commits

1. **Task 1: Remove direct-key configuration and enforce production startup** — `ea82ae6`
2. **Task 2: Execute real SunPKCS11 SoftHSM conformance** — `ae88e3a`

## Exact Runtime Evidence

| Identity or invariant | Verified value |
| --- | --- |
| SoftHSM archive SHA-256 | `be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573` |
| Fixture capability-patch SHA-256 | `61f77b1f78ecb94b55da8decb5041d5a40e661c3034eed59f6c2f44645cb3efd` |
| Patched runtime SHA-256 | `893bf3c6f8abf26cff568393fe555aeb93b311a013ad8547964dea10bd5a61cb` |
| Operation-mechanism SHA-256 | `e36054896ed7690f1803c95325de0541946aa78bbe234d8ad3af0516c8d92273` |
| Required-attribute SHA-256 | `95752f3837b20d39ee38e9da4f0f32a3871b59493204218fc444078804a1cc6a` |
| Reservation sequence | `983040,983041,1048576,1048576,1` |
| Successful competing callers at ceiling | `16` |

The result line contains only the preceding hashes/counts. The test rejects PIN, password, secret, alias, path, library, token and provider text in that durable evidence. The exact run-owned SoftHSM/MySQL fixture is removed before the cleanup assertion passes.

## Java 21 and SoftHSM Compatibility Basis

- The installed JDK 21 `sun.security.pkcs11.Config` source recognizes the internal `PCKM_KEYSTORE` entry used by `P11KeyStore`; no wildcard or additional pseudo mechanism was admitted.
- JDK 21 `P11KeyStore` describes `CKA_VALUE_LEN` as a byte count. The provider factory converts the parsed positive value with `Math.multiplyExact(value, Byte.SIZE)` and rejects malformed, nonpositive or overflowing input before key admission.
- SoftHSM 2.7.0 publishes PKCS#11 3.x declarations from the newer headers while its session-cancel capability is not implemented for the Java 21 AES-GCM path. The fixture changes only `C_GetInfo` in `src/lib/SoftHSM.cpp` and `functionList` in `src/lib/main.cpp` to the upstream legacy-version macros so the advertised capability matches the implementation.
- `src/lib/SoftHSM.cpp` is locked from `c963edb9315e25ae81e104e97e3b5805ae3d7e63be30a75e7d753f32bcf2a8e1` to `1891c10f0172c85af3f0316e153ba818a0fb64c737cf6f1d2d67405a5bd7eef4`; `src/lib/main.cpp` is locked from `2a0b44e1647c138d8410daa392326c5d35484d777edcc358e6b8fb2755d22a5f` to `4939c8f142cae61ff35c040105324b1cd893c29176dc7e455330412833efba2f`.

This patch belongs only to the source-verified SoftHSM test fixture. Production configuration still requires the exact real PKCS11 provider contract, has no deterministic or software-key fallback, and makes no physical-HSM certification claim.

## Automated Checks

- `mvn -f core/pom.xml -Dtest='Pkcs11ProviderFactoryTest,SunPkcs11KeyAdapterTest,CryptoStorageStartupVerifierTest' test` — PASS; 16 focused tests.
- `ruby scripts/lib/phase-03/test_service_checks.rb` — PASS; 14 runs and 62 assertions, including missing context, patch-digest drift and per-target source-hash drift rejection.
- `mvn -f core/pom.xml -Pphase03-integration -Dtest=Phase03Pkcs11IntegrationTest test` — PASS; one real production-adapter/SoftHSM/MySQL proof.
- `/usr/bin/env ruby scripts/lib/phase-03/service_checks.rb assert-clean --service softhsm` — PASS.
- `mvn -f core/pom.xml test` — PASS; 140 tests, no failures or errors; opt-in integrations remain skipped in the default lane and the real Plan 07 lane was run separately above.
- `git diff --check` — PASS.

## Decisions Made

- The startup verifier compares complete closed sets for mechanisms and attributes and exact values for aliases and reservation bounds. It does not weaken configuration to accommodate a fixture.
- The provisioner uses the handoff's exact discovered slot; no slot guessing or alias substitution is allowed.
- Flyway placeholder replacement is disabled for the real V1200 migration because the migration's SQL syntax contains literal template markers unrelated to Flyway configuration.
- Child migration logs are discarded only inside the test evidence process so fixture coordinates cannot contaminate the hashes/counts-only proof line; output restoration is unconditional.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added the Java 21 closed mechanism prerequisites**

- **Found during:** Task 2 real provider construction.
- **Issue:** The Plan 06 configuration enabled only operation mechanisms, so Java 21 `P11KeyStore` could not load/create the real token handles.
- **Fix:** First added the two Plan 03-verified key-generation mechanisms, then added only JDK 21's required `PCKM_KEYSTORE` pseudo mechanism. The focused regression asserts the complete five-entry closure exactly.
- **Files modified:** `Pkcs11ProviderFactory.java`, `Pkcs11ProviderFactoryTest.java`.
- **Verification:** Plan 06 focused tests, real adapter proof, and complete backend suite.
- **Committed in:** `ae88e3a`.

**2. [Rule 1 - Bug] Normalized JDK 21 key length units fail closed**

- **Found during:** Task 2 real token inventory inspection.
- **Issue:** `P11KeyStore` describes a 256-bit secret key as `CKA_VALUE_LEN=32`; treating that value as bits falsely rejected the real key.
- **Fix:** Convert positive byte length to bits with checked multiplication and reject invalid, nonpositive or overflowing descriptions.
- **Files modified:** `Pkcs11ProviderFactory.java`, `Pkcs11ProviderFactoryTest.java`.
- **Verification:** Exact `32 -> 256`, malformed and overflow regressions plus the real proof.
- **Committed in:** `ae88e3a`.

**3. [Rule 3 - Blocking] Corrected two SoftHSM 2.7.0 fixture capability declarations**

- **Found during:** Task 2 real AES-GCM/AAD execution after provider construction succeeded.
- **Issue:** The fixture advertised a newer PKCS11 function-list version while the corresponding session-cancel capability was unavailable, leaving Java 21's GCM operation active.
- **Fix:** After archive verification and safe extraction, strictly replace version macros in only `SoftHSM.cpp` and `main.cpp`; lock whole-patch, preimage and result hashes; reject any context/hash drift before writing or building.
- **Files modified:** `service_checks.rb`, `test_service_checks.rb`, `provision-phase-03-softhsm`, `Phase03ServiceHarness.java`.
- **Verification:** Destructive fixture regressions, a fresh provision/build, real AES-GCM/AAD adapter proof and strict cleanup.
- **Committed in:** `ae88e3a`.

**4. [Rule 3 - Blocking] Used exact fixture and migration identities**

- **Found during:** Task 2 harness composition.
- **Issue:** Slot inference and Flyway placeholder parsing prevented the real handoff/V1200 lane from representing the provisioned runtime exactly.
- **Fix:** Consume `handoff.slot` directly and disable unrelated Flyway placeholder replacement for the locked migrations.
- **Files modified:** `Phase03Pkcs11IntegrationTest.java`.
- **Verification:** Real profile proof and cleanup.
- **Committed in:** `ae88e3a`.

**5. [Rule 2 - Evidence confidentiality] Isolated fixture logs from durable proof output**

- **Found during:** Task 2 parent-process proof validation.
- **Issue:** Flyway informational output prefixed the otherwise sanitized result line with fixture coordinates.
- **Fix:** Suppress stdout only around migration inside the child process, restore it unconditionally, then require the complete stdout to match the hashes/counts-only grammar.
- **Files modified:** `Phase03Pkcs11IntegrationTest.java`.
- **Verification:** Real profile proof and negative forbidden-text assertions.
- **Committed in:** `ae88e3a`.

The authorized cross-plan corrections are limited to the Plan 06 provider factory and its focused regression. An `explicitCancel=false` experiment did not resolve the fixture mismatch and was removed; it is absent from the final configuration. Alias, attribute, slot, credential, mechanism and ceiling gates were not relaxed.

## Known Stubs

None. The integration profile remains opt-in by design and is backed by a real provisioned runtime rather than a mock or deterministic adapter.

## Threat Surface Review

- **Spoofing:** exact adapter/provider identity, canonical module allowlist, exact slot and canonical aliases fail closed.
- **Information disclosure:** reference-only configuration and a grammar-checked hashes/counts-only proof omit secrets and fixture coordinates.
- **Denial of service:** startup rejects incomplete/unavailable providers; reservation concurrency cannot overrun the ceiling and a failed provider operation burns its admitted count.
- **Repudiation:** source archive, fixture patch, both patched targets, runtime, operation mechanisms and required attributes have explicit SHA-256 identities.

No endpoint, authentication path or schema was added. The strictly locked source-build correction stays inside the existing Plan 03 fixture trust boundary.

## Physical-HSM and Obligation Boundary

The run proves Java 21 SunPKCS11 protocol conformance against SoftHSM 2.7.0 only. It is not physical-HSM certification and does not claim production vendor/module approval. `OBL-CRYPTO-STORAGE-003` and every Phase 03 obligation TODO remain open for later lifecycle, recovery, canonical evidence, independent review and scoped-TODO closure.

## Self-Check: PASSED

- Both task commits `ea82ae6` and `ae88e3a` exist and contain no tracked deletion.
- All declared production, test, profile and fixture files exist.
- Focused, destructive, real-service, cleanup and complete-backend checks pass against the committed task state.
- No secret, production data, local runtime state or generated build output is tracked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`.
- `requirements-completed` is empty and all Phase 03 obligation TODO rows remain open.
