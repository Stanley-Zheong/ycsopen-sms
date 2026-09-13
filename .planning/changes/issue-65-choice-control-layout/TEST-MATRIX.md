# Issue 65 Choice-Control Test Matrix

This matrix covers the issue-scoped shared choice-control and compact
number-attribution lookup layout. Existing phase tests continue to own business
permissions, mutations, API serialization, and persistence.

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-ISSUE-65-CHOICE-CONTROLS | PROJECT-UI-CONTRACT | issue-65-shared-choice-controls | T-ISSUE-65-CHOICE-CONTROLS:playwright | pw-issue-65-choice-control-layout | shared choice-control routes plus admin-number-attribution `/admin/number-attribution` | admin-number-attribution-lookup-form | C-ISSUE-65-CHOICE-CONTROL-LAYOUT | Shared semantic CSS covers native checkbox and radio inputs on every route. Component coverage verifies the lookup region and explicit label associations. The browser case checks desktop left-to-right order, common vertical center, computed checkbox/radio size, the current page-specific label-class cascade variants, and narrow-viewport wrapping. | `npm --prefix web test`; `npm --prefix web exec -- playwright test test/scripts/number-attribution.spec.ts --config web/playwright.config.ts --project=local-google-chrome --grep pw-issue-65-choice-control-layout` | Unit: executed-pass, 34 files and 124 tests. Browser registration: executed-pass, one matching case. Browser execution record: `.planning/changes/issue-65-choice-control-layout/EVIDENCE/playwright-choice-control-report.json`; APIs are intercepted and the record does not claim backend-service acceptance. |
