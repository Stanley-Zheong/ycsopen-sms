# Issue 119 Verification

## Environment

- OpenJDK `21.0.12`, Maven `3.8.7`.
- Node.js `v22.23.2`, npm `10.9.8`.
- No local Google Chrome executable, Ruby interpreter, or reachable Docker
  daemon. The PR CI lanes own real MySQL, Chrome, Ruby validators, and Docker
  release evidence.

## Executed Evidence

| Gate | Result | Evidence |
|---|---|---|
| Migration allocation | PASS | Before authoring, `python3 skills/flyway-migration/scripts/next_flyway_version.py --owner issue-119-statistics-refresh` selected V6700 and `--check V6700` passed. The new SCHEMA-C119 range is V6700-V6799. |
| Focused backend | PASS | `mvn -o -f core/pom.xml -Dtest=StatisticsAggregationServiceTest,StatisticsAggregationRefreshSchedulerTest,StatisticsRefreshMigrationTest,OperationalDashboardServiceTest,OperationalDashboardControllerTest,MessageAcceptanceIdempotencyServiceTest,MessageSubmitServiceTest,MessageSubmitTransactionIntegrationTest,BillingServiceTest test`: 42 tests, 0 failures/errors/skips. |
| Final release acceptance | PASS | `mvn -o -f core/pom.xml -Dtest=FinalReleaseAcceptanceTest test`: 5 tests passed after closing the repository TODO. |
| Dependency install | PASS | `npm --prefix web ci` completed from the committed lockfile. It reported 10 pre-existing advisories (5 moderate, 3 high, 2 critical); no out-of-scope audit rewrite was applied. |
| Focused frontend | PASS | `npm --prefix web test -- test/unit/operational-dashboards.test.tsx test/unit/dashboard-page.test.tsx`: 11 tests across 2 files, all passed after review closure. |
| Full frontend | LOCAL FLAKE BOUNDARY; CI REQUIRED | One exact `npm --prefix web test` run passed 50 files/234 tests. The final post-review rerun passed every Issue 119 test but the unrelated identity permission-save case timed out under suite load (233/234); that exact old test passed 1/1 in isolation. PR CI must provide the final portable full-suite verdict. |
| Production build | PASS | `npm --prefix web run build`; TypeScript and Vite completed. The existing bundle-size advisory remains. |
| Playwright discovery | PASS | From `web`, `npx playwright test operational-dashboards.spec.ts --project=local-google-chrome --workers=1 --grep pw-issue-119 --list` found exactly `pw-issue-119-aggregation-states` and `pw-issue-119-refresh`. |
| Local Chrome execution | BLOCKED LOCALLY | The exact repository command cannot launch the default macOS Chrome path and this Linux runtime has no Chrome. `.github/workflows/ci.yml` runs the same two tests with `YCSOPEN_CHROME_PATH=/usr/bin/google-chrome`. |
| Full backend | BLOCKED LOCALLY | `mvn -f core/pom.xml test` started 567 tests before Surefire was terminated. Reported failures were confined to missing Ruby in `Phase01ServiceHarnessProcessTest`, unavailable process-tree reaping in `Phase08OwnedProcessTest`, runtime `/home` ownership mapping in `ProductionMigrationCommandServicesFactoryTest`, and the then-open Issue 119 TODO in `FinalReleaseAcceptanceTest`. Issue-focused tests passed; the TODO is closed and PR CI is authoritative for portable full-suite evidence. |
| Docker release/MySQL | BLOCKED LOCALLY | `docker info` cannot reach a daemon. The Phase 01 registry now includes `Issue119StatisticsRefreshMySqlTest`, and PR CI owns its Connector/J, UTC session, Shanghai boundary, unhinted production-query plans, rejection, and explicit row-lock serialization evidence plus the scoped Docker release gate. |
| Ruby planning validators | BLOCKED LOCALLY | `/usr/bin/env ruby` is unavailable. Entry review was performed independently against the complete amendment; PR portable-contract validators remain authoritative. |
| Diff hygiene | PASS | `git diff --check` on the independently reviewed complete Issue 119 diff. |

## Review Closure

- Entry review: PASS after five planning review cycles.
- First independent pre-push review found timezone conversion, stale-after-empty,
  transaction-proof, and discovery-index gaps. The implementation now uses
  JDBC `LocalDateTime`, suppresses stale-empty derived values, runs real Spring
  rollback tests, and adds/tests six source indexes.
- A frontend review found missing retained-data retry coverage and an optional
  `todayAggregation` contract. The field is now required, and both React and
  Playwright cover stale retained data, failed refresh, explicit retry, and
  successful replacement.

Final diff hygiene, review verdicts, PR CI, and merge evidence are recorded in
the pull request/provider history because they occur after this repository
artifact is committed.
