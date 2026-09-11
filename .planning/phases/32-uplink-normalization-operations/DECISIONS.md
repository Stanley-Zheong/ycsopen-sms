# Phase 32 Decisions

- DR-32-001: Use `V4100__uplink_normalization_operations.sql` because SCHEMA-P32 owns migration namespace V4100-V4199.
- DR-32-002: Reuse `webhook_delivery_events` for UPLINK push reliability evidence instead of creating a second retry engine.
- DR-32-003: Store `phone_masked` and `phone_hash`; full phone numbers are not returned by uplink search APIs.
- DR-32-004: Chrome is the only browser verification target.
- DR-32-005: Do not implement unsubscribe suppression in this phase; it belongs to the next unsubscribe compliance package.
- DR-32-006: Do not expose a console ingest endpoint; HTTP/CMPP production ingress stays at service-level connector boundaries.
- DR-32-007: UPLINK push always uses the tenant's configured UPLINK callback destination; raw callback URLs are not accepted for this event type.
- DR-32-008: Console filters apply only on explicit search button action, avoiding backend queries on every keystroke.
- DR-32-009: Push enqueue failures must not roll back normalized uplink records; records keep `PUSH_FAILED` or `NOT_CONFIGURED` state for operations follow-up.
- DR-32-010: Extend the legacy `uplink_records` table instead of recreating it, because V1 and Phase03 already own `uplink_records.mobile_encrypted` as a protected-data migration target.
