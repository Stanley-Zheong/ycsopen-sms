# Phase 33 Spec

## Goal

A matching uplink creates exactly one tenant suppression and one linked unsubscribe evidence record.

## In scope

- Global and tenant unsubscribe keyword configuration.
- Deterministic keyword normalization and match rules.
- Atomic tenant blacklist insertion through existing protected list writer.
- One linked unsubscribe evidence row per uplink record.
- Optional tenant notification through configured UNSUBSCRIBE webhook destination.
- Tenant evidence search and export request.
- Admin evidence search, statistics, and abnormal unsubscribe-rate alert event.

## Out of scope

- Generic uplink transport.
- Export file creation.
- Alert delivery.
- Mobile runtime or mobile UI.

## Acceptance

- The Phase33 TODO set is empty.
- Backend targeted and full tests pass.
- Frontend unit/build and local Chrome Playwright pass.
- PRD obligation validator passes for owner `unsubscribe-compliance`.
- UI contract validator passes for Phase 33 production.
- Review boundary is recorded before commit.
