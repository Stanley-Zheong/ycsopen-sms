---
phase: 01-engineering-verification-foundation
plan: 08
subsystem: verification
tags: [java, ruby, mysql, redis, flyway, timezone, docker, integration-testing]
supersession_notice: "Historical execution record only: any 12-plan bootstrap or old browser-entry claim is superseded by DR-01-016/017; later plans must regression-check this output after Plan 00 independent ENTRY PASS."

requires:
  - phase: 01-engineering-verification-foundation
    plan: 01
    provides: evidence kernel, fail-closed runner contract, and repository verification baseline
provides:
  - pinned disposable real-MySQL verification for authentication, Flyway V1, UTF-8, rollback, and session identity
  - pinned disposable real-Redis verification for TTL, deletion, isolation, and Spring StringRedisTemplate wiring
  - host-independent Asia/Shanghai MySQL and serialization contract retaining IANA zone identity
affects: [phase-01-plan-09, phase-01-plan-10, OBL-FOUND-TRACE-003, final-release-acceptance]

tech-stack:
  added: []
  patterns: [digest-pinned disposable Docker services, tmpfs-only state, fixed-argv process execution, Spring integration profile, synthetic temporal contracts, destructive fixture mutations]

key-files:
  created:
    - core/src/test/resources/application-phase01-integration.yml
    - core/src/test/resources/verification/timezone-contract.json
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase01RedisIntegrationTest.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase01TimezoneContractTest.java
    - scripts/lib/phase-01/service_checks.rb
    - scripts/lib/phase-01/test_service_checks.rb
  modified:
    - core/pom.xml

key-decisions:
  - "Real-service verification is opt-in through the phase01-integration Maven profile; the ordinary unit suite discovers and explicitly skips the six Docker-backed tests."
  - "MySQL and Redis PASS requires the exact locally inspected RepoDigest, authenticated or functional operations, Spring wiring, isolated names, tmpfs-only state, and confirmed cleanup."
  - "Timezone proof uses only a synthetic boundary instant and a test DTO while forcing the JVM host default away from Shanghai; it creates no product persistence claim."
  - "The existing V1 migration remains immutable; Flyway placeholder replacement is disabled only in the integration-test profile because V1 contains a literal message-template marker."

patterns-established:
  - "Readiness never establishes PASS: MySQL proceeds only after official initialization completion plus authenticated SELECT 1, and Redis requires PING plus isolated SET/GET/TTL/DEL behavior."
  - "Every service run owns random validated container/network names and is cleaned by exact identity in ensure and JVM shutdown paths."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-only; no schedule, duration, percentage, or completion-date metric
---

# Phase 01 Plan 08: Real services and IANA timezone contract Summary

**Digest-pinned disposable MySQL and Redis now prove real Spring dependency behavior, while a synthetic Asia/Shanghai contract proves host-independent JDBC and IANA-zone serialization without adding business persistence.**

## Scope result

- **Tasks:** 3/3 plan tasks implemented and verified.
- **Files:** seven test/harness artifacts created, `core/pom.xml` modified, and this SUMMARY added.
- **Phase status:** Not asserted. This plan contributes evidence capability only; Phase 1 obligations and TODOs remain open for the later evidence, review, and delivery gates.
- **Requirement status:** `REQ-NFR-COMPATIBILITY` is addressed but not marked complete.

## Accomplishments

- Added an opt-in `phase01-integration` Maven profile that runs Docker-backed verification without making a missing local service look like a default unit-test PASS.
- Built a standard-library Ruby harness around exact official MySQL 8.4.11 and Redis 8.4.5 image digests, fixed argv execution, bounded commands, generated test-only credentials, loopback-only random ports, tmpfs state, and exact cleanup.
- Proved MySQL through authenticated `SELECT 1`, database/current-user/version/charset identity, clean schema, existing Flyway V1 application and validation, exact V1 SHA-256, simplified-Chinese utf8mb4 round trip, Spring transaction rollback, and no residual test table.
- Added eight MySQL harness assertions that fail closed for unavailable Docker, unpinned images, access denial, readiness-only proof, stale state, missing Flyway V1, checksum mutation, or incomplete functional identity.
- Proved Redis through real PING, random-prefix absence, `SET ... EX ... NX`, GET, bounded TTL, DEL, post-delete absence, server version, exact container identity, and a separate Spring `StringRedisTemplate` round trip.
- Added nine Redis harness assertions that reject unavailable Docker, unpinned images, readiness-only evidence, missing TTL, cross-run keys, deletion failure, malformed identity, and absent Spring wiring.
- Forced the integration JVM default to `America/New_York`, set and inspected the MySQL session as `Asia/Shanghai`, and round-tripped a synthetic leap-day boundary through MySQL `TIMESTAMP` and `DATETIME` values inside a rolled-back transaction.
- Added a JSON synthetic contract and test DTO that preserve the fixed instant, `+08:00` offset, local datetime, and the `Asia/Shanghai` IANA identity across serialization; six mutations reject host-default leakage, wrong offset, missing IANA identity, changed instant, missing session proof, and missing serialized identity.
- Kept all changes in test/config/harness scope: no production route, entity, repository, product time field, DOM selector, business schema, or Flyway migration was added or modified.

