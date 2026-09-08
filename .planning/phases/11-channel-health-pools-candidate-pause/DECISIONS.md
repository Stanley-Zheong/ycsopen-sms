# Phase 11 Decisions

## D-11-001: Candidate eligibility is a shared service

`ChannelCandidateEligibilityService` becomes the single source for whether a
channel may receive new work. UI, pools, and `ChannelSelector` consume the same
decision shape to avoid parallel route eligibility rules.

## D-11-002: Health failure creates maintenance, manual/risk creates pause

Health-check failure and planned maintenance use `MAINTENANCE`; manual,
complaint, and ratio triggers use `PAUSED`. Both states are candidate
ineligible.

## D-11-003: No durable in-flight migration in Phase11

Phase11 proves immediate exclusion from new route candidates only. Durable
already-owned task migration is intentionally left to the dispatch-task phase.

## D-11-004: Pool semantics are only weighted or primary-backup

The API rejects ambiguous pool modes. Weighted mode requires positive weights.
Primary-backup mode requires exactly one primary. Disabled members are retained
for audit/configuration continuity but are ineligible.

## D-11-005: Chrome-only UI verification

Production browser evidence uses installed local Google Chrome only. The phase
does not add Safari, Edge, Firefox, downloaded Chrome, or mobile validation.

## D-11-006: Use JdbcTemplate for Phase11 health/pool tables

Phase11 stores health observations, pause events, pools, and pool members in
simple additive tables. The implementation uses JdbcTemplate in the service
layer instead of adding JPA entity/repository classes for these tables. This
keeps the slice smaller while preserving typed DTOs, transactions, MySQL
migrations, and focused tests.

## D-11-007: Actor evidence comes from authentication

Pause and maintenance APIs do not trust editable client input for actor
evidence. `ChannelHealthController` rewrites the actor from the authenticated
principal before calling the service; the UI shows this as a read-only note.

## D-11-008: Maintenance validation uses microsecond timestamp boundaries

Maintenance end must prove a successful health sample after maintenance begins.
V2003 upgrades Phase11 timestamp boundaries to `DATETIME(6)` and adds `RESUME`
event support so MySQL behaves consistently with the Java/H2 focused tests.
