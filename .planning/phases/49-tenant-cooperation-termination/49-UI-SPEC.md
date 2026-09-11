# Phase 49 UI Spec

## Page

`admin-tenant-terminations` at `/admin/tenant/terminations`.

## Regions

- request card: tenant id, reason, evidence, submit;
- clearance card: finance state, prepaid refund item, postpaid settlement item, refresh action;
- approval card: status, opinion, approve action, effect action;
- request table: request records and detail action;
- participant table: machine-readable participant state;
- timeline: retained audit/evidence.

## Selector contract

Selectors are listed in UI-ELEMENTS.md and are stable for automated test generation.
