# Spirit 04: Delivery Data Workbench Spec

## Intent

Make sending, delivery, receipt, uplink, error, export, and callback workbench pages trustworthy for technical, operations, finance, and customer users.

## Scope

### In

- Message submission/detail, send tasks, receipts, errors, uplinks, callback failures, exports, retry, replay, correction, and evidence views.
- Data lineage and freshness notes for result tables, exports, and derived status.
- Follow-up from issues `#88`, `#91`, `#120`, and PRD V2 message-flow TODOs.

### Out

- New routing algorithms unless required to label existing data correctly.
- Commercial billing policy outside displayed data lineage.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-04-LINEAGE | Each workbench result identifies source, status semantics, freshness, and unknown/incomplete states. | Users can distinguish submitted, sent, delivered, failed, callback failed, and unknown states. |
| FE-SPIRIT-04-COMMAND | Retry, replay, resend, export, correction, and problem-mark actions bind target, snapshot filters, reason when required, and idempotency identity. | Playwright observes correct request payload and no partial/truncated bulk submission. |
| FE-SPIRIT-04-EXPORT | Export dialogs disclose included datasets and excluded filters before submission. | Export payload matches disclosed snapshot and unsupported filters are not silently submitted. |
| FE-SPIRIT-04-UPLINK-TENANT-IDENTITY | Admin uplink rows, details, and push-monitor rows identify an institution with its short name and institution number; details also show the full name and stable internal tenant ID. Missing or deleted institution metadata falls back to the internal ID. | The uplink APIs return tenant display fields in the same response, Chrome shows them on all three surfaces, and institution-name/number selection submits only `tenantId`. |

## Remaining TODO

- Selected route: `/admin/uplink` for issue `#120` tenant identity work.
- Open item: Inventory existing export and retry behavior against PRD V2 command contract.
- Open item: Record executed results and evidence in `QUALITY-GATEWAY.md`.
- Selected item: Deliver issue `#120` through `FE-SPIRIT-04-UPLINK-TENANT-IDENTITY` and record its completed checks in `QUALITY-GATEWAY.md`.
