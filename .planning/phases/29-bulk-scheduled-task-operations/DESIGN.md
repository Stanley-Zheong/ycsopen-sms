# Phase 29 Design

- Backend service: `BulkScheduledTaskService`.
- API boundary: `BulkScheduledTaskController`.
- Data model: `bulk_sendings` stores task-level state/count/cost/snapshot; `bulk_sending_items` stores valid item linkage to protected `message_tasks`.
- UI pages: tenant bulk import, tenant scheduled tasks, admin bulk details, admin send jobs.
- Automation: unit tests for API calls and local Chrome Playwright for main user flows.
