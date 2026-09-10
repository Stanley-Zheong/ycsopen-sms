# Phase 34 Context

Package: `statistics-aggregation-pipeline`

Phase 34 provides the source-backed aggregate layer consumed by later dashboard/reporting phases.

Authoritative scoped obligations:

- OBL-F-3-8-A: signature/template use, success, and rejection metrics from versioned review and final-message sources.
- OBL-F-11-1-A: channel send, success, cost, and latency aggregates from final receipt, billing, and dispatch/message sources with late correction handling.
- OBL-F-11-2-A: tenant send behavior, consumption, and activity aggregates from tenant-scoped sources and corrected final states.
- OBL-DATA-10-11-AGGREGATES: aggregate data model preserves bucket, tenant, channel, carrier, type, geography, counts, fee, response time, source version, and correction identity.

Inputs:

- `message_submits`
- `message_tasks`
- `delivery_reports`
- `billing_records`
- metric registry rows created by V4300

Non-goals:

- No dashboard UI.
- No custom report authoring.
- No scheduler/worker orchestration.
- No browser matrix; this backend phase has no UI contract.
