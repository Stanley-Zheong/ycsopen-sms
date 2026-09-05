---
status: resolved
trigger: "Phase 03 canonical root run failed only in real-service-integration after the production executable trust boundary was tightened."
created: 2026-09-06
updated: 2026-09-06T04:42:18+08:00
---

# Debug Session: Phase 03 Real-Service Lane Failure

## Symptoms

- Expected behavior: all 14 fixed Phase 03 root lanes pass on one immutable subject.
- Actual behavior: 13 lanes pass and `real-service-integration` exits nonzero.
- Error messages: `Phase03MigrationIntegrationTest` production snapshot-create returns exit 26; `Phase03ProtectedPersistenceIntegrationTest` child exits nonzero with sanitized empty stderr.
- Timeline: first canonical root replay after replacing user-writable executable staging with root-owned trust roots.
- Reproduction: `./scripts/verify-phase-03 --all --result-root core/target/phase03/results`.

## Current Focus

- hypothesis: confirmed and fixed; both independent failures pass their original narrow reproductions without weakening the production executable-authority path.
- test: completed targeted class replays, explicit fixture cleanup, temporary-instrumentation scan, process scan, and diff whitespace validation.
- expecting: satisfied.
- next_action: return exact root cause, fix, verification, and file list to the parent orchestrator; do not commit or run the broad root suite.
- reasoning_checkpoint:
    hypothesis: "After substituting the Docker process, snapshot-create fails because the adapter forwards the host-published JDBC port to a client running inside the container, where MySQL is reachable at 127.0.0.1:3306."
    confirming_evidence:
      - "Replacing only MySqlSnapshotProcess changes the command result from authority exit 26 to snapshot exit 25, proving process launch is now reached."
      - "Production derives snapshot host/port from the externally published JDBC URL; DockerMySqlSnapshotProcess forwards them unchanged despite launching via docker exec inside the MySQL container."
      - "The earlier direct Docker snapshot proof passes because its Database value already uses 127.0.0.1:3306."
    falsification_test: "If translating only the adapter's process endpoint to 127.0.0.1:3306 leaves snapshot-create at exit 25, the network-namespace hypothesis is wrong."
    fix_rationale: "The adapter owns the test transport boundary and must translate an externally addressed logical Database to the same pinned service's container-local endpoint; schema and credentials remain production-derived."
    blind_spots: "A later multi-command lifecycle or cleanup assumption may surface once create succeeds."

## Evidence

- timestamp: 2026-09-06T04:15:00+08:00
  observation: 13/14 root lanes passed; cleanup passed; `real-service-integration` alone failed.
- timestamp: 2026-09-06T04:18:00+08:00
  observation: migration test selected PATH/Homebrew clients for the production ServiceLoader factory, which the root-owned executable policy rejects with exit 26.
- timestamp: 2026-09-06T04:22:04+08:00
  checked: failing test source, production composition, test fixture adapter, and Surefire reports
  found: the production-entry sub-proof serializes `snapshotConfiguration(directory)` from host `PATH`, invokes the real ServiceLoader launcher, and therefore constructs `FixedArgumentClient`; its new authority policy admits only root-owned executables under `/usr/bin` or `/opt/ycsopen/mysql-client`. The separate direct snapshot proof already uses the test-only `DockerMySqlSnapshotProcess` against the pinned fixture container.
  implication: changing the production allowlist would weaken the intended trust boundary; the real-service test needs to replace only the external process boundary while retaining the production stores, HSM, JDBC, verifier, snapshot operations, and lifecycle composition.
- timestamp: 2026-09-06T04:22:04+08:00
  checked: `Phase03ProtectedPersistenceIntegrationTest` Surefire report
  found: its child process exited nonzero with no retained stderr diagnostic, so the prior combined run does not identify a mechanism.
  implication: persistence must be replayed independently before treating it as part of the migration composition defect.
- timestamp: 2026-09-06T04:23:23+08:00
  checked: isolated `Phase03MigrationIntegrationTest` replay under `phase03-integration`
  found: both tests ran; `createsAndRestoresEncryptedSnapshotIntoFreshSchema` deterministically failed at `assertProductionMigrationLauncherReachable` because `snapshot-create` returned exit 26, exactly matching the canonical lane.
  implication: H1 is supported and independently reproducible; the failing boundary is the production-entry sub-proof, not Flyway, fixture startup, or the separate Docker-backed snapshot proof.
- timestamp: 2026-09-06T04:24:24+08:00
  checked: isolated `Phase03ProtectedPersistenceIntegrationTest` replay under `phase03-integration`
  found: the child again exited nonzero after about 44 seconds with an empty sanitized stderr, exactly matching the canonical lane.
  implication: H2 is deterministic and independent; it requires observability at the parent/child process boundary before any fix.
- timestamp: 2026-09-06T04:26:00+08:00
  checked: complete persistence parent launch and `Phase03ServiceHarness.execute/runChecked`
  found: the harness captures bounded stdout, stderr, and exit code, but on nonzero exit reports only sanitized stderr; no JVM crash or Surefire dump artifact exists and no fixture child remains.
  implication: the evidence loss is at `runChecked`; exposing already-bounded sanitized context is the smallest discriminating experiment.
