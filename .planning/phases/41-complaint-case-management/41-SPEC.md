# Phase 41 Spec

Phase 41 delivers a focused complaint case module.

Functional contract:

1. Operators can create complaint cases with source, summary, complained mobile, content type, and every available tenant/channel/signature/template/message link.
2. Missing attribution is preserved as explicit `UNKNOWN`; the service does not fabricate tenant, channel, signature, template, or message dimensions.
3. Complaint state changes are limited to pending → processing → handled → closed and retain actor, opinion, remediation, requirement, and timestamps.
4. Remediation targets exact linked resources: mobile blacklist, tenant freeze, channel pause, and signature/template unusable state.
5. Remediation is idempotent per complaint/type/target/authorized review and records partial failures for recovery.
6. Failed remediation remains visible after refresh; recovery requires authorized review evidence, targets the persisted failed record, and references the original complaint.
7. Analytics reconcile to complaint cases and expose an ordered daily trend, total count, unknown attribution count, and distribution by tenant, signature, and content type.

Issue `#124` amendment:

8. Each action is opened from a server-loaded single-case workspace; page-level handling drafts are no longer part of the contract.
9. New commands append immutable actor/time/evidence/result events, while legacy history exposes only provable values and labels unavailable evidence honestly.
10. Complaint transitions and recovery reject stale concurrent state, and exact action eligibility is derived from the loaded case and failed-remediation state.

Non-functional contract:

- Keep implementation local to the complaint module.
- Reuse existing database tables and lifecycle concepts where practical.
- Issue `#124` may add the registered `complaint_case_events` table without rewriting Phase 41 migration history.
- Chrome is the only browser validation target for this project.
