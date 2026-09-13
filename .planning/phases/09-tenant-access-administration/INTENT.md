# Intent

## Status

Closed

## Goal

Tenant administrators can delegate tenant-scoped access, developers can manage
revocable HTTP/CMPP credentials, and no credential or subaccount path crosses a
tenant boundary.

## Deliverables

- Completed: Tenant administrator/subaccount API and role isolation — Evidence: `EVIDENCE/OBL-F-1-3-A.json`, `EVIDENCE/OBL-F-1-3-B.json`, `EVIDENCE/OBL-F-2-7-A.json`
- Completed: HTTP API-key policy, one-time secret handoff, encryption, and revocation — Evidence: `EVIDENCE/OBL-F-2-6-A.json`, `EVIDENCE/OBL-F-2-6-B.json`
- Completed: CMPP credential request, safe metadata, encryption, and revocation — Evidence: `EVIDENCE/OBL-F-2-6-C.json`
- Completed: Complete credential data contract and no-plaintext proof — Evidence: `EVIDENCE/OBL-DATA-10-6-ACCESS.json`
- Completed: Three Chrome-only production pages with documented test IDs and real Playwright cases — Evidence: `EVIDENCE/ui-contract.json`, `EVIDENCE/playwright-execution.json`
- Completed: Independent GSD and Claude reviews pass; scoped TODO query is empty — Evidence: `09-VERIFICATION.md`, `09-REVIEW.md`, `CLAUDE-REVIEW.md`

## Plans

1. `09-01-PLAN.md` — Tenant subaccounts, tenant roles, and isolation.
2. `09-02-PLAN.md` — HTTP API-key management and revocation.
3. `09-03-PLAN.md` — Downstream CMPP credential management.
4. `09-04-PLAN.md` — Tenant access UI and real Chrome acceptance.

## Verification

Planned commands are the exact backend/frontend suites in the plan files,
real installed-Chrome Playwright, the production UI validator, the phase entry
validator, and the scoped TODO query. No duration or schedule estimate is used.
