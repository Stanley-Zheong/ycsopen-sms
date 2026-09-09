# Phase 18 UI Spec

Routes:

- `/admin/frequency/rules`
- `/tenant/api/keys` existing page receives one Phase18 rate-limit selector.

Required production selectors:

- `admin-frequency-api-frequency-rules-page`
- `admin-frequency-api-frequency-rules-import`
- `tenant-frequency-api-api-keys-rate-limits`
- `shared-frequency-api-queued-feedback`

Chrome-only verification uses `web/playwright.config.ts#local-google-chrome`.
