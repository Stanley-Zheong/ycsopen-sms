# Phase 54 Decisions

## DEC-54-001 — Add a code registry, not a vendor telemetry stack

Decision: implement the PRD 7.1 event list as immutable Java contract classes and tests.

Reason: the PRD gap was the absence of a central event registry. Adding a vendor integration or rewriting all business flows would be disproportionate for the current assurance phase.

## DEC-54-002 — Keep operational dashboards with their original owner

Decision: Phase 54 does not add or modify operational UI.

Reason: `OBL-NFR-OBS-HEALTH` is owned by `operational-dashboards`; duplicating UI ownership would create overlap and future test-id conflicts.

## DEC-54-003 — Tenant context is present on all events

Decision: events with possible platform scope use optional `tenantId`, not an absent tenant field.

Reason: the observability obligation requires tenant-aware correlation while some alert/audit/dashboard events may be global.
