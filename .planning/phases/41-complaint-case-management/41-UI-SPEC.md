# Phase 41 UI Spec

Routes:

- `/admin/complaints`: complaint intake, list, state action, remediation, and recovery surface.
- `/admin/complaint/analytics`: daily complaint trend, distribution, and attribution-quality analytics.

Design style:

- Reuse the existing admin console card/table layout and Phase 2 shell conventions.
- Use dense operations panels instead of wizard flows.
- Keep resource attribution visible in the case row so operators see exact remediation targets before action.

Interaction contract:

- Register complaint: sends source, summary, nullable link fields, content type, mobile, attribution quality, and requirement.
- Accept: moves pending complaint to processing with actor/opinion evidence.
- Handle: records opinion, remediation, requirement, actor, and handled timestamp.
- Resource remediation: applies exact target action and writes an audited disposal record.
- Remediation readback: shows the persisted status and failure reason after refresh.
- Recovery: is available only for a persisted failed disposal record and records the authorized review and resume condition against that exact record.
- Close: closes only after handled state with closure confirmation.
- Analytics: shows an ordered daily trend, totals, unknown attribution, and tenant/signature/content type distribution, with explicit loading, error, and empty states.
