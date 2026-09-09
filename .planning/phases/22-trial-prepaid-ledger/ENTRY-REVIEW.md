# Phase 22 Entry Review

| Criterion ID | Verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| ENTRY-22-01 | PASS | Owner query selects 15 obligations for `trial-prepaid-ledger`. | `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner trial-prepaid-ledger --assert-unique --assert-traced` |
| ENTRY-22-02 | PASS | Scope is limited to trial quota, prepaid ledger, consumption ledger, and balance audit. | Inspect `22-SPEC.md` and `22-CONTEXT.md`. |
| ENTRY-22-03 | PASS | UI design artifacts declare 5 routes and 9 direct selectors. | `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 22 --package trial-prepaid-ledger --stage design` |
| ENTRY-22-04 | PASS | Verification uses local Chrome only. | Inspect `22-UI-SPEC.md` and `web/playwright.config.ts`. |
