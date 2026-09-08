# Phase 12 Decisions

- D12-001 — Reuse `TenantEligibilityPolicy.requireNewWorkAllowed` for tenant application eligibility.
- D12-002 — Reuse `ChannelCandidateEligibilityService` for usable channel exposure.
- D12-003 — Store review history in a phase-owned append-only table rather than adding generic review infrastructure.
- D12-004 — Filing retries are explicit operator actions in this phase; no scheduler is introduced.
- D12-005 — `SUPPLEMENT_REQUIRED` is a first-class signature audit status because PRD F-3.2 requires supplement feedback distinct from rejection.
- D12-006 — Browser verification remains local Google Chrome only.
