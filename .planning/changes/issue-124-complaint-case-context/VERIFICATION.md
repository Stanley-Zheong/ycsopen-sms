# Issue 124 Verification

Verification date: 2026-10-06

## Final Local Diff

| Gate | Command | Result |
|---|---|---|
| Live scope | authenticated `$GH_CMD` issue view with complete comments | PASS; issue `#124` and all live comments were read; no attachment was present. |
| Dependencies | `npm --prefix web ci` | PASS; 357 packages installed. The existing audit report contains 8 dependency advisories and is not changed by this issue. |
| Backend focused matrix | `mvn -f core/pom.xml -Dtest=ComplaintCaseEventMigrationTest,ComplaintCaseServiceTest,ComplaintCaseTransactionIntegrationTest,ComplaintCaseControllerTest,ComplaintCaseAuthorizationTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerLoggingTest test` | PASS; 37 tests, 0 failures/errors/skips. |
| React focused matrix | `npm --prefix web test -- test/unit/complaint-case.test.tsx` | PASS; 11 tests. |
| Playwright discovery | `npm --prefix web exec -- playwright test complaint-case.spec.ts --config web/playwright.config.ts --project=local-google-chrome --list` | PASS; 11 tests discovered with unique Issue 124 IDs and legacy Phase 41 closure. |
| Frontend build | `npm --prefix web run build` | PASS on the final local diff; the existing 500 kB chunk warning remains. |
| Diff hygiene | `git diff --check` | PASS on the final local diff. |
| Independent pre-push review | backend and frontend bounded review agents | PASS; no remaining BLOCKER, HIGH, or MEDIUM finding. |

The default full Vitest run reached 224/225 passing tests and failed one pre-existing Phase 5 identity test after its save button remained disabled. That unchanged file passed 8/8 immediately in isolation. A serial full-suite rerun reproduced the same cross-file isolation failure before it was stopped after the reproduction was captured; the GitHub Node 20 job remains the authoritative clean-run gate.

## Local Environment Boundaries

- The local image has no branded Google Chrome. A cached Chromium run on an earlier diff executed the complaint cases but hung during runner teardown, so it is not claimed as final evidence. The pull-request runner must execute the final 11-test file using `/usr/bin/google-chrome` and preserve its JSON artifact.
- Ruby is absent, so the repository's Ruby planning validators cannot run locally. They remain mandatory in pull-request CI.
- Claude CLI is installed but not authenticated. The required external Claude review invocation failed with `Not logged in`; two independent repository review passes were completed instead, but this does not masquerade as Claude evidence.
- The first full Maven run executed 1,001 tests and exposed repository/environment boundaries in Phase 01 process cleanup, Phase 08 owned-process cleanup, production migration configuration, and the then-open Issue 124 TODO. Changed backend behavior is covered by the 37-test focused matrix; the clean GitHub Java 21 job is the authoritative full-suite gate.
- The repository precheck stops on historical `System.out` statements in unchanged Java tests. The changed-file scan and `git diff --check` are clean.

## Provider Evidence

Branch, commit, pull request, CI run, branded-Chrome artifact, and merge receipt are recorded here after they exist. Historical Phase 41 evidence is not used to claim Issue 124 passed.
