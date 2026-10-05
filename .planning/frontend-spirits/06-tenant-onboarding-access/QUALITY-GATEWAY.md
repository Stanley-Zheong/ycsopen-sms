# Spirit 06 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
| --- | --- | --- | --- |
| Dependency install | `npm --prefix web ci` | Pass | PASS; 357 packages installed from the lockfile. |
| Targeted backend | `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest test` | Pass | PASS on the final source at 2026-10-05 14:49 UTC; 19 tests, 0 failures, 0 errors. |
| Backend suite | `mvn -f core/pom.xml test` | Pass | PASS in PR 126 run `37334010539`, attempt 2: 993 tests, 0 failures, 0 errors, 33 skipped. The first attempt's sole `KeyLifecycleServiceTest` concurrency failure did not reproduce in its 10/10 rerun. Local full-suite output remains non-authoritative because the constrained worker lacks Ruby and Surefire was terminated under host memory pressure. |
| Targeted frontend | `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` | Pass | PASS on the final source at 2026-10-05 15:03 UTC; 3 files, 25 tests. Existing warning output remains non-failing. |
| Frontend suite | `npm --prefix web test` | Pass | PASS on the final source at 2026-10-05 15:05 UTC; 50 files, 224 tests. Existing React Router, `act(...)`, and intentionally unserved-network stderr warnings remain non-failing. |
| Build | `npm --prefix web run build` | Pass | PASS on the final source at 2026-10-05 15:04 UTC; TypeScript and Vite, 306 modules. Existing chunk-size warning remains non-failing. |
| Chrome Playwright | `npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=local-google-chrome --workers=1 --reporter=line,json` | Pass | PASS, 5/5, in PR 126 run `37334010539` on Ubuntu 24.04 with Google Chrome 154.0.8037.57. The downloaded JSON artifact and exact source hashes are recorded in the Issue 121 `EVIDENCE` directory. The local bundled-Chromium result remains supporting evidence only. |
| Planning validator fixtures | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | Pass | PASS in the `Phase 03 portable contracts` job of PR 126 run `37334010539`. This validates the fixture implementation; it is not represented as a replay of historical Phase 08/09 production manifests. |
| Real integration | Phase 03 MySQL, MinIO, and SoftHSM suites | Pass | PASS in PR 126 run `37334010539`; the job also proved every named suite executed and cleaned its owned services. |
| Diff hygiene | `git diff --check` | Pass | PASS after the provider evidence and independent review updates. |

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

The delivery worker is Debian 12 on ARM64. Google publishes the CI browser
package used by this repository for amd64, and Ruby is not installed in the
worker. No browser identity or planning-validator success is inferred from the
local substitutes. PR 126 run `37334010539` supplies the authoritative Ubuntu
Google Chrome, Ruby, full-Maven, and real-integration results.

The closed Phase 08/09 summaries and production manifests describe their
historical atomic deliveries and are not relabeled as current-HEAD executions.
Issue 121 uses a separate change-package addendum. Its normalized Google Chrome
report and UI contract are stored under
`.planning/changes/issue-121-tenant-onboarding-access/EVIDENCE/`; the raw report
remains in artifact `docker-release-37334010539` until 2026-10-19.
