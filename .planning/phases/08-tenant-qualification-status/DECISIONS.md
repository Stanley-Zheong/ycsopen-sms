# Decisions

## DR-08-001 — Three routes, one shared form

Use `/tenant/register`, `/tenant/qualification`, and `/admin/tenants`. Registration and recertification reuse one form model; Admin review, edit, and status operations are drawers/dialogs on the list route. No detail-route tree, batch operations, export, mobile surface, or browser matrix is added.

## DR-08-002 — Reuse account status

`tenant_accounts.status` is the sole operating switch. Certification and commercial lifecycle keep their existing distinct meanings; Phase 08 does not add a competing operational enum to `tenants`.

## DR-08-003 — Narrow OCR provider, human decision remains authoritative

Implement a configured HTTP inspection port and real local sandbox test. Persist only safe extraction facts. Approval requires both completed inspection and explicit human confirmation; OCR never becomes an automatic admission engine.

## DR-08-004 — Internal evidence proxy

Review evidence is returned through an authenticated, audited, no-store application endpoint that internally issues and consumes a one-time object capability. Object IDs, tokens, and storage locations never cross the browser boundary.

## DR-08-005 — Cross-owner Phase 03 size correction

The Phase 08 catalog requires each legal-representative identity image to accept up to 10 MiB, while the Phase 03 target currently says 5 MiB. Phase 08 may update only those two closed target limits and their existing tests/docs. It does not change encryption format, media allowlists, object states, token rules, or storage architecture.

## DR-08-006 — Eligibility policy, no future-module stubs

One policy guards the current HTTP submit service and is the required dependency for later signature/template and ingress owners. Phase 08 does not add fake signature/template/CMPP/batch APIs merely to test absence; those creation APIs do not exist today, so no uncertified tenant can call them.

## DR-08-007 — Two new stores only

Use one contact-challenge table and one immutable qualification-event ledger. Do not create per-action tables, a workflow engine, an event bus, a document service, or a second audit pipeline.

## DR-08-008 — Configured platform-message adapter

Contact codes require a working Phase 04 SPI implementation. Add one small HTTP adapter configured from deployment secrets and exercise it against a local provider sandbox. Do not build SMS routing, billing, or retry orchestration into registration.
