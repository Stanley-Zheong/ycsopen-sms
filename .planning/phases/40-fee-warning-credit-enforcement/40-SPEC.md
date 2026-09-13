# Phase 40 Spec

Goal: real balance and credit sources trigger one deduplicated warning episode under configured rules, with notification evidence and enforcement at message ingress.

Owned obligations:

- OBL-F-8-1-C: prepaid low-balance warning uses source amount and target recipients.
- OBL-F-8-2-B: over-credit behavior executes configured block or manual approval.
- OBL-F-8-10-A: prepaid amount/days and postpaid credit-ratio thresholds are configurable.
- OBL-F-8-10-B: rules select recipients/channels and record delivery evidence.
- OBL-F-8-10-C: credit breach creates one deduplicated episode and enforces block/manual approval in ingress.
- OBL-FLOW-12-1-FEE-WARNING: lifecycle fee warning notification and self-service/approval action.
- OBL-FLOW-12-2-FINANCE: finance supervision flow remains traceable through warning and approval.

Acceptance truths:

1. A matching prepaid or postpaid threshold creates exactly one active episode for the tenant/rule/metric source key.
2. Notification evidence is written to alert delivery attempts.
3. Message submission calls the Phase40 enforcement fence after idempotency, template compliance, and routing acceptance, but before persistence/billing.
4. `BLOCK` rejects immediately; `MANUAL_APPROVAL` rejects until a finance/admin approval updates the episode.
