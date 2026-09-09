# Phase 29 Decisions

- Reuse `bulk_sendings` and `bulk_sending_items`; do not create a second bulk task model.
- Valid bulk rows call `MessageSubmitService.submit`; this preserves the proven compliance, risk, routing, entitlement, idempotency, outbox, and billing chain.
- Invalid import rows are represented in the import snapshot and counts, without storing invalid phone plaintext in item rows.
- Tenant control APIs verify task tenant ownership before state mutation.
- Browser verification remains local Google Chrome only.
- Future scheduled tasks are recorded as `PENDING` and are not submitted through the single-message pipeline during creation. The due-time dispatcher is intentionally outside this phase; this phase must not silently send early.
- Bulk creation is fail-fast after a valid item submission failure, but already-linked item tracking remains committed and the batch is marked `FAILED`.
- Import preview and stored import snapshots use masked phone rows only; raw phone values stay inside the internal validation/create path.