- timestamp: 2026-09-06T04:26:37+08:00
  checked: persistence replay with bounded failure context retained
  found: the child exits normally with code 1 and both stdout and stderr empty; it is not signal termination and produced no JVM crash artifact.
  implication: stdout-loss is eliminated; a stage-level binary search is required because the child suppresses application logs and exposes no exception.
- timestamp: 2026-09-06T04:28:00+08:00
  checked: persistence replay with stage-level observability
  found: the child completes provisioning, Flyway migration, and key-metadata seeding, prints `application-start`, but never reaches `application-ready`.
  implication: H2a is confirmed; the second defect is specifically in Spring crypto application composition/startup, not physical fixtures or later persistence assertions.
- timestamp: 2026-09-06T04:30:00+08:00
  checked: complete `CryptoStorageConfiguration` and repository-wide `FieldReferencePublicationFence` definitions
  found: `cryptoKeyLifecycleFactory` requires a `FieldReferencePublicationFence`, but no `@Bean` produces one; only private constructor defaults create `JdbcFieldReferencePublicationFence` instances.
  implication: the production Spring graph is incomplete after the lifecycle-factory composition change; adding the authoritative JDBC implementation is the minimal root fix.
- timestamp: 2026-09-06T04:30:44+08:00
  checked: persistence counterfactual after adding only the production JDBC fence bean
  found: startup progressed to a directly observable `UnsatisfiedDependencyException`: Spring cannot instantiate `BlindIndexMetadataRepository` because the publication-fence change added a second constructor without annotating the public production constructor, causing Spring to seek a nonexistent default constructor.
  implication: the missing fence bean was one graph gap, and constructor selection is a second independent graph gap; both are production composition corrections rather than authority relaxations.
- timestamp: 2026-09-06T04:32:43+08:00
  checked: persistence counterfactual with both production graph corrections
  found: `Phase03ProtectedPersistenceIntegrationTest` passed its complete real MySQL + SoftHSM proof (1 test, 0 failures, 0 errors) in 44.99 seconds.
  implication: the persistence root cause and both minimal wiring corrections are confirmed.
- timestamp: 2026-09-06T04:36:32+08:00
  checked: migration counterfactual using signed production composition with only `MySqlSnapshotProcess` substituted
  found: the failure changed deterministically from key/provider exit 26 to snapshot-invalid exit 25 at create, proving the strict executable authority was bypassed only at the intended boundary and the real Docker process was reached.
  implication: the primary authority mismatch is confirmed; inspection shows the adapter forwards the host-published JDBC port even though it launches the client via `docker exec` inside the database container.
- timestamp: 2026-09-06T04:39:15+08:00
  checked: migration replay after container-local endpoint translation
  found: `Phase03MigrationIntegrationTest` passed both tests with 0 failures and 0 errors in 89.96 seconds, including the production-composed create/preflight/restore/status/delete sequence.
  implication: both the executable-authority mismatch and Docker network-namespace mismatch are confirmed and corrected while the public production factory path remains strict.
- timestamp: 2026-09-06T04:40:56+08:00
  checked: final persistence replay after removing temporary stage markers
  found: `Phase03ProtectedPersistenceIntegrationTest` passed with 0 failures and 0 errors in 44.74 seconds.
  implication: the production Spring graph fixes are stable in the original isolated reproduction with diagnostic instrumentation removed.
- timestamp: 2026-09-06T04:42:18+08:00
  checked: explicit Phase 03 fixture cleanup and scoped final inspection
  found: `service_checks.rb assert-clean --all` returned `{"status":"PASS","cleanup":"all"}`; `git diff --check` passed; no temporary persistence markers or surviving narrow test/service processes remain; public `create()` still selects `fixedArgumentSnapshotProcess` and the strict production authority policy.
  implication: targeted verification and cleanup are complete; broad/root verification was intentionally not run.

## Eliminated

- hypothesis: root verification left dirty service fixtures
  reason: the final `fixture-cleanup` lane passed.
- hypothesis: persistence child crashed natively or was externally killed
  evidence: isolated replay reports ordinary exit code 1, empty output, no crash dump, and no surviving child process.
  timestamp: 2026-09-06T04:26:37+08:00
- hypothesis: the missing `FieldReferencePublicationFence` bean is the only persistence startup defect
  evidence: after supplying only that bean, Spring progressed but failed on ambiguous `BlindIndexMetadataRepository` constructor selection.
  timestamp: 2026-09-06T04:30:44+08:00

## Resolution

- root_cause: persistence Spring composition was incomplete after publication-fence changes (missing JDBC fence bean plus ambiguous repository constructors); migration real evidence incorrectly required user-writable host MySQL clients to satisfy the intentionally root-owned production executable policy instead of substituting its pinned Docker process boundary.
- fix: supply the missing production JDBC publication-fence bean; select the repository production constructor explicitly; compose real migration evidence from its signed configuration while substituting only the pinned Docker MySQL process; translate that adapter to the container-local MySQL endpoint.
- verification: isolated migration replay PASS (2 tests, 0 failures/errors, 89.96s); final isolated persistence replay PASS (1 test, 0 failures/errors, 44.74s); fixture cleanup PASS; diff check PASS; no temporary markers or surviving processes.
- files_changed: [core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageConfiguration.java, core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProductionMigrationCommandServicesFactory.java, core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexMetadataRepository.java, core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProductionMigrationCommandServicesTestBridge.java, core/src/test/java/com/ycsopen/sms/core/verification/DockerMySqlSnapshotProcess.java, core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java]
