# Phase 41 Design

Backend design:

- `V5000__complaint_case_management.sql` extends `complaints` and `disposal_records` with missing Phase41 fields.
- Issue `#124` adds `V6600__complaint_case_events.sql`; it leaves V5000 unchanged and owns append-only command history with honest legacy backfill.
- `ComplaintCaseService` owns validation and state transitions.
- `ComplaintCaseController` exposes platform-console endpoints under `/api/v1/console`.
- Complaint remediation readback exposes persisted records so failed work and
  recovery eligibility survive a client refresh.
- Existing blacklist, tenant, channel, signature, and template tables are reused for remediation effects.
- Mutations are authorized for ADMIN/OPERATOR and derive actor evidence from the authenticated principal.
- Remediation is only allowed after complaint handling reaches `PROCESSED`.
- Recovery is only allowed for the latest failed remediation record and records compensation evidence; it does not reverse successful disablement effects. Issue `#124` locks the complaint before recomputing that latest record.
- Blacklist remediation requires tenant attribution because blacklist entries are tenant-scoped.
- Resource remediation records `APPLIED` only after a one-row target update; missing targets fail without writing false applied evidence.
- Resource remediation must match the complaint's attributed tenant/channel/signature/template/mobile before mutation; unrelated existing resources are rejected.
- Issue `#124` uses compare-and-set transitions/recovery and a locked complaint state for remediation. Failed nested resource work exits its transaction before the still-locking outer command transaction persists sanitized failure evidence.

Frontend design:

- `complaintCaseApi.ts` wraps console complaint endpoints.
- `AdminComplaintsPage.tsx` provides empty intake, source-backed attribution choices, case list, selected-case detail/timeline, contextual actions, remediation, and recovery.
- `AdminComplaintAnalyticsPage.tsx` provides daily trend, distribution, and attribution-quality analytics.
- `AdminLayout.tsx` exposes complaint management and complaint analytics navigation for operations roles.
- Issue `#124` supersedes the original page-level evidence layout: handling, remediation, recovery, and closure fields exist only in a selected-case dialog and are discarded on cancel.
- Remediation target/type defaults to automatic matching and can be explicitly selected by the operator.
- Recovery action is enabled only when persisted readback contains a `FAILED`
  remediation record for that case and the case is `PROCESSED` or `CLOSED`;
  `APPLIED` and `RECOVERED` records cannot be submitted for recovery.

Data-quality design:

- Complete attribution requires tenant, channel, signature, template, and message linkage.
- Otherwise attribution quality is explicit `UNKNOWN`.
- UI displays `UNKNOWN` instead of filling dimensions from partial data.
