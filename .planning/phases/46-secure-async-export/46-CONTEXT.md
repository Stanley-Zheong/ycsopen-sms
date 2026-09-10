# Phase 46 CONTEXT

Phase 46 depends on existing export-request handoffs from earlier phases. Those handoffs did not produce a final file and intentionally named `secure-async-export` as the future owner.

Current implementation choice:

- Reuse the existing `export_tasks` table instead of introducing a parallel job table.
- Store immutable authorization/source snapshots and encrypted artifact bytes in the repository database for the first secure implementation.
- Keep existing page APIs compatible while adding Phase 46-specific job creation.
- Validate with current local Google Chrome only.

Out-of-scope work remains for object-store deployment, archive/retention and final release-wide Chinese/export acceptance.
