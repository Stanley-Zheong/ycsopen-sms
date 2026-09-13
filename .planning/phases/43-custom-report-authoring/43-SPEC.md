# Phase 43 Spec — Custom report authoring

## Scope

Package: `custom-report-authoring`

Phase 43 delivers custom report authoring for `REQ-F-11-4` only:

- Users select supported dimensions/measures from a declared aggregate registry.
- Preview results come from `statistics_aggregates`.
- Results expose formula, formula version, freshness, quality state, drill-down key, and an accessible table.
- Saved definitions and export requests preserve immutable definition snapshots.

## Non-goals

- No new aggregation engine.
- No dashboard framework.
- No export file creation.
- No cross-browser validation beyond local Chrome.

## Acceptance boundary

The phase is complete only when the scoped TODO list is empty and verification evidence exists for the two owned obligations:

- `OBL-F-11-4-A`
- `OBL-F-11-4-B`
