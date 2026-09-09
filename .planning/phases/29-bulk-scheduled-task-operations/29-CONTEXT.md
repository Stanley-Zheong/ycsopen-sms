# Phase 29 Context

Package: `bulk-scheduled-task-operations`

Phase 29 owns bulk API, import preview, scheduled task state/control, admin bulk details, admin send job search, batch state-machine evidence, and the bulk task/item data model.

Implementation reuses the existing single-message acceptance path for valid bulk items instead of creating another compliance/risk/routing/billing pipeline.
