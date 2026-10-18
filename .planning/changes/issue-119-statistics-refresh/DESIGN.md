# Issue 119 Design

Schema migrations: declared

## Ownership

| Concern | Owner | Contract |
|---|---|---|
| Aggregate formulas and rebuild | `StatisticsAggregationService` | Delete and recreate one UTC source window atomically; store hourly bucket labels in Asia/Shanghai local time. |
| Automatic discovery | `StatisticsAggregationRefreshScheduler` and refresh state | Run at `ycsopen.statistics.refresh.fixed-delay` (default `5s`), lock the singleton pipeline row, re-scan an overlap window, rebuild all changed business dates plus today, and advance the watermark only after success. |
| Durable rejection fact | `MessageSubmitService`, `MessageRejectionRecorder`, `MessageAcceptanceIdempotencyService`, and `message_submit_claim_leases` | Commit the idempotency claim/lease in `REQUIRES_NEW`; execute accepted work while holding the lease-row lock in a bounded transaction; after controlled rejection has rolled back and released that lock, commit REJECTED through token-fenced lease-first CAS. Duplicate reads reproduce the controlled code, while expired non-terminal leases can be fenced and reclaimed. |
| Refresh truth | `statistics_refresh_checkpoints` | One successful row per business date records source window, source count, aggregate count, changed-at, and refreshed-at, including zero-source runs. |
| Dashboard classification | `OperationalDashboardService` | Read the Shanghai date, checkpoint, live source watermark, and aggregate values; return `NOT_REFRESHED`, `EMPTY`, `STALE`, or `FRESH`. |
| Browser presentation | `DashboardPage.tsx` | Never present absent aggregates as a true zero; show the state, source, date/time zone, timestamps, and stale/empty guidance. |

## Refresh Flow

1. The scheduler locks
   `statistics_refresh_state('STATISTICS_AGGREGATION')` with `FOR UPDATE`, then
   captures one UTC `scanEnd` no earlier than the durable watermark. This
   prevents an earlier-started concurrent tick or backward clock adjustment
   from moving the watermark backward.
   V6700 seeds its `scanned_through` at `1970-01-01T00:00:00`, so the first
   successful run discovers all retained source history, including the 216
   pre-existing simulator tasks described by the issue. No deployment-time
   “start now” shortcut is allowed.
2. It scans from the persisted watermark with a one-minute overlap. Changed
   task `updated_at`, delivery report `report_time`, billing `created_at`, and
   rejected-submit `updated_at` values map back to the source task/submit
   creation date and then to an Asia/Shanghai business date. The current
   business date is always included, so a successful empty run is observable.
3. Each date becomes a half-open UTC source window. The service deletes only
   that business date's three Phase 34 metrics by `bucket_date`, recreates them
   by reading the corresponding UTC source window, and upserts the date
   checkpoint in the same transaction. A manual UTC range converts both bounds
   to Shanghai wall-clock bucket bounds before its replacement delete. Source
   UTC predicates are never reused against Shanghai-local `bucket_start`.
4. After every date succeeds, the pipeline watermark advances to `scanEnd`.
   Any failure rolls back aggregates, checkpoints, and watermark together.
5. Manual rebuild takes the same pipeline lock, preserving serialization and
   its existing arbitrary half-open UTC window contract.

The overlap intentionally repeats work at the watermark boundary. Date rebuild
is replacement-based, so the repeat is safe and prevents equal-precision source
writes from being skipped.

`BillingService.confirm` and `BillingService.reverse` must update their owning
`message_tasks.updated_at` in the same transaction whenever a RESERVED billing
row transitions. This is the change-discovery invariant for an in-place billing
status change because `billing_records` has no `updated_at`. A failed/no-op
billing transition does not touch the task. Focused tests cover late CONFIRMED
and REVERSED transitions and the scheduler's original-date rebuild.

## Business Time

- Persistent source timestamps and scheduler watermarks are UTC-local database
  timestamps.
- JDBC reads and writes these timezone-free `DATETIME(6)` values as
  `LocalDateTime`; it does not use `Timestamp` conversions that depend on the
  Connector/J `serverTimezone` setting.
- Business date/time zone is fixed as `Asia/Shanghai`.
- `2026-10-06T15:59:59Z` belongs to Shanghai 2026-10-06; the next second belongs
  to 2026-10-07.
- Aggregate `bucket_start` is the Shanghai wall-clock hour and `bucket_date` is
  derived from that value. Dashboard queries pass the business date explicitly;
  they never depend on MySQL `CURRENT_DATE` or the container default zone.

## Dashboard State

| State | Meaning | Numeric presentation |
|---|---|---|
| `NOT_REFRESHED` | No successful checkpoint exists for the business date. | Aggregate-derived today values display `—`; guidance says the pipeline has not completed. |
| `EMPTY` | A successful checkpoint found zero task/rejected-submit source rows. | Values display `—`; guidance says the business date has no message data. |
| `STALE` | Live source changed after the checkpoint, or checkpoint age exceeds the configured maximum. | Last aggregate values remain visible only when the checkpoint recorded aggregate rows; a stale empty checkpoint still displays `—`. Both paths show a stale warning and timestamps. |
| `FRESH` | Successful checkpoint is current and has source rows. | Aggregate values, including legitimate zero success/failure components, display normally. |

