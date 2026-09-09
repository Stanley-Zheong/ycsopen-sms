# UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-frequency-rules /admin/frequency/rules | ADMIN/OPERATOR frequency:read | metrics and rows | Frequency rules page | rule name, dimension, count/window, action, scope, state, hit count, total, block rate, coverage | GET `/api/v1/console/frequency/rules`; GET `/api/v1/console/frequency/analytics` | loading, populated, denied | admin-frequency-api-frequency-rules-page | OBL-F-5-6-A,REQ-F-5-6 | frequency-api-rate-controls-01 | T-F-5-6-A:playwright | pw-p18-rules |
| admin-frequency-rules /admin/frequency/rules | ADMIN/OPERATOR frequency:import | import/export | Import action | newline rule names plus shared dimension/action/scope/window fields | POST `/api/v1/console/frequency/rules/import`; partial failure evidence | import success/error summary | admin-frequency-api-frequency-rules-import | OBL-F-5-6-B,REQ-F-5-6 | frequency-api-rate-controls-01 | T-F-5-6-B:integration | pw-p18-import |
| tenant-api-keys /tenant/api/keys | TENANT_ADMIN/TENANT_DEV | API key rate policy | Rate limits summary | per-second, per-minute, per-hour, per-day | POST `/api/v1/console/tenant/api-keys`; enforcement occurs at `/api/v1/sms/send` | visible limits; secret still once-only | tenant-frequency-api-api-keys-rate-limits | OBL-F-6-5-A,REQ-F-6-5 | frequency-api-rate-controls-03 | T-F-6-5-A:integration | pw-p18-api-key |
| admin-frequency-rules /admin/frequency/rules | ADMIN/OPERATOR frequency:read | high concurrency | Queued/delayed feedback | API 429, console queued/delayed guidance | no mutation; documents user-facing high-concurrency state | visible feedback | shared-frequency-api-queued-feedback | OBL-EDGE-HIGH-CONCURRENCY,PROJECT-EXCEPTION-FLOW | frequency-api-rate-controls-04 | T-EDGE-HIGH-CONCURRENCY:load | pw-p18-high-concurrency |

## Runtime-only controls

| Control | Purpose |
| --- | --- |
| `FrequencyChecker` | Covers OBL-F-5-6-C Redis atomic counters and scoped exemptions. |
| `RateLimitExceededException` + `GlobalExceptionHandler` | Covers OBL-F-6-5-B standard HTTP 429 response with retry guidance. |
