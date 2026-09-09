# Phase 06 — Privileged data access and audit

## Intent

Provide the smallest complete security boundary for privileged console data: masked defaults, purpose-bound temporary reveal, append-protected administrative audit, and deduplicated security events.

## Scope

In scope: console audit capture/query, phone reveal for platform accounts, unusual-login/repeated-failure/bulk-export event ingestion, and the two desktop administration pages. Out of scope: generic alert delivery, export execution, business ledgers, archive policy, mobile layouts, and non-Chrome browser support.

## Behaviors

- `privileged-data-access-audit-01`: every authenticated non-login `/api/v1/console/**` request writes a structural, redacted `STARTED` record before controller code executes and finalizes it once with outcome and latency after completion. An unfinalized `STARTED` row remains searchable evidence of an interrupted terminal write. Records include actor, tenant, operation/resource, trusted client IP and trace ID; the runtime database account cannot update, delete, truncate, alter, or drop the audit store directly.
- `privileged-data-access-audit-02`: platform account phone values remain masked in ordinary responses; a current permission plus a nonblank purpose permits an explicit no-store reveal response and creates a linked audit row. The UI clears plaintext when closed.
- `privileged-data-access-audit-03`: unusual login, the lockout threshold, and a reusable bulk-export detector entry emit attributable security events under stable deduplication keys; authorized readers can filter them.
- `privileged-data-access-audit-04`: security-sensitive actions use exact current database permissions. This phase proves reveal and role-permission-change enforcement/audit and publishes one reusable audit API for the later listed modules; it does not fabricate modules that do not yet exist.

## Owned obligations

- OBL-F-14-1-A
- OBL-F-14-1-B
- OBL-PRIVILEGED-REVEAL-001
- OBL-F-14-2-A
- OBL-F-14-2-B
- OBL-PERMISSION-KEY-ACTIONS
- OBL-DISPLAY-MASKING

## Completion rule

Complete only when all seven obligations have executable evidence, independent and Claude reviews contain no BLOCKER/HIGH, the production UI validator passes, and `TODO.md` contains no unchecked item.
