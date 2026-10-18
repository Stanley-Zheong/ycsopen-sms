---
phase: 37-contract-pricing-postpaid
plan: 02
subsystem: trial-conversion-workbench
tags: [java, react, billing, trial, contract, playwright]
requires:
  - phase: 37-01
    provides: contract pricing and postpaid baseline
provides:
  - source-backed trial candidate workbench and analysis
  - tenant-bound adjustment and conversion commands
  - locked eligibility and atomic contract lifecycle transition
affects: [trial-operations, contract-pricing, tenant-overview]
requirements-completed: []
requirements-addressed: [REQ-F-2-5, REQ-F-2-9, PROJECT-STATE-MACHINE, PROJECT-CHAPTER-12-FLOW]
completion-policy: issue-scope-with-runtime-boundaries
---

# Phase 37 Plan 02 Summary

Issue #122 replaces free-form tenant and price entry with a filtered trial
workbench backed by persisted tenant, account, trial, message, complaint, and
price-book data. Operators can inspect trial-period analysis, adjust the
selected tenant's trial, and convert only a server-eligible tenant using an
active price.

Approval locks tenant/account, trial, and price in order, repeats eligibility,
and atomically inserts the contract while transitioning trial and tenant
lifecycle. HTTP result audit remains a separate fail-closed interceptor.

Focused backend, component, build, bundled Chromium, planning, and UI contract
checks pass. The production browser report is bound to implementation commit
`29dbc61246e3bff4efec8ddea3c2e172544092eb`. Full Maven process-reaping and
Docker-daemon boundaries are documented in `37-VERIFICATION.md`.
