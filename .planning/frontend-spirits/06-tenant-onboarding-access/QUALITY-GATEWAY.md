# Spirit 06 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
| --- | --- | --- | --- |
| Dependency install | `npm --prefix web ci` | Pass | PASS on 2026-10-06; 357 packages installed from the lockfile. |
| Targeted backend | `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest,HmacRequestAuthenticatorTest test` | Pass | PASS on repaired source at 2026-10-06: 24 tests, 0 failures/errors/skips. |
| Backend suite | `mvn -f core/pom.xml test` | Pass | The local ARM64 run executed 1,031 tests but is not a passing verdict: 8 failures and 4 errors were confined to Ruby-dependent process harnesses, host process-tree reaping, unavailable migration configuration, and the intentionally open final checklist; fresh Ubuntu provider result pending. |
| Targeted frontend | `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` | Pass | PASS on repaired source at 2026-10-06: 3 files and 30 tests. Existing non-failing React `act` and mocked-network diagnostics remain. |
| Frontend suite | `npm --prefix web test` | Pass | PR 126 run `37489547230` passed the clean provider Web job. A later focused local shared-dialog suite passed 3 files/26 tests after the Docker regression repair. |
| Build | `npm --prefix web run build` | Pass | PASS on repaired source at 2026-10-06; TypeScript and Vite transformed 306 modules. Existing chunk-size warning remains non-failing. |
| Deterministic Chrome states | `npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=local-google-chrome --workers=1 --reporter=line,json` | Pass | PR 126 run `37489547230` passed all 5 expected cases with 0 skipped, unexpected, or flaky cases. The subsequent fixture/backdrop repair requires one final head run before this evidence is sealed. |
| Real-service Chrome | `mvn -f core/pom.xml -Pphase01-integration -Dphase01.integration.enabled=true -Dtest=Phase08RealServicePlaywrightTest test` | Pass with Google Chrome and real Spring/MySQL/SoftHSM/Vite | Pending new CI lane. |
| Phase09 MySQL | `mvn -f core/pom.xml -Pphase01-integration -Dphase01.integration.enabled=true -Dtest=Phase09TenantCredentialMySqlTest test` | Tests > 0; 0 skipped/failures/errors; indexed EXPLAIN | Run `37489547230` started 4 tests and passed the EXPLAIN assertion, then found one audit-count failure caused by rows retained between methods. The fixture now clears its owned tenant/resource audit rows; final provider rerun pending. |
| Change UI production validator | `/usr/bin/env ruby .planning/tools/validate-change-ui-contract.rb --change issue-121-tenant-onboarding-access` | Pass | Pending fresh checksum-bound evidence. |
| Planning validator fixtures | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | Pass | Pending fresh repaired-source result. |
| Docker release | `./scripts/verify-docker-release` | Pass fresh and repeated | Pending fresh repaired-source result. |
| Diff hygiene | `git diff --check` | Pass | PASS during implementation; rerun required after final evidence and review updates. |

## Merge Gate

- The approval response and UI show the four requested tenant/trial concepts and
  contain no login password or App Secret field.
- `/tenant/config` resolves to the single API Key implementation.
- The created plaintext appears once, survives an unrelated list-refresh
  failure until acknowledgement, and never appears in list or audit data.
- List, revoke, and audit reads are tenant-scoped; denied users receive no data.
- Loading, empty, error/retry, denied, mask, confirmation, pending, success, and
  failure states have stable selectors and focused coverage.
- The final local diff has a blocking-free independent review before push.

## Environment Boundary

The delivery worker is Debian 12 on ARM64 and has neither Ruby, a reachable
Docker daemon, nor the shared libraries required by bundled Chromium. No
browser, MySQL, or planning-validator success is inferred locally. A fresh PR
126 run must supply the authoritative Ubuntu Google Chrome, Ruby, MySQL,
full-Maven, and Docker results.

The closed Phase 08/09 summaries and production manifests describe their
historical atomic deliveries and are not relabeled as current-HEAD executions.
Issue 121 uses a separate change-package addendum enforced by
`validate-change-ui-contract.rb`. The checked-in report and source hashes are
currently stale and must be replaced from the fresh repaired-source Google
Chrome run before this gateway closes.