## Task commits

1. **Task 1: Prove real MySQL migration, UTF-8, transaction, and session identity** — deferred to the single Phase 1 delivery commit.
2. **Task 2: Prove real Redis TTL and Spring application wiring** — deferred to the single Phase 1 delivery commit.
3. **Task 3: Prove the UTC+8 and IANA identity verifier contract** — deferred to the single Phase 1 delivery commit.

No Git staging or commit was performed. The project-specific single atomic Phase 1 delivery rule overrides GSD's default per-task commit convention.

## Files created

- `core/src/test/resources/application-phase01-integration.yml` — isolated Spring datasource/Flyway/Redis integration settings with explicit Shanghai MySQL session initialization.
- `core/src/test/resources/verification/timezone-contract.json` — synthetic non-host-default leap-day instant, offset, local time, and IANA identity.
- `core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java` — real MySQL/Flyway/UTF-8/transaction tests plus the shared service-process bridge.
- `core/src/test/java/com/ycsopen/sms/core/verification/Phase01RedisIntegrationTest.java` — real Redis CLI identity and Spring `StringRedisTemplate` TTL lifecycle tests.
- `core/src/test/java/com/ycsopen/sms/core/verification/Phase01TimezoneContractTest.java` — non-Shanghai host-default, MySQL session/temporal, and serialization verifier.
- `scripts/lib/phase-01/service_checks.rb` — pinned service lifecycle, functional validation, redaction, isolation, and cleanup harness.
- `scripts/lib/phase-01/test_service_checks.rb` — MySQL, Redis, and timezone positive/destructive fixtures with selector-specific execution.

## Files modified

- `core/pom.xml` — added the explicit `phase01-integration` test profile and opt-in system property.

## Decisions made

- Docker availability is verified, never assumed. A missing daemon, local digest, functional service, or cleanup proof yields a stable nonzero `BLOCKED`/`FAIL` diagnostic rather than a substituted H2, mock, or in-memory PASS.
- The harness uses a generated 0600 environment file for MySQL initialization secrets and environment variables for child processes; credential values do not enter the recorded Docker argv or repository files.
- MySQL state lives only on container tmpfs `/var/lib/mysql` and Redis state only on tmpfs `/data`. Runtime inspection rejects bind or volume mounts, and cleanup removes only validated run-owned names.
- The official MySQL image already contains `Asia/Shanghai`; the harness verifies the named zone before attempting an exact import so repeated initialization does not create duplicate timezone rows.
- Redis application resources are destroyed before the disposable container stops, preventing shutdown-time Lettuce reconnect noise and proving a clean Spring lifecycle.
- The V1 migration SHA-256 remains `fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9`; no schema repair or compatibility claim was inferred from this plan.

## TDD gate compliance

All RED and GREEN gates were executed, while their commits are intentionally deferred to the single Phase 1 delivery commit.

- **Task 1 RED:** the first MySQL harness run failed with `SERVICE_IMAGE_NOT_PINNED`; the negative suite subsequently proved distinct access, readiness, stale-state, Flyway, checksum, UTF-8, rollback, and identity failures.
- **Task 1 GREEN:** the exact MySQL Ruby selector passed eight assertions and `Phase01MySqlIntegrationTest` passed two real-service tests.
- **Task 2 RED:** a fixture with CLI success but no Spring round trip was rejected as `REDIS_SPRING_WIRING_MISSING` after the validator separated CLI readiness from application wiring.
- **Task 2 GREEN:** the exact Redis Ruby selector passed nine assertions and `Phase01RedisIntegrationTest` passed two real-service tests.
- **Task 3 RED:** a serialized contract missing its IANA identity initially exposed the absent nested-identity check.
- **Task 3 GREEN:** the exact timezone Ruby selector passed six assertions and `Phase01TimezoneContractTest` passed two real-service tests under the deliberately different JVM default.

