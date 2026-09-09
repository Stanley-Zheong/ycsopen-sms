# Phase 25 Spec

## Scope

Phase 25 provides a focused dispatch recovery path:

1. Operators can inspect dispatch tasks that are PENDING/FAILED or have a claimed outbox state.
2. READY/PENDING tasks can migrate once from a non-routable channel to a routable fallback channel.
3. FAILED tasks can create one new PENDING retry attempt without overwriting the failed original attempt.
4. CLAIMED tasks with provider error evidence are quarantined as uncertain outcome and cannot be automatically migrated or retried.
5. No-backup cases record explicit evidence and leave the original task untouched.
6. Paused channels require a successful recovery test record before they can return to NORMAL routing eligibility.

## Obligation trace

| Obligation | Implementation | Verification |
| --- | --- | --- |
| OBL-F-4-7-C | `DispatchTaskRecoveryService.migrateReadyTask`, `/api/v1/console/dispatch-recovery/tasks/{taskId}/migrate`, channel health recovery panel | `DispatchTaskRecoveryServiceTest.migratesReadyPendingTaskToFallbackChannelOnce`, `dispatch-recovery.spec.ts` |
| OBL-F-4-7-D | recovery test table and resume API | `DispatchTaskRecoveryServiceTest.pausedChannelRequiresSuccessfulRecoveryTestBeforeResume`, `dispatch-recovery.spec.ts` |
| OBL-F-5-10-B | failed retry creates a new protected task and READY outbox row | `DispatchTaskRecoveryServiceTest.retryFailedTaskCreatesNewPendingAttemptWithoutOverwritingOriginal` |
| OBL-STATE-CHANNEL-RECOVER | resume requires latest successful recovery test | `DispatchTaskRecoveryServiceTest.pausedChannelRequiresSuccessfulRecoveryTestBeforeResume` |
| OBL-STATE-MESSAGE-RETRY | original FAILED attempt remains immutable while retry becomes PENDING | `DispatchTaskRecoveryServiceTest.retryFailedTaskCreatesNewPendingAttemptWithoutOverwritingOriginal` |
| OBL-EDGE-UPSTREAM-OUTAGE | inventory exposes migration/failover and uncertain timeout quarantine | `DispatchTaskRecoveryServiceTest.uncertainClaimedOutcomeCannotBeMigratedOrRetried`, `dispatch-recovery.spec.ts` |

## Acceptance rules

- TODO is complete only when every scoped TODO is checked with executable evidence.
- Migration is idempotent: a repeated request for the same task returns existing migration evidence instead of reselecting a channel.
- Retry is idempotent: a repeated retry returns the existing retry attempt.
- Recovery resume is fail-closed until successful test evidence exists.
- UI verification uses local Chrome only.
