# Issue 124 Verification

Verification date: 2026-10-06

## Final Local Diff

| Gate | Command | Result |
|---|---|---|
| Live scope | authenticated `$GH_CMD` issue view with complete comments | PASS; issue `#124` and all live comments were read; no attachment was present. |
| Dependencies | `npm --prefix web ci` | PASS; 357 packages installed. The existing audit report contains 8 dependency advisories and is not changed by this issue. |
| Backend focused matrix | `mvn -f core/pom.xml -Dtest=ComplaintCaseEventMigrationTest,ComplaintCaseServiceTest,ComplaintCaseTransactionIntegrationTest,ComplaintCaseControllerTest,ComplaintCaseAuthorizationTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerLoggingTest test` | PASS; 37 tests, 0 failures/errors/skips. |
| React focused matrix | `npm --prefix web test -- --run test/unit/complaint-case.test.tsx` | PASS; 12 tests, including backdrop dismissal and opener-focus restoration. |
| Playwright discovery | `npm --prefix web exec -- playwright test complaint-case.spec.ts --config web/playwright.config.ts --project=local-google-chrome --list` | PASS; 11 tests discovered with unique Issue 124 IDs and legacy Phase 41 closure. |
| Frontend build | `npm --prefix web run build` | PASS on the final local diff; the existing 500 kB chunk warning remains. |
| Diff hygiene | `git diff --check` | PASS on the final local diff. |
| Independent pre-push review | backend and frontend bounded review agents | PASS; no remaining BLOCKER, HIGH, or MEDIUM finding. |

The default full Vitest run reached 224/225 passing tests and failed one pre-existing Phase 5 identity test after its save button remained disabled. That unchanged file passed 8/8 immediately in isolation. A serial full-suite rerun reproduced the same cross-file isolation failure before it was stopped after the reproduction was captured. The first clean GitHub Node 20 run passed the full suite.

## Pull Request Feedback

- Branch: `feature/124-complaint-case-context`.
- Initial implementation commit: `722d13aca033506173e494327ea548a602bb61e3`.
- Pull request: `#128`.
- First CI run `37402564417`: Node 20 PASS. Java 21 executed 1,022 tests with no changed-behavior failure; its only failure was the then-open Issue 124 delivery TODO. The Phase 41 design validator stopped on the intentionally stale UI inventory checksum.
- First branded-Chrome execution used Google Chrome 154 and passed 9/11 tests. It exposed a 36.8px submit control against the 38px minimum and backdrop-close focus loss. The follow-up raises the complaint submit selector above the shared 36.8px rule, closes from the click event, captures the opener before dialog focus entry, and adds a focused regression test.
- Post-fix run `37403509270` checked out commit `b402642ce5dcf0c129988091fc6e6074f93f42f5`: Node 20 PASS; Google Chrome 154 complaint suite PASS, 11/11 with no skipped, unexpected, or flaky tests; fresh/repeated Docker release PASS; Phase 03 real integration against MySQL, MinIO, and SoftHSM PASS. The raw Chrome report SHA-256 is `152642297a40e7bac02d4b33ea2294527f24b5dc50f82ffc18fe9cba8ce8ce1b` and its normalized Phase 41 record is checksum-bound in `EVIDENCE/ui-contract.json`.
- Evidence-sealing run `37404774251` checked out commit `5b5da37be119fe4bcbc93a64cbaa7a652178a3fe`: Java 21 PASS, 1,022 tests with no failures or errors. Its planning gate identified missing direct-obligation metadata and a non-expanded Playwright command; both are corrected in the follow-up without changing product behavior.

## Local Environment Boundaries

- The local image has no branded Google Chrome. A cached Chromium run on an earlier diff executed the complaint cases but hung during runner teardown, so it is not claimed as final evidence. GitHub run `37403509270` supplies the final `/usr/bin/google-chrome` result and JSON artifact.
- Ruby is absent, so the repository's Ruby planning validators cannot run locally. They remain mandatory in pull-request CI.
- Claude CLI is installed but not authenticated. The required external Claude review invocation failed with `Not logged in`; two independent repository review passes were completed instead, but this does not masquerade as Claude evidence.
- The first full Maven run executed 1,001 tests and exposed repository/environment boundaries in Phase 01 process cleanup, Phase 08 owned-process cleanup, production migration configuration, and the then-open Issue 124 TODO. Changed backend behavior is covered by the 37-test focused matrix; the clean GitHub Java 21 job is the authoritative full-suite gate.
- The repository precheck stops on historical `System.out` statements in unchanged Java tests. The changed-file scan and `git diff --check` are clean.

## Provider Evidence

Final checksum/planning CI and merge receipt are recorded in the provider response after they exist. The Issue 124 Chrome claim uses run `37403509270`, not historical Phase 41 evidence.
