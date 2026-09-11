# Phase 49 Design

Schema migrations: declared

## Backend

- `TenantCooperationTerminationService` is the orchestration boundary.
- `tenant_termination_requests` stores request status, clearance snapshot, participant snapshot and compensation summary.
- `tenant_termination_participants` stores a machine-readable participant inventory per request.
- `tenant_termination_audits` stores request/refresh/approval/effect audit events.

## UI

One admin page at `/admin/tenant/terminations` contains:

- termination request form;
- clearance checklist;
- approval/effect action card;
- request table;
- participant table;
- retained timeline/audit evidence.

## Test strategy

- Backend service tests prove state machine and resource revocation.
- Migration test proves new schema and permissions are executable.
- Chrome Playwright proves UI flow, selectors and user-facing evidence.
