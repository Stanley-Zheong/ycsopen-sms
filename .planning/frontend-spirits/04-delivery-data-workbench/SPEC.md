# Spirit 04: Delivery Data Workbench Spec

## Intent

Make sending, delivery, receipt, uplink, error, export, and callback workbench pages trustworthy for technical, operations, finance, and customer users.

## Scope

### In

- Message submission/detail, send tasks, receipts, errors, uplinks, callback failures, exports, retry, replay, correction, and evidence views.
- Data lineage and freshness notes for result tables, exports, and derived status.
- Follow-up from issues `#88`, `#91`, and PRD V2 message-flow TODOs.

### Out

- New routing algorithms unless required to label existing data correctly.
- Commercial billing policy outside displayed data lineage.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-04-LINEAGE | Each workbench result identifies source, status semantics, freshness, and unknown/incomplete states. | Users can distinguish submitted, sent, delivered, failed, callback failed, and unknown states. |
| FE-SPIRIT-04-COMMAND | Retry, replay, resend, export, correction, and problem-mark actions bind target, snapshot filters, reason when required, and idempotency identity. | Playwright observes correct request payload and no partial/truncated bulk submission. |
| FE-SPIRIT-04-EXPORT | Export dialogs disclose included datasets and excluded filters before submission. | Export payload matches disclosed snapshot and unsupported filters are not silently submitted. |

## Remaining TODO

- [ ] Select first workbench route for implementation.
- [ ] Inventory existing export and retry behavior against PRD V2 command contract.
- [ ] Record final verification commands in `QUALITY-GATEWAY.md`.
