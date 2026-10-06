# Spirit 06 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
| --- | --- | --- | --- |
| Dependency install | `npm --prefix web ci` | Pass | PASS on 2026-10-06; 357 packages installed from the lockfile. |
| Targeted backend | `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest,HmacRequestAuthenticatorTest test` | Pass | PASS after merging current `main` on 2026-10-06: 25 tests, 0 failures/errors/skips. |
| Backend suite | `mvn -f core/pom.xml test` | Pass | PR 126 run `37499157472` executed 1,045 tests with 1 failure, 0 errors, and 36 conditional skips. The sole failure was the two deliberately open evidence-dependent delivery checkboxes; every other test passed, including `KeyLifecycleServiceTest` 10/10. After closure, the focused `FinalReleaseAcceptanceTest` passed locally 5/5; the closed-checklist provider rerun is final. |
| Targeted frontend | `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` | Pass | PASS on repaired source at 2026-10-06: 3 files and 30 tests. Existing non-failing React `act` and mocked-network diagnostics remain. |
| Frontend suite | `npm --prefix web test` | Pass | PR 126 run `37499157472` passed 50 files and 243 tests. |
| Build | `npm --prefix web run build` | Pass | PASS locally after the base merge and in run `37499157472`; TypeScript and Vite transformed 306 modules. Existing chunk-size warning remains non-failing. |
| Deterministic Chrome states | `npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=local-google-chrome --workers=1 --reporter=line,json` | Pass | Run `37499157472` passed all 5 cases in Google Chrome 154 with 0 skipped/unexpected/flaky results. Artifact ID `11429790507`; the normalized report is checksum-bound in the change-package inventory. |
| Real-service Chrome | `mvn -f core/pom.xml -Pphase01-integration -Dphase01.integration.enabled=true -Dtest=Phase08RealServicePlaywrightTest test` | Pass with Google Chrome and real Spring/MySQL/SoftHSM/Vite | PASS in run `37499157472`: wrapper 1/1 with zero skips/failures/errors over the 18-case tenant qualification suite and real service topology. |
| Phase09 MySQL | `mvn -f core/pom.xml -Pphase01-integration -Dphase01.integration.enabled=true -Dtest=Phase09TenantCredentialMySqlTest test` | Tests > 0; 0 skipped/failures/errors; indexed EXPLAIN | PASS in run `37499157472`: 4/4, including append-only scoped audit readback, IPv6 CIDR authentication, database-UTC throttling, explicit-offset projection, and V6800 index selection. |
| Change UI production validator | `/usr/bin/env ruby .planning/tools/validate-change-ui-contract.rb --change issue-121-tenant-onboarding-access` | Pass | Run `37499157472` proved all structural checks and reported only the four intentionally stale report fields. The fresh run artifact now supplies matching command, commit, config, case set, and checksum; the closed-evidence provider rerun is authoritative. |
| Planning validator fixtures | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | Pass | PASS in run `37499157472`. |
| Docker release | `./scripts/verify-docker-release` | Pass fresh and repeated | PASS in run `37499157472`: layout, Issue 119, Issue 121, and complaint Chrome coverage plus fresh, upgrade, and restart lanes for commit `6128e6f`. |
| Diff hygiene | `git diff --check` | Pass | PASS after the final evidence and documentation update. |

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
browser, MySQL, or planning-validator success is inferred locally. PR 126 run
`37499157472` supplies the authoritative Ubuntu Google Chrome, Ruby, MySQL,
full-Maven, and Docker evidence.

The closed Phase 08/09 summaries and production manifests describe their
historical atomic deliveries and are not relabeled as current-HEAD executions.
Issue 121 uses a separate change-package addendum enforced by
`validate-change-ui-contract.rb`. Its checked-in execution report is normalized
from run `37499157472` and bound to the raw artifact, verified commit, browser
configuration, case set, and current reviewed source hashes.
