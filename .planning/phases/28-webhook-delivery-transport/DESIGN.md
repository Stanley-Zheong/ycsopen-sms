# Phase 28 Design

## Backend

- `tenant_callback_configs` stores distinct status/uplink/unsubscribe destinations and retry policy.
- `webhook_delivery_events` stores the versioned signed envelope, destination snapshot, idempotency key, state, and terminal evidence.
- `webhook_delivery_attempts` records every outbound attempt.
- `WebhookDeliveryTransportService` validates destinations, enqueues logical events, signs payloads, delivers one due event, and performs replay/pause/resume.

## UI

- `/tenant/webhooks`: tenant configuration form and SSRF-safe test action.
- `/admin/push/failures`: failed-push table, retry policy visibility, replay/pause/resume actions.
