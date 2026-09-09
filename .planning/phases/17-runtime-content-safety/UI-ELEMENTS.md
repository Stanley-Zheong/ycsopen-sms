# UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-content-safety /admin/content-safety | ADMIN/OPERATOR content-safety:read | policy metrics and rows | Content safety page | word, category, level, replacement, action, scope, state, creation time, total, intercept, rate, coverage | GET `/api/v1/console/content-safety/policies`; GET `/api/v1/console/content-safety/analytics` | loading, populated, denied | admin-runtime-content-content-safety-page | OBL-F-5-5-A,REQ-F-5-5 | runtime-content-safety-01 | T-F-5-5-A:playwright | pw-p17-policy |
| admin-content-safety /admin/content-safety | ADMIN/OPERATOR content-safety:import | policy import/export | Import action | newline word list; category, level, replacement, action, scope, scope ref | POST `/api/v1/console/content-safety/policies/import`; partial failure evidence | success/error import summary | admin-runtime-content-content-safety-import | OBL-F-5-5-B,REQ-F-5-5 | runtime-content-safety-01 | T-F-5-5-B:integration | pw-p17-import |

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-content-safety /admin/content-safety | ADMIN/OPERATOR content-safety:write | policy form | Save button | word, category, level, replacement, action, scope, scope ref, status | POST `/api/v1/console/content-safety/policies` | hot update saved/error | admin-runtime-content-content-safety-save | OBL-F-5-5-B,REQ-F-5-5 | runtime-content-safety-01 | T-F-5-5-B:integration | pw-p17-import |
| admin-content-safety /admin/content-safety | ADMIN/OPERATOR content-safety:export | policy export | Export request button | current filters | POST `/api/v1/console/content-safety/policies/export-request` | export request id | admin-runtime-content-content-safety-export | OBL-F-5-5-B,REQ-F-5-5 | runtime-content-safety-01 | T-F-5-5-B:integration | pw-p17-import |
| admin-content-safety /admin/content-safety | ADMIN/OPERATOR content-safety:write | policy row | Delete/disable button | selected policy id | POST `/api/v1/console/content-safety/policies/{id}/delete` | disabled with history retained | admin-runtime-content-content-safety-delete | OBL-F-5-5-B,REQ-F-5-5 | runtime-content-safety-01 | T-F-5-5-B:integration | pw-p17-import |

## Runtime scan controls

These controls support `OBL-F-5-5-C` and `OBL-F-5-5-D`, whose PRD catalog entries are runtime records rather than direct UI records. They remain documented for automation generation.

| Control | data-testid | Purpose |
| --- | --- | --- |
| Final-content scan panel | admin-runtime-content-content-safety-scan-panel | Contains tenant id, template id, final rendered content, scan button, and scan result. |
| Final-content scan button | admin-runtime-content-content-safety-scan | Executes `/api/v1/console/content-safety/scan` and displays block/replaced/alert outcome. |