The API adds `todayAggregation` and makes aggregate-derived today fields
nullable for absence/empty. Existing route and non-aggregate cards remain.
The maximum age is configured by `ycsopen.statistics.refresh.max-age` and
defaults to `5m`; it is evaluated from an injected UTC `Clock`.

Aggregate-derived platform fields are `realtime.todayMessages`,
`realtime.successRate`, `realtime.comparisonMessages`, `kpi.todaySend`,
`kpi.successRate`, `kpi.todayRevenue`, `hourlyTrend`, and `tenantRank`. The
live user/tenant/channel-health/finance-warning fields keep their existing
sources. For `NOT_REFRESHED` and `EMPTY`, every derived scalar is null and both
derived collections are empty. For `STALE`, they retain last-known values only
when `aggregateRowCount > 0`; a stale successful-empty checkpoint keeps every
derived value unavailable.

## Failure and Compatibility

- Concurrent scheduled/manual runs serialize on the pipeline row.
- A failed date leaves its previous aggregate/checkpoint and old watermark.
- A downgrade disables the new scheduler but keeps both additive tables. Old
  readers ignore them; a later forward deployment resumes safely.
- `MessageAcceptanceIdempotencyService.claim` runs in `REQUIRES_NEW`, so the
  unique `(tenant_id, submit_id)` owner and a random fenced lease token exist
  before business processing. The lease expires after
  `ycsopen.message.submit-claim-lease` (default `2m`). Duplicate claim always
  locks the lease row before reading the submit row. A non-expired QUEUED row is
  `REQUEST_IN_PROGRESS`; an expired QUEUED row atomically rotates its token and
  expiry and becomes the only recovery owner. ACCEPTED/REJECTED are terminal.
- `MessageSubmitService` uses `TransactionTemplate` for compliance, routing,
  task, billing, and outbox. Its first statement locks the lease row and verifies
  the token, so even an expired waiter cannot reclaim while a live attempt owns
  the business transaction. It renews expiry to the two-minute lease horizon;
  the transaction timeout is `ycsopen.message.submit-transaction-timeout`
  (default `60s`) and startup validation requires it to be at least one whole
  second and shorter than the claim lease. It does not update
  `message_submits` until a terminal outcome.
- On a controlled `BusinessException`, the callback throws it out of
  `TransactionTemplate`; Spring first rolls back task/billing/outbox work and
  releases the lease lock. The outer catch then calls
  `MessageRejectionRecorder` in `REQUIRES_NEW`. The recorder locks lease first,
  verifies the unchanged token/non-expired ownership, then CAS-updates the exact
  QUEUED submit to REJECTED and attaches safe resource IDs. A contender released
  from the old lock still sees a non-expired lease and cannot rotate the token;
  it may transiently receive REQUEST_IN_PROGRESS before the recorder commits.
  There is no suspended-transaction self-lock.
- On success, the lease-owning transaction marks the submit ACCEPTED last and
  commits all business writes atomically. On process crash or unknown runtime
  rollback, QUEUED remains until lease expiry; a same-digest retry then fences
  the old token and re-executes. On recorder failure, the business transaction
  rolls back, the persistence failure is returned, and the same lease-recovery
  path applies. No response claims a terminal outcome that did not commit.
- A same-digest replay of a REJECTED submit throws `BusinessException` with the
  stored controlled error code. A different digest remains
  `IDEMPOTENCY_CONFLICT`; accepted/in-progress semantics are unchanged.
- Rejected rows store a controlled business error code, never raw exception
  text, phone number, content, credentials, or request parameters.
- Unknown runtime failures roll back as before and are not mislabeled as a
  product rejection.

## Verification Ladder

- Migration: V6700 fresh/existing schema, all change-discovery source indexes,
  seed, and owner/version check; real-MySQL verification loads a selective data
  distribution and applies unhinted `EXPLAIN` to the production query shapes so
  the declared scheduler access paths cannot silently drift.
- Service: initial backlog, repeat idempotency, late old-date receipt, empty
  checkpoint, rollback/watermark, concurrency lock, rejection persistence, and
  Shanghai midnight boundary.
- Acceptance: routing/credit rejection survives the outer rollback with no
  task/outbox/billing; same/different-digest replays are deterministic; active
  QUEUED cannot be stolen; expired QUEUED is fenced/recovered; crash/runtime and
  recorder-failure paths remain recoverable.
- Dashboard: four states, nullable absence values including stale-after-empty,
  stale source detection, and explicit date parameter instead of `CURRENT_DATE`.
- React: stable selectors and all four state renderings plus response replacement.
- Chrome: NOT_REFRESHED, EMPTY, STALE, FRESH, and refresh replacement on the real React DOM.
- Regression: required backend/frontend/build/diff gates and PR CI/Docker lane.
