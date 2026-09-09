# Phase 27 Spec

## Owned obligations

- OBL-F-6-3-A: API status query by message ID or permitted range returns normalized state and trace.
- OBL-F-6-3-B: status query rejects unknown ownership/cross-tenant probing without existence leak.
- OBL-F-7-1-A: submission detail exposes ingress/source/tenant/state/request summary/rejection reason.
- OBL-F-7-1-B: submission detail links acceptance/risk/route/task/provider/receipt/billing/trace IDs without protected input exposure.
- OBL-F-7-2-A: send detail supports filters and masked sensitive columns.
- OBL-F-7-2-B: eligible resend and appeal are permission checked, reasoned, idempotent, and preserve original evidence.
- OBL-F-7-4-A: receipt detail exposes message/channel/error/carrier/created metadata.
- OBL-F-7-4-B: receipt correction/replay requires reason, preserves original receipt, uses taxonomy version, and is idempotent.
- OBL-F-7-6-A: error detail groups final failures by normalized code/type with counts and drill-down.
- OBL-F-7-6-B: bulk retry/problem marking validates selected records, reports partial results, and avoids duplicate contact.
- OBL-DATA-10-7-MESSAGE: message/receipt records preserve serial, tenant, protocol, resource, protected phone, content, status, provider, carrier, location, error, fee, retry, raw receipt, and optimistic version evidence.

## Delivery contract

Backend:

- Add `message_operation_events` as the idempotent operation/evidence table.
- Add `MessageReceiptErrorOperationsService` for query, details, resend, appeal, receipt correction/replay, bulk retry/problem marking, and export handoff.
- Add `MessageReceiptErrorOperationsController` under `/api/v1/console/message-operations`.
- Reuse `MessageStatusQueryService`, `DispatchTaskRecoveryService`, and `HttpMessageDeliveryService`; do not create a second send/delivery pipeline.

Frontend:

- Add operations API adapter.
- Add admin records operations page with routes `/admin/submission/details`, `/admin/send/details`, `/admin/receipt/details`, and `/admin/error/details`.
- Every page, table, action, and feedback surface used by automation has stable `data-testid`.

Verification:

- Backend service and migration tests.
- Frontend unit tests.
- Local Google Chrome Playwright tests.
- PRD obligation and UI contract validators.
