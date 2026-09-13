# Phase 38 Summary

Status: scoped TODO set empty with executable verification evidence.

Delivered:

- Statement/difference/settlement/invoice schema.
- Source-backed statement service using postpaid usage ledger rows and active tenant contract price version.
- Reconciliation difference and confirmation state machine.
- Settlement start, settled, and received state machine.
- Invoice request/issue entitlement checks.
- Server-side tenant-scope guard for statement confirmation and difference reads.
- Admin and tenant React workbenches with stable `data-testid`.
- Chrome-only Playwright evidence using the local Google Chrome project.

Verification evidence:

- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner reconciliation-settlement-invoices --assert-unique --assert-traced`: PASS, selected=13.
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 38 --package reconciliation-settlement-invoices --stage design`: PASS.
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 38 --package reconciliation-settlement-invoices --stage production`: PASS.
- `mvn -f core/pom.xml -Dtest=ReconciliationSettlementServiceTest,ReconciliationSettlementInvoicesMigrationTest,ReconciliationSettlementControllerTest test`: PASS, 6 tests.
- `mvn -f core/pom.xml test`: PASS, 861 tests, 0 failures, 0 errors, 33 skipped.
- `npm --prefix web ci`: PASS with existing npm warnings/vulnerabilities.
- `npm --prefix web test`: PASS, 31 files / 103 tests.
- `npm --prefix web run build`: PASS with existing bundle-size warning.
- `npm --prefix web exec -- playwright test reconciliation-settlement.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json`: PASS, 3 expected / 0 unexpected.
- Claude review: full and code-only review attempts timed out; boundary and local review closure recorded in `CLAUDE-REVIEW.md`.
