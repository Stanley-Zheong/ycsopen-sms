# Phase 24 Spec

Goal: a real or contract-authoritative HTTP upstream receives exactly one dispatch for an accepted task, and final receipt state settles billing exactly once.

Owned PRD obligations:

- OBL-ACCEPT-UPSTREAM-HTTP-REAL
- OBL-HTTP-UPSTREAM-001
- OBL-HTTP-UPSTREAM-002
- OBL-HTTP-UPSTREAM-003
- OBL-STATE-MESSAGE-SENT
- OBL-STATE-MESSAGE-DELIVERED
- OBL-STATE-MESSAGE-FAILED-PRE
- OBL-STATE-MESSAGE-FAILED-REPORT

Interfaces:

- Dispatch reads `message_send_outbox.state='READY'`, claims one row, sends one HTTP provider request with an idempotency key, and updates task/outbox state.
- Provider accepted response moves `PENDING -> SENT` with `channel_msg_id`.
- Provider rejection moves `PENDING -> FAILED` and reverses reserved billing.
- Unknown provider outcome leaves the outbox claimed with an explicit error and does not auto-retry.
- Receipt command writes an idempotent `delivery_reports` row and moves `SENT -> DELIVERED` or `SENT -> FAILED`.
- Status query returns safe trace fields for the owning tenant only.
