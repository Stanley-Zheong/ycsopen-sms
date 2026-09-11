# Phase 43 Intent

Build the smallest useful custom report authoring slice:

- Make supported report fields explicit.
- Prevent users from querying unsupported or unauthorized aggregate columns.
- Show source formula/freshness/quality beside results.
- Preserve immutable snapshots for saved definitions and export requests.

The implementation intentionally avoids a general BI/query-builder platform.
