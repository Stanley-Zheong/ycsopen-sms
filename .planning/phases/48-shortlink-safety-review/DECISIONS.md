# Phase 48 DECISIONS

- DR-P48-001: Extend existing V1 short-link tables instead of creating parallel tables.
- DR-P48-002: Automated review is deterministic and testable in-repo; no live external crawler or threat-intelligence subscription is added in this phase.
- DR-P48-003: Public redirect checks state at request time. Non-approved states return a safe platform page with no target link.
- DR-P48-004: Target inspection records `TARGET_OFFLINE_ALERT` audit evidence in the short-link audit table; alert transport fan-out remains an assurance/integration concern.
- DR-P48-005: Tenant console APIs use `/api/v1/console/tenant/shortlinks/**` so they run inside the existing JWT console security path.
- DR-P48-006: Tenant scope is resolved from the authenticated user record, not from request payload fields.
