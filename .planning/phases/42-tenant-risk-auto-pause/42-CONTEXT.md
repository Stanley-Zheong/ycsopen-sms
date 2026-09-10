# Phase 42 Context

Package: `tenant-risk-auto-pause`

This phase implements tenant-level risk warning and auto-pause after complaint case management is available.

Dependency facts:

- Phase 34 provides source-backed statistics registry concepts.
- Phase 35 provides alert records.
- Phase 40 proves episode-style financial warning and enforcement.
- Phase 41 records complaint attribution and remediation evidence.
- Existing `TenantEligibilityPolicy` rejects new work when `tenants.lifecycle_status` is not `TRIAL` or `SIGNED`; therefore auto-pause can reuse `FROZEN` instead of changing the message submission path.

Scope boundary:

- In scope: tenant risk rule configuration, source snapshot evaluation, unknown data behavior, one episode per source key, auto-pause, reviewed recovery, console UI, executable tests.
- Out of scope: generic rules engine, new source aggregate jobs, new notification transport, non-Chrome browser certification.
