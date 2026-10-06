# Issue 121 Tenant Onboarding And API Access Verification

## Local checks

- `npm --prefix web ci` — PASS on 2026-10-06; 357 packages installed from the lockfile.
- `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest,HmacRequestAuthenticatorTest test` — PASS after the final `main` merge: 25 tests with zero failures, errors, or skips.
- `mvn -f core/pom.xml -Dtest=FinalReleaseAcceptanceTest test` — PASS after sealing evidence and closing the delivery checkboxes: 5 tests with zero failures, errors, or skips.
- `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` — PASS after the final `main` merge: 3 files and 30 tests.
- `npm --prefix web run test:e2e -- tenant-qualification.spec.ts --list` — PASS: all 18 real-service browser cases are discoverable after fixture scoping.
- `python3 -m unittest discover -s skills/flyway-migration/tests -p 'test_*.py'` — PASS: 6 tests. Before the renamed migration was created, the repository selector returned `NEXT=V6800` and `--check V6800` returned PASS for owner `issue-121-tenant-onboarding-access`.
- `npm --prefix web run build` — PASS: 306 modules; the existing chunk-size warning is non-failing.
- `git diff --check` — PASS after the base merge and before evidence sealing; repeated after final evidence changes.

The local worker has no Ruby executable, reachable Docker daemon, or browser
runtime libraries, so it does not claim the provider-owned validator, Docker,
MySQL, or Chrome gates.

## Provider verification

PR 126 run [`37499157472`](https://github.com/Stanley-Zheong/ycsopen-sms/actions/runs/37499157472) verified commit `6128e6fd9cd1588966d16da88f22be7ceb69543a` after merging the current `main`:

- Web passed 50 files and 243 tests, then built 306 modules.
- Core executed 1,045 tests with zero errors and 36 conditional skips. Its only failure was `FinalReleaseAcceptanceTest.repositoryHasNoActiveUncheckedProjectTodos`, while the two evidence-dependent delivery checkboxes were deliberately still open; `KeyLifecycleServiceTest` passed 10/10.
- The deterministic Issue 121 suite passed all five Google Chrome 154 cases with zero skipped, unexpected, or flaky results. Artifact `docker-release-37499157472` (ID `11429790507`) contains the raw JSON report with SHA-256 `4bf81161ec123de382176b34d213ba7647b4f8338d9ec04096de05c736cf042f`.
- The Docker job passed layout, Issue 119 dashboard, Issue 121, and complaint Chrome coverage, then passed fresh, upgrade, and restart release lanes for the exact commit.
- Phase08 real-service acceptance passed its wrapper test against Spring, MySQL, SoftHSM, MinIO, notification/inspection sandboxes, Vite, and installed Chrome. The wrapper executes the 18-case tenant qualification suite and rejects any failed Playwright result.
- Phase09 passed 4/4 real-MySQL tests with zero skips, including IPv6 CIDR create-to-HMAC behavior, database-UTC last-use throttling under a non-UTC session, explicit-offset readback, append-only audit selection, and EXPLAIN selection of `idx_audit_tenant_resource_id` after migration V6800.
- The real-integration proof found all nine named suites with more than zero tests and zero skips, failures, or errors.
- Planning validator fixtures passed. The change-package validator then reported exactly the four deliberately stale execution-report errors and no structural, route, selector, source, matrix, or schema errors.

The fresh Chrome artifact has now replaced the stale report. Its normalized
report and `ui-contract.json` have identical command, commit, config, result,
case set, and checksum bindings. Closing the two evidence-dependent checkboxes
removes the only Core failure; the next PR run is the authoritative final
closed-checklist and production-validator verdict.

## Acceptance boundary

The deterministic browser suite uses controlled API responses to exercise the
real React routes, state transitions, selectors, secret lifetime, and layout.
Backend unit and MySQL tests own authorization, tenant isolation, persistence,
audit selection, shared IP/CIDR semantics, and response shapes. The separate
Phase08 real-service browser lane joins approval, API Key create, masked
readback, audit readback, and revoke across the production service topology.