## Deviations from plan

### Auto-fixed issues

**1. [Rule 1 - Bug] Avoided duplicate timezone import in the official MySQL image**

- **Found during:** Task 1 GREEN real-service execution.
- **Issue:** unconditional `mysql_tzinfo_to_sql` import failed because MySQL 8.4.11 already contained `Asia/Shanghai`.
- **Fix:** query the named zone first and import only when it is absent, then require an exact named-zone count.
- **Files modified:** `scripts/lib/phase-01/service_checks.rb`.
- **Verification:** MySQL and timezone real-service selectors/tests pass repeatedly against fresh containers.
- **Commit:** deferred to the single Phase 1 delivery commit.

**2. [Rule 1 - Bug] Distinguished final MySQL readiness from the entrypoint's temporary initialization server**

- **Found during:** Task 1 GREEN real-service execution.
- **Issue:** an early authenticated probe could reach the official entrypoint's temporary server before it shut down, followed by a socket failure against the final server.
- **Fix:** wait for the official initialization-complete marker before performing the bounded authenticated `SELECT 1` readiness probe.
- **Files modified:** `scripts/lib/phase-01/service_checks.rb`.
- **Verification:** repeated fresh-container MySQL and timezone runs complete without the readiness race.
- **Commit:** deferred to the single Phase 1 delivery commit.

**3. [Rule 3 - Blocking] Preserved literal message-template syntax during test Flyway execution**

- **Found during:** Task 1 GREEN Flyway migration.
- **Issue:** V1 contains a literal `${var}` message-template marker that Flyway interpreted as an unresolved placeholder.
- **Fix:** set `spring.flyway.placeholder-replacement: false` only in the Phase 01 integration profile; V1 itself and production configuration remain unchanged.
- **Files modified:** `core/src/test/resources/application-phase01-integration.yml`.
- **Verification:** Flyway applies and validates V1 with its exact repository SHA-256.
- **Commit:** deferred to the single Phase 1 delivery commit.

**4. [Rule 1 - Bug] Closed Redis application resources before stopping the service**

- **Found during:** Task 2 GREEN shutdown verification.
- **Issue:** stopping the disposable Redis container before the Spring client was destroyed caused a misleading Lettuce reconnect warning during normal cleanup.
- **Fix:** explicitly destroy the `LettuceConnectionFactory` in `@AfterAll`, then close the run-owned service session.
- **Files modified:** `core/src/test/java/com/ycsopen/sms/core/verification/Phase01RedisIntegrationTest.java`.
- **Verification:** focused Redis execution passes without the shutdown reconnect warning and leaves no run-owned container/network.
- **Commit:** deferred to the single Phase 1 delivery commit.

**Total deviations:** 4 auto-fixed (three runtime bugs and one blocking test-profile configuration issue). None changes production behavior, schema, browser scope, product persistence, or delivery authority.

## Verification evidence

Passed commands:

- `/usr/bin/env ruby scripts/lib/phase-01/test_service_checks.rb --mysql` — `PASS`, eight MySQL positive/fail-closed assertions against the real pinned service.
- `mvn -f core/pom.xml -Pphase01-integration -Dtest=Phase01MySqlIntegrationTest test` — `BUILD SUCCESS`, two real MySQL/Flyway/UTF-8/rollback tests.
- `/usr/bin/env ruby scripts/lib/phase-01/test_service_checks.rb --redis` — `PASS`, nine Redis positive/fail-closed assertions against the real pinned service.
- `mvn -f core/pom.xml -Pphase01-integration -Dtest=Phase01RedisIntegrationTest test` — `BUILD SUCCESS`, two real Redis/Spring/TTL lifecycle tests.
- `/usr/bin/env ruby scripts/lib/phase-01/test_service_checks.rb --timezone-contract` — `PASS`, six timezone/IANA positive and destructive assertions.
- `mvn -f core/pom.xml -Pphase01-integration -Dtest=Phase01TimezoneContractTest test` — `BUILD SUCCESS`, two real MySQL temporal and serialization tests under `America/New_York` JVM default.
- `mvn -f core/pom.xml -Pphase01-integration -Dtest='Phase01MySqlIntegrationTest,Phase01RedisIntegrationTest,Phase01TimezoneContractTest' test` — `BUILD SUCCESS`, six real integration tests with zero failure/error/skip.
- `mvn -f core/pom.xml test` — `BUILD SUCCESS`, 30 discovered tests, zero failure/error, and six expected opt-in integration skips.
- `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` — `PASS`, 19 destructive mutations.
- `/usr/bin/env ruby scripts/lib/phase-01/test_run_checks.rb` — `PASS`, 10 runner cases.
- `/usr/bin/env ruby .planning/tools/test-bootstrap-phase-01.rb` — `PASS`, 25 Gate D mutations and exact seven-obligation ownership.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — `PASS`, existing positive and fail-closed planning regressions.
- `/usr/bin/env ruby .planning/tools/bootstrap-phase-01.rb --phase-dir .planning/phases/01-engineering-verification-foundation --catalog .planning/PRD-OBLIGATIONS.md` — `PHASE_01_BOOTSTRAP PASS`, seven obligations and 12 plans.
- Ruby syntax checks, JSON parse, `git diff --check`, migration-diff check, generated-output check, sensitive-data scan, scope scan, and run-owned Docker resource inspection — `PASS`.

