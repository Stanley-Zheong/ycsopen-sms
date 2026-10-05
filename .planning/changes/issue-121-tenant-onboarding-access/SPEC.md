# Issue 121 Tenant Onboarding And API Access

GitHub issue #121 joins two existing owners: Phase 08 qualification approval and
Phase 09 tenant API credentials. It corrects the safe approval projection,
retires the configuration placeholder, completes the API Key page states, and
adds tenant-scoped credential audit readback. HTTP authentication and CMPP are
unchanged.

## Behavior

| Change obligation | Existing owner obligations | Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- | --- | --- |
| OBL-ISSUE-121-APPROVAL-RESULT | OBL-F-2-2-B, OBL-F-2-8-A | issue-121-approval-result | Approval returns and displays tenant ID, tenant number, trial quota, and trial validity without password or App Secret fields. | Focused Java and Chrome tests assert the allowlisted result. |
| OBL-ISSUE-121-API-KEY-LIFECYCLE | OBL-F-2-6-A, OBL-F-2-6-B | issue-121-api-key-lifecycle | The configuration entry reaches one API Key page where `TENANT_ADMIN` and `TENANT_DEV` create, list, and revoke own-tenant credentials. | Chrome follows `/tenant/config`, creates one key, acknowledges the secret, observes a masked row, and revokes it. |
| OBL-ISSUE-121-SECRET-SAFETY | OBL-F-2-6-B | issue-121-secret-safety | Create-only `appSecret` is structurally separate from list-only `appSecretMask`, is absent from logs/audit/later responses, and remains visible while the mounted page handles unrelated refresh failures. Explicit acknowledgement clears it during that page lifetime; navigation, reload, tab close, or unmount discards the in-memory value without persistence or recovery. | Java response/string tests and browser assertions find plaintext only in the one-time dialog and prove both acknowledgement and page-lifecycle disposal. |
| OBL-ISSUE-121-TENANT-AUDIT | OBL-F-2-6-A, OBL-F-2-6-B, OBL-F-14-1-B | issue-121-tenant-audit | Create and revoke append queryable, redacted, tenant-scoped audit records. | A two-tenant backend test and Chrome audit table prove isolation and readback. |
| OBL-ISSUE-121-PAGE-STATES | OBL-F-2-6-A, OBL-F-2-6-B | issue-121-page-states | The API Key page exposes stable loading, empty, service-error/retry, denied, populated, mutation-pending, success, and mutation-error states. | Vitest and Chrome exercise each state and keep the table header visible. |

## Authorization And Audit Contract

- Qualification approval remains governed by the existing
  `tenant:qualification:review` permission; Issue 121 adds no review authority.
- API Key list, create, revoke, and audit endpoints admit only
  `ROLE_TENANT_ADMIN` and `ROLE_TENANT_DEV`. The service resolves the tenant from
  the authenticated database user and never accepts tenant identity from a
  request field.
- `GET /api/v1/console/tenant/api-keys/audits` returns at most the newest 100
  `TENANT_API_KEY` records for the current tenant, ordered by descending audit
  ID. Each row contains only `id`, `actor`, `operation`, `resourceId`, `result`,
  and `occurredAt`; it has no secret, request body, IP address, trace, or tenant
  selector. A 403 produces the page denied state. Other failures remain local
  to the audit region and can be retried without hiding credential data.

## Verification Boundary

Chrome coverage runs the real React routes in Google Chrome and uses controlled
API responses for deterministic UI state coverage. Java tests prove database
tenant predicates, audit selection, response shape, and secret redaction. The
full Maven and frontend suites remain non-regression gates.
