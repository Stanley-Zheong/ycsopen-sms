# Phase 29 Verification

## Contract checks

- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner bulk-scheduled-task-operations --assert-unique --assert-traced` — PASS, selected=17.
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 29 --package bulk-scheduled-task-operations --stage design` — PASS.
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 29 --package bulk-scheduled-task-operations --stage production` — PASS.

## Focused checks

- `mvn -f core/pom.xml -Dtest='BulkScheduledTaskServiceTest,BulkScheduledTaskOperationsMigrationTest' test` — PASS, 10 tests.
- `npm --prefix web test -- bulk-scheduled.test.tsx` — PASS, 3 tests.
- `YCSOPEN_CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm --prefix web exec -- playwright test bulk-scheduled.spec.ts --config web/playwright.config.ts --project=local-google-chrome` — PASS, 4 tests.

## Full repository checks

- `mvn -f core/pom.xml test` — PASS, 804 tests, 0 failures, 0 errors, 33 skipped.
- `npm --prefix web ci` — PASS. Existing dependency audit warnings remain: 7 vulnerabilities reported by npm audit.
- `npm --prefix web test` — PASS, 25 files, 92 tests.
- `npm --prefix web run build` — PASS. Existing Vite chunk-size warning remains.

## Evidence files

- `EVIDENCE/mvn-focused.log`
- `EVIDENCE/mvn-full.log`
- `EVIDENCE/npm-ci.log`
- `EVIDENCE/npm-unit-focused.log`
- `EVIDENCE/npm-test-full.log`
- `EVIDENCE/npm-build-full.log`
- `EVIDENCE/playwright-bulk-scheduled.log`
- `EVIDENCE/playwright-bulk-scheduled-after-ci.log`
