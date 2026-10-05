# Spirit 06 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
| --- | --- | --- | --- |
| Dependency install | `npm --prefix web ci` | Pass | PASS; 357 packages installed from the lockfile. |
| Targeted backend | `mvn -f core/pom.xml -Dtest=TenantReviewServiceTest,AdminTenantStatusWorkflowTest,TenantApiKeyServiceTest,TenantApiKeyControllerSecurityTest,OperationAuditServiceTest test` | Pass | PASS on the final source at 2026-10-05 14:49 UTC; 19 tests, 0 failures, 0 errors. |
| Backend suite | `mvn -f core/pom.xml test` | Pass | Local boundary: the constrained worker ran 363 tests, then failed only in pre-existing Ruby/process-tree harness classes because Ruby is absent and the JVM was terminated with exit 137 at 12 GiB usage. A clean GitHub Ubuntu `Core / Java 21` run is required before merge. |
| Targeted frontend | `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx test/unit/tenant-access-forms.test.tsx test/unit/action-reason-dialog.test.tsx` | Pass | PASS on the final source at 2026-10-05 15:03 UTC; 3 files, 25 tests. Existing warning output remains non-failing. |
| Frontend suite | `npm --prefix web test` | Pass | PASS on the final source at 2026-10-05 15:05 UTC; 50 files, 224 tests. Existing React Router, `act(...)`, and intentionally unserved-network stderr warnings remain non-failing. |
| Build | `npm --prefix web run build` | Pass | PASS on the final source at 2026-10-05 15:04 UTC; TypeScript and Vite, 306 modules. Existing chunk-size warning remains non-failing. |
| Chrome Playwright | `npm --prefix web run test:e2e -- issue-121-tenant-onboarding-access.spec.ts --project=local-google-chrome --workers=1` | Pass | Current ARM64 worker has no compatible Google Chrome package. All five final-source cases PASS with bundled Chromium at 1440x900 at 2026-10-05 15:03 UTC after user-space runtime libraries are supplied. `.github/workflows/ci.yml` runs the suite with `/usr/bin/google-chrome`; that PR check is required before merge. |
| Planning validator fixtures | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | Pass | Local boundary: exit 127 because Ruby is absent. The existing GitHub portable-contract job runs this exact command before merge. This command tests the validator implementation; it is not claimed as a current Phase 08/09 production-manifest validation. The Issue 121 incremental package is instead bound by its own matrix, Chrome JSON report, source hashes, and independent review. |
| Diff hygiene | `git diff --check` | Pass | PASS after final evidence and independent review updates. |

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
local substitutes: the pull request's clean Ubuntu jobs remain the authoritative
Google Chrome, Ruby, and full-Maven merge gates.

The closed Phase 08/09 summaries and production manifests describe their
historical atomic deliveries and are not relabeled as current-HEAD executions.
Issue 121 uses a separate change-package addendum; its Google Chrome JSON report
and implementation-source hashes are recorded only after the pull-request run.
