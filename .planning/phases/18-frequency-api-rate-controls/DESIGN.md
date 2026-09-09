# Phase 18 Design

The Admin page follows existing card + form + table layout.

Backend design:

- `RedisFixedWindowCounter` owns atomic counter mutation.
- `FrequencyChecker` owns routing-time rule dimensions, scope matching, exemptions, and hit evidence.
- `ApiKeyRateLimitService` owns tenant API key second/minute/hour/day limits.
- `FrequencyRuleService` owns management CRUD/import/export request/analytics.

UI design:

- `/admin/frequency/rules` contains metrics, filters, rule form, import/export buttons, rule table, enable/disable action, and high-concurrency feedback.
- `/tenant/api/keys` exposes `tenant-frequency-api-api-keys-rate-limits` in the existing API key rate policy area.
