# Test Matrix

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-IA-ADMIN-REVIEW-HISTORY | REQ-F-3-2,REQ-F-3-5,REQ-F-3-6 | resource-review-history-01 | T-IA-ADMIN-REVIEW-HISTORY:playwright | pw-p15-review-history | admin-review-history /admin/review-history | admin-resource-review-history-review-page | C-P15-REVIEW-HISTORY | Operator searches unified signature/template/exemption history and opens immutable detail | `npm --prefix web exec -- playwright test resource-review-history.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json` | `EVIDENCE/OBL-IA-ADMIN-REVIEW-HISTORY.json` |
