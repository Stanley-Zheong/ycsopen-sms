# Phase 47 CONTEXT

Phase 47 builds on Phase 46 secure async exports and keeps the archive implementation in the same repository/service boundary.

Important constraints:

- Chrome-only UI verification.
- No browser downloads.
- No mobile surface.
- Do not implement an external object store just to satisfy a local phase; archive artifacts are encrypted inside `archive_manifests` for this implementation slice.
- Retention scan SQL must use a fixed whitelist of known source tables, not arbitrary client-provided SQL.
