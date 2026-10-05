# Issue 121 Tenant Onboarding And API Access Verification

## Executed checks

- `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest test` — PASS on final source, 19 tests.
- `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` — PASS on final source, 25 tests.
- `npm --prefix web test` — PASS, 50 files and 224 tests.
- `npm --prefix web run build` — PASS, 306 modules.
- `YCSOPEN_USE_BUNDLED_CHROMIUM=true YCSOPEN_E2E_ISOLATED=true npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=bundled-chromium --workers=1 --timeout=60000 --reporter=line` — PASS on final source, 5 tests at 1440x900.
- `git diff --check` — PASS after final evidence and independent review updates.

The local full Maven suite reached 363 tests but did not form a valid product
verdict: Ruby-dependent pre-existing harness tests failed with Ruby absent,
pre-existing process-tree cleanup tests could not reap children in this worker,
and Surefire then exited 137 under host memory pressure. The targeted changed
surface is green. The clean GitHub Ubuntu full-Maven job is the authoritative
suite gate.

The ARM64 worker has no compatible installed Google Chrome package and no Ruby
runtime. The pull request therefore runs the five Issue 121 cases with
`/usr/bin/google-chrome` and runs the repository Ruby validators; both checks
must pass before merge. No success is inferred for those unavailable local
tools.

The Ruby command is the repository validator fixture suite, not a claim that
the historical Phase 08/09 production manifests were replayed at the Issue 121
commit. This change package will store its own Chrome report, exact source
hashes, command, browser identity, and pull-request commit as incremental
evidence after the provider run.

## Acceptance boundary

The Issue 121 browser suite uses controlled API responses to exercise the real
React routes and deterministic UI states in Google Chrome. Backend tests remain
authoritative for authorization, tenant isolation, persistence, audit
selection, and secret-response shape.
