# Phase 41 UI Spec

Routes:

- `/admin/complaints`: complaint intake, list, selected-case workspace, contextual state action, timeline, remediation, and recovery surface.
- `/admin/complaint/analytics`: daily complaint trend, distribution, and attribution-quality analytics.

Design style:

- Reuse the existing admin console card/table layout and Phase 2 shell conventions.
- Use dense operations panels instead of wizard flows.
- Keep resource attribution visible in the selected case so operators see exact remediation targets before action.

Interaction contract:

- Register complaint: starts with no invented business value and sends controlled source/content type, source-backed nullable link fields, mobile, server-derived attribution quality, summary, and requirement.
- Open case: loads one complaint detail and its complete persisted timeline before exposing an action.
- Accept: moves a pending complaint to processing with actor/opinion evidence from a case-local dialog.
- Handle: records opinion, remediation, requirement, actor, and handled timestamp.
- Resource remediation: applies exact target action and writes an audited disposal record.
- Remediation readback: shows the persisted status and failure reason after refresh.
- Recovery: is available only for a persisted failed disposal record and records the authorized review and resume condition against that exact record.
- Close: closes only after handled state with case context and closure confirmation.
- Concurrent change: a stable HTTP 409 stale response refreshes the selected case and disables the old action.
- Modal safety: the action dialog blocks the underlying page; pending work blocks cancel, Escape, backdrop close, and duplicate submit.
- Analytics: shows an ordered daily trend, totals, unknown attribution, and tenant/signature/content type distribution, with explicit loading, error, and empty states.
