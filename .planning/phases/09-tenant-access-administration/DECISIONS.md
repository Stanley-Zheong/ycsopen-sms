# Decisions

## DR-09-001 — Reuse existing identity and credential tables

### Status

Accepted

### Context

Phase 05 already supplies tenant users, tenant roles, membership, JWT identity,
and authorization. The baseline schema already contains API/CMPP credential
columns; only missing management metadata needs an additive migration.

### Decision

Per D-09-002, reuse existing identity and credential tables and require every
operation to resolve tenant ownership server-side. Add no second auth/session or
credential store.

### Consequences

- Existing readers remain compatible and cross-tenant predicates are centralized.
- Existing legacy entity mappings must be completed without serializing entities.

### References

- `.planning/SCHEMA-OWNERSHIP.md` SCHEMA-P09
- `docs/PRD.md` sections 3.2, 5.1 F-1.3, 5.2 F-2.6/F-2.7, 10.6

## DR-09-002 — One-time protected secret handoff

### Status

Accepted

### Context

The PRD requires API/CMPP secret protection and safe credential management,
while the user requires a practical integration handoff.

### Decision

Per D-09-003, generate secrets with `SecureRandom`, store Phase 03 envelopes,
return plaintext only in the successful create response, and return masked
metadata for every later list/detail/read. There is no reveal or recovery API.

### Consequences

- A lost handoff requires creating a new credential; the old secret cannot be
  recovered through the console.
- Evidence must scan responses, logs, database values, and audit summaries.

### References

- `docs/PRD.md` section 6.2.1 and 10.6
- `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/TenantRegistrationProtectionAdapter.java`

## DR-09-003 — Status plus synchronous revocation event

### Status

Accepted

### Context

The roadmap requires live revocation, but current credential authentication
already performs a database status lookup and no protocol session exists yet.

### Decision

Per D-09-004, guard the state transition transactionally, persist `DISABLED`,
and publish one small `TenantCredentialRevokedEvent` after commit. No event bus,
workflow engine, cache invalidation framework, or CMPP session is introduced.

### Consequences

- Current lookups see revocation immediately.
- Future protocol consumers can subscribe to the stable event without changing
  the Phase 09 data contract.

### References

- `.planning/ROADMAP.md` Phase 09 in-scope/exit gate
- `docs/PRD.md` sections 9.1, 9.2, 10.6

## DR-09-004 — Chrome-only desktop UI

### Status

Accepted

### Context

The project compatibility decision explicitly narrows acceptance to the local
desktop Google Chrome installation.

### Decision

Per D-09-001, use only installed Google Chrome at 1440x900 and do not add
mobile layout or other browser tests.

### Consequences

- UI evidence is a single real-browser path with role/permission states.
- A browser matrix is intentionally absent from plans and evidence.

### References

- `docs/PRD.md` section 6.3
- `.planning/UI-TEST-CONTRACT.md`

