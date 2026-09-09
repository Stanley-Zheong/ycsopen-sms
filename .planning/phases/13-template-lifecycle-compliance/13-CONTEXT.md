# Phase 13 Context

Dependencies complete:

- Phase 2 provides console layout, page registry, and UI contract validators.
- Phase 8 provides tenant eligibility fence.
- Phase 12 provides signature approval and channel filing lifecycle.

Existing state before this phase:

- `templates` table existed but Java entity and APIs only covered a thin send path.
- `MessageSubmitService` performed local template/signature checks and rendered variables internally.
- `/tenant/templates` was still a placeholder.

Lean implementation choice:

- Add one template lifecycle service and one shared send-compliance service.
- Keep the first production slice domestic-template focused.
- Do not implement future batch/CMPP transports in this phase.
