# Decisions

## DR-07-001 — Closed registry instead of arbitrary configuration

Only code-registered keys are accepted. This makes validation, defaults, sensitivity, and runtime consumption reviewable and prevents the page becoming an unbounded key/value store.

## DR-07-002 — Single-node atomic reload

Phase 07 validates a complete immutable snapshot before one `AtomicReference` swap. Cross-node delivery, event buses, and distributed locks are excluded because they are not required by the owned obligation.

## DR-07-003 — Secret references only

Secret-classified settings store an allow-listed external reference syntax and return a masked display value. This phase never accepts, persists, resolves, logs, or displays secret material.

## DR-07-004 — Rollback creates history

Rollback copies a historical snapshot into a new attributable version and activates it. It never mutates or re-labels the historical record.

## DR-07-006 — Change-set API and server-owned secret preservation

Clients submit only changed keys. The server merges against the authoritative active snapshot and writes a complete new version. A masked display token is never accepted as a configuration value, so editing a non-sensitive key cannot erase or disclose a secret reference.

## DR-07-007 — Apply only after a successful database commit

Runtime prepare occurs before the transaction; the state transition commits inside `TransactionTemplate`; atomic apply occurs only after `execute` returns. A `PENDING` reload state plus startup rehydration makes a process interruption observable and recoverable without distributed coordination.

## DR-07-005 — Pencil baseline limitation

Pencil Desktop/MCP is unavailable in the current environment. `design-output/platform-system-configuration.pen` is an unchanged approved Phase 06 baseline retained for traceability; it is not represented as a newly rendered Phase 07 design. The checksum-bound HTML prototype and `07-UI-SPEC.md` are the Phase 07 interaction truth.

## DR-07-008 — Version-monotonic runtime and database lifecycle guard

Runtime snapshot apply accepts only a higher version, so a delayed older activation cannot overwrite a newer committed activation. The version table rejects DELETE and rejects every UPDATE except the service's explicit draft, activation, supersede, reload-rejection, and pending-to-applied transitions. This closes silent drift and immutable-history claims without adding locks, brokers, or a generic configuration framework.

## DR-07-009 — One isolated real-service Chrome harness

The acceptance harness starts disposable MySQL, the actual Spring application, and Vite on loopback ports, then drives the locally installed Google Chrome. It uses seeded database identities and browser offline mode instead of platform API interception or production fault endpoints. Other browsers and a reusable multi-service orchestration framework remain out of scope.

## DR-07-010 — Append-only registry compatibility and stable failure codes

A newly registered key defaults into older stored snapshots before validation, checksum, reload, stage, or rollback. Persisted keys unknown to the running binary fail closed; removing or renaming a key requires an explicit compatibility migration. Configuration mutation responses expose `data.errorCode`, and the web client classifies stale/reload outcomes only by that code, never by translated message text.

## DR-07-011 — Bounded operational history

The configuration page deliberately returns the newest 50 immutable version records. Phase 07 needs recent rollback and audit visibility, not an unbounded reporting browser; pagination belongs to a later history/reporting owner if required. This bound is documented rather than silently presented as complete history.
