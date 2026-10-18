# Issue 119 Reviews

## Entry Review

| Criterion ID | Verdict | Evidence | Required closure |
|---|---|---|---|
| ENTRY-119-SCOPE-OWNER | PASS | `SPEC.md` keeps the amendment within Issue 119's Phase 34 aggregation and Phase 44 dashboard surfaces. `DESIGN.md` now names the scheduler, aggregate, rejection recorder/idempotency, checkpoint, dashboard, and browser owners; polling, a mutating refresh endpoint, and unrelated delivery/billing semantics remain excluded. | Preserve these boundaries during implementation. |
| ENTRY-119-SCHEMA-BOOTSTRAP | PASS | `SCHEMA-C119` allocates the next V6700 range after V6600 and limits ownership to additive Issue 119 refresh/checkpoint and submit-claim lease state. The two claims depend on their actual V4300 and V3200 owners; the epoch singleton seed, lease table, enable-after-expand order, and non-destructive downgrade rules are explicit. | Keep V6700 additive and prove the refresh seed, lease constraints/indexes, and both fresh and existing-schema application. |
| ENTRY-119-REFRESH-SERIALIZATION | PASS | One captured UTC `scanEnd`, singleton `FOR UPDATE`, one-minute overlap, whole-date replacement, checkpoint-in-date-transaction, watermark-after-all-dates, and the shared scheduled/manual lock form a coherent serialized and retry-idempotent flow. The first scan explicitly includes retained history and current date. | Preserve this ordering and the old-watermark rollback invariant. |
| ENTRY-119-REJECTION-TRANSACTION | PASS | The controlled `BusinessException` now exits `TransactionTemplate`, so Spring actually rolls back task/billing/outbox work and releases the lease lock before the outer catch invokes `MessageRejectionRecorder` in `REQUIRES_NEW`. The recorder then follows lease-before-submit order, verifies the unchanged token and non-expired owner, and commits `REJECTED`; no suspended transaction retains the row lock. Same/different-digest replay and terminal non-overwrite remain explicit. | Preserve the rollback-before-recorder boundary and prove it with real Spring transactions rather than mocks alone. |
| ENTRY-119-QUEUED-RECOVERY | PASS | V6700 adds a dedicated lease with configurable two-minute expiry and random fencing token. Business work renews and holds the lease in a transaction whose default timeout is 60 seconds; startup rejects a timeout not shorter than the lease. A released waiter therefore sees a live lease during recorder handoff, while expired `QUEUED` atomically rotates the token; terminal rows cannot be reclaimed. Crash, runtime rollback, recorder failure, handoff, and invalid-configuration cases are all declared. | Preserve lease-first ordering, token verification on every terminal write, and the validated timeout-before-expiry invariant. |
| ENTRY-119-LATE-BILLING | PASS | `DR-119-008` now makes every successful `RESERVED` to `CONFIRMED`/`REVERSED` transition advance the owning `message_tasks.updated_at` in the same transaction, while failed/no-op transitions do not. `C-119-AUTO` explicitly covers both late directions and scheduler rebuild of the original business date. | Prove the task touch and billing transition share one rollback boundary and that both directions are discovered. |
| ENTRY-119-BUSINESS-TIME | PASS | Scheduled source reads use half-open UTC windows and delete aggregates by Shanghai `bucket_date`; manual UTC ranges convert to Shanghai-local bucket bounds. The design forbids reusing UTC source predicates against local `bucket_start`, fixes Asia/Shanghai independent of host defaults, and requires both sides of 16:00Z in service/dashboard tests. | Keep source-read and aggregate-delete parameters separate in implementation and assertions. |
| ENTRY-119-FOUR-STATE | PASS | `NOT_REFRESHED`, `EMPTY`, `STALE`, and `FRESH` now have server-owned meanings and numeric behavior. The stale-age owner is `ycsopen.statistics.refresh.max-age`, default `5m`, evaluated from an injected UTC `Clock`; the complete aggregate-derived scalar/trend/rank set and the unaffected live fields are enumerated. | Prove both live-source-newer and age-expired stale paths plus nullable/empty collection behavior. |
| ENTRY-119-UI-INVENTORY | PASS | `UI-ELEMENTS.md` inventories the status/source facts, Shanghai date/zone, timestamps, every aggregate-derived scalar, trend/rank, and refresh action with stable selectors, one route, permissions, four-state behavior, and atomic obligation/Playwright links. | Keep the implemented DOM and response replacement aligned to this inventory. |
| ENTRY-119-TEST-VERIFICATION | PASS | `C-119-REJECTION` now requires actual business rollback before token-fenced recording, no self-deadlock, lock-wait handoff, active/expired `QUEUED`, token rotation, terminal non-reclaim, crash/runtime/recorder recovery, and fail-fast invalid timeout/lease configuration. The refresh, billing, time, dashboard, React, four-state Chrome, exact validator, full-suite, build, diff, and Docker boundaries remain complete and mutually consistent. | Execute the declared focused and full gates and record exact evidence/boundaries before implementation or merge is claimed complete. |

