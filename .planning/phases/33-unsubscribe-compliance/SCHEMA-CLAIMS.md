# Phase 33 Schema Claims

- V4200 owns additive schema changes for `unsubscribe_keywords`, `unsubscribe_records`, and `unsubscribe_alert_events`.
- V4200 does not recreate or drop V1 `unsubscribe_records` or `unsubscribe_keywords`.
- Existing `unsubscribe_records.mobile_encrypted` and `mobile_hash` remain present.
- `unsubscribe_records.uplink_record_id` is the idempotency boundary for one evidence row per normalized uplink.
- `unsubscribe_keywords.scope_key + keyword_normalized` prevents duplicate active scope definitions at the table level.
