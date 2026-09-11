# Phase 25 Verification

Verification evidence is stored under `EVIDENCE/`.

Required checks:

- `mvn -f core/pom.xml -Dtest='DispatchTaskRecoveryServiceTest,DispatchTaskRecoveryMigrationTest' test`
- `npm --prefix web test -- channel-health.test.tsx`
- `npm --prefix web exec -- playwright test dispatch-recovery.spec.ts --config web/playwright.config.ts --project=local-google-chrome`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner dispatch-task-migration-recovery --assert-unique --assert-traced`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 25 --package dispatch-task-migration-recovery --stage design`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 25 --package dispatch-task-migration-recovery --stage production`
- `mvn -f core/pom.xml test`
- `npm --prefix web ci`
- `npm --prefix web test`
- `npm --prefix web run build`
- scoped TODO query
- `git diff --check`

## Result

PASS.

Evidence captured:

- `EVIDENCE/mvn-focused.log`: focused Phase25 service and migration tests passed.
- `EVIDENCE/npm-unit-channel-health.log`: channel health UI unit tests passed.
- `EVIDENCE/playwright-dispatch-recovery.log`: local Chrome Playwright Phase25 scenario passed.
- `EVIDENCE/prd-obligations.log`: owner obligation query passed, selected 6 obligations.
- `EVIDENCE/ui-contract-design.log`: UI design contract passed.
- `EVIDENCE/ui-contract-production.log`: UI production contract passed.
- `EVIDENCE/mvn-test.log`: full backend test suite passed, 778 tests, 0 failures, 0 errors, 33 skipped.
- `EVIDENCE/npm-ci.log`: frontend clean install passed; npm audit warnings are existing dependency advisories and no dependency was added by Phase25.
- `EVIDENCE/npm-test.log`: frontend unit suite passed, 81 tests.
- `EVIDENCE/npm-build.log`: frontend production build passed.
- `EVIDENCE/open-todos.log`: empty scoped TODO query.
- `EVIDENCE/git-diff-check.log`: empty diff whitespace check.