Real environment identity:

- Java: OpenJDK 21.0.10.
- Maven: 3.9.11.
- Docker Desktop engine: client/server 28.1.1, Linux arm64 server.
- MySQL image: `mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`, local image ID equal to that digest, Linux arm64, server 8.4.11.
- Redis image: `redis@sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d`, local image ID equal to that digest, Linux arm64, server 8.4.5.
- After every verification run: no container, network, or volume carrying the Phase 01 owner label remained.

## Known verification boundaries

- Flyway 10.10 reports that MySQL 8.4 is newer than its maximum tested MySQL 8.1. The exact pinned MySQL 8.4.11 migration/validation and functional assertions pass, but this warning is retained as evidence rather than represented as library-certified support.
- Existing V1 SQL comments contain lock emoji bytes that MySQL reports it cannot convert from utf8mb4 to utf8mb3 while executing the script; V1 still applies and validates, and the actual simplified-Chinese table/value round trip is asserted as utf8mb4. The immutable migration was not edited to suppress a comment-only warning.
- This plan proves a synthetic verifier DTO and temporary test table only. It intentionally does not prove international-message product persistence, a production API contract, or any UI behavior.

## Security and privacy check

- Official images are exact digest pins and are inspected locally for RepoDigest, image ID, and Linux arm64 platform before startup.
- Docker commands use fixed argv arrays without shell interpolation; service diagnostics redact environment secret values.
- MySQL credentials are generated per run, supplied through a permission-0600 temporary environment file and child environment, and removed in an `ensure` path.
- Published ports bind only to `127.0.0.1` with Docker-selected host ports. Service state is tmpfs-only, and bind/volume mounts fail verification.
- Cleanup targets only strict run-name patterns and confirms absence after removal; Redis never issues `FLUSHALL`.
- All content is synthetic. No credential, phone number, production record, message body, private repository material, agent state, or local absolute path was added.
- No H2, mock, in-memory service, readiness-only result, or unavailable environment can be accepted as a real-service PASS.

## Known stubs

None. The ordinary Maven suite's opt-in integration skips are an explicit execution boundary, not placeholder behavior; the `phase01-integration` profile executes all six real-service tests.

## Threat surface scan

No unplanned threat surface was introduced. Local process execution, loopback service ports, temporary credential files, Docker lifecycle, and synthetic database tables are the plan-declared test-process/service boundary and are covered by fixed argv, digest pins, permission checks, random identities, tmpfs, rollback/drop, and guaranteed cleanup.

## Self-check: PASSED

- All eight plan-owned implementation/config/test files and this SUMMARY exist.
- All three exact task verification command pairs, the combined six-test integration run, the default 30-test backend suite, four Plan 01 regressions, real bootstrap, syntax/JSON checks, whitespace checks, and scope/privacy scans pass.
- The existing V1 migration remains unchanged with SHA-256 `fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9`.
- No run-owned Docker container, network, or volume remains; no generated `core/target` output is tracked.
- No file outside Plan 01-08 ownership plus this SUMMARY was created or modified by this executor.
- No Gate D artifact, production source, TODO, STATE, ROADMAP, requirement checkbox, Git staging area, branch, commit, stash, remote, or push state was changed.

## Next plan readiness

The real-service and timezone verification contracts are ready for Plan 09 registry integration and Plan 10 evidence execution. Phase 1 remains incomplete until its scoped TODO is empty and its single final delivery commit is independently reviewed and remotely attested.
