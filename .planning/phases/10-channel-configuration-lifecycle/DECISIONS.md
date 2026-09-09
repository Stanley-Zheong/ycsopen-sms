# Decisions

## DR-10-001 — Extend existing channel storage and add immutable versions

### Status

Accepted

### Context

The baseline `channels` table already contains most PRD fields and protected
columns, while current readers use a smaller entity projection.

### Decision

Per D-10-002, retain the existing table and add only additive version metadata
and immutable configuration snapshots under SCHEMA-P10. Return explicit safe
DTOs and keep encrypted values at the persistence boundary.

### Consequences

- Existing channel/routing readers remain compatible during migration.
- Version rows provide a stable activation and rollback identity without
  retroactively changing historical prices.

### References

- `docs/PRD.md` sections 5.4 and 10.4
- `.planning/SCHEMA-OWNERSHIP.md` SCHEMA-P10

## DR-10-002 — Atomic in-process effective snapshot

### Status

Accepted

### Context

REQ-F-4-2 requires no-restart effect and an observable prior-version fallback
when a new configuration cannot load.

### Decision

Per D-10-003, activation validates and writes an immutable version, then swaps
the effective snapshot atomically in one process. A failed swap leaves the old
pointer active and records a safe retryable result. No distributed bus is
introduced.

### Consequences

- Consumers can identify the version used by a decision.
- Multi-instance propagation is explicitly outside this phase's evidence.

### References

- `.planning/ROADMAP.md` Phase 10 goal and REQ-F-4-2

## DR-10-003 — Provider-based dependency inventory

### Status

Accepted

### Context

REQ-F-4-4 requires discovering route, pool, filing, price, task, and other
declared references before mutation, while several owning modules are not yet
implemented.

### Decision

Per D-10-004, `ChannelDependencyInventoryService` calls explicit providers for
current route, pool, filing, price, and active-task references. A provider may
return an empty result when its owning table has no rows, but it cannot be
silently omitted; migration records the destination per dependency before the
offline guard can pass.

### Consequences

- The dependency contract can expand through an explicit provider registration
  without duplicating retirement rules in controllers.
- No generic workflow engine or hidden first-match fallback is introduced.

### References

- `docs/PRD.md` F-4.4 and section 10.4

## DR-10-004 — Desktop Chrome design boundary

### Status

Accepted

### Context

The project compatibility contract limits browser verification to installed
desktop Chrome.

### Decision

Per D-10-001, UI design and acceptance use only local desktop Chrome at
1440x900. No mobile layout, browser matrix, downloaded browser, or ChromeDriver
is added.

### Consequences

- UI evidence is one real browser path with explicit role/state assertions.
- Other browser compatibility is not claimed.

### References

- `docs/PRD.md` section 6.3
- `.planning/UI-TEST-CONTRACT.md`

## DR-10-005 — Current draft activation must bind configuration_version

### Status

Accepted

### Context

Activating the current mutable channel draft reads a payload and later swaps the
effective version. A concurrent edit between those two operations must not let
an outdated draft become effective.

### Decision

Current-draft activation includes both `effective_version_id` and
`configuration_version` in the guarded update. Retry and rollback use immutable
version payloads and therefore guard only the effective pointer plus OFFLINE
status.

### Consequences

- A changed draft returns `STALE_EXPECTED_VERSION` instead of activating stale
  data.
- Retry and rollback remain independent of unrelated mutable draft edits.

### References

- `ChannelConfigurationVersionService.activatePayload`
- `ChannelConfigurationHotReloadTest.activationRejectsDraftChangedAfterPayloadRead`