### Verdict

PASS. The Issue 119 amendment is implementation-ready: scope and ownership,
V6700 compatibility, serialized refresh, fenced acceptance/rejection recovery,
late billing discovery, UTC/Shanghai boundaries, four-state dashboard, UI
inventory, and executable verification contracts have no remaining BLOCKER or
HIGH finding. This verdict does not replace implementation review, executed
verification, pre-push review, pull-request CI, or merge evidence.

## Implementation Review

| Finding | Initial severity | Closure |
|---|---|---|
| Connector/J could shift UTC-local `DATETIME` values when configured with `serverTimezone=Asia/Shanghai`. | BLOCKER | All Issue 119 JDBC timestamp reads use `LocalDateTime`; Phase 01 MySQL deliberately combines the Shanghai driver setting with a UTC session and verifies lease and Shanghai-midnight results. |
| A stale checkpoint with zero aggregate rows could expose misleading zeros/old values. | HIGH | Backend and frontend require `aggregateRowCount > 0` before stale values are available; H2, React, and Playwright cover stale-after-empty separately from stale-with-data. |
| Mock-only transaction tests did not prove rollback-before-recorder behavior. | HIGH | `MessageSubmitTransactionIntegrationTest` uses Spring transaction interception and a real `DataSourceTransactionManager`; aggregation rollback also proves aggregate/checkpoint/watermark atomicity. |
| Scheduler source scans lacked declared range indexes. | MEDIUM | V6700 adds six discovery/bucket indexes; migration tests assert all six and Phase 01 MySQL runs unhinted production-shaped `EXPLAIN` over selective fixture data. |
| Concurrent refresh test did not force lock contention. | MEDIUM | Real MySQL now holds the singleton row from an independent connection and proves both automatic and manual operations remain blocked until commit, then complete. |
| Phase 01 evidence inputs did not bind the new harness, migration, configuration, and implementation owners. | MEDIUM | `SERVICE_INPUTS` now includes Phase 03 harness/script, V6700, application configuration, and the statistics/dashboard/idempotency/recorder owners. |
| Optional frontend metadata could disguise an API contract regression, and retained-data refresh failure/retry was not executable. | MEDIUM | `todayAggregation` is required; React and Playwright cover STALE retained data, pending refresh, 503/error feedback, old-value retention, button recovery, explicit retry, and FRESH replacement. |
| Sub-second transaction timeout rounding could outlive the claim lease. | LOW | Configuration now rejects a timeout below one second and still requires it to be strictly shorter than the lease; unit coverage includes 900ms/950ms. |

Verdict: PASS. Every BLOCKER/HIGH/MEDIUM/LOW finding was closed and independently
re-reviewed. Focused backend, frontend, build, and static discovery checks pass;
real MySQL, installed Chrome, full portable suites, and Docker remain PR CI gates.

## Pre-Push Review

PASS. Backend and frontend reviewers independently re-read the closure diff and
reported no remaining finding. The final local semantic diff preserves Issue
119 scope, V6700 ownership, server-owned state semantics, stable selectors, and
the rollback/lease/serialization invariants. Local runtime boundaries are
recorded in `VERIFICATION.md` and `QUALITY-GATEWAY.md`; merge remains conditional
on required PR CI.
