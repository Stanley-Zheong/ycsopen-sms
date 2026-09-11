# Phase 32 Design

Schema migrations: declared

## Backend

- `uplink_records` is the legacy V1 uplink table extended with normalized source labels, masked/hash phone lookup, receive-time aliases, push state, and push event linkage. The legacy `mobile_encrypted` column is retained for Phase03 protected-data migration compatibility.
- `tenant_uplink_auto_reply_configs` stores tenant-scoped auto-reply policy.
- `tenant_uplink_auto_reply_attempts` stores replayable auto-reply decisions for loop-guard enforcement.
- `UplinkNormalizationService` owns HTTP/CMPP connector normalization, search, auto-reply config, auto-reply decision planning, and push-state synchronization.
- `UplinkNormalizationController` exposes only human operations surfaces: search/detail/replay/push monitor and tenant auto-reply config. It does not expose an admin/operator ingest endpoint.
- `WebhookDeliveryTransportService.enqueueUplinkEvent` reuses the existing signed webhook envelope and retry state machine. It only uses the configured tenant UPLINK callback destination; callers cannot replace the URL.

## Frontend

- `/admin/uplink` is one operations page with uplink list/detail and push monitor.
- `/tenant/uplink` is one tenant page with tenant-scoped list and auto-reply config.
- Styling reuses existing card/table/sidebar conventions; no new design system layer.

## Browser boundary

Chrome-only validation through `web/playwright.config.ts` project `local-google-chrome`.
