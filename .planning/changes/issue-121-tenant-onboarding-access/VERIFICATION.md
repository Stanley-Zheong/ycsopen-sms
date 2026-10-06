# Issue 121 Tenant Onboarding And API Access Verification

## Repaired-source local checks

- `npm --prefix web ci` — PASS on 2026-10-06; 357 packages installed from the lockfile.
- `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest,HmacRequestAuthenticatorTest test` — PASS, 24 tests with 0 failures, errors, or skips.
- `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` — PASS, 3 files and 30 tests.
- `npm --prefix web test` — the pre-rereview pass covered 50 files and 234
  tests. A later full rerun on the saturated local worker exceeded the shared
  five-second timeout in both changed and unrelated files and was stopped
  after eight minutes; the focused changed suite subsequently passed all 30
  tests. This later attempt is not a product verdict; the clean provider Web
  job remains authoritative for the final 237-test suite.
- `npm --prefix web run build` — PASS, 306 modules; the existing chunk-size warning is non-failing.
- `git diff --check` — PASS during implementation; it must be repeated after the final evidence update.

`mvn -f core/pom.xml test` executed 1,031 tests locally but is not a passing
product verdict: 8 failures and 4 errors were confined to four infrastructure
test classes. `Phase01ServiceHarnessProcessTest` requires Ruby, the
`Phase08OwnedProcessTest` process-tree oracle cannot reap descendants in this
worker, `ProductionMigrationCommandServicesFactoryTest` cannot obtain this
worker's production migration configuration, and `FinalReleaseAcceptanceTest`
correctly detected the still-open delivery checkboxes. The changed backend
surface is green; the clean GitHub Ubuntu Core job remains the authoritative
full-suite gate.

## Provider gates pending

The prior PR 126 run `37334010539` and its Chrome report predate the owner-review
repairs and are superseded. A fresh pull-request head must still prove:

- the full Java 21 suite;
- Phase09 MySQL execution with more than zero tests, zero skips/failures/errors,
  create-to-repository-to-HMAC IPv6 CIDR behavior, and EXPLAIN selection of
  `idx_audit_tenant_resource_id`;
- Phase08 real-service Google Chrome approval through API Key create, masked
  readback, audit readback, and revoke against Spring, MySQL, SoftHSM, and Vite;
- all five deterministic Issue 121 Google Chrome cases;
- the change-package production evidence validator with fresh report and source
  checksums;
- fresh and repeated Docker release verification.

## Local environment boundary

The delivery worker is Debian 12 on ARM64. Ruby is absent, the Docker client
cannot reach a daemon, and bundled Chromium cannot start because GTK/X11 shared
libraries are absent. Consequently local Phase09, real-service Chrome,
deterministic Chrome, Ruby validator, and Docker-release attempts do not provide
success evidence. The provider Ubuntu jobs own those executable gates; no
success is inferred from unavailable local capabilities.

## Acceptance boundary

The deterministic browser suite uses controlled API responses to exercise the
real React routes, state transitions, selectors, and layout. Backend tests own
authorization, tenant isolation, persistence, audit selection, shared IP/CIDR
semantics, and secret-response shape. The separate real-service browser lane
joins those boundaries for the approval-to-credential happy path.
