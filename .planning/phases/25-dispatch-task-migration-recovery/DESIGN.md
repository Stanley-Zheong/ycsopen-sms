# Phase 25 Design

## Backend

- `dispatch_recovery_events`: records MIGRATED, RETRY_CREATED, and NO_BACKUP recovery actions.
- `channel_recovery_tests`: records small-traffic recovery test evidence.
- `DispatchTaskRecoveryService.inventory()`: returns operator inventory and a simple recovery state.
- `migrateReadyTask`: moves `message_tasks.channel_id` and `message_send_outbox.channel_id` together, records evidence, and returns existing migration on replay.
- `retryFailedTask`: reveals the protected recipient only through `MessageTaskProtectionAdapter.revealMobileForDispatch`, creates a new protected task, inserts a READY outbox row, and returns existing retry on replay.
- `resumeAfterRecovery`: checks the latest channel recovery test before moving PAUSED to NORMAL.

## Frontend

The existing channel health page gains two cards:

- Dispatch task migration and recovery.
- Small-traffic recovery test.

The page keeps Phase 11 visual style and does not add a separate route.
