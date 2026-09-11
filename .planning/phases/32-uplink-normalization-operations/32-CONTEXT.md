# Phase 32 Context

Phase32 owns `uplink-normalization-operations`.

Dependencies available:

- Phase28 provides `WebhookDeliveryTransportService`, persistent `webhook_delivery_events`, retries, replay, pause, and resume.
- Phase30 and Phase31 provide HTTP/CMPP connector boundaries that can call a single normalization service.
- Phase19 provides carrier/location semantics already used elsewhere; Phase32 stores the correlated values rather than rebuilding attribution.

Scope is intentionally bounded:

- Implement one normalized uplink table.
- Implement one tenant auto-reply config table.
- Reuse generic webhook delivery for UPLINK push.
- Replace `/admin/uplink` and `/tenant/uplink` placeholders with production pages.
- Verify only with local Google Chrome.
