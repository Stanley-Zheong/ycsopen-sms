# Issue 121 Decisions

## DR-C121-001: Add A Tenant Resource Audit Lookup Index

### Status

Accepted

### Decision

Issue 121 may add one non-unique index to the Phase 06-owned
`privileged_operation_audits` table for the tenant API Key audit read path.
The index keys are `(tenant_id, resource_type, id)` so the existing equality
predicates and descending ID limit can use one bounded lookup. The change is
additive and does not alter the Phase 06 writer or row shape.

### Compatibility And Compensation

Mixed application versions can read and write the table before, during, and
after index creation. A downgrade keeps the index. After every tenant audit
reader is removed, a compensating migration may execute
`DROP INDEX idx_audit_tenant_resource_id ON privileged_operation_audits`.

## DR-C121-002: Throttle API Key Last-Used Writes

### Status

Accepted

### Decision

A successful full HTTP HMAC verification attempts one atomic
`last_used_time` update when the stored value is absent or older than one
minute. The update and cutoff both use MySQL `UTC_TIMESTAMP`, so a connection
session time zone cannot skew or suppress the throttle window. The stored UTC
`DATETIME` is projected as an ISO-8601 `Instant` with an explicit `Z` offset.
Invalid signatures never update it. The timestamp is operational telemetry
rather than part of the authentication decision, so a write failure is logged
without converting a valid request whose nonce has already been consumed into
an authentication failure. A later successful request repairs the timestamp.

## DR-C121-003: Reserve A Non-Conflicting Migration Namespace

### Status

Accepted

### Decision

The Issue 119 statistics refresh package owns `V6700-V6799`, including its
merged `V6700` migration. Issue 121 therefore owns `V6800-V6899`, and the
tenant API Key audit lookup index is introduced by `V6800`. The Issue 121
migration was renumbered before merge and before application in a shared
environment; no applied Flyway history is rewritten.
