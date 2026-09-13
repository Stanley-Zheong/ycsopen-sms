# Phase 49 Decisions

## D1 — No distributed termination framework

Decision: use one transactional service to converge local database state and write compensation counts.

Reason: the current system is a database-backed implementation; adding a queue/distributed transaction layer would not improve ROI for this phase.

## D2 — Existing eligibility fence remains source of truth for new work

Decision: do not duplicate global ingress rules. `TenantEligibilityPolicy` already denies TERMINATED lifecycle for message/signature/template new work.

Reason: one lifecycle fence is easier to verify and avoids inconsistent gateway logic.

## D3 — Qualification-expiry reason maps to existing enum

Decision: use existing `EXPIRED_CREDENTIALS` termination reason for "资质到期未续".

Reason: it satisfies the PRD meaning without risky enum churn.
