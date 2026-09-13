# Issue 79 Finance Layout Verification

## Executed passes

| Surface | Command | Result |
| --- | --- | --- |
| Dependency install | `npm --prefix web ci` | PASS; lockfile install completed. The audit reported 7 existing dependency advisories. |
| Focused UI unit tests | From `web/`: `npm test -- test/unit/financial-source-analytics.test.tsx test/unit/query-panel.test.tsx` | PASS, 2 files / 9 tests. |
| Full UI unit tests | `npm --prefix web test` | PASS, 46 files / 144 tests. |
| Production build | `npm --prefix web run build` | PASS; Vite emitted only its existing large-chunk advisory. |
| Changed-file lint | From `web/`: `npx eslint src/components/common/QueryPanel.tsx src/pages/admin/billing/AdminFinancialAnalyticsPage.tsx test/unit/financial-source-analytics.test.tsx test/scripts/financial-source-analytics.chrome.spec.ts --max-warnings 0` | PASS. |
| Issue #79 acceptance | See the exact command in `EVIDENCE/playwright-finance-layout-report.json`. | PASS, 3/3 in actual `Chrome/152.0.7977.84`: query geometry/interaction, table readability/overflow, and loading/error/retry/empty states. |
| Existing Phase 39 regression | From `web/`: `LD_LIBRARY_PATH=/tmp/issue79-browser-libs-r_HYV5CF77XH66/root/usr/lib/aarch64-linux-gnu YCSOPEN_USE_BUNDLED_CHROMIUM=true YCSOPEN_WEB_PORT=4287 YCSOPEN_E2E_ISOLATED=1 npm run test:e2e -- financial-source-analytics.spec.ts --project=bundled-chromium --workers=1 --reporter=line` | PASS, 2/2. |
| Finance backend regression | `JAVA_TOOL_OPTIONS=-Xmx512m mvn -f core/pom.xml -Dtest=FinancialSourceAnalyticsMigrationTest,FinancialSourceAnalyticsServiceTest,FinancialSourceAnalyticsControllerTest test` | PASS, 8/8. No backend file changed. |
| Planning validator self-test | `PATH=/tmp/issue79-ruby.bBM4HQ/bin:$PATH /usr/bin/env ruby .planning/tools/test-planning-validators.rb` | PASS. The temporary user-space Ruby was used because the base image has no system Ruby. |

## Executed boundaries

- `npm --prefix web run lint` reached the whole repository and failed on two
  pre-existing warnings in untouched files:
  `ComplaintRatioPanel.tsx:54` (`react-hooks/exhaustive-deps`) and
  `DashboardPage.tsx:86` (`react-refresh/only-export-components`). The scoped
  changed-file lint passed.
- `mvn -f core/pom.xml test` was executed twice. The shared host initially had
  no Ruby, so the repository's process-harness tests could not execute their
  Ruby helpers; active maintenance TODOs also intentionally failed the release
  sentinel during the in-progress run. The host PID 1 does not reap owned child
  processes, leaving zombies and failing `Phase08OwnedProcessTest`, and the
  shared test JVM was ultimately killed with exit 137 while other issue runs
  consumed the machine's memory and swap. The narrower finance backend suite
  passed after this boundary was isolated.
- The required read-only Claude review command was executed but stopped before
  review because the installed CLI was not logged in and no Anthropic API key
  was available. `REVIEW.md` records the successful independent agent review
  and the failed Claude authentication boundary without claiming equivalence.

## Acceptance scope

The Chrome cases execute the current React application in the authorized host
Google Chrome and isolate the existing APIs with deterministic route responses.
They establish the Issue #79 UI contract, not real-service financial accuracy.
