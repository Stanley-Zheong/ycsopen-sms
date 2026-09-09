# Phase 15 Spec

## Goal

Authorized platform operators can reconstruct signature, template, and exemption review decisions from one immutable production history.

## Scope

- `OBL-IA-ADMIN-REVIEW-HISTORY`
- Read-only Admin page `/admin/review-history`.
- Read-only API `/api/v1/console/review-history`.
- Normalized history rows from signature, template, and exemption history tables.
- Filters by resource type, tenant, state, reviewer, risk, keyword, and decision time.
- Detail drawer showing immutable submitted version, decision, actor, reason, evidence reference, and lifecycle link.

## Out of Scope

- Creating or changing review decisions.
- Generic operation-log search.
- Export file generation.
