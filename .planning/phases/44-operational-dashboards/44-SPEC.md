# Phase 44 Spec — Operational dashboards

## Scope

Package: `operational-dashboards`

Phase 44 delivers source-backed operational dashboard surfaces for platform and tenant users:

- Platform dashboard reads user, tenant, channel, warning, and Phase 34 aggregate sources.
- Tenant overview reads only the authenticated tenant scope and shows balance, usage, trial, contract, and service state.
- Resource statistics and channel comparison pages expose formula, freshness, permission scope, empty/error state, and accessible table data.
- API status monitoring exposes live source/freshness/impact/drill-down rows.
- Dashboard configuration stores role-specific visibility and refresh settings; tenant roles cannot enable global cards.

## Non-goals

- No new metric aggregation engine.
- No drag-and-drop dashboard layout implementation.
- No full browser matrix. Verification is local Google Chrome only.
- No replacement of existing complaint-ratio panels.

## Owned PRD obligations

- `OBL-F-1-5-B`
- `OBL-F-3-8-B`
- `OBL-F-4-5-B`
- `OBL-F-8-10-D`
- `OBL-F-11-1-B`
- `OBL-F-11-2-B`
- `OBL-F-11-3-A`
- `OBL-F-11-5-A`
- `OBL-F-11-5-B`
- `OBL-F-11-5-C`
- `OBL-F-11-6-A`
- `OBL-F-11-6-B`
- `OBL-F-11-6-C`
- `OBL-F-11-6-D`
- `OBL-F-11-10-A`
- `OBL-F-11-10-B`
- `OBL-F-11-10-C`
- `OBL-NFR-OBS-HEALTH`
- `OBL-DISPLAY-DASH-SOURCE`

## Acceptance boundary

Phase 44 is complete only when `TODO.md` has no unchecked scoped TODO and the verification commands recorded in `SUMMARY.md` have executable PASS evidence.
