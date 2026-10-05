# Issue 121 Tenant Onboarding And API Access Verification

## Executed checks

- `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest test` — PASS on final source, 19 tests.
- `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` — PASS on final source, 25 tests.
- `npm --prefix web test` — PASS, 50 files and 224 tests.
- `npm --prefix web run build` — PASS, 306 modules.
- `YCSOPEN_USE_BUNDLED_CHROMIUM=true YCSOPEN_E2E_ISOLATED=true npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=bundled-chromium --workers=1 --timeout=60000 --reporter=line` — PASS on final source, 5 tests at 1440x900.
- PR 126 `Core / Java 21`, run `37334010539`, attempt 2 — PASS, 993 tests, 0 failures, 0 errors, 33 skipped. The first attempt's sole existing concurrency-test failure did not reproduce; `KeyLifecycleServiceTest` passed 10/10.
- PR 126 `Phase 03 portable contracts`, run `37334010539` — PASS, including the Ruby validator fixtures and Phase 41 source-reachability check.
- PR 126 `Phase 03 real integration`, run `37334010539` — PASS against MySQL, MinIO, and SoftHSM, with named-suite execution and cleanup checks.
- PR 126 `Web / Node 20`, run `37334010539` — PASS.
- PR 126 `Docker release / Google Chrome`, run `37334010539` — PASS, including Issue 121 Google Chrome 154.0.8037.57 coverage 5/5 and fresh/repeated Docker release verification.
- `git diff --check` — PASS after final evidence and independent review updates.

The local full Maven suite reached 363 tests but did not form a valid product
verdict: Ruby-dependent pre-existing harness tests failed with Ruby absent,
pre-existing process-tree cleanup tests could not reap children in this worker,
and Surefire then exited 137 under host memory pressure. The targeted changed
surface is green, and the clean GitHub Ubuntu full-Maven job now supplies the
authoritative suite result above.

The ARM64 worker has no compatible installed Google Chrome package and no Ruby
runtime. The pull request therefore ran the five Issue 121 cases with
`/usr/bin/google-chrome` and ran the repository Ruby validators. Both provider
checks passed; no success is inferred from unavailable local tools.

The Ruby command is the repository validator fixture suite, not a claim that
the historical Phase 08/09 production manifests were replayed at the Issue 121
commit. This change package stores its normalized Chrome report, exact source
hashes, command, browser identity, pull-request commit, artifact digest, and
acceptance boundary under `EVIDENCE/`. The raw provider JSON has SHA-256
`fdb59e77daf3824c97c1347d5485378c6330fb7647a1e5d18cce489230f9e3be`.

## Acceptance boundary

The Issue 121 browser suite uses controlled API responses to exercise the real
React routes and deterministic UI states in Google Chrome. Backend tests remain
authoritative for authorization, tenant isolation, persistence, audit
selection, and secret-response shape.
