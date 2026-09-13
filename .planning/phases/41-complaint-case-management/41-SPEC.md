# Phase 41 Spec

Phase 41 delivers a focused complaint case module.

Functional contract:

1. Operators can create complaint cases with source, summary, complained mobile, content type, and every available tenant/channel/signature/template/message link.
2. Missing attribution is preserved as explicit `UNKNOWN`; the service does not fabricate tenant, channel, signature, template, or message dimensions.
3. Complaint state changes are limited to pending → processing → handled → closed and retain actor, opinion, remediation, requirement, and timestamps.
4. Remediation targets exact linked resources: mobile blacklist, tenant freeze, channel pause, and signature/template unusable state.
5. Remediation is idempotent per complaint/type/target/authorized review and records partial failures for recovery.
6. Recovery requires authorized review evidence and references the original complaint.
7. Analytics reconcile to complaint cases and expose total count, unknown attribution count, and distribution by tenant, signature, and content type.

Non-functional contract:

- Keep implementation local to the complaint module.
- Reuse existing database tables and lifecycle concepts where practical.
- Chrome is the only browser validation target for this project.
