# Phase 52 Decisions

## D-P52-001 — Performance baseline interpretation

Decision: the PRD’s 1,000 TPS peak baseline and 100,000/day daily baseline are independent acceptance baselines. The 100,000/day number is a minimum capacity-planning floor, not a maximum and not a replacement for 1,000 TPS.

Evidence:

- `docs/PRD.md` states the daily value is a minimum planning baseline and non-upper-limit.
- `EVIDENCE/performance-prd-baseline.log` records the exact matching PRD lines.

Consequence:

- Phase 52 must preserve the 1,000 TPS peak assertion.
- Phase 52 must preserve the 100,000/day baseline as a capacity-floor assertion.
- Neither value may be silently dropped or converted into the other.

## D-P52-002 — No new heavyweight load harness

Decision: use a focused local submit orchestration performance regression instead of adding a new distributed load framework.

Reason:

- Current implementation can be verified with existing Maven tooling.
- A new distributed harness would add operational complexity without improving the immediate repository-level correctness signal.

Boundary:

- This does not prove deployed multi-node capacity or provider-network behavior.
