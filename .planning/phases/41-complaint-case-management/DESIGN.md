# Phase 41 Design

Backend design:

- `V5000__complaint_case_management.sql` extends `complaints` and `disposal_records` with missing Phase41 fields.
- `ComplaintCaseService` owns validation and state transitions.
- `ComplaintCaseController` exposes platform-console endpoints under `/api/v1/console`.
- Existing blacklist, tenant, channel, signature, and template tables are reused for remediation effects.
- Mutations are authorized for ADMIN/OPERATOR and derive actor evidence from the authenticated principal.
- Remediation is only allowed after complaint handling reaches `PROCESSED`.
- Recovery is only allowed for a failed remediation record and records compensation evidence; it does not reverse successful disablement effects.
- Blacklist remediation requires tenant attribution because blacklist entries are tenant-scoped.
- Resource remediation records `APPLIED` only after a one-row target update; missing targets fail without writing false applied evidence.
- Resource remediation must match the complaint's attributed tenant/channel/signature/template/mobile before mutation; unrelated existing resources are rejected.

Frontend design:

- `complaintCaseApi.ts` wraps console complaint endpoints.
- `AdminComplaintsPage.tsx` provides intake, case list, state actions, remediation, and recovery.
- `AdminComplaintAnalyticsPage.tsx` provides distribution and attribution-quality analytics.
- `AdminLayout.tsx` exposes complaint management and complaint analytics navigation for operations roles.
- Handling, remediation, and recovery evidence are editable page inputs with stable test ids.
- Remediation target/type defaults to automatic matching and can be explicitly selected by the operator.
- Recovery action is disabled until this page has a remediation record id for that case.

Data-quality design:

- Complete attribution requires tenant, channel, signature, template, and message linkage.
- Otherwise attribution quality is explicit `UNKNOWN`.
- UI displays `UNKNOWN` instead of filling dimensions from partial data.
