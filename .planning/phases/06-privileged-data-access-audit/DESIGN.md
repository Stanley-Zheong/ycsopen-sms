# Design

Schema migrations: declared

## Backend boundary

`OperationAuditInterceptor` writes a sanitized `STARTED` record before authenticated console controller code runs, so audit-storage failure blocks the operation. Completion calls a `SQL SECURITY DEFINER` procedure that permits exactly one `STARTED`-to-terminal transition; a terminal-write outage therefore leaves visible `STARTED` evidence instead of losing the operation. V1500 creates the Phase 06-owned canonical `privileged_operation_audits` store; the unused legacy `operation_logs` table is neither changed nor dual-written because it belongs to the legacy schema owner. The summary contains route/method and safe parameter names, never parameter or body values.

Flyway and runtime credentials are separate. After migration, `RuntimeDatabaseGrantCallback` removes schema-wide runtime grants, grants ordinary tables only their DML needs, and limits the audit/event tables to `SELECT/INSERT`; the runtime account receives only `EXECUTE` on the terminal procedure. It cannot update/delete/truncate/drop the audit table or disable its triggers. The migration account remains outside the application datasource.

`PrivilegedDataController` requires the live API and full-account-scope authorities (`privileged:data:reveal:api` and `identity:accounts:all`) or the platform administrator role, plus a stated purpose. The UI action additionally requires `privileged:data:reveal`. The purpose/audit service is colocated with the encrypted phone store so its raw decrypt method is package-private; outside callers can only use masked reads. The response uses `Cache-Control: no-store`, and the linked audit never includes plaintext.

`SecurityEventService` inserts stable deduplication keys and verifies every persisted field on duplicate instead of using `INSERT IGNORE`; bulk-export comparison includes the detected record count. Event results use the controlled `DETECTED`, `BLOCKED`, `SUCCESS`, and `FAILURE` set shared with API validation and UI filters. Phase 5 unusual-login and lockout decision points call it; later export code calls the same `recordBulkExport` boundary when Phase 46 exists.

`TrustedProxyClientIpResolver` is shared by login anomaly detection, generic audit, and reveal. It accepts exactly one numeric `X-Forwarded-For` value only when the direct peer is loopback, matching the documented same-host Nginx deployment. Nginx overwrites the header with `$remote_addr`; non-loopback clients and append-style proxy chains cannot spoof forwarding headers.

## UI boundary

Two desktop pages reuse the Phase 2 shell: `/admin/system/logs` and `/admin/system/security-events`. All page controls, actions, rows, conditional states, pagination, detail elements, and reveal-dialog elements have 53 documented literal test IDs. Platform accounts keep masked phone text and add a reveal action only for authorized users. The purpose dialog never pre-fills or persists plaintext and closes/clears it explicitly.

## Visual source boundary

Pencil Desktop was not installed or connectable through the local MCP transport. The phase therefore preserves the approved Phase 2/5 `.pen` visual baseline as an unchanged source asset and makes the phase-specific interaction truth explicit in the clickable HTML prototype and UI contract. No software or browser is downloaded for this limitation.

## Failure policy

- Audit payloads are structural and bounded before persistence.
- Audit-start failure prevents controller execution; terminal-write failure leaves a searchable `STARTED` row.
- Reveal fails closed on missing authority, purpose, ciphertext, or decrypt error.
- Security-event duplicate insertion is idempotent; unrelated database errors are not swallowed.
- Query endpoints expose redacted records only and enforce current read/all-scope authorities.
