# Issue 119 Decisions

## DR-119-001: Persist Successful Zero-Row Refreshes

Aggregate absence cannot distinguish “not run” from “ran and found no data”.
Store one checkpoint for every successfully rebuilt business date, including
zero-row results. The dashboard classifies state from this checkpoint instead
of `COALESCE(SUM(...), 0)` alone.

## DR-119-002: Use One Locked Watermark With Overlap

A singleton refresh-state row is the serialization point for scheduled and
manual rebuilds. Automatic discovery overlaps the saved watermark by one minute
and replaces whole affected dates. This covers timestamp precision ties and
late changes without a queue or per-write aggregation coupling.

The automatic run captures its scan end only after obtaining the singleton
lock and clamps it to at least the persisted watermark. Concurrent ticks and a
backward wall-clock adjustment therefore cannot regress durable progress.

V6700 seeds the watermark at the Unix epoch, so first deployment scans all
retained history. Scheduler delay defaults to five seconds. A checkpoint older
than `ycsopen.statistics.refresh.max-age` (default five minutes) is stale even
when no newer source row is visible.

## DR-119-003: Freeze Asia/Shanghai as the Business Zone

Source storage remains UTC. Phase 34 owns conversion to Asia/Shanghai before
forming hourly/date buckets, and Phase 44 obtains today's business date from the
same zone. Database/container defaults cannot change dashboard semantics.

The UTC-local database columns are timezone-free MySQL `DATETIME(6)` values.
JDBC therefore reads and writes them as `LocalDateTime`, avoiding implicit
Connector/J conversion through the configured server time zone.

## DR-119-004: Make Aggregate Absence Explicit

The platform response requires a server-owned `todayAggregation` state object;
its absence is an API contract failure, not `NOT_REFRESHED`. Today aggregate
fields are nullable for `NOT_REFRESHED` and `EMPTY`. `STALE` retains last-known
values only when the checkpoint recorded aggregate rows; stale-after-empty also
remains nullable. This is preferable to preserving a compatible but false zero.

## DR-119-005: Record Only Claimed Business Rejections

After a request wins the `(tenant_id, submit_id)` claim, a controlled
`BusinessException` from compliance, routing, quota, or credit is persisted as
`REJECTED` after the owning transaction rolls back. Pre-claim tenant/integrity
failures and unexpected runtime failures are not counted. The row retains the
request digest and controlled error code for safe idempotent replay.

The idempotency claim and fenced lease commit first in `REQUIRES_NEW`.
`MessageSubmitService` locks/verifies that lease and runs accepted work with
`TransactionTemplate`. A controlled rejection exits that template so its work
rolls back and releases the lease lock; only then does a distinct
`MessageRejectionRecorder` lock the lease, verify the same token, and commit
REJECTED. Accepted work marks ACCEPTED last. All contenders lock lease before
submit. Same-digest replay throws the stored error code; different digest
remains an idempotency conflict.

QUEUED leases expire after a configurable two-minute default. An expired retry
atomically rotates the token and becomes the only recovery owner; a live worker
holds the lease lock, so it cannot be stolen merely because wall time elapsed.
The business transaction defaults to a 60-second timeout and configuration must
keep it at or above Spring's one-second timeout granularity and below the lease
horizon. Thus controlled rollback leaves a non-expired
fencing interval for the synchronous outer recorder, and a released duplicate
cannot rotate the token during that handoff.
This recovers process crashes, unknown-runtime rollbacks, and recorder failures.
Recorder failure is surfaced and cannot create a false terminal response.

## DR-119-007: Keep UTC Source and Business-Bucket Predicates Separate

Source reads use half-open UTC windows. Scheduled replacement deletes by the
target `bucket_date`; manual range replacement converts UTC boundaries to
Shanghai wall-clock bucket boundaries. No query compares UTC source boundaries
directly with local `bucket_start`.

Rejected submissions retain `created_at` as their business-bucket source but
use the V6700 `updated_at` column for change discovery and freshness. This
prevents a controlled rejection committed near the claim timeout from falling
behind the scheduler's overlap window.

## DR-119-008: Billing Transitions Touch Their Task Watermark

`billing_records` has only `created_at`, while CONFIRMED/REVERSED are in-place
updates. A successful `BillingService.confirm` or `reverse` therefore advances
the owning `message_tasks.updated_at` in the same transaction. Scheduler
discovery already maps that task back to its original business date. This
explicit invariant avoids an unbounded table comparison and is covered for both
late transition directions.

## DR-119-009: Fence and Reclaim Non-Terminal Submit Claims

V6700 adds `message_submit_claim_leases` without changing the Phase 23 submit
schema. Each claim has a random token, expiry, and updated timestamp. Business
processing locks and verifies the token; duplicate/recovery processing uses the
same lease-first lock order. Only expired QUEUED can rotate the token. Terminal
submits are never reclaimed. This makes committed-first claims recoverable
without permitting two task writers.

## DR-119-006: Register a New Forward Migration Owner

Phase 34 owns V4300-V4399, but deployed history already reaches V6600. Adding
V4301 would require out-of-order Flyway behavior and would not reliably run on
upgraded installations. Issue 119 therefore owns only the additive refresh
checkpoint prefix in V6700-V6799; `statistics_aggregates` remains Phase 34-owned.
The repository allocator selected V6700.
